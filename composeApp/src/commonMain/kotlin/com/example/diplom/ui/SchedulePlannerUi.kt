package com.example.diplom.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.diplom.api.DayScheduleColumnDto
import com.example.diplom.api.ScheduledLessonDto
import com.example.diplom.api.WeekScheduleDto
import com.example.diplom.domain.UserRole
import com.example.diplom.time.todayLocalDateInAppZone
import com.example.diplom.ui.design.DiplomColors
import com.example.diplom.ui.design.GlassCard
import com.example.diplom.ui.design.ShimmerBar
import com.example.diplom.ui.design.isLightUi
import com.example.diplom.ui.design.themedAccent
import com.example.diplom.ui.design.themedScreenBrush
import com.example.diplom.ui.design.themedTextPrimary
import com.example.diplom.ui.design.themedTextSecondary
import com.example.diplom.ui.platform.isWebComposeClient
import com.example.diplom.ui.web.WebScrollColumn
private enum class ScheduleFilterMode { ALL, SUBJECT, ROOM }

private val dayShort = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
private val dayLong = mapOf(
    "Пн" to "ПОНЕДЕЛЬНИК",
    "Вт" to "ВТОРНИК",
    "Ср" to "СРЕДА",
    "Чт" to "ЧЕТВЕРГ",
    "Пт" to "ПЯТНИЦА",
    "Сб" to "СУББОТА",
    "Вс" to "ВОСКРЕСЕНЬЕ",
)

private fun subjectAccent(subject: String): Color = when {
    subject.contains("сет", ignoreCase = true) -> Color(0xFF3B82F6)
    subject.contains("програм", ignoreCase = true) -> Color(0xFF8B5CF6)
    subject.contains("баз", ignoreCase = true) -> Color(0xFF10B981)
    subject.contains("истор", ignoreCase = true) -> Color(0xFFF59E0B)
    else -> DiplomColors.LightPrimary
}

private fun applyScheduleFilters(
    lessons: List<ScheduledLessonDto>,
    mode: ScheduleFilterMode,
    subject: String?,
    room: String?,
): List<ScheduledLessonDto> {
    val sorted = lessons.sortedBy { it.startTimeEpochMillis }
    return when (mode) {
        ScheduleFilterMode.ALL -> sorted
        ScheduleFilterMode.SUBJECT -> subject?.let { s -> sorted.filter { it.subject == s } } ?: sorted
        ScheduleFilterMode.ROOM -> room?.let { r -> sorted.filter { it.room == r } } ?: sorted
    }
}

private fun todayDayShort(): String {
    val today = todayLocalDateInAppZone().dayOfWeek
    val idx = today.ordinal
    return dayShort.getOrElse(idx) { "Пн" }
}

private fun totalLessons(week: WeekScheduleDto): Int =
    week.columns.sumOf { it.lessons.size }

private fun nextLesson(week: WeekScheduleDto): ScheduledLessonDto? {
    val now = com.example.diplom.time.nowEpochMillis()
    return week.columns
        .flatMap { it.lessons }
        .filter { it.startTimeEpochMillis > 0 }
        .sortedBy { it.startTimeEpochMillis }
        .firstOrNull { it.startTimeEpochMillis >= now }
        ?: week.columns.flatMap { it.lessons }.minByOrNull { it.startTimeEpochMillis }
}

