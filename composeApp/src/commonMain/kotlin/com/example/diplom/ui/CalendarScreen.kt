package com.example.diplom.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diplom.api.CalendarEventDto
import com.example.diplom.api.ScheduledLessonDto
import com.example.diplom.api.UserStateDto
import com.example.diplom.api.WeekScheduleDto
import com.example.diplom.api.emptyWeekSchedule
import com.example.diplom.domain.UserRole
import com.example.diplom.schedule.ScheduleRepository
import com.example.diplom.state.UserStateRepository
import com.example.diplom.ui.calendar.buildMonthGrid
import com.example.diplom.ui.calendar.lessonKindShortRu
import com.example.diplom.ui.calendar.monthTitleRu
import com.example.diplom.ui.calendar.noteColorCompose
import com.example.diplom.ui.calendar.parseYmd
import com.example.diplom.ui.calendar.scheduledLessonsOnDate
import com.example.diplom.ui.calendar.todayYmd
import com.example.diplom.ui.calendar.ymdToIso
import com.example.diplom.ui.design.DiplomColors
import com.example.diplom.ui.design.DiplomGradients
import com.example.diplom.ui.design.GlassCard
import com.example.diplom.ui.design.diplomShadow
import com.example.diplom.ui.design.isLightUi
import com.example.diplom.ui.design.themedAccent
import com.example.diplom.ui.design.themedBorder
import com.example.diplom.ui.design.themedMutedText
import com.example.diplom.ui.design.themedTextPrimary
import com.example.diplom.ui.design.themedTextSecondary
import com.example.diplom.ui.platform.isWebComposeClient
import com.example.diplom.ui.web.WebScrollColumn
import kotlinx.coroutines.launch

private enum class CalendarViewMode { MONTH, WEEK, DAY }

