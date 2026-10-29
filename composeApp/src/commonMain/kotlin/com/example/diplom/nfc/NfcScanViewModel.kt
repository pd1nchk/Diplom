package com.example.diplom.nfc

import com.example.diplom.api.AttendanceSessionDto
import com.example.diplom.api.NfcMarkRequestDto
import com.example.diplom.api.SessionRosterRowDto
import com.example.diplom.api.StartAttendanceSessionRequestDto
import com.example.diplom.api.StopAttendanceSessionRequestDto
import com.example.diplom.api.TeacherLessonCheckDto
import com.example.diplom.attendance.AttendanceRepository
import com.example.diplom.domain.UserRole
import com.example.diplom.ui.platform.NfcBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.diplom.time.nowEpochMillis
import com.example.diplom.time.nowEpochMillis
import com.example.diplom.time.parseUtcIsoToEpochMillis

enum class NfcTeacherPhase {
    Idle,
    CheckingLesson,
    NoLesson,
    ReadyForSession,
    StartingSession,
    SessionActive,
    Error,
}

enum class NfcStudentPhase {
    Idle,
    Reading,
    ConfirmPending,
    Success,
    Error,
}

data class NfcScanUiState(
    val role: UserRole,
    val roomUidDraft: String = "",
    val teacherPhase: NfcTeacherPhase = NfcTeacherPhase.Idle,
    val teacherHint: String = "",
    val teacherError: String? = null,
    val teacherCheck: TeacherLessonCheckDto? = null,
    val showTeacherStartDialog: Boolean = false,
    val activeSession: AttendanceSessionDto? = null,
    val roster: List<SessionRosterRowDto> = emptyList(),
    val sessionRemainingSeconds: Long? = null,
    val studentPhase: NfcStudentPhase = NfcStudentPhase.Idle,
    val studentMessage: String = "",
    val pendingSession: AttendanceSessionDto? = null,
    val pendingUid: String? = null,
    val showStudentConfirm: Boolean = false,
    /** Одноразовое событие для Snackbar */
    val snackbar: String? = null,
)

/**
 * NFC-экран: единая точка сетевых вызовов и таймеров.
 *
 * Соответствие сценарию из ТЗ (sealed state → фазы):
 * - **Idle** — `NfcTeacherPhase.Idle` / `NfcStudentPhase.Idle`
 * - **CheckingLesson** — `NfcTeacherPhase.CheckingLesson`
 * - **Ready** — `NfcTeacherPhase.ReadyForSession` + диалог
 * - **SessionActive** — `NfcTeacherPhase.SessionActive`
 * - **Scanning** — `NfcStudentPhase.Reading` / `ConfirmPending`
 * - **Success** — `NfcStudentPhase.Success`
 * - **Error** — `NfcTeacherPhase.Error` / `NfcStudentPhase.Error`
 * - **Expired** — таймер до 0 → сброс в Idle + подсказка
 * - **NoLesson** — `NfcTeacherPhase.NoLesson` (empty state в UI)
 */