@Composable
fun SchedulePlannerContent(
    onBack: () -> Unit,
    userRole: UserRole,
    userName: String,
    week: WeekScheduleDto?,
    loading: Boolean,
    error: String?,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filterMode by remember { mutableStateOf(ScheduleFilterMode.ALL) }
    var filterSubject by remember { mutableStateOf<String?>(null) }
    var filterRoom by remember { mutableStateOf<String?>(null) }
    var selectedDay by remember(week) {
        val today = todayDayShort()
        val hasToday = week?.columns?.any { it.dayOfWeek == today && it.lessons.isNotEmpty() } == true
        val firstWith = week?.columns?.firstOrNull { it.lessons.isNotEmpty() }?.dayOfWeek
        mutableStateOf(if (hasToday) today else firstWith ?: today)
    }

    val allSubjects = remember(week) {
        week?.columns?.flatMap { it.lessons }?.map { it.subject }?.distinct()?.sorted().orEmpty()
    }
    val allRooms = remember(week) {
        week?.columns?.flatMap { it.lessons }?.map { it.room }?.distinct()?.sorted().orEmpty()
    }

    val columns = remember(week, filterMode, filterSubject, filterRoom) {
        week?.columns?.map { col ->
            DayScheduleColumnDto(
                dayOfWeek = col.dayOfWeek,
                lessons = applyScheduleFilters(col.lessons, filterMode, filterSubject, filterRoom),
            )
        } ?: emptyList()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(themedScreenBrush()),
    ) {
        val wideLayout = maxWidth >= 720.dp
        Column(Modifier.fillMaxSize()) {
            SchedulePlannerTopBar(onBack = onBack, onToday = onToday)
            val bodyModifier = Modifier
                .widthIn(max = 1080.dp)
                .padding(horizontal = 20.dp, vertical = 12.dp)
            val bodyContent: @Composable ColumnScope.() -> Unit = {
                    ScheduleHeroCard(
                        userName = userName,
                        week = week,
                        userRole = userRole,
                    )
                    ScheduleFilterChips(
                        filterMode = filterMode,
                        subjects = allSubjects,
                        rooms = allRooms,
                        selectedSubject = filterSubject,
                        selectedRoom = filterRoom,
                        onModeChange = { mode ->
                            filterMode = mode
                            if (mode != ScheduleFilterMode.SUBJECT) filterSubject = null
                            if (mode != ScheduleFilterMode.ROOM) filterRoom = null
                        },
                        onSubject = { filterSubject = it },
                        onRoom = { filterRoom = it },
                    )
                    ScheduleDaySwitcher(
                        columns = columns,
                        selectedDay = selectedDay,
                        onSelect = { selectedDay = it },
                    )
                    when {
                        loading -> repeat(4) {
                            ShimmerBar(Modifier.fillMaxWidth(), height = 100.dp)
                        }
                        error != null -> {
                            GlassCard(Modifier.fillMaxWidth()) {
                                Text(error, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        week == null -> {
                            Text("Нет данных", color = themedTextSecondary())
                        }
                        else -> {
                            val col = columns.firstOrNull { it.dayOfWeek == selectedDay }
                            if (wideLayout) {
                                ScheduleWeekGrid(
                                    columns = listOfNotNull(col),
                                    dayTitle = dayLong[selectedDay] ?: selectedDay,
                                )
                            } else {
                                ScheduleMobileDayList(
                                    dayLabel = selectedDay,
                                    lessons = col?.lessons.orEmpty(),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
            }
            if (isWebComposeClient()) {
                WebScrollColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    Column(
                        modifier = bodyModifier,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        content = bodyContent,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .then(bodyModifier),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = bodyContent,
                )
            }
        }
    }
}

@Composable
private fun SchedulePlannerTopBar(onBack: () -> Unit, onToday: () -> Unit) {
    val light = isLightUi()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (light) Color.White.copy(alpha = 0.88f) else Color(0xFF111827).copy(alpha = 0.92f),
            )
            .border(
                width = 1.dp,
                color = if (light) Color(0xFFE2E8F0) else Color(0xFF1E293B),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = themedTextPrimary())
        }
        Column(Modifier.weight(1f)) {
            Text(
                "Расписание",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = themedTextPrimary(),
            )
            Text(
                "Ваши пары на текущую неделю",
                style = MaterialTheme.typography.bodySmall,
                color = themedTextSecondary(),
            )
        }
        Text(
            "Сегодня",
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onToday)
                .background(themedAccent().copy(alpha = 0.12f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            color = themedAccent(),
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun ScheduleWeekBanner(week: WeekScheduleDto?) {
    val weekNumber = week?.weekNumber
    val weekYear = week?.weekYear
    val isEvenWeek = week?.isEvenWeek
    if (weekNumber == null) {
        Text(
            week?.weekParityHint ?: "Текущая неделя",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.88f),
        )
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScheduleWeekChip("$weekNumber-я неделя")
        if (weekYear != null) {
            ScheduleWeekChip("$weekYear год")
        }
        if (isEvenWeek != null) {
            ScheduleWeekChip(if (isEvenWeek) "Чётная" else "Нечётная")
        }
    }
}

@Composable
private fun ScheduleWeekChip(label: String) {
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.2f))
            .border(1.dp, Color.White.copy(alpha = 0.32f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
    )
}

@Composable
private fun ScheduleHeroCard(userName: String, week: WeekScheduleDto?, userRole: UserRole) {
    val gradient = Brush.linearGradient(
        listOf(Color(0xFF4F46E5), Color(0xFF6366F1), Color(0xFF8B5CF6)),
    )
    val total = week?.let { totalLessons(it) } ?: 0
    val next = week?.let { nextLesson(it) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(gradient)
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ScheduleWeekBanner(week)
            if (userRole == UserRole.TEACHER && userName.isNotBlank()) {
                Text(
                    userName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HeroStat("Пар на неделе", total.toString())
                if (next != null) {
                    HeroStat("Ближайшая", next.timeRange.substringBefore("-").trim())
                }
            }
            if (next != null) {
                Text(
                    "Следующая: ${next.subject}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.92f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String) {
    Column {
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Text(label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ScheduleFilterChips(
    filterMode: ScheduleFilterMode,
    subjects: List<String>,
    rooms: List<String>,
    selectedSubject: String?,
    selectedRoom: String?,
    onModeChange: (ScheduleFilterMode) -> Unit,
    onSubject: (String) -> Unit,
    onRoom: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SortChip("Все", filterMode == ScheduleFilterMode.ALL) {
                onModeChange(ScheduleFilterMode.ALL)
            }
            SortChip("По предмету", filterMode == ScheduleFilterMode.SUBJECT) {
                onModeChange(ScheduleFilterMode.SUBJECT)
                if (subjects.isNotEmpty() && selectedSubject == null) onSubject(subjects.first())
            }
            SortChip("По аудитории", filterMode == ScheduleFilterMode.ROOM) {
                onModeChange(ScheduleFilterMode.ROOM)
                if (rooms.isNotEmpty() && selectedRoom == null) onRoom(rooms.first())
            }
        }
        if (filterMode == ScheduleFilterMode.SUBJECT && subjects.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                subjects.forEach { subject ->
                    SortChip(subject, selectedSubject == subject) { onSubject(subject) }
                }
            }
        }
        if (filterMode == ScheduleFilterMode.ROOM && rooms.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rooms.forEach { room ->
                    SortChip(room, selectedRoom == room) { onRoom(room) }
                }
            }
        }
    }
}

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) themedAccent().copy(alpha = 0.18f) else Color.White.copy(alpha = if (isLightUi()) 0.7f else 0.08f),
        tween(200),
        label = "bg",
    )
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(
                1.dp,
                if (selected) themedAccent().copy(alpha = 0.5f) else Color.White.copy(alpha = 0.35f),
                RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        color = if (selected) themedAccent() else themedTextPrimary(),
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun ScheduleDaySwitcher(
    columns: List<DayScheduleColumnDto>,
    selectedDay: String,
    onSelect: (String) -> Unit,
) {
    val today = todayDayShort()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        columns.forEach { col ->
            val count = col.lessons.size
            val selected = col.dayOfWeek == selectedDay
            val isToday = col.dayOfWeek == today
            DayTab(
                short = col.dayOfWeek,
                count = count,
                selected = selected,
                isToday = isToday,
                onClick = { onSelect(col.dayOfWeek) },
            )
        }
    }
}

@Composable
private fun DayTab(short: String, count: Int, selected: Boolean, isToday: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1.04f else 1f, tween(180), label = "s")
    val bg by animateColorAsState(
        when {
            selected -> themedAccent().copy(alpha = 0.2f)
            isToday -> Color(0xFF22C55E).copy(alpha = 0.12f)
            else -> Color.White.copy(alpha = if (isLightUi()) 0.65f else 0.07f)
        },
        tween(200),
        label = "bg",
    )
    Column(
        modifier = Modifier
            .scale(scale)
            .width(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(
                1.dp,
                if (selected) themedAccent() else Color.Transparent,
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(short.uppercase(), fontWeight = FontWeight.Bold, color = if (selected) themedAccent() else themedTextPrimary())
        Text(
            if (count == 0) "—" else "$count ${lessonWord(count)}",
            style = MaterialTheme.typography.labelSmall,
            color = themedTextSecondary(),
        )
    }
}

private fun lessonWord(n: Int): String = when {
    n % 10 == 1 && n % 100 != 11 -> "пара"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "пары"
    else -> "пар"
}

@Composable
private fun ScheduleWeekGrid(columns: List<DayScheduleColumnDto>, dayTitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (dayTitle != null) {
            Text(
                dayTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = themedTextPrimary(),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        columns.forEach { col ->
            Column(
                modifier = Modifier.widthIn(min = 280.dp, max = 520.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (dayTitle == null) {
                    Text(
                        dayLong[col.dayOfWeek] ?: col.dayOfWeek,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = themedTextSecondary(),
                    )
                }
                if (col.lessons.isEmpty()) {
                    ScheduleEmptyDayCard()
                } else {
                    col.lessons.forEach { lesson ->
                        ScheduleLessonCard(lesson = lesson)
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun ScheduleMobileDayList(dayLabel: String, lessons: List<ScheduledLessonDto>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            dayLong[dayLabel] ?: dayLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = themedTextPrimary(),
        )
        if (lessons.isEmpty()) {
            ScheduleEmptyDayCard()
        } else {
            lessons.forEach { ScheduleLessonCard(lesson = it) }
        }
    }
}

@Composable
private fun ScheduleEmptyDayCard() {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        fillAlpha = 0.06f,
        borderAlpha = 0.1f,
        contentPadding = 20.dp,
    ) {
        Text(
            "На этот день занятий нет",
            color = themedTextSecondary(),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun ScheduleLessonCard(lesson: ScheduledLessonDto, modifier: Modifier = Modifier) {
    val accent = subjectAccent(lesson.subject)
    val light = isLightUi()
    val bg = if (light) Color.White.copy(alpha = 0.92f) else Color(0xFF1E293B).copy(alpha = 0.85f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .padding(start = 0.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .width(5.dp)
                    .height(120.dp)
                    .background(accent, RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    lesson.subject,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = themedTextPrimary(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Schedule, null, tint = accent, modifier = Modifier.size(18.dp))
                    Text(lesson.timeRange, color = themedTextPrimary(), fontWeight = FontWeight.Medium)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Place, null, tint = themedTextSecondary(), modifier = Modifier.size(18.dp))
                    Text("ауд. ${lesson.room}", color = themedTextSecondary(), style = MaterialTheme.typography.bodyMedium)
                }
                val audience = lesson.mergedGroupsLabel
                if (!audience.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(accent.copy(alpha = 0.1f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Default.Groups, null, tint = accent, modifier = Modifier.size(18.dp))
                        Text(
                            audience,
                            color = themedTextPrimary(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (lesson.teacher.isNotBlank()) {
                    Text(
                        lesson.teacher,
                        style = MaterialTheme.typography.labelMedium,
                        color = themedTextSecondary(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