@Composable
fun CalendarScreen(
    onBack: () -> Unit,
    userRole: UserRole,
    userTicket: String,
) {
    val repo = remember { UserStateRepository() }
    val scheduleRepo = remember { ScheduleRepository() }
    val scope = rememberCoroutineScope()
    val events = remember { mutableStateListOf<CalendarEventDto>() }
    var schedule by remember { mutableStateOf(emptyWeekSchedule()) }

    val today = remember { todayYmd() }
    var viewYear by remember { mutableIntStateOf(today.year) }
    var viewMonth by remember { mutableIntStateOf(today.month) }
    var selectedYmd by remember { mutableStateOf(ymdToIso(today.year, today.month, today.day)) }
    var viewMode by remember { mutableStateOf(CalendarViewMode.MONTH) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var editNote by remember { mutableStateOf<CalendarEventDto?>(null) }

    LaunchedEffect(userTicket) {
        val s = repo.load(userTicket) ?: UserStateDto()
        events.clear()
        events.addAll(s.calendarEvents.filter { it.ownerTicket == userTicket })
        schedule = scheduleRepo.weekForUser(userTicket)
    }

    fun persist() {
        scope.launch {
            val s = repo.load(userTicket) ?: UserStateDto()
            val others = s.calendarEvents.filter { it.ownerTicket != userTicket }
            repo.save(userTicket, s.copy(calendarEvents = others + events.toList()))
        }
    }

    val canEditNotes = userRole != UserRole.ADMIN
    val monthCells = remember(viewYear, viewMonth) { buildMonthGrid(viewYear, viewMonth) }
    val ownNotes = events.filter { it.ownerTicket == userTicket }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 720.dp

        Scaffold(
            floatingActionButton = {
                if (canEditNotes) {
                    FloatingActionButton(
                        onClick = {
                            editNote = null
                            showNoteDialog = true
                        },
                        containerColor = Color(0xFF6366F1),
                        contentColor = Color.White,
                    ) {
                        Icon(Icons.Default.Add, "Добавить заметку")
                    }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(
                        if (isLightUi()) Color(0xFFF4F6FB) else DiplomColors.DarkBg,
                    ),
            ) {
                CalendarStickyHeader(
                    onBack = onBack,
                    viewYear = viewYear,
                    viewMonth = viewMonth,
                    onPrevMonth = {
                        if (viewMonth == 1) {
                            viewMonth = 12
                            viewYear--
                        } else viewMonth--
                    },
                    onNextMonth = {
                        if (viewMonth == 12) {
                            viewMonth = 1
                            viewYear++
                        } else viewMonth++
                    },
                    onToday = {
                        viewYear = today.year
                        viewMonth = today.month
                        selectedYmd = ymdToIso(today.year, today.month, today.day)
                    },
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CalendarViewMode.entries.forEach { mode ->
                        val label = when (mode) {
                            CalendarViewMode.MONTH -> "Месяц"
                            CalendarViewMode.WEEK -> "Неделя"
                            CalendarViewMode.DAY -> "День"
                        }
                        FilterChip(
                            selected = viewMode == mode,
                            onClick = { viewMode = mode },
                            label = { Text(label) },
                        )
                    }
                }

                val dayPanelModifier = if (compact && !isWebComposeClient()) {
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                }
                val dayPanel: @Composable () -> Unit = {
                    DayDetailPanel(
                        modifier = dayPanelModifier,
                        ymd = selectedYmd,
                        schedule = schedule,
                        notes = ownNotes.filter { it.date == selectedYmd },
                        canEdit = canEditNotes,
                        onAddNote = {
                            editNote = null
                            showNoteDialog = true
                        },
                        onEditNote = {
                            editNote = it
                            showNoteDialog = true
                        },
                        onDeleteNote = { note ->
                            events.removeAll { it.id == note.id }
                            persist()
                        },
                    )
                }

                if (isWebComposeClient()) {
                    WebScrollColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                    ) {
                        AnimatedContent(
                            targetState = viewMode,
                            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                            label = "calView",
                        ) { mode ->
                            when (mode) {
                                CalendarViewMode.MONTH -> PremiumMonthGrid(
                                    cells = monthCells,
                                    selectedYmd = selectedYmd,
                                    todayYmd = ymdToIso(today.year, today.month, today.day),
                                    notes = ownNotes,
                                    schedule = schedule,
                                    onSelect = { selectedYmd = it },
                                )
                                CalendarViewMode.WEEK -> WeekTimelineView(
                                    schedule = schedule,
                                    selectedYmd = selectedYmd,
                                    notes = ownNotes,
                                )
                                CalendarViewMode.DAY -> DayAgendaView(
                                    ymd = selectedYmd,
                                    schedule = schedule,
                                    notes = ownNotes,
                                )
                            }
                        }
                        dayPanel()
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .then(if (compact) Modifier.fillMaxWidth() else Modifier.weight(1.15f))
                                .verticalScroll(rememberScrollState()),
                        ) {
                            AnimatedContent(
                                targetState = viewMode,
                                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                                label = "calView",
                            ) { mode ->
                                when (mode) {
                                    CalendarViewMode.MONTH -> PremiumMonthGrid(
                                        cells = monthCells,
                                        selectedYmd = selectedYmd,
                                        todayYmd = ymdToIso(today.year, today.month, today.day),
                                        notes = ownNotes,
                                        schedule = schedule,
                                        onSelect = { selectedYmd = it },
                                    )
                                    CalendarViewMode.WEEK -> WeekTimelineView(
                                        schedule = schedule,
                                        selectedYmd = selectedYmd,
                                        notes = ownNotes,
                                    )
                                    CalendarViewMode.DAY -> DayAgendaView(
                                        ymd = selectedYmd,
                                        schedule = schedule,
                                        notes = ownNotes,
                                    )
                                }
                            }
                        }

                        if (!compact) {
                            DayDetailPanel(
                                modifier = Modifier
                                    .weight(0.85f)
                                    .padding(start = 12.dp, bottom = 12.dp),
                                ymd = selectedYmd,
                                schedule = schedule,
                                notes = ownNotes.filter { it.date == selectedYmd },
                                canEdit = canEditNotes,
                                onAddNote = {
                                    editNote = null
                                    showNoteDialog = true
                                },
                                onEditNote = {
                                    editNote = it
                                    showNoteDialog = true
                                },
                                onDeleteNote = { note ->
                                    events.removeAll { it.id == note.id }
                                    persist()
                                },
                            )
                        }
                    }

                    if (compact) {
                        dayPanel()
                    }
                }
            }
        }
    }

    if (showNoteDialog && canEditNotes) {
        NoteEditorDialog(
            initial = editNote,
            dateYmd = selectedYmd,
            onDismiss = { showNoteDialog = false },
            onSave = { dto ->
                val saved = dto.copy(ownerTicket = userTicket)
                val idx = events.indexOfFirst { it.id == saved.id }
                if (idx >= 0) events[idx] = saved else events.add(saved)
                persist()
                showNoteDialog = false
            },
            userTicket = userTicket,
        )
    }
}

