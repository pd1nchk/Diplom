package com.example.diplom.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.diplom.api.AttendanceSessionDto
import com.example.diplom.api.LessonJournalEntryDto
import com.example.diplom.api.RescheduleRequestCreateDto
import com.example.diplom.api.SessionRosterRowDto
import com.example.diplom.api.StartAttendanceSessionRequestDto
import com.example.diplom.api.StopAttendanceSessionRequestDto
import com.example.diplom.api.StudentJournalRowDto
import com.example.diplom.api.TeacherGroupCourseDto
import com.example.diplom.attendance.AttendanceRepository
import com.example.diplom.nfc.NfcUidNormalizer
import com.example.diplom.ui.design.DiplomColors
import com.example.diplom.ui.design.DiplomGradients
import com.example.diplom.ui.design.GlassCard
import com.example.diplom.ui.design.GradientStrokeCard
import com.example.diplom.ui.design.ShimmerBar
import com.example.diplom.ui.design.themedAccent
import com.example.diplom.ui.design.themedError
import com.example.diplom.ui.design.themedTextPrimary
import com.example.diplom.ui.design.themedTextSecondary
import com.example.diplom.ui.platform.NfcBridge
import com.example.diplom.ui.platform.isWebComposeClient
import com.example.diplom.ui.web.WebScrollColumn
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

sealed class TeacherNav {
    data object Courses : TeacherNav()
    data class Subjects(val year: Int) : TeacherNav()
    data class Groups(val year: Int, val courseId: Long, val courseName: String) : TeacherNav()
    data class Journal(val group: TeacherGroupCourseDto) : TeacherNav()
}

fun popTeacherNav(nav: TeacherNav): TeacherNav =
    when (nav) {
        is TeacherNav.Courses -> nav
        is TeacherNav.Subjects -> TeacherNav.Courses
        is TeacherNav.Groups -> TeacherNav.Subjects(nav.year)
        is TeacherNav.Journal -> TeacherNav.Groups(nav.group.course, nav.group.courseId, nav.group.courseName)
    }

private fun subjectIcon(name: String): ImageVector =
    when {
        name.contains("баз", ignoreCase = true) -> Icons.Default.Book
        name.contains("web", ignoreCase = true) || name.contains("веб", ignoreCase = true) -> Icons.Default.Layers
        name.contains("сет", ignoreCase = true) -> Icons.Default.Schedule
        else -> Icons.Default.School
    }

private fun parseExpiresAtEpochMillis(s: String): Long? =
    com.example.diplom.time.parseUtcIsoToEpochMillis(s)

