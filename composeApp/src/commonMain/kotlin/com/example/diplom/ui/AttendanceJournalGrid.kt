package com.example.diplom.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diplom.api.AttendanceSessionDto
import com.example.diplom.api.JournalUpdateRequest
import com.example.diplom.api.LessonJournalEntryDto
import com.example.diplom.api.StudentJournalRowDto
import com.example.diplom.api.TeacherGroupCourseDto
import com.example.diplom.api.UserProfileDto
import com.example.diplom.attendance.AttendanceRepository
import com.example.diplom.time.nowEpochMillis
import com.example.diplom.ui.design.themedTextPrimary
import com.example.diplom.ui.design.themedTextSecondary
import com.example.diplom.ui.platform.decodeImageFromBytes
import com.example.diplom.ui.platform.isWebComposeClient
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

internal val JournalStudentColWidth = 232.dp
private val JournalCellWidth = 96.dp

enum class JournalFilter { ALL, PRESENT, ABSENT, EXCUSED }

internal enum class DisplayMark {
    Present,
    Absent,
    Excused,
    Unmarked,
}

internal fun displayMark(
    row: StudentJournalRowDto,
    lessonId: Long,
    sessionLessonId: Long?,
    rosterByTicket: Map<String, Boolean>,
    phase: LessonJournalPhase = LessonJournalPhase.IN_PROGRESS,
): DisplayMark {
    val status = row.attendanceStatus?.trim()?.uppercase()
    val reason = row.absenceReason?.trim()
    val presentByWire =
        status == "PRESENT" ||
            row.isPresent ||
            (reason?.equals("PRESENT", ignoreCase = true) == true)
    val core = when {
        presentByWire -> DisplayMark.Present
        reason == "UNEXCUSED" -> DisplayMark.Absent
        reason == "EXCUSED" -> DisplayMark.Excused
        reason != null -> DisplayMark.Unmarked
        sessionLessonId == lessonId && rosterByTicket.isNotEmpty() ->
            when {
                rosterByTicket[row.studentTicket] == true -> DisplayMark.Present
                else -> DisplayMark.Unmarked
            }
        else -> DisplayMark.Unmarked
    }
    return if (phase == LessonJournalPhase.ENDED && core == DisplayMark.Unmarked) {
        DisplayMark.Absent
    } else {
        core
    }
}

private enum class AttendanceEditChoice { PRESENT, ABSENT, EXCUSED, CLEAR }

private data class JournalPickRequest(
    val studentName: String,
    val lessonLabel: String,
    val allowClear: Boolean,
    val onPick: (AttendanceEditChoice) -> Unit,
)

/** Явные маркеры для API журнала (сервер нормализует «присутствует» в status=PRESENT, reason=null). */
private fun wireAbsenceReasonForChoice(choice: AttendanceEditChoice): String? =
    when (choice) {
        AttendanceEditChoice.PRESENT -> "PRESENT"
        AttendanceEditChoice.ABSENT -> "UNEXCUSED"
        AttendanceEditChoice.EXCUSED -> "EXCUSED"
        AttendanceEditChoice.CLEAR -> "CLEAR"
    }

internal fun patchJournalReason(
    entries: List<LessonJournalEntryDto>,
    lessonId: Long,
    studentTicket: String,
    absenceReason: String?,
    isPresent: Boolean,
): List<LessonJournalEntryDto> =
    entries.map { lesson ->
        if (lesson.lessonId != lessonId) lesson
        else {
            lesson.copy(
                rows = lesson.rows.map { r ->
                    if (r.studentTicket == studentTicket) {
                        val status = when {
                            isPresent -> "PRESENT"
                            absenceReason == "UNEXCUSED" || absenceReason == "EXCUSED" -> "ABSENT_MANUAL"
                            else -> null
                        }
                        r.copy(
                            absenceReason = if (isPresent) null else absenceReason,
                            isPresent = isPresent,
                            attendanceStatus = status,
                        )
                    } else {
                        r
                    }
                },
            )
        }
    }

private fun rowForLesson(lesson: LessonJournalEntryDto, ticket: String): StudentJournalRowDto? =
    lesson.rows.firstOrNull { it.studentTicket == ticket }

internal fun allStudentsFromJournal(entries: List<LessonJournalEntryDto>): List<StudentJournalRowDto> {
    val map = linkedMapOf<String, StudentJournalRowDto>()
    entries.forEach { lesson ->
        lesson.rows.forEach { r ->
            map.getOrPut(r.studentTicket) { r }
        }
    }
    return map.values.sortedBy { it.fullName }
}