@Composable
private fun CalendarStickyHeader(
    onBack: () -> Unit,
    viewYear: Int,
    viewMonth: Int,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
) {
    val headerBg = if (isLightUi()) Color.White.copy(alpha = 0.88f) else DiplomColors.DarkElevated.copy(alpha = 0.92f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(headerBg)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = themedTextPrimary())
            }
            Column(Modifier.weight(1f)) {
                Text("Календарь", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = themedTextPrimary())
                Text("Ваше расписание и заметки", style = MaterialTheme.typography.bodySmall, color = themedTextSecondary())
            }
            TextButton(onClick = onToday) {
                Icon(Icons.Default.Today, null, modifier = Modifier.size(18.dp), tint = themedAccent())
                Spacer(Modifier.width(4.dp))
                Text("Сегодня", color = themedAccent())
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            IconButton(onClick = onPrevMonth) {
                Icon(Icons.Default.ChevronLeft, null, tint = themedTextPrimary())
            }
            Text(
                monthTitleRu(viewYear, viewMonth),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = themedTextPrimary(),
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            IconButton(onClick = onNextMonth) {
                Icon(Icons.Default.ChevronRight, null, tint = themedTextPrimary())
            }
        }
    }
}

@Composable
private fun PremiumMonthGrid(
    cells: List<com.example.diplom.ui.calendar.CalendarDayCell?>,
    selectedYmd: String,
    todayYmd: String,
    notes: List<CalendarEventDto>,
    schedule: WeekScheduleDto,
    onSelect: (String) -> Unit,
) {
    val weekdays = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    Column(Modifier.fillMaxWidth().padding(bottom = 80.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            weekdays.forEach {
                Text(it, style = MaterialTheme.typography.labelMedium, color = themedMutedText(), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(8.dp))
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { cell ->
                    if (cell == null) {
                        Spacer(Modifier.weight(1f).height(92.dp))
                    } else {
                        val lessons = scheduledLessonsOnDate(schedule, cell.ymd)
                        val dayNotes = notes.count { it.date == cell.ymd }
                        PremiumDayCard(
                            day = cell.day,
                            ymd = cell.ymd,
                            lessonCount = lessons.size,
                            noteCount = dayNotes,
                            previewLesson = lessons.firstOrNull()?.subject,
                            isSelected = cell.ymd == selectedYmd,
                            isToday = cell.ymd == todayYmd,
                            onClick = { onSelect(cell.ymd) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumDayCard(
    day: Int,
    ymd: String,
    lessonCount: Int,
    noteCount: Int,
    previewLesson: String?,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val gradient = Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF8B5CF6)))
    val surfaceBg = if (isLightUi()) Color.White else DiplomColors.DarkElevated
    val cardModifier = modifier
        .height(92.dp)
        .diplomShadow(if (isSelected) 14.dp else 6.dp, shape)
        .clip(shape)
        .then(
            if (isSelected) {
                Modifier.background(gradient)
            } else {
                Modifier
                    .background(surfaceBg)
                    .border(
                        width = if (isToday) 2.dp else 1.dp,
                        color = if (isToday) themedAccent() else themedBorder().copy(alpha = 0.4f),
                        shape = shape,
                    )
            },
        )
        .clickable(onClick = onClick)
        .padding(8.dp)

    Box(modifier = cardModifier) {
        Column(Modifier.fillMaxSize()) {
            Text(
                day.toString().padStart(2, '0'),
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else themedTextPrimary(),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            if (lessonCount > 0) {
                Text(
                    "Пары: $lessonCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) Color.White.copy(0.9f) else themedAccent(),
                    maxLines = 1,
                )
            }
            if (noteCount > 0) {
                Text(
                    "Заметки: $noteCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) Color.White.copy(0.85f) else themedTextSecondary(),
                )
            }
            previewLesson?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) Color.White.copy(0.8f) else themedMutedText(),
                )
            }
        }
    }
}

@Composable
private fun WeekTimelineView(
    schedule: WeekScheduleDto,
    selectedYmd: String,
    notes: List<CalendarEventDto>,
) {
    val lessons = scheduledLessonsOnDate(schedule, selectedYmd)
    Column(Modifier.fillMaxWidth().padding(bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Неделя · выбранный день", fontWeight = FontWeight.SemiBold, color = themedTextPrimary())
        schedule.columns.forEach { col ->
            GlassCard(Modifier.fillMaxWidth()) {
                Text(col.dayOfWeek, fontWeight = FontWeight.Bold, color = themedAccent())
                col.lessons.forEach { l ->
                    LessonBlockCard(l)
                }
                if (col.lessons.isEmpty()) {
                    Text("Нет пар", color = themedMutedText(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (lessons.isEmpty() && schedule.columns.all { it.lessons.isEmpty() }) {
            EmptyCalendarState()
        }
    }
}

@Composable
private fun DayAgendaView(ymd: String, schedule: WeekScheduleDto, notes: List<CalendarEventDto>) {
    val lessons = scheduledLessonsOnDate(schedule, ymd)
    val dayNotes = notes.filter { it.date == ymd }
    Column(Modifier.fillMaxWidth().padding(bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        parseYmd(ymd)?.let { y ->
            Text(formatDateRu(y.year, y.month, y.day), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = themedTextPrimary())
        }
        lessons.forEach { LessonBlockCard(it) }
        dayNotes.forEach { NoteCard(it, onEdit = {}, onDelete = {}, canEdit = false) }
        if (lessons.isEmpty() && dayNotes.isEmpty()) EmptyCalendarState()
    }
}

@Composable
private fun DayDetailPanel(
    modifier: Modifier,
    ymd: String,
    schedule: WeekScheduleDto,
    notes: List<CalendarEventDto>,
    canEdit: Boolean,
    onAddNote: () -> Unit,
    onEditNote: (CalendarEventDto) -> Unit,
    onDeleteNote: (CalendarEventDto) -> Unit,
) {
    val lessons = scheduledLessonsOnDate(schedule, ymd)
    GlassCard(modifier = modifier.fillMaxWidth(), fillAlpha = if (isLightUi()) 0.65f else 0.12f) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            parseYmd(ymd)?.let { y ->
                Text(formatDateRu(y.year, y.month, y.day), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = themedTextPrimary())
            }
            Text("Пары", style = MaterialTheme.typography.labelLarge, color = themedMutedText())
            if (lessons.isEmpty()) {
                Text("На этот день пар нет", color = themedTextSecondary(), style = MaterialTheme.typography.bodySmall)
            } else {
                lessons.forEach { LessonBlockCard(it) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Заметки", style = MaterialTheme.typography.labelLarge, color = themedMutedText())
                if (canEdit) {
                    TextButton(onClick = onAddNote) { Text("+ Добавить", color = themedAccent()) }
                }
            }
            if (notes.isEmpty()) {
                EmptyCalendarState(compact = true)
            } else {
                notes.sortedBy { it.title.lowercase() }.forEach { note ->
                    NoteCard(note, onEdit = { onEditNote(note) }, onDelete = { onDeleteNote(note) }, canEdit = canEdit)
                }
            }
        }
    }
}

@Composable
private fun LessonBlockCard(lesson: ScheduledLessonDto) {
    val accent = Color(0xFF4F46E5)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(10.dp),
    ) {
        Text(lesson.subject, fontWeight = FontWeight.SemiBold, color = themedTextPrimary())
        Text(lesson.timeRange, style = MaterialTheme.typography.bodySmall, color = themedAccent())
        Text(
            buildString {
                append(lessonKindShortRu(lesson.lessonKind))
                append(" · ")
                append(lesson.room)
                lesson.mergedGroupsLabel?.let { append(" · $it") }
            },
            style = MaterialTheme.typography.labelSmall,
            color = themedTextSecondary(),
        )
    }
}

@Composable
private fun NoteCard(
    note: CalendarEventDto,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    canEdit: Boolean,
) {
    val c = noteColorCompose(note.color)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.copy(alpha = 0.1f))
            .border(1.dp, c.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .then(if (canEdit) Modifier.clickable(onClick = onEdit) else Modifier)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(note.title, fontWeight = FontWeight.SemiBold, color = themedTextPrimary())
            if (note.description.isNotBlank()) {
                Text(note.description, style = MaterialTheme.typography.bodySmall, color = themedTextSecondary())
            }
        }
        if (canEdit) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = themedTextSecondary())
            }
        }
    }
}

@Composable
private fun EmptyCalendarState(compact: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 12.dp else 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("На этот день событий нет", color = themedTextSecondary(), style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteEditorDialog(
    initial: CalendarEventDto?,
    dateYmd: String,
    userTicket: String,
    onDismiss: () -> Unit,
    onSave: (CalendarEventDto) -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    var color by remember { mutableStateOf(initial?.color ?: "violet") }
    val colors = listOf("violet", "blue", "green", "red", "yellow")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новая заметка" else "Редактировать заметку") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Дата: $dateYmd", color = themedTextSecondary(), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Заголовок") },
                    placeholder = { Text("Например: купить клей на пару") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание") },
                    placeholder = { Text("По желанию") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Text("Цвет метки", style = MaterialTheme.typography.labelMedium, color = themedTextSecondary())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colors.forEach { key ->
                        val sel = color == key
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(noteColorCompose(key))
                                .border(if (sel) 2.dp else 0.dp, themedAccent(), CircleShape)
                                .clickable { color = key },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isBlank()) return@TextButton
                    val id = initial?.id ?: (kotlin.random.Random.nextLong(1_000_000, 9_000_000_000))
                    onSave(
                        CalendarEventDto(
                            id = id,
                            date = dateYmd,
                            title = title.trim(),
                            ownerTicket = userTicket,
                            description = description.trim(),
                            color = color,
                        ),
                    )
                },
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private fun formatDateRu(year: Int, month: Int, day: Int): String {
    val months = listOf("", "января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
    return "$day ${months.getOrElse(month) { month.toString() }} $year"
}