@Composable
fun TeacherAttendanceHost(
    userTicket: String,
    teacherGroups: List<TeacherGroupCourseDto>,
    repo: AttendanceRepository,
    snackMessage: (String) -> Unit,
    nav: TeacherNav,
    onNavChange: (TeacherNav) -> Unit,
    modifier: Modifier = Modifier,
) {
    val yearsWithData = remember(teacherGroups) {
        (1..4).mapNotNull { y ->
            val rows = teacherGroups.filter { it.course == y }
            if (rows.isEmpty()) null else YearSummary(
                year = y,
                groupCount = rows.map { it.groupId }.distinct().size,
                subjectCount = rows.map { it.courseId }.distinct().size,
            )
        }
    }

    val navSubjects = remember(nav, teacherGroups) {
        val nn = nav
        if (nn is TeacherNav.Subjects) {
            teacherGroups
                .filter { it.course == nn.year }
                .groupBy { it.courseId }
                .map { (cid, list) ->
                    SubjectSummary(
                        courseId = cid,
                        name = list.first().courseName,
                        groupCount = list.map { it.groupId }.distinct().size,
                    )
                }
                .sortedBy { it.name }
        } else {
            emptyList()
        }
    }

    val navGroups = remember(nav, teacherGroups) {
        val nn = nav
        if (nn is TeacherNav.Groups) {
            teacherGroups
                .filter { it.course == nn.year && it.courseId == nn.courseId }
                .distinctBy { it.groupId }
        } else {
            emptyList()
        }
    }

    when (val n = nav) {
        is TeacherNav.Journal -> {
            if (isWebComposeClient()) {
                WebScrollColumn(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    NavBackChip("К группам") {
                        onNavChange(TeacherNav.Groups(n.group.course, n.group.courseId, n.group.courseName))
                    }
                    Spacer(Modifier.height(8.dp))
                    TeacherJournalDetail(
                        modifier = Modifier.fillMaxWidth(),
                        userTicket = userTicket,
                        group = n.group,
                        repo = repo,
                        snackMessage = snackMessage,
                    )
                }
            } else {
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    NavBackChip("К группам") {
                        onNavChange(TeacherNav.Groups(n.group.course, n.group.courseId, n.group.courseName))
                    }
                    Spacer(Modifier.height(8.dp))
                    TeacherJournalDetail(
                        modifier = Modifier.weight(1f),
                        userTicket = userTicket,
                        group = n.group,
                        repo = repo,
                        snackMessage = snackMessage,
                    )
                }
            }
        }

        else -> {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    Text(
                        text = "Журнал посещаемости",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = themedTextPrimary(),
                    )
                    Text(
                        text = "Выберите курс → предмет → группу",
                        style = MaterialTheme.typography.bodyMedium,
                        color = themedTextSecondary(),
                    )
                }

                when (n) {
                    is TeacherNav.Courses -> {
                        if (yearsWithData.isEmpty()) {
                            item {
                                TeacherEmptyCard(
                                    title = "Нет данных",
                                    subtitle = "Для вашего аккаунта пока не назначены группы и дисциплины.",
                                )
                            }
                        } else {
                            items(yearsWithData, key = { it.year }) { y ->
                                YearGlassCard(
                                    summary = y,
                                    onOpen = { onNavChange(TeacherNav.Subjects(y.year)) },
                                )
                            }
                        }
                    }

                    is TeacherNav.Subjects -> {
                        val subjects = navSubjects
                        item {
                            NavBackChip("К курсам") { onNavChange(TeacherNav.Courses) }
                        }
                        if (subjects.isEmpty()) {
                            item {
                                TeacherEmptyCard("Нет предметов", "На этом курсе нет назначенных дисциплин.")
                            }
                        } else {
                            items(subjects, key = { it.courseId }) { s ->
                                SubjectGradientCard(
                                    summary = s,
                                    onOpen = {
                                        onNavChange(TeacherNav.Groups(n.year, s.courseId, s.name))
                                    },
                                )
                            }
                        }
                    }

                    is TeacherNav.Groups -> {
                        val groups = navGroups
                        item {
                            NavBackChip("К предметам") { onNavChange(TeacherNav.Subjects(n.year)) }
                        }
                        item {
                            Text(
                                text = n.courseName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = themedTextPrimary(),
                            )
                        }
                        if (groups.isEmpty()) {
                            item {
                                TeacherEmptyCard("Нет групп", "Для этой дисциплины не найдено групп в расписании.")
                            }
                        } else {
                            items(groups, key = { "${it.groupId}_${it.courseId}" }) { g ->
                                GroupSummaryCard(
                                    group = g,
                                    onOpen = { onNavChange(TeacherNav.Journal(g)) },
                                )
                            }
                        }
                    }

                    is TeacherNav.Journal -> {}
                }
            }
        }
    }
}

private data class YearSummary(
    val year: Int,
    val groupCount: Int,
    val subjectCount: Int,
)

private data class SubjectSummary(
    val courseId: Long,
    val name: String,
    val groupCount: Int,
)

@Composable
private fun NavBackChip(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = themedAccent(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun YearGlassCard(summary: YearSummary, onOpen: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interaction, indication = null, onClick = onOpen),
        fillAlpha = 0.12f,
        borderAlpha = 0.16f,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(themedAccent().copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.School, contentDescription = null, tint = themedAccent(), modifier = Modifier.size(28.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${summary.year} курс",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = themedTextPrimary(),
                )
                Text(
                    text = "${summary.subjectCount} предметов · ${summary.groupCount} групп",
                    style = MaterialTheme.typography.bodySmall,
                    color = themedTextSecondary(),
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = themedTextSecondary())
        }
    }
}