internal fun studentMatchesFilter(
    ticket: String,
    entries: List<LessonJournalEntryDto>,
    sessionLessonId: Long?,
    roster: Map<String, Boolean>,
    filter: JournalFilter,
): Boolean {
    if (filter == JournalFilter.ALL) return true
    return lessonsEligibleForStats(entries, sessionLessonId).any { lesson ->
        val row = rowForLesson(lesson, ticket) ?: return@any false
        val phase = computeLessonJournalPhase(lesson.dateIso, lesson.timeRange)
        when (displayMark(row, lesson.lessonId, sessionLessonId, roster, phase)) {
            DisplayMark.Present -> filter == JournalFilter.PRESENT
            DisplayMark.Absent -> filter == JournalFilter.ABSENT
            DisplayMark.Excused -> filter == JournalFilter.EXCUSED
            DisplayMark.Unmarked -> filter == JournalFilter.ABSENT
        }
    }
}

internal fun formatLessonHeader(dateIso: String, timeRange: String): String {
    val date = formatLessonDateOnly(dateIso)
    val from = timeRange.substringBefore("-").trim()
    return if (from.isNotBlank()) "$date, $from" else date
}

internal fun lessonHeaderText(lesson: LessonJournalEntryDto): String =
    lesson.journalColumnLabel ?: formatLessonHeader(lesson.dateIso, lesson.timeRange)

