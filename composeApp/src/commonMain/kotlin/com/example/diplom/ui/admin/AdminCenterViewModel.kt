package com.example.diplom.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.diplom.admin.AdminCatalogRepository
import com.example.diplom.admin.AdminScheduleRepository
import com.example.diplom.api.AdminCatalogSnapshotDto
import com.example.diplom.api.AdminCourseCreateRequestDto
import com.example.diplom.api.AdminCourseGroupsRequestDto
import com.example.diplom.api.AdminCourseUpdateRequestDto
import com.example.diplom.api.AdminLessonCreateRequestDto
import com.example.diplom.api.AdminLessonSummaryDto
import com.example.diplom.api.AdminLessonUpdateRequestDto
import com.example.diplom.api.AdminRescheduleRequestDto
import com.example.diplom.api.AdminRoomDto
import com.example.diplom.api.AdminScheduleRefsDto
import com.example.diplom.api.ForgotPasswordAdminItemDto
import com.example.diplom.time.epochMillisToAppZoneYmd
import com.example.diplom.time.nowEpochMillis
import com.example.diplom.time.parseUtcIsoToEpochMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

enum class AdminNavSection(val label: String) {
    DASHBOARD("Обзор"),
    SCHEDULE("Расписание"),
    USERS("Пользователи"),
    CATALOG("Каталог"),
    NFC_ROOMS("NFC"),
}

enum class ScheduleSub { LESSONS, RESCHEDULES }

enum class UsersSub { STUDENTS, TEACHERS, PASSWORDS }

enum class CatalogSub { SUBJECTS, GROUPS, FACULTIES, DEPARTMENTS, SPECIALTIES }

enum class LessonKindOption(val api: String, val label: String) {
    LECTURE("LECTURE", "Лекция"),
    SEMINAR("SEMINAR", "Семинар"),
    LAB("LAB", "Лабораторная"),
}

data class LessonEditorState(
    val lessonId: Long? = null,
    val courseId: Long? = null,
    val groupId: Long? = null,
    val lessonDate: LocalDate = todayInMinsk(),
    val startHour: Int = 9,
    val startMinute: Int = 0,
    val endHour: Int = 10,
    val endMinute: Int = 30,
    val room: String = "",
    val kind: LessonKindOption = LessonKindOption.LECTURE,
)

data class AdminCenterUiState(
    val nav: AdminNavSection = AdminNavSection.DASHBOARD,
    val scheduleSub: ScheduleSub = ScheduleSub.LESSONS,
    val usersSub: UsersSub = UsersSub.STUDENTS,
    val catalogSub: CatalogSub = CatalogSub.SUBJECTS,
    val globalRefreshing: Boolean = false,
    val globalSearch: String = "",
    val scheduleRefs: AdminScheduleRefsDto? = null,
    val scheduleRefsBanner: String? = null,
    val lessons: List<AdminLessonSummaryDto> = emptyList(),
    val lessonsBanner: String? = null,
    val catalogError: String? = null,
    val roomsError: String? = null,
    val rooms: List<AdminRoomDto> = emptyList(),
    val catalog: AdminCatalogSnapshotDto = AdminCatalogSnapshotDto(),
    val snackbar: String? = null,
    val subjectSearch: String = "",
    val scheduleFilterCourseId: Long? = null,
    val lessonSearch: String = "",
    val editingCourseId: Long? = null,
    val editCourseNameDraft: String = "",
    val editCourseTeacherId: Long? = null,
    val editCourseGroupIds: Set<Long> = emptySet(),
    val pendingDeleteCourseId: Long? = null,
    val showNewCourseDialog: Boolean = false,
    val newCourseName: String = "",
    val newCourseTeacherId: Long? = null,
    val showLessonEditor: Boolean = false,
    val lessonEditor: LessonEditorState = LessonEditorState(),
    val nfcSelectedRoomId: Long? = null,
    val nfcMessage: String? = null,
    val nfcFilterUnlinkedOnly: Boolean = false,
    val userInputs: UserFormInputs = UserFormInputs(),
    val forgotPasswordPending: Int = 0,
    val reschedulePending: Int = 0,
)

data class UserFormInputs(
    val fullName: String = "",
    val ticket: String = "",
    val course: String = "1",
    val groupNum: String = "1",
    val specialtyId: Long? = null,
    val facultyName: String = "",
    val deptName: String = "",
    val deptFacultyId: Long? = null,
    val specCode: String = "",
    val specName: String = "",
    val specProfile: String = "",
    val specFacultyId: Long? = null,
    val teacherName: String = "",
    val teacherDegree: String = "",
    val teacherDepartmentId: Long? = null,
)