@Composable
private fun SubjectGradientCard(summary: SubjectSummary, onOpen: () -> Unit) {
    GradientStrokeCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DiplomGradients.lightAccent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(subjectIcon(summary.name), contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = themedTextPrimary(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${summary.groupCount} групп · ближайшее занятие: см. журнал",
                    style = MaterialTheme.typography.bodySmall,
                    color = themedTextSecondary(),
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = themedTextSecondary())
        }
    }
}

@Composable
private fun GroupSummaryCard(group: TeacherGroupCourseDto, onOpen: () -> Unit) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        fillAlpha = 0.1f,
        borderAlpha = 0.14f,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.Groups, contentDescription = null, tint = themedAccent(), modifier = Modifier.size(32.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(group.groupName, fontWeight = FontWeight.Bold, color = themedTextPrimary(), style = MaterialTheme.typography.titleSmall)
                Text(
                    "Подгруппа ${group.groupNumber} · ${group.courseName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = themedTextSecondary(),
                )
            }
            Text(
                text = "Журнал",
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(themedAccent().copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                color = themedAccent(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun TeacherEmptyCard(title: String, subtitle: String) {
    GlassCard(modifier = Modifier.fillMaxWidth(), fillAlpha = 0.1f, borderAlpha = 0.14f) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = themedTextPrimary())
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = themedTextSecondary())
    }
}