private fun String.hexToByteArray(): ByteArray {
    if (length % 2 != 0) return ByteArray(0)
    return ByteArray(length / 2) { index ->
        substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

internal data class AttendanceGridStats(
    val totalStudents: Int,
    val totalLessons: Int,
    val totalCells: Int,
    val present: Int,
    val absent: Int,
    val excused: Int,
)

internal fun filteredStudentsForJournal(
    entries: List<LessonJournalEntryDto>,
    search: String,
    filter: JournalFilter,
    sessionLessonId: Long?,
    roster: Map<String, Boolean>,
): List<StudentJournalRowDto> {
    val all = allStudentsFromJournal(entries)
    return all
        .filter {
            if (search.isBlank()) true else {
                it.fullName.contains(search, ignoreCase = true) ||
                    it.studentTicket.contains(search, ignoreCase = true)
            }
        }
        .filter { studentMatchesFilter(it.studentTicket, entries, sessionLessonId, roster, filter) }
}

internal fun lessonsEligibleForStats(
    lessons: List<LessonJournalEntryDto>,
    sessionLessonId: Long? = null,
): List<LessonJournalEntryDto> =
    lessons.filter {
        sessionLessonId == it.lessonId ||
            computeLessonJournalPhase(it.dateIso, it.timeRange) != LessonJournalPhase.UPCOMING
    }

internal fun computeAttendanceGridStats(
    students: List<StudentJournalRowDto>,
    lessons: List<LessonJournalEntryDto>,
    sessionLessonId: Long?,
    roster: Map<String, Boolean>,
): AttendanceGridStats {
    val counted = lessonsEligibleForStats(lessons, sessionLessonId)
    var present = 0
    var absent = 0
    var excused = 0
    var unmarked = 0
    students.forEach { s ->
        counted.forEach { lesson ->
            val row = rowForLesson(lesson, s.studentTicket) ?: return@forEach
            val phase = effectiveLessonJournalPhase(
                dateIso = lesson.dateIso,
                timeRange = lesson.timeRange,
                lessonId = lesson.lessonId,
                sessionLessonId = sessionLessonId,
            )
            when (displayMark(row, lesson.lessonId, sessionLessonId, roster, phase)) {
                DisplayMark.Present -> present++
                DisplayMark.Absent -> absent++
                DisplayMark.Excused -> excused++
                DisplayMark.Unmarked -> unmarked++
            }
        }
    }
    return AttendanceGridStats(
        totalStudents = students.size,
        totalLessons = counted.size,
        totalCells = students.size * counted.size,
        present = present,
        absent = absent + unmarked,
        excused = excused,
    )
}

@Composable
fun AttendanceJournalGrid(
    modifier: Modifier = Modifier,
    teacherTicket: String,
    group: TeacherGroupCourseDto,
    journalEntries: List<LessonJournalEntryDto>,
    rosterMap: Map<String, Boolean>,
    sessionLessonId: Long?,
    activeSession: AttendanceSessionDto?,
    search: String,
    filter: JournalFilter,
    flashTicket: String?,
    repo: AttendanceRepository,
    snackMessage: (String) -> Unit,
    onEntriesChange: (List<LessonJournalEntryDto>) -> Unit,
    onRosterRefresh: () -> Unit,
    onLessonHeaderClick: (LessonJournalEntryDto) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val latestJournalEntries by rememberUpdatedState(journalEntries)
    val lessons = journalEntries
    val hScroll = rememberScrollState()
    var pickRequest by remember { mutableStateOf<JournalPickRequest?>(null) }
    var phaseTick by remember { mutableStateOf(nowEpochMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            phaseTick = nowEpochMillis()
        }
    }
    val lessonPhases = remember(lessons, phaseTick, sessionLessonId) {
        lessons.associate { lesson ->
            lesson.lessonId to effectiveLessonJournalPhase(
                dateIso = lesson.dateIso,
                timeRange = lesson.timeRange,
                lessonId = lesson.lessonId,
                sessionLessonId = sessionLessonId,
                nowMs = phaseTick,
            )
        }
    }

    val allStudents = remember(journalEntries) { allStudentsFromJournal(journalEntries) }
    val students = remember(journalEntries, search, filter, sessionLessonId, rosterMap) {
        filteredStudentsForJournal(journalEntries, search, filter, sessionLessonId, rosterMap)
    }

    var profilePhotos by remember { mutableStateOf(emptyMap<String, ImageBitmap?>()) }
    LaunchedEffect(journalEntries.map { it.lessonId }, allStudents.map { it.studentTicket }) {
        val tickets = allStudents.map { it.studentTicket }.distinct().take(80)
        coroutineScope {
            tickets.map { t ->
                async {
                    if (profilePhotos.containsKey(t)) return@async
                    val p = runCatching { repo.profile(t) }.getOrNull()
                    val bmp = p?.photoHex?.let { hex ->
                        runCatching { decodeImageFromBytes(hex.hexToByteArray()) }.getOrNull()
                    }
                    profilePhotos = profilePhotos + (t to bmp)
                }
            }.awaitAll()
        }
    }

    var profileDialog by remember { mutableStateOf<UserProfileDto?>(null) }
    val listState = rememberLazyListState()
    val lessonsRowWidth = JournalCellWidth * lessons.size.coerceAtLeast(1)

    fun saveAttendanceChoice(
        student: StudentJournalRowDto,
        lesson: LessonJournalEntryDto,
        row: StudentJournalRowDto,
        choice: AttendanceEditChoice,
    ) {
        val wireReason = wireAbsenceReasonForChoice(choice)
        scope.launch {
            val err = repo.updateJournal(
                teacherTicket,
                JournalUpdateRequest(
                    lessonId = lesson.lessonId,
                    studentTicket = student.studentTicket,
                    absenceReason = wireReason,
                ),
            )
            if (err != null) {
                snackMessage(err)
            } else {
                onEntriesChange(
                    patchJournalReason(
                        latestJournalEntries,
                        lesson.lessonId,
                        student.studentTicket,
                        if (choice == AttendanceEditChoice.CLEAR) null else wireReason,
                        isPresent = choice == AttendanceEditChoice.PRESENT,
                    ),
                )
                runCatching {
                    repo.journal(teacherTicket, group.groupId, group.courseId)
                }.getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { fresh -> onEntriesChange(fresh) }
                if (activeSession != null) {
                    onRosterRefresh()
                }
            }
        }
    }

    fun openAttendancePicker(
        student: StudentJournalRowDto,
        lesson: LessonJournalEntryDto,
        row: StudentJournalRowDto,
        phase: LessonJournalPhase,
    ) {
        pickRequest = JournalPickRequest(
            studentName = student.fullName,
            lessonLabel = lessonHeaderText(lesson),
            allowClear = phase == LessonJournalPhase.UPCOMING,
            onPick = { choice -> saveAttendanceChoice(student, lesson, row, choice) },
        )
    }

    val stats = remember(students, lessons, sessionLessonId, rosterMap) {
        computeAttendanceGridStats(students, lessons, sessionLessonId, rosterMap)
    }

    if (isWebComposeClient()) {
        Box(modifier = modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                JournalGridLegend(stats)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp, max = 84.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(JournalStudentColWidth)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            "Студент",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = themedTextPrimary(),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(hScroll),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(modifier = Modifier.width(lessonsRowWidth)) {
                            lessons.forEach { lesson ->
                                val phase = lessonPhases[lesson.lessonId] ?: LessonJournalPhase.IN_PROGRESS
                                val sessionColumn = sessionLessonId == lesson.lessonId
                                LessonHeaderCell(
                                    text = lessonHeaderText(lesson),
                                    selected = sessionColumn,
                                    active = phase == LessonJournalPhase.IN_PROGRESS,
                                    upcoming = phase == LessonJournalPhase.UPCOMING,
                                    onClick = { onLessonHeaderClick(lesson) },
                                )
                            }
                        }
                    }
                }
                students.forEach { student ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(JournalStudentColWidth)
                                .height(60.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            StudentLeftCell(
                                student = student,
                                photo = profilePhotos[student.studentTicket],
                                flash = flashTicket == student.studentTicket,
                                onOpenProfile = {
                                    scope.launch {
                                        profileDialog = runCatching { repo.profile(student.studentTicket) }.getOrNull()
                                            ?: UserProfileDto(
                                                studentTicket = student.studentTicket,
                                                fullName = student.fullName,
                                                role = "STUDENT",
                                                groupName = group.groupName,
                                                course = group.course,
                                                groupNumber = group.groupNumber,
                                            )
                                    }
                                },
                            )
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(hScroll),
                        ) {
                            Row(
                                modifier = Modifier
                                    .width(lessonsRowWidth)
                                    .height(60.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.22f)),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                lessons.forEach { lesson ->
                                    val row = rowForLesson(lesson, student.studentTicket) ?: student
                                    val phase = lessonPhases[lesson.lessonId] ?: LessonJournalPhase.IN_PROGRESS
                                    val mark = displayMark(row, lesson.lessonId, sessionLessonId, rosterMap, phase)
                                    val sessionColumn = sessionLessonId == lesson.lessonId
                                    GridAttendanceCell(
                                        display = mark,
                                        phase = phase,
                                        sessionPending = sessionColumn && mark == DisplayMark.Unmarked,
                                        sessionActive = sessionColumn,
                                        flash = flashTicket == student.studentTicket,
                                        onClick = { openAttendancePicker(student, lesson, row, phase) },
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            profileDialog?.let { p ->
                UserProfileIdCardDialog(profile = p, onDismiss = { profileDialog = null })
            }
            pickRequest?.let { req ->
                JournalAttendancePickerOverlay(
                    studentName = req.studentName,
                    lessonLabel = req.lessonLabel,
                    allowClear = req.allowClear,
                    onDismiss = { pickRequest = null },
                    onPick = { choice ->
                        pickRequest = null
                        req.onPick(choice)
                    },
                )
            }
        }
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            JournalGridLegend(stats)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp, max = 84.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(JournalStudentColWidth)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        "Студент",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = themedTextPrimary(),
                    )
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(hScroll),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(modifier = Modifier.width(lessonsRowWidth)) {
                        lessons.forEach { lesson ->
                            val phase = lessonPhases[lesson.lessonId] ?: LessonJournalPhase.IN_PROGRESS
                            val sessionColumn = sessionLessonId == lesson.lessonId
                            LessonHeaderCell(
                                text = lessonHeaderText(lesson),
                                selected = sessionColumn,
                                active = phase == LessonJournalPhase.IN_PROGRESS,
                                upcoming = phase == LessonJournalPhase.UPCOMING,
                                onClick = { onLessonHeaderClick(lesson) },
                            )
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    modifier = Modifier
                        .width(JournalStudentColWidth)
                        .fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(students, key = { "s-${it.studentTicket}" }) { student ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            StudentLeftCell(
                                student = student,
                                photo = profilePhotos[student.studentTicket],
                                flash = flashTicket == student.studentTicket,
                                onOpenProfile = {
                                    scope.launch {
                                        profileDialog = runCatching { repo.profile(student.studentTicket) }.getOrNull()
                                            ?: UserProfileDto(
                                                studentTicket = student.studentTicket,
                                                fullName = student.fullName,
                                                role = "STUDENT",
                                                groupName = group.groupName,
                                                course = group.course,
                                                groupNumber = group.groupNumber,
                                            )
                                    }
                                },
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .horizontalScroll(hScroll),
                ) {
                    LazyColumn(
                        modifier = Modifier.width(lessonsRowWidth),
                        state = listState,
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(students, key = { "m-${it.studentTicket}" }) { student ->
                            Row(
                                modifier = Modifier
                                    .width(lessonsRowWidth)
                                    .height(60.dp)
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.22f)),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                lessons.forEach { lesson ->
                                    val row = rowForLesson(lesson, student.studentTicket) ?: student
                                    val phase = lessonPhases[lesson.lessonId] ?: LessonJournalPhase.IN_PROGRESS
                                    val mark = displayMark(row, lesson.lessonId, sessionLessonId, rosterMap, phase)
                                    val sessionColumn = sessionLessonId == lesson.lessonId
                                    GridAttendanceCell(
                                        display = mark,
                                        phase = phase,
                                        sessionPending = sessionColumn && mark == DisplayMark.Unmarked,
                                        sessionActive = sessionColumn,
                                        flash = flashTicket == student.studentTicket,
                                        onClick = { openAttendancePicker(student, lesson, row, phase) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        profileDialog?.let { p ->
            UserProfileIdCardDialog(profile = p, onDismiss = { profileDialog = null })
        }

        pickRequest?.let { req ->
            JournalAttendancePickerOverlay(
                studentName = req.studentName,
                lessonLabel = req.lessonLabel,
                allowClear = req.allowClear,
                onDismiss = { pickRequest = null },
                onPick = { choice ->
                    pickRequest = null
                    req.onPick(choice)
                },
            )
        }
    }
}

@Composable
private fun JournalGridLegend(stats: AttendanceGridStats) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Занятий: ${stats.totalLessons} · студентов: ${stats.totalStudents}",
                style = MaterialTheme.typography.bodySmall,
                color = themedTextSecondary(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Прис.: ${stats.present}", color = Color(0xFF22C55E), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text("Отс.: ${stats.absent}", color = Color(0xFFEF4444), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text("Ув.: ${stats.excused}", color = Color(0xFFF59E0B), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
        if (stats.totalLessons > 2) {
            Text(
                "Все колонки занятий — прокрутите таблицу вправо.",
                style = MaterialTheme.typography.labelSmall,
                color = themedTextSecondary(),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun LessonHeaderCell(
    text: String,
    selected: Boolean,
    active: Boolean,
    upcoming: Boolean,
    onClick: () -> Unit,
) {
    val sessionBlue = Color(0xFF3B82F6)
    val bg = when {
        selected -> sessionBlue.copy(alpha = 0.32f)
        active -> sessionBlue.copy(alpha = 0.18f)
        upcoming -> Color(0xFF94A3B8).copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    }
    val border = when {
        selected -> sessionBlue.copy(alpha = 0.72f)
        active -> sessionBlue.copy(alpha = 0.45f)
        upcoming -> Color(0xFF94A3B8).copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    }
    Box(
        modifier = Modifier
            .width(JournalCellWidth)
            .heightIn(min = 48.dp, max = 78.dp)
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = border,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected || active) FontWeight.Bold else FontWeight.SemiBold,
            color = when {
                selected || active -> sessionBlue
                upcoming -> themedTextSecondary().copy(alpha = 0.65f)
                else -> themedTextPrimary()
            },
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 13.sp,
        )
    }
}

@Composable
private fun StudentLeftCell(
    student: StudentJournalRowDto,
    photo: ImageBitmap?,
    flash: Boolean,
    onOpenProfile: () -> Unit,
) {
    val brushIndex = abs(student.studentTicket.hashCode()) % 6
    val letter = student.fullName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val pastel = listOf(
        Brush.linearGradient(listOf(Color(0xFFFFB4C8), Color(0xFFFF8FB7))),
        Brush.linearGradient(listOf(Color(0xFFB4E7FF), Color(0xFF8FD4FF))),
        Brush.linearGradient(listOf(Color(0xFFC8F7C5), Color(0xFF9AE596))),
        Brush.linearGradient(listOf(Color(0xFFE8D4FF), Color(0xFFC9A8FF))),
        Brush.linearGradient(listOf(Color(0xFFFFE0B2), Color(0xFFFFCC80))),
        Brush.linearGradient(listOf(Color(0xFFD1F4F4), Color(0xFF9EE5E5))),
    )[brushIndex]
    val scale by animateFloatAsState(if (flash) 1.03f else 1f, tween(220), label = "s")
    Row(
        modifier = Modifier
            .width(JournalStudentColWidth)
            .height(56.dp)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpenProfile)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(pastel)
                .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                Image(
                    bitmap = photo,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(letter, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                student.fullName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = themedTextPrimary(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
            )
            Text(
                student.studentTicket,
                style = MaterialTheme.typography.labelSmall,
                color = themedTextSecondary(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GridAttendanceCell(
    display: DisplayMark,
    phase: LessonJournalPhase,
    sessionPending: Boolean,
    sessionActive: Boolean = false,
    flash: Boolean,
    onClick: () -> Unit,
) {
    // Серая «будущая» ячейка только если отметки ещё нет; присутствие по NFC не должно выглядеть серым.
    val upcoming = phase == LessonJournalPhase.UPCOMING && !sessionActive && display == DisplayMark.Unmarked
    val sessionBlue = Color(0xFF3B82F6)

    val glow by animateColorAsState(
        targetValue = when {
            flash -> Color(0xFF22C55E).copy(alpha = 0.55f)
            sessionPending -> sessionBlue.copy(alpha = 0.45f)
            sessionActive -> sessionBlue.copy(alpha = 0.32f)
            display == DisplayMark.Present -> Color(0xFF22C55E).copy(alpha = 0.38f)
            display == DisplayMark.Absent -> Color(0xFFEF4444).copy(alpha = 0.38f)
            display == DisplayMark.Excused -> Color(0xFFF59E0B).copy(alpha = 0.38f)
            upcoming -> Color(0xFF94A3B8).copy(alpha = 0.12f)
            else -> Color(0xFF94A3B8).copy(alpha = 0.2f)
        },
        animationSpec = tween(280),
        label = "glow",
    )
    val base = when {
        sessionPending || sessionActive -> sessionBlue
        upcoming -> Color(0xFFCBD5E1)
        display == DisplayMark.Present -> Color(0xFF22C55E)
        display == DisplayMark.Absent -> Color(0xFFEF4444)
        display == DisplayMark.Excused -> Color(0xFFF59E0B)
        else -> Color(0xFF94A3B8)
    }

    val cellShape = RoundedCornerShape(14.dp)
    val cellModifier = Modifier
        .padding(horizontal = 4.dp, vertical = 4.dp)
        .width(JournalCellWidth - 8.dp)
        .height(48.dp)
        .shadow(
            if (upcoming) 0.dp else 8.dp,
            cellShape,
            ambientColor = glow,
            spotColor = glow,
        )
        .clip(cellShape)
        .background(
            base.copy(
                alpha = when {
                    upcoming -> 0.10f
                    sessionPending -> 0.22f
                    sessionActive -> 0.16f
                    else -> 0.16f
                },
            ),
        )
        .border(1.dp, base.copy(alpha = if (upcoming) 0.28f else 0.5f), cellShape)
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )

    Box(modifier = cellModifier, contentAlignment = Alignment.Center) {
        when {
            sessionPending -> {
                Text(
                    "?",
                    color = sessionBlue,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            display == DisplayMark.Unmarked -> {
                Text(
                    if (upcoming) "·" else "—",
                    color = base,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            else -> {
                val icon = when (display) {
                    DisplayMark.Present -> Icons.Default.Check
                    DisplayMark.Absent -> Icons.Default.Close
                    DisplayMark.Excused -> Icons.Default.Warning
                    DisplayMark.Unmarked -> Icons.Default.Check
                }
                Icon(icon, contentDescription = null, tint = base, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun JournalAttendancePickerOverlay(
    studentName: String,
    lessonLabel: String,
    allowClear: Boolean,
    onDismiss: () -> Unit,
    onPick: (AttendanceEditChoice) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.58f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Отметка посещаемости",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                studentName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                lessonLabel,
                style = MaterialTheme.typography.bodySmall,
                color = themedTextSecondary(),
                modifier = Modifier.padding(bottom = 8.dp),
            )
            AttendancePickRow("Присутствует", Icons.Default.Check, Color(0xFF22C55E)) {
                onPick(AttendanceEditChoice.PRESENT)
            }
            AttendancePickRow("Отсутствует", Icons.Default.Close, Color(0xFFEF4444)) {
                onPick(AttendanceEditChoice.ABSENT)
            }
            AttendancePickRow("Уважительная причина", Icons.Default.Warning, Color(0xFFF59E0B)) {
                onPick(AttendanceEditChoice.EXCUSED)
            }
            if (allowClear) {
                AttendancePickRowDot("Не отмечено (серое поле)", Color(0xFF94A3B8)) {
                    onPick(AttendanceEditChoice.CLEAR)
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Отмена")
            }
        }
    }
}

@Composable
private fun AttendancePickRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(end = 12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AttendancePickRowDot(
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            "·",
            color = tint,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .padding(end = 12.dp)
                .width(24.dp),
            textAlign = TextAlign.Center,
        )
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