class NfcScanViewModel(
    private val repo: AttendanceRepository,
    private val ticket: String,
    val role: UserRole,
) {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main)

    private val _ui = MutableStateFlow(initialUi())
    val ui: StateFlow<NfcScanUiState> = _ui.asStateFlow()

    private var pollTeacherJob: Job? = null
    private var countdownJob: Job? = null
    private var studentUidJob: Job? = null
    private var teacherRoomUidJob: Job? = null
    private var lastTeacherNfcAt = 0L

    private fun initialUi(): NfcScanUiState {
        val sMsg = if (role == UserRole.TEACHER) {
            ""
        } else {
            "Приложите метку аудитории или введите UID и подтвердите присутствие."
        }
        return NfcScanUiState(
            role = role,
            teacherPhase = NfcTeacherPhase.Idle,
            teacherHint = if (role == UserRole.TEACHER) "Загрузка…" else "",
            studentMessage = sMsg,
        )
    }

    init {
        if (role == UserRole.TEACHER) {
            pollTeacherJob = scope.launch {
                while (isActive) {
                    refreshTeacherSessionSilently()
                    delay(5_000)
                }
            }
            scope.launch {
                refreshTeacherSessionSilently()
                val existing = _ui.value.activeSession
                if (existing != null) {
                    _ui.value = _ui.value.copy(
                        teacherPhase = NfcTeacherPhase.SessionActive,
                        teacherHint = "Сессия уже открыта. Студенты могут отмечаться.",
                    )
                    restartCountdown(existing)
                } else {
                    _ui.value = _ui.value.copy(teacherPhase = NfcTeacherPhase.CheckingLesson)
                    delay(250)
                    checkTeacherLesson(auto = true)
                }
            }
            teacherRoomUidJob = scope.launch {
                NfcBridge.lastUid
                    .map { it }
                    .distinctUntilChanged()
                    .collect { raw ->
                        if (raw.isNullOrBlank()) return@collect
                        if (_ui.value.activeSession != null) return@collect
                        if (_ui.value.teacherPhase == NfcTeacherPhase.StartingSession) return@collect
                        val normalized = NfcUidNormalizer.canonicalColonUid(raw)
                            ?: raw.trim().uppercase().takeIf { it.isNotBlank() }
                            ?: return@collect
                        val t = nowEpochMillis()
                        if (t - lastTeacherNfcAt < 1600L) return@collect
                        lastTeacherNfcAt = t
                        _ui.value = _ui.value.copy(roomUidDraft = normalized, teacherError = null)
                        checkTeacherLesson(auto = true)
                        NfcBridge.clearLastUid()
                        delay(180)
                        if (_ui.value.activeSession == null &&
                            _ui.value.teacherCheck != null &&
                            (_ui.value.teacherPhase == NfcTeacherPhase.ReadyForSession || _ui.value.showTeacherStartDialog)
                        ) {
                            startSessionFromDialog()
                        }
                    }
            }
        } else {
            studentUidJob = scope.launch {
                NfcBridge.lastUid
                    .map { it }
                    .distinctUntilChanged()
                    .collect { raw ->
                        if (raw.isNullOrBlank()) return@collect
                        val normalized = NfcUidNormalizer.canonicalColonUid(raw)
                            ?: raw.trim().uppercase()
                        onStudentTagRead(normalized)
                    }
            }
        }
    }

    fun dispose() {
        pollTeacherJob?.cancel()
        countdownJob?.cancel()
        studentUidJob?.cancel()
        teacherRoomUidJob?.cancel()
        job.cancel()
    }

    fun consumeSnackbar() {
        if (_ui.value.snackbar != null) {
            _ui.value = _ui.value.copy(snackbar = null)
        }
    }

    fun onRoomUidChange(value: String) {
        _ui.value = _ui.value.copy(roomUidDraft = value.uppercase())
    }

    fun dismissTeacherStartDialog() {
        _ui.value = _ui.value.copy(
            showTeacherStartDialog = false,
            teacherCheck = null,
            teacherPhase = if (_ui.value.activeSession != null) {
                NfcTeacherPhase.SessionActive
            } else {
                NfcTeacherPhase.Idle
            },
            teacherHint = if (_ui.value.activeSession != null) {
                "Сессия активна. Студенты отмечаются через NFC."
            } else {
                "Нажмите «Проверить», чтобы снова запросить расписание."
            },
        )
    }

    fun dismissStudentConfirm() {
        _ui.value = _ui.value.copy(
            showStudentConfirm = false,
            pendingSession = null,
            pendingUid = null,
            studentPhase = NfcStudentPhase.Idle,
            studentMessage = "Приложите метку аудитории или введите UID и подтвердите присутствие.",
        )
    }

    fun checkTeacherLessonAgain() {
        scope.launch { checkTeacherLesson(auto = false) }
    }

    private suspend fun checkTeacherLesson(auto: Boolean) {
        if (role != UserRole.TEACHER) return
        val roomRaw = _ui.value.roomUidDraft.trim().takeIf { it.isNotEmpty() }
        val roomForApi = roomRaw?.let { NfcUidNormalizer.canonicalColonUid(it) ?: it }
        if (!auto) {
            _ui.value = _ui.value.copy(
                teacherPhase = NfcTeacherPhase.CheckingLesson,
                teacherError = null,
                teacherHint = "Проверяем расписание и аудиторию…",
            )
        }
        val check = withContext(Dispatchers.Default) {
            repo.teacherLessonCheck(ticket, roomForApi)
        }
        if (check == null) {
            _ui.value = _ui.value.copy(
                teacherPhase = NfcTeacherPhase.NoLesson,
                teacherHint = "",
                teacherError = null,
                teacherCheck = null,
                showTeacherStartDialog = false,
            )
            if (!auto) {
                emitSnack("Сейчас нет активной пары по расписанию или метка аудитории не совпала.")
            }
            return
        }
        _ui.value = _ui.value.copy(
            teacherPhase = NfcTeacherPhase.ReadyForSession,
            teacherHint = if (check.phase == "IN_PROGRESS") {
                "Пара идёт сейчас — можно начать отметку присутствия."
            } else {
                "Скоро начало пары — можно открыть сессию заранее."
            },
            teacherCheck = check,
            teacherError = null,
            showTeacherStartDialog = true,
        )
    }

    fun requestTeacherLessonCheck() {
        scope.launch { checkTeacherLesson(auto = false) }
    }

    fun startSessionFromDialog() {
        val lessonId = _ui.value.teacherCheck?.lessonId ?: return
        scope.launch {
            _ui.value = _ui.value.copy(
                teacherPhase = NfcTeacherPhase.StartingSession,
                showTeacherStartDialog = false,
                teacherHint = "Открываем сессию…",
            )
            val session = withContext(Dispatchers.Default) {
                repo.startAttendanceSession(
                    ticket,
                    StartAttendanceSessionRequestDto(lessonId = lessonId, durationMinutes = 20),
                )
            }
            if (session == null) {
                _ui.value = _ui.value.copy(
                    teacherPhase = NfcTeacherPhase.Error,
                    teacherError = "Не удалось начать сессию (пара завершена, нет доступа или сеть).",
                    teacherHint = "",
                )
                emitSnack(_ui.value.teacherError ?: "Ошибка")
                return@launch
            }
            val roster = withContext(Dispatchers.Default) {
                repo.sessionRoster(ticket, session.sessionId)
            }
            _ui.value = _ui.value.copy(
                teacherPhase = NfcTeacherPhase.SessionActive,
                activeSession = session,
                roster = roster,
                teacherCheck = null,
                teacherHint = "Сессия активна. Студенты отмечаются через NFC.",
            )
            emitSnack("NFC-сессия открыта")
            restartCountdown(session)
        }
    }

    fun stopTeacherSession() {
        val sid = _ui.value.activeSession?.sessionId ?: return
        scope.launch {
            val ok = withContext(Dispatchers.Default) {
                repo.stopAttendanceSession(ticket, StopAttendanceSessionRequestDto(sessionId = sid))
            }
            if (ok) {
                countdownJob?.cancel()
                _ui.value = _ui.value.copy(
                    teacherPhase = NfcTeacherPhase.Idle,
                    activeSession = null,
                    roster = emptyList(),
                    sessionRemainingSeconds = null,
                    teacherHint = "Сессия закрыта.",
                )
                emitSnack("Сессия завершена")
            } else {
                emitSnack("Не удалось завершить сессию")
            }
        }
    }

    private suspend fun refreshTeacherSessionSilently() {
        if (role != UserRole.TEACHER) return
        val session = withContext(Dispatchers.Default) { repo.activeSessionForTeacher(ticket) }
        val roster = if (session != null) {
            withContext(Dispatchers.Default) { repo.sessionRoster(ticket, session.sessionId) }
        } else {
            emptyList()
        }
        val hadSession = _ui.value.activeSession != null
        val phase = when {
            session != null -> NfcTeacherPhase.SessionActive
            hadSession -> NfcTeacherPhase.Idle
            else -> _ui.value.teacherPhase
        }
        _ui.value = _ui.value.copy(
            activeSession = session,
            roster = roster,
            teacherPhase = if (session != null) NfcTeacherPhase.SessionActive else phase,
            sessionRemainingSeconds = session?.let { remainingSeconds(it.expiresAtIso) },
        )
        if (session != null) {
            if (countdownJob == null || countdownJob?.isActive != true) {
                restartCountdown(session)
            }
        } else {
            countdownJob?.cancel()
        }
    }

    private fun restartCountdown(session: AttendanceSessionDto) {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            while (isActive) {
                val s = _ui.value.activeSession ?: break
                val rem = remainingSeconds(s.expiresAtIso) ?: 0L
                _ui.value = _ui.value.copy(sessionRemainingSeconds = rem.coerceAtLeast(0))
                if (rem <= 0L) {
                    _ui.value = _ui.value.copy(
                        teacherPhase = NfcTeacherPhase.Idle,
                        activeSession = null,
                        roster = emptyList(),
                        sessionRemainingSeconds = 0L,
                        teacherHint = "Время сессии истекло.",
                    )
                    emitSnack("Сессия NFC завершена по таймеру")
                    break
                }
                delay(1_000)
            }
        }
    }

    private fun remainingSeconds(expiresAtIso: String): Long? {
        val expMs = parseUtcIsoToEpochMillis(expiresAtIso) ?: return null
        return (expMs - nowEpochMillis()) / 1000
    }

    private fun emitSnack(text: String) {
        _ui.value = _ui.value.copy(snackbar = text)
    }

    // --- Student ---

    private fun onStudentTagRead(uid: String) {
        if (role != UserRole.STUDENT) return
        if (_ui.value.showStudentConfirm) return
        scope.launch {
            _ui.value = _ui.value.copy(
                studentPhase = NfcStudentPhase.Reading,
                studentMessage = "Проверяем занятие…",
                roomUidDraft = uid,
            )
            val session = withContext(Dispatchers.Default) { repo.activeSessionForStudent(ticket) }
            if (session == null) {
                NfcBridge.clearLastUid()
                _ui.value = _ui.value.copy(
                    studentPhase = NfcStudentPhase.Error,
                    studentMessage = "Сейчас нет открытой NFC-сессии для вашей группы.",
                )
                emitSnack("Нет активной сессии преподавателя")
                return@launch
            }
            _ui.value = _ui.value.copy(
                studentPhase = NfcStudentPhase.ConfirmPending,
                studentMessage = "Подтвердите присутствие на занятии.",
                pendingSession = session,
                pendingUid = uid,
                showStudentConfirm = true,
            )
            NfcBridge.clearLastUid()
        }
    }

    fun studentManualOpenConfirm() {
        val uid = NfcUidNormalizer.canonicalColonUid(_ui.value.roomUidDraft)
            ?: _ui.value.roomUidDraft.trim().uppercase().takeIf { it.isNotEmpty() }
            ?: return
        onStudentTagRead(uid)
    }

    fun confirmStudentPresence() {
        val session = _ui.value.pendingSession ?: return
        val uid = _ui.value.pendingUid ?: return
        scope.launch {
            _ui.value = _ui.value.copy(studentPhase = NfcStudentPhase.Reading, studentMessage = "Отправляем отметку…")
            val result = withContext(Dispatchers.Default) {
                repo.markByNfc(
                    NfcMarkRequestDto(
                        sessionId = session.sessionId,
                        cardUid = uid,
                        studentTicket = ticket,
                    ),
                )
            }
            _ui.value = _ui.value.copy(
                showStudentConfirm = false,
                pendingSession = null,
                pendingUid = null,
            )
            when {
                result.success -> {
                    _ui.value = _ui.value.copy(
                        studentPhase = NfcStudentPhase.Success,
                        studentMessage = "${result.studentName ?: "Студент"}: присутствие зафиксировано",
                    )
                    emitSnack("Отметка NFC принята")
                    delay(1_400)
                    _ui.value = _ui.value.copy(
                        studentPhase = NfcStudentPhase.Idle,
                        studentMessage = "Приложите следующую метку",
                    )
                }
                else -> {
                    val msg = mapStudentMarkError(result.code, result.message)
                    _ui.value = _ui.value.copy(
                        studentPhase = NfcStudentPhase.Error,
                        studentMessage = msg,
                    )
                    emitSnack(msg)
                }
            }
        }
    }

    private fun mapStudentMarkError(code: String?, message: String): String = when (code) {
        "wrong_group" -> "У вас нет занятия в этой сессии (другая группа или предмет)."
        "session_expired", "expired" -> "Сессия преподавателя уже закрыта или истекла."
        "duplicate", "already_marked" -> "Вы уже отмечены на этом занятии."
        "invalid_tag", "unknown_card" -> "Метка не распознана. Обратитесь к администратору для регистрации карты."
        "network" -> "Сеть: $message"
        else -> message.ifBlank { "Не удалось подтвердить присутствие" }
    }
}