@Composable
private fun TeacherJournalDetail(
    modifier: Modifier = Modifier,
    userTicket: String,
    group: TeacherGroupCourseDto,
    repo: AttendanceRepository,
    snackMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var journalLoading by remember { mutableStateOf(true) }
    var journalError by remember { mutableStateOf<String?>(null) }
    var journalEntries by remember { mutableStateOf<List<LessonJournalEntryDto>>(emptyList()) }
    var filter by remember { mutableStateOf(JournalFilter.ALL) }
    var search by remember { mutableStateOf("") }
    var roster by remember { mutableStateOf<List<SessionRosterRowDto>>(emptyList()) }
    var activeSession by remember { mutableStateOf<AttendanceSessionDto?>(null) }
    var nfcBusy by remember { mutableStateOf(false) }
    var lastNfcProcessed by remember { mutableLongStateOf(0L) }
    var flashTicket by remember { mutableStateOf<String?>(null) }
    var detailLesson by remember { mutableStateOf<LessonJournalEntryDto?>(null) }
    var rescheduleLessonId by remember { mutableStateOf<Long?>(null) }
    val scannedUid by NfcBridge.lastUid.collectAsState()

    fun reloadJournal() {
        scope.launch {
            journalEntries = runCatching {
                repo.journal(userTicket, group.groupId, group.courseId)
            }.getOrElse { journalEntries }
        }
    }

    LaunchedEffect(group.groupId, group.courseId, userTicket) {
        journalLoading = true
        journalError = null
        journalEntries = runCatching {
            repo.journal(userTicket, group.groupId, group.courseId)
        }.onFailure {
            journalError = it.message ?: "Ошибка загрузки"
        }.getOrElse { emptyList() }
        journalLoading = false
    }

    // Периодически подтягиваем журнал, чтобы одобренный админом перенос появлялся без ручного перезахода.
    LaunchedEffect(userTicket, group.groupId, group.courseId) {
        while (isActive) {
            delay(10_000)
            val refreshed = runCatching {
                repo.journal(userTicket, group.groupId, group.courseId)
            }.getOrNull() ?: continue
            journalEntries = refreshed
            if (journalError != null) journalError = null
        }
    }

    LaunchedEffect(userTicket, group.groupId, group.courseId) {
        while (isActive) {
            activeSession = runCatching { repo.activeSessionForTeacher(userTicket) }.getOrNull()
            delay(4000)
        }
    }

    val sessionLessonId = activeSession?.takeIf { s ->
        journalEntries.any { it.lessonId == s.lessonId }
    }?.lessonId

    LaunchedEffect(activeSession?.sessionId, userTicket) {
        val sid = activeSession?.sessionId ?: return@LaunchedEffect
        while (isActive) {
            roster = runCatching { repo.sessionRoster(userTicket, sid) }.getOrElse { emptyList() }
            delay(1600)
        }
    }

    val rosterMap by remember {
        derivedStateOf {
            roster.associate { it.studentTicket to it.present }
        }
    }

    val headerStats by remember(journalEntries, search, filter, rosterMap, sessionLessonId) {
        derivedStateOf {
            val students = filteredStudentsForJournal(journalEntries, search, filter, sessionLessonId, rosterMap)
            computeAttendanceGridStats(students, journalEntries, sessionLessonId, rosterMap)
        }
    }

    fun startLessonSession(lessonId: Long) {
        scope.launch {
            if (nfcBusy) return@launch
            nfcBusy = true
            val s = runCatching {
                repo.startAttendanceSession(
                    userTicket,
                    StartAttendanceSessionRequestDto(lessonId = lessonId, durationMinutes = null),
                )
            }.getOrNull()
            nfcBusy = false
            if (s != null) {
                activeSession = s
                roster = runCatching { repo.sessionRoster(userTicket, s.sessionId) }.getOrElse { emptyList() }
                snackMessage("Сессия посещаемости открыта")
                journalEntries = runCatching {
                    repo.journal(userTicket, group.groupId, group.courseId)
                }.getOrElse { journalEntries }
            } else {
                snackMessage("Не удалось начать сессию (время пары или доступ)")
            }
        }
    }

    fun stopLessonSession() {
        val sid = activeSession?.sessionId ?: return
        scope.launch {
            if (nfcBusy) return@launch
            nfcBusy = true
            val ok = runCatching {
                repo.stopAttendanceSession(userTicket, StopAttendanceSessionRequestDto(sessionId = sid))
            }.getOrDefault(false)
            nfcBusy = false
            if (ok) {
                activeSession = null
                roster = emptyList()
                snackMessage("Лекция завершена")
                journalEntries = runCatching {
                    repo.journal(userTicket, group.groupId, group.courseId)
                }.getOrElse { journalEntries }
            } else {
                snackMessage("Не удалось завершить лекцию")
            }
        }
    }

    val pageScroll = isWebComposeClient()
    Column(
        modifier = if (pageScroll) modifier.fillMaxWidth() else modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        JournalDashboardHeader(
            group = group,
            stats = headerStats,
            search = search,
            onSearch = { search = it },
            filter = filter,
            onFilter = { filter = it },
        )

        NfcSessionBlock(
            activeSession = activeSession,
            journalLessonIds = journalEntries.map { it.lessonId }.toSet(),
            nfcBusy = nfcBusy,
            waitingRoomTag = false,
            waitingRoomName = null,
            onCancelWaiting = { NfcBridge.clearLastUid() },
            onStop = {
                val sid = activeSession?.sessionId ?: return@NfcSessionBlock
                scope.launch {
                    if (nfcBusy) return@launch
                    nfcBusy = true
                    val ok = runCatching {
                        repo.stopAttendanceSession(userTicket, StopAttendanceSessionRequestDto(sessionId = sid))
                    }.getOrDefault(false)
                    nfcBusy = false
                    if (ok) {
                        activeSession = null
                        roster = emptyList()
                        snackMessage("Сессия остановлена")
                        journalEntries = runCatching {
                            repo.journal(userTicket, group.groupId, group.courseId)
                        }.getOrElse { journalEntries }
                    } else {
                        snackMessage("Не удалось остановить сессию")
                    }
                }
            },
        )

        if (journalLoading) {
            repeat(4) {
                ShimmerBar(Modifier.fillMaxWidth(), height = 72.dp)
                Spacer(Modifier.height(8.dp))
            }
        } else if (journalError != null) {
            GlassCard(modifier = Modifier.fillMaxWidth(), fillAlpha = 0.1f, borderAlpha = 0.14f) {
                Text(journalError!!, color = themedError())
            }
        } else if (journalEntries.isEmpty()) {
            TeacherEmptyCard("Журнал пуст", "Для этой группы и дисциплины пока нет занятий в расписании.")
        } else {
            val gridModifier = if (pageScroll) Modifier.fillMaxWidth() else Modifier.fillMaxSize()
            val gridHost = if (pageScroll) Modifier.fillMaxWidth() else Modifier.weight(1f).fillMaxWidth()
            Box(modifier = gridHost) {
                AttendanceJournalGrid(
                    modifier = gridModifier,
                    teacherTicket = userTicket,
                    group = group,
                    journalEntries = journalEntries,
                    rosterMap = rosterMap,
                    sessionLessonId = sessionLessonId,
                    activeSession = activeSession,
                    search = search,
                    filter = filter,
                    flashTicket = flashTicket,
                    repo = repo,
                    snackMessage = snackMessage,
                    onEntriesChange = { journalEntries = it },
                    onRosterRefresh = {
                        scope.launch {
                            val sid = activeSession?.sessionId ?: return@launch
                            roster = runCatching { repo.sessionRoster(userTicket, sid) }.getOrElse { roster }
                        }
                    },
                    onLessonHeaderClick = { lesson ->
                        detailLesson = lesson
                    },
                )
            }
        }
    }

    detailLesson?.let { lesson ->
        val groupLabel = "${group.course} курс • ${group.groupName}"
        val phase = computeLessonJournalPhase(lesson.dateIso, lesson.timeRange)
        val session = activeSession
        val isThisLesson = session?.lessonId == lesson.lessonId
        val otherSession = session != null && !isThisLesson
        val otherHint = session?.let { "${it.subject}, ${it.groupName}" }
        JournalLessonDetailDialog(
            lesson = lesson,
            groupLabel = groupLabel,
            isSessionForThisLesson = isThisLesson,
            hasOtherActiveSession = otherSession,
            otherActiveSessionHint = otherHint,
            showReschedule = phase != LessonJournalPhase.ENDED && !isThisLesson,
            onDismiss = { detailLesson = null },
            onStartLesson = { startLessonSession(lesson.lessonId) },
            onEndLesson = { stopLessonSession() },
            onReschedule = { rescheduleLessonId = lesson.lessonId },
        )
    }

    val rescheduleTarget = rescheduleLessonId?.let { lid ->
        journalEntries.find { it.lessonId == lid }
    }
    if (rescheduleTarget != null) {
        val from = rescheduleTarget.timeRange.substringBefore("-").trim()
        val to = rescheduleTarget.timeRange.substringAfter("-", "").trim().ifBlank { "09:30" }
        JournalRescheduleDialog(
            lessonId = rescheduleTarget.lessonId,
            initialDateIso = rescheduleTarget.dateIso,
            initialFrom = from,
            initialTo = to,
            onDismiss = { rescheduleLessonId = null },
            onSubmit = { req ->
                scope.launch {
                    val ok = runCatching {
                        repo.createRescheduleRequest(userTicket, req)
                    }.getOrDefault(false)
                    rescheduleLessonId = null
                    if (ok) {
                        snackMessage("Запрос на перенос отправлен администратору")
                        reloadJournal()
                    } else {
                        snackMessage("Не удалось отправить запрос")
                    }
                }
            },
        )
    }
}