class AdminCenterViewModel(
    private val adminTicket: String,
) : ViewModel() {
    private val scheduleRepo = AdminScheduleRepository()
    private val catalogRepo = AdminCatalogRepository()
    private val _ui = MutableStateFlow(AdminCenterUiState())
    val ui: StateFlow<AdminCenterUiState> = _ui.asStateFlow()

    init {
        refreshAll()
    }

    fun dismissSnackbar() = _ui.update { it.copy(snackbar = null) }

    fun setNav(nav: AdminNavSection) = _ui.update { it.copy(nav = nav) }

    fun setScheduleSub(sub: ScheduleSub) = _ui.update { it.copy(scheduleSub = sub) }

    fun setUsersSub(sub: UsersSub) = _ui.update { it.copy(usersSub = sub) }

    fun setCatalogSub(sub: CatalogSub) = _ui.update { it.copy(catalogSub = sub) }

    fun setGlobalSearch(q: String) = _ui.update { it.copy(globalSearch = q) }

    fun navigateLegacy(name: String) {
        when (name) {
            "SUBJECTS" -> _ui.update { it.copy(nav = AdminNavSection.CATALOG, catalogSub = CatalogSub.SUBJECTS) }
            "SCHEDULE" -> _ui.update { it.copy(nav = AdminNavSection.SCHEDULE, scheduleSub = ScheduleSub.LESSONS) }
            "RESCHEDULE_REQUESTS" -> _ui.update { it.copy(nav = AdminNavSection.SCHEDULE, scheduleSub = ScheduleSub.RESCHEDULES) }
            "GROUPS" -> _ui.update { it.copy(nav = AdminNavSection.CATALOG, catalogSub = CatalogSub.GROUPS) }
            "TEACHERS" -> _ui.update { it.copy(nav = AdminNavSection.USERS, usersSub = UsersSub.TEACHERS) }
            "NFC" -> _ui.update { it.copy(nav = AdminNavSection.NFC_ROOMS) }
            "USERS" -> _ui.update { it.copy(nav = AdminNavSection.USERS, usersSub = UsersSub.STUDENTS) }
            "PASSWORDS", "FORGOT", "SYSTEM" -> _ui.update { it.copy(nav = AdminNavSection.USERS, usersSub = UsersSub.PASSWORDS) }
            else -> AdminNavSection.entries.find { it.name == name }?.let { setNav(it) }
        }
    }

    fun setSubjectSearch(q: String) = _ui.update { it.copy(subjectSearch = q) }

    fun setLessonSearch(q: String) = _ui.update { it.copy(lessonSearch = q) }

    fun setScheduleFilterCourse(id: Long?) = _ui.update { it.copy(scheduleFilterCourseId = id) }

    fun setUserInputs(transform: UserFormInputs.() -> UserFormInputs) =
        _ui.update { s -> s.copy(userInputs = s.userInputs.transform()) }

    fun setNfcRoom(id: Long?) = _ui.update { it.copy(nfcSelectedRoomId = id) }

    fun setNfcMessage(m: String?) = _ui.update { it.copy(nfcMessage = m) }

    fun setNfcFilterUnlinkedOnly(v: Boolean) = _ui.update { it.copy(nfcFilterUnlinkedOnly = v) }

    fun refreshAll() {
        viewModelScope.launch {
            _ui.update { it.copy(globalRefreshing = true) }
            val refsLoad = scheduleRepo.listScheduleRefs(adminTicket)
            val lessonsLoad = if (refsLoad.refs != null) scheduleRepo.listLessons(adminTicket)
            else com.example.diplom.api.AdminLessonsLoad(emptyList(), null)
            val roomsLoad = scheduleRepo.listRooms(adminTicket)
            val catResult = runCatching { catalogRepo.snapshot(adminTicket) }
            val cat = catResult.getOrNull() ?: AdminCatalogSnapshotDto()
            val forgotCount = runCatching { catalogRepo.forgotPasswordRequests("PENDING").size }.getOrDefault(0)
            val rescheduleCount = runCatching { catalogRepo.rescheduleRequests("PENDING").size }.getOrDefault(0)
            _ui.update { s ->
                val refs = refsLoad.refs
                val merged = if (refs != null && s.editingCourseId != null) {
                    val c = refs.courses.find { it.id == s.editingCourseId }
                    if (c != null) {
                        s.copy(
                            editCourseNameDraft = c.name,
                            editCourseTeacherId = c.teacherUserId.takeIf { it > 0 }
                                ?: refs.teachers.firstOrNull { it.fullName == c.teacherFullName }?.userId
                                ?: refs.teachers.firstOrNull()?.userId,
                            editCourseGroupIds = c.linkedGroupIds.toSet(),
                        )
                    } else s
                } else s
                merged.copy(
                    globalRefreshing = false,
                    scheduleRefs = refs,
                    scheduleRefsBanner = refsLoad.error,
                    lessons = lessonsLoad.lessons,
                    lessonsBanner = lessonsLoad.error,
                    rooms = roomsLoad.rooms,
                    roomsError = roomsLoad.error,
                    catalog = cat,
                    catalogError = catResult.exceptionOrNull()?.message,
                    forgotPasswordPending = forgotCount,
                    reschedulePending = rescheduleCount,
                    lessonEditor = merged.lessonEditor.copy(
                        room = merged.lessonEditor.room.ifBlank { roomsLoad.rooms.firstOrNull()?.name.orEmpty() },
                    ),
                )
            }
        }
    }

    fun lessonsTodayCount(): Int {
        val today = epochMillisToAppZoneYmd(nowEpochMillis())
        return _ui.value.lessons.count { l ->
            parseUtcIsoToEpochMillis(l.startIso)?.let { epochMillisToAppZoneYmd(it) == today } == true
        }
    }

    fun unlinkedRoomsCount(): Int = _ui.value.rooms.count { it.nfcUid.isNullOrBlank() }

    fun openNewLessonEditor() {
        _ui.update {
            it.copy(
                showLessonEditor = true,
                lessonEditor = LessonEditorState(room = it.rooms.firstOrNull()?.name.orEmpty()),
            )
        }
    }

    fun openEditLesson(lesson: AdminLessonSummaryDto) {
        val ms = parseUtcIsoToEpochMillis(lesson.startIso)
        val endMs = parseUtcIsoToEpochMillis(lesson.endIso)
        val date = ms?.let { epochMillisToAppZoneYmd(it) }?.let { y -> LocalDate(y.year, y.month, y.day) } ?: todayInMinsk()
        val startSecs = ms?.let { ((it / 1000 + 3 * 3600) % 86_400 + 86_400) % 86_400 } ?: 9 * 3600
        val endSecs = endMs?.let { ((it / 1000 + 3 * 3600) % 86_400 + 86_400) % 86_400 } ?: 10 * 3600 + 30 * 60
        val kind = LessonKindOption.entries.find { it.api == lesson.lessonKind.uppercase() } ?: LessonKindOption.LECTURE
        _ui.update {
            it.copy(
                showLessonEditor = true,
                lessonEditor = LessonEditorState(
                    lessonId = lesson.id,
                    courseId = lesson.courseId,
                    groupId = lesson.groupId,
                    lessonDate = date,
                    startHour = (startSecs / 3600).toInt(),
                    startMinute = ((startSecs % 3600) / 60).toInt(),
                    endHour = (endSecs / 3600).toInt(),
                    endMinute = ((endSecs % 3600) / 60).toInt(),
                    room = lesson.roomName,
                    kind = kind,
                ),
            )
        }
    }

    fun closeLessonEditor() = _ui.update { it.copy(showLessonEditor = false) }

    fun updateLessonEditor(transform: LessonEditorState.() -> LessonEditorState) =
        _ui.update { it.copy(lessonEditor = it.lessonEditor.transform()) }

    fun saveLessonEditor() {
        val s = _ui.value
        val ed = s.lessonEditor
        val cid = ed.courseId ?: run {
            _ui.update { it.copy(snackbar = "Выберите предмет") }; return
        }
        val gid = ed.groupId ?: run {
            _ui.update { it.copy(snackbar = "Выберите группу") }; return
        }
        val room = ed.room.trim()
        if (room.isEmpty()) {
            _ui.update { it.copy(snackbar = "Укажите аудиторию") }; return
        }
        val pair = buildIsoForLesson(ed.lessonDate, ed.startHour, ed.startMinute, ed.endHour, ed.endMinute)
            ?: run {
                _ui.update { it.copy(snackbar = "Проверьте время") }; return
            }
        viewModelScope.launch {
            val err = if (ed.lessonId != null) {
                scheduleRepo.updateLesson(
                    adminTicket,
                    ed.lessonId,
                    AdminLessonUpdateRequestDto(
                        roomName = room,
                        startIso = pair.first,
                        endIso = pair.second,
                        lessonKind = ed.kind.api,
                    ),
                )
            } else {
                val (_, e) = scheduleRepo.createLesson(
                    adminTicket,
                    AdminLessonCreateRequestDto(
                        courseId = cid,
                        groupId = gid,
                        roomName = room,
                        startIso = pair.first,
                        endIso = pair.second,
                        lessonKind = ed.kind.api,
                    ),
                )
                e
            }
            _ui.update {
                it.copy(
                    showLessonEditor = false,
                    snackbar = err?.let { e -> "Ошибка: $e" } ?: if (ed.lessonId != null) "Занятие сохранено" else "Занятие создано",
                )
            }
            refreshAll()
        }
    }

    fun cancelLesson(lessonId: Long) {
        viewModelScope.launch {
            val err = scheduleRepo.updateLesson(
                adminTicket,
                lessonId,
                AdminLessonUpdateRequestDto(status = "CANCELLED"),
            )
            _ui.update { it.copy(snackbar = err?.let { e -> "Ошибка: $e" } ?: "Занятие отменено") }
            refreshAll()
        }
    }

    fun duplicateLesson(lesson: AdminLessonSummaryDto) {
        openEditLesson(lesson)
        _ui.update { it.copy(lessonEditor = it.lessonEditor.copy(lessonId = null)) }
    }

    fun openEditCourse(id: Long) {
        val refs = _ui.value.scheduleRefs ?: return
        val c = refs.courses.find { it.id == id } ?: return
        _ui.update {
            it.copy(
                editingCourseId = id,
                editCourseNameDraft = c.name,
                editCourseTeacherId = c.teacherUserId.takeIf { tid -> tid > 0 }
                    ?: refs.teachers.firstOrNull { t -> t.fullName == c.teacherFullName }?.userId
                    ?: refs.teachers.firstOrNull()?.userId,
                editCourseGroupIds = c.linkedGroupIds.toSet(),
            )
        }
    }

    fun closeEditCourse() = _ui.update {
        it.copy(editingCourseId = null, editCourseNameDraft = "", editCourseTeacherId = null, editCourseGroupIds = emptySet())
    }

    fun setEditCourseDraft(name: String, teacherId: Long?, groups: Set<Long>) {
        _ui.update { it.copy(editCourseNameDraft = name, editCourseTeacherId = teacherId, editCourseGroupIds = groups) }
    }

    fun saveEditedCourse(onDone: (String?) -> Unit) {
        val id = _ui.value.editingCourseId ?: return
        viewModelScope.launch {
            val err = scheduleRepo.updateCourse(
                adminTicket, id,
                AdminCourseUpdateRequestDto(
                    name = _ui.value.editCourseNameDraft.trim().ifBlank { null },
                    teacherUserId = _ui.value.editCourseTeacherId,
                ),
            )
            val err2 = if (err == null) {
                scheduleRepo.setCourseGroups(adminTicket, id, AdminCourseGroupsRequestDto(_ui.value.editCourseGroupIds.toList()))
            } else err
            refreshAll()
            onDone(err2)
        }
    }

    fun requestDeleteCourse(id: Long) = _ui.update { it.copy(pendingDeleteCourseId = id) }

    fun cancelDeleteCourse() = _ui.update { it.copy(pendingDeleteCourseId = null) }

    fun confirmDeleteCourse() {
        val id = _ui.value.pendingDeleteCourseId ?: return
        viewModelScope.launch {
            val err = scheduleRepo.deleteCourse(adminTicket, id)
            _ui.update { it.copy(pendingDeleteCourseId = null, snackbar = err?.let { e -> "Ошибка: $e" } ?: "Предмет удалён") }
            refreshAll()
        }
    }

    fun showNewCourse(show: Boolean) = _ui.update {
        it.copy(
            showNewCourseDialog = show,
            newCourseName = if (show) it.newCourseName else "",
            newCourseTeacherId = if (show) it.newCourseTeacherId ?: it.scheduleRefs?.teachers?.firstOrNull()?.userId else null,
        )
    }

    fun setNewCourseDraft(name: String, teacherId: Long?) =
        _ui.update { it.copy(newCourseName = name, newCourseTeacherId = teacherId) }

    fun submitNewCourse() {
        val name = _ui.value.newCourseName.trim()
        val tid = _ui.value.newCourseTeacherId ?: return
        if (name.isEmpty()) return
        viewModelScope.launch {
            val (id, err) = scheduleRepo.createCourse(adminTicket, AdminCourseCreateRequestDto(name, tid))
            _ui.update {
                it.copy(
                    showNewCourseDialog = false,
                    newCourseName = "",
                    snackbar = when {
                        id != null -> "Предмет создан"
                        err != null -> "Ошибка: $err"
                        else -> "Не удалось создать"
                    },
                )
            }
            refreshAll()
        }
    }

    fun bindNfc(
        roomId: Long,
        uid: String,
        onResult: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            val err = scheduleRepo.bindRoomNfc(adminTicket, roomId, uid)
            refreshAll()
            onResult(err)
        }
    }

    fun addFaculty(name: String) = viewModelScope.launch { runCatching { catalogRepo.addFaculty(name) }; refreshAll() }

    fun deleteFaculty(id: Long) = viewModelScope.launch { runCatching { catalogRepo.deleteFaculty(id) }; refreshAll() }

    fun addDepartment(facultyId: Long, name: String) = viewModelScope.launch {
        runCatching { catalogRepo.addDepartment(facultyId, name) }; refreshAll()
    }

    fun deleteDepartment(id: Long) = viewModelScope.launch { runCatching { catalogRepo.deleteDepartment(id) }; refreshAll() }

    fun addSpecialty(facultyId: Long, code: String, name: String, profile: String) = viewModelScope.launch {
        runCatching { catalogRepo.addSpecialty(facultyId, code, name, profile) }; refreshAll()
    }

    fun deleteSpecialty(id: Long) = viewModelScope.launch { runCatching { catalogRepo.deleteSpecialty(id) }; refreshAll() }

    fun addTeacher(fullName: String, degree: String, departmentId: Long) = viewModelScope.launch {
        runCatching { catalogRepo.addTeacher(fullName, degree, departmentId) }; refreshAll()
    }

    fun deleteTeacher(id: Long) = viewModelScope.launch { runCatching { catalogRepo.deleteTeacher(id) }; refreshAll() }

    fun addStudent(fullName: String, ticket: String, password: String, course: Int, groupNumber: Int, specialtyId: Long) =
        viewModelScope.launch {
            runCatching { catalogRepo.addStudent(fullName, ticket, password, course, groupNumber, specialtyId) }
            refreshAll()
        }

    fun deleteStudent(id: Long) = viewModelScope.launch { runCatching { catalogRepo.deleteStudent(id) }; refreshAll() }

    fun loadForgot(onResult: (List<ForgotPasswordAdminItemDto>) -> Unit) {
        viewModelScope.launch {
            val r = runCatching { catalogRepo.forgotPasswordRequests("PENDING") }
            r.onFailure { ex -> _ui.update { it.copy(snackbar = "Заявки: ${ex.message}") } }
            onResult(r.getOrElse { emptyList() })
        }
    }

    fun resolveForgot(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { catalogRepo.resolveForgotPassword(adminTicket, id, "Пароль сброшен до номера билета") }
            refreshAll()
            onDone()
        }
    }

    fun loadReschedule(onResult: (List<AdminRescheduleRequestDto>) -> Unit) {
        viewModelScope.launch {
            val r = runCatching { catalogRepo.rescheduleRequests("PENDING") }
            r.onFailure { ex -> _ui.update { it.copy(snackbar = "Переносы: ${ex.message}") } }
            onResult(r.getOrElse { emptyList() })
        }
    }

    fun approveReschedule(req: AdminRescheduleRequestDto, newStartIso: String, newEndIso: String, roomId: Long?, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val err = catalogRepo.approveReschedule(
                adminTicket,
                com.example.diplom.api.AdminRescheduleDecisionRequestDto(req.id, newStartIso, newEndIso, roomId, null),
            )
            refreshAll()
            onDone(err)
        }
    }

    fun rejectReschedule(req: AdminRescheduleRequestDto, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val err = catalogRepo.rejectReschedule(adminTicket, com.example.diplom.api.AdminRescheduleRejectRequestDto(req.id, null))
            refreshAll()
            onDone(err)
        }
    }
}