private fun allTicketsForStudent(name: String, students: List<StudentJournalRowDto>, roster: List<SessionRosterRowDto>): String? {
    roster.firstOrNull { it.fullName == name }?.studentTicket?.let { return it }
    return students.firstOrNull { it.fullName == name }?.studentTicket
}

@Composable
private fun JournalDashboardHeader(
    group: TeacherGroupCourseDto,
    stats: AttendanceGridStats,
    search: String,
    onSearch: (String) -> Unit,
    filter: JournalFilter,
    onFilter: (JournalFilter) -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), fillAlpha = 0.12f, borderAlpha = 0.16f) {
        Text(
            text = group.courseName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = themedTextPrimary(),
        )
        Text(
            text = group.groupName,
            style = MaterialTheme.typography.bodyMedium,
            color = themedTextSecondary(),
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatGlowMini("Студентов", stats.totalStudents, DiplomColors.LightPrimary)
            StatGlowMini("Присутствуют", stats.present, Color(0xFF22C55E))
            StatGlowMini("Отсутствуют", stats.absent, Color(0xFFEF4444))
            StatGlowMini("Уважит.", stats.excused, Color(0xFFF59E0B))
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChipUi("Все", filter == JournalFilter.ALL) { onFilter(JournalFilter.ALL) }
            }
            item {
                FilterChipUi("Присутствуют", filter == JournalFilter.PRESENT) { onFilter(JournalFilter.PRESENT) }
            }
            item {
                FilterChipUi("Отсутствуют", filter == JournalFilter.ABSENT) { onFilter(JournalFilter.ABSENT) }
            }
            item {
                FilterChipUi("Уважит.", filter == JournalFilter.EXCUSED) { onFilter(JournalFilter.EXCUSED) }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = search,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = themedTextSecondary()) },
            placeholder = { Text("Поиск по ФИО или билету", color = themedTextSecondary()) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = themedTextPrimary(),
                unfocusedTextColor = themedTextPrimary(),
                focusedBorderColor = themedAccent(),
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                cursorColor = themedAccent(),
            ),
        )
    }
}

@Composable
private fun StatGlowMini(label: String, value: Int, accent: Color) {
    val animated by animateFloatAsState(targetValue = value.toFloat(), animationSpec = tween(320), label = "v")
    GlassCard(
        modifier = Modifier.width(104.dp),
        fillAlpha = 0.14f,
        borderAlpha = 0.2f,
        contentPadding = 10.dp,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = themedTextSecondary(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            animated.roundToInt().toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
    }
}

@Composable
private fun FilterChipUi(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) themedAccent().copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = if (selected) themedAccent() else themedTextPrimary(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
    )
}

@Composable
private fun NfcSessionBlock(
    activeSession: AttendanceSessionDto?,
    journalLessonIds: Set<Long>,
    nfcBusy: Boolean,
    waitingRoomTag: Boolean,
    waitingRoomName: String?,
    onCancelWaiting: () -> Unit,
    onStop: () -> Unit,
) {
    val expires = remember(activeSession?.expiresAtIso) {
        activeSession?.expiresAtIso?.let { parseExpiresAtEpochMillis(it) }
    }
    var tick by remember { mutableLongStateOf(com.example.diplom.time.nowEpochMillis()) }
    LaunchedEffect(activeSession?.sessionId) {
        while (isActive) {
            tick = com.example.diplom.time.nowEpochMillis()
            delay(1000)
        }
    }
    val remainingSec by remember(expires, tick) {
        derivedStateOf {
            val endMs = expires ?: return@derivedStateOf 0
            max(0, ((endMs - tick) / 1000L).toInt())
        }
    }
    val pulse = rememberInfiniteTransition(label = "nfc")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pa",
    )
    val sessionInJournal = activeSession != null && journalLessonIds.contains(activeSession.lessonId)

    GlassCard(modifier = Modifier.fillMaxWidth(), fillAlpha = 0.12f, borderAlpha = 0.16f) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            activeSession != null -> themedAccent().copy(alpha = pulseAlpha)
                            waitingRoomTag -> themedAccent().copy(alpha = pulseAlpha * 0.7f)
                            else -> themedTextSecondary().copy(alpha = 0.12f)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Sensors, contentDescription = null, tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("NFC-сессия", fontWeight = FontWeight.Bold, color = themedTextPrimary(), style = MaterialTheme.typography.titleSmall)
                when {
                    waitingRoomTag && activeSession == null -> {
                        Text(
                            "Поднесите NFC-метку аудитории ${waitingRoomName ?: "«…»"} к телефону — так подтверждается комната пары.",
                            color = themedAccent(),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    activeSession == null -> Text("Неактивна — после подтверждения метки аудитории студенты отмечаются картой.", color = themedTextSecondary(), style = MaterialTheme.typography.bodySmall)
                    !sessionInJournal -> Text("Сессия для другого занятия — прокрутите журнал по датам или дождитесь нужной пары.", color = themedTextSecondary(), style = MaterialTheme.typography.bodySmall)
                    else -> Text("Активна · осталось ${remainingSec / 60}:${(remainingSec % 60).toString().padStart(2, '0')}", color = themedAccent(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (waitingRoomTag && activeSession == null) {
                    Text(
                        text = "Отмена",
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .clickable(onClick = onCancelWaiting),
                        color = themedTextSecondary(),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = if (nfcBusy) "…" else "Ожидание метки…",
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(themedTextSecondary().copy(alpha = 0.35f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge,
                    )
                } else if (activeSession == null) {
                    Text(
                        text = "Запуск из карточки пары",
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(themedTextSecondary().copy(alpha = 0.25f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge,
                    )
                } else {
                    Text(
                        text = if (nfcBusy) "…" else "Остановить",
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(themedError().copy(alpha = 0.85f))
                            .clickable(enabled = !nfcBusy, onClick = onStop)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
