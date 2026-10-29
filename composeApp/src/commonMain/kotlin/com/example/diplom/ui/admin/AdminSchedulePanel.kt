package com.example.diplom.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.diplom.api.AdminCourseRefDto
import com.example.diplom.api.AdminGroupRefDto
import com.example.diplom.api.AdminLessonSummaryDto
import com.example.diplom.api.AdminRescheduleRequestDto
import com.example.diplom.api.AdminRoomDto
import com.example.diplom.time.localDateFromEpochMillis
import com.example.diplom.time.localDateStartEpochMillis
import kotlinx.datetime.LocalDate

@Composable
fun AdminSchedulePanel(state: AdminCenterUiState, vm: AdminCenterViewModel) {
    val subIndex = when (state.scheduleSub) {
        ScheduleSub.LESSONS -> 0
        ScheduleSub.RESCHEDULES -> 1
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        AdminSubTabs(
            labels = listOf("Занятия", "Переносы"),
            selected = subIndex,
            onSelect = { idx ->
                vm.setScheduleSub(if (idx == 0) ScheduleSub.LESSONS else ScheduleSub.RESCHEDULES)
            },
            modifier = Modifier.padding(bottom = 12.dp),
        )
        when (state.scheduleSub) {
            ScheduleSub.LESSONS -> AdminLessonsSection(state, vm)
            ScheduleSub.RESCHEDULES -> AdminRescheduleSection(state, vm)
        }
    }
    if (state.showLessonEditor) {
        LessonEditorDialog(state, vm)
    }
}

@Composable
private fun AdminLessonsSection(state: AdminCenterUiState, vm: AdminCenterViewModel) {
    val refs = state.scheduleRefs
    if (refs == null) {
        AdminEmptyState(
            title = "Нет данных",
            description = "Нажмите «Обновить» вверху экрана.",
            actionLabel = "Обновить",
            onAction = { vm.refreshAll() },
        )
        return
    }
    val q = state.lessonSearch.trim().lowercase()
    val filtered = state.lessons.filter { l ->
        val courseOk = state.scheduleFilterCourseId?.let { l.courseId == it } ?: true
        val searchOk = q.isEmpty() ||
            l.courseName.lowercase().contains(q) ||
            l.groupDisplayName.lowercase().contains(q) ||
            l.teacherName.lowercase().contains(q) ||
            l.roomName.lowercase().contains(q)
        courseOk && searchOk
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.lessonSearch,
            onValueChange = vm::setLessonSearch,
            label = { Text("Поиск занятий") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        CourseFilterDropdown(
            courses = refs.courses,
            selectedId = state.scheduleFilterCourseId,
            onSelect = vm::setScheduleFilterCourse,
        )
        Button(onClick = { vm.openNewLessonEditor() }, modifier = Modifier.fillMaxWidth()) {
            Text("+ Занятие")
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 720.dp
            if (wide) {
                AdminTableHeader(
                    cells = listOf("Предмет", "Группа", "Время", "Аудитория", "Статус", ""),
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            if (filtered.isEmpty()) {
                AdminEmptyState(
                    title = "Нет занятий",
                    description = "Создайте занятие или сбросьте фильтры.",
                    actionLabel = "+ Занятие",
                    onAction = { vm.openNewLessonEditor() },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(filtered, key = { it.id }) { lesson ->
                        CompactLessonRow(lesson = lesson, wide = wide, vm = vm)
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseFilterDropdown(
    courses: List<AdminCourseRefDto>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = courses.firstOrNull { it.id == selectedId }?.name ?: "Все предметы"
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Все предметы") },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            courses.forEach { c ->
                DropdownMenuItem(
                    text = { Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        onSelect(c.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CompactLessonRow(
    lesson: AdminLessonSummaryDto,
    wide: Boolean,
    vm: AdminCenterViewModel,
) {
    var menuOpen by remember { mutableStateOf(false) }
    AdminSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        if (wide) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(lesson.courseName, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(lesson.groupDisplayName, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${formatInstantRu(lesson.startIso)} — ${formatInstantRu(lesson.endIso)}",
                    Modifier.weight(1.2f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(lesson.roomName, Modifier.weight(0.7f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                AdminStatusChip(lesson.status)
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Действия")
                    }
                    LessonActionsMenu(menuOpen, { menuOpen = false }, lesson, vm)
                }
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(lesson.courseName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(lesson.groupDisplayName, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${formatInstantRu(lesson.startIso)} · ${lesson.roomName}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    AdminStatusChip(lesson.status, modifier = Modifier.padding(top = 4.dp))
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Действия")
                    }
                    LessonActionsMenu(menuOpen, { menuOpen = false }, lesson, vm)
                }
            }
        }
    }
}

@Composable
private fun LessonActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    lesson: AdminLessonSummaryDto,
    vm: AdminCenterViewModel,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Изменить") },
            onClick = {
                vm.openEditLesson(lesson)
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text("Дублировать") },
            onClick = {
                vm.duplicateLesson(lesson)
                onDismiss()
            },
        )
        if (lesson.status != "CANCELLED") {
            DropdownMenuItem(
                text = { Text("Отменить") },
                onClick = {
                    vm.cancelLesson(lesson.id)
                    onDismiss()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminRescheduleSection(state: AdminCenterUiState, vm: AdminCenterViewModel) {
    var items by remember { mutableStateOf<List<AdminRescheduleRequestDto>>(emptyList()) }
    var banner by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.loadReschedule { items = it } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        banner?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = { vm.loadReschedule { items = it } }, modifier = Modifier.fillMaxWidth()) {
            Text("Обновить заявки")
        }
        if (items.isEmpty()) {
            AdminEmptyState("Нет заявок", "Все переносы обработаны или список пуст.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { r ->
                    RescheduleRequestCard(r, state.rooms, vm, onReload = { vm.loadReschedule { items = it } }, onBanner = { banner = it })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RescheduleRequestCard(
    r: AdminRescheduleRequestDto,
    rooms: List<AdminRoomDto>,
    vm: AdminCenterViewModel,
    onReload: () -> Unit,
    onBanner: (String?) -> Unit,
) {
    val defaultDate = r.proposedDateIso.ifBlank { isoToLocalDatePart(r.proposedStartIso) }
    val defaultFrom = r.proposedTimeFrom.ifBlank { isoToLocalTimePart(r.proposedStartIso) }
    val defaultTo = r.proposedTimeTo.ifBlank { isoToLocalTimePart(r.proposedEndIso) }
    var dateIso by remember(r.id, defaultDate) { mutableStateOf(defaultDate) }
    var timeFrom by remember(r.id, defaultFrom) { mutableStateOf(defaultFrom) }
    var timeTo by remember(r.id, defaultTo) { mutableStateOf(defaultTo) }
    var showDatePicker by remember(r.id) { mutableStateOf(false) }
    var showFromPicker by remember(r.id) { mutableStateOf(false) }
    var showToPicker by remember(r.id) { mutableStateOf(false) }
    var roomId by remember(r.id, rooms) {
        mutableStateOf(rooms.firstOrNull { it.name == r.roomName }?.id)
    }
    AdminSurfaceCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${r.subject} · ${r.groupName}", fontWeight = FontWeight.Medium)
            Text(
                "Было: ${formatInstantRu(r.currentStartIso)} — ${formatInstantRu(r.currentEndIso)} · ${r.roomName}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Запрос: $defaultDate, $defaultFrom — $defaultTo",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Утвердить перенос на:", style = MaterialTheme.typography.labelMedium)
            OutlinedTextField(
                value = dateIso,
                onValueChange = {},
                readOnly = true,
                label = { Text("Дата") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = null)
                    }
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = timeFrom,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("С") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { showFromPicker = true }) {
                            Icon(Icons.Default.Schedule, contentDescription = null)
                        }
                    },
                )
                OutlinedTextField(
                    value = timeTo,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("До") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { showToPicker = true }) {
                            Icon(Icons.Default.Schedule, contentDescription = null)
                        }
                    },
                )
            }
            if (rooms.isNotEmpty()) {
                var roomMenu by remember { mutableStateOf(false) }
                val roomLabel = rooms.firstOrNull { it.id == roomId }?.name ?: "Выберите аудиторию"
                TextButton(onClick = { roomMenu = true }) { Text("Аудитория: $roomLabel") }
                DropdownMenu(expanded = roomMenu, onDismissRequest = { roomMenu = false }) {
                    rooms.forEach { room ->
                        DropdownMenuItem(
                            text = { Text(room.name) },
                            onClick = {
                                roomId = room.id
                                roomMenu = false
                            },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val startIso = buildUtcIsoFromParts(dateIso, timeFrom)
                            ?: normalizeInstantIsoForApi(r.proposedStartIso)
                        val endIso = buildUtcIsoFromParts(dateIso, timeTo)
                            ?: normalizeInstantIsoForApi(r.proposedEndIso)
                        if (startIso == null || endIso == null) {
                            onBanner("Неверная дата или время")
                            return@Button
                        }
                        vm.approveReschedule(r, startIso, endIso, roomId) { err ->
                            if (err == null) {
                                onBanner("Одобрено №${r.id}")
                                onReload()
                            } else {
                                onBanner(err)
                            }
                        }
                    },
                ) { Text("Одобрить") }
                OutlinedButton(
                    onClick = {
                        vm.rejectReschedule(r) { err ->
                            if (err == null) {
                                onBanner("Отклонено №${r.id}")
                                onReload()
                            } else {
                                onBanner(err)
                            }
                        }
                    },
                ) { Text("Отклонить") }
            }
        }
    }
    if (showDatePicker) {
        val initMs = runCatching { LocalDate.parse(dateIso) }.getOrNull()?.let { localDateStartEpochMillis(it) }
        val dateState = rememberDatePickerState(initialSelectedDateMillis = initMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { ms ->
                        dateIso = localDateFromEpochMillis(ms).toString()
                    }
                    showDatePicker = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Отмена") } },
        ) { DatePicker(state = dateState) }
    }
    if (showFromPicker) {
        val (h, m) = parseHm(timeFrom) ?: (8 to 0)
        val t = rememberTimePickerState(h, m, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showFromPicker = false },
            title = { Text("Время начала") },
            text = { TimePicker(state = t) },
            confirmButton = {
                TextButton(onClick = {
                    timeFrom = formatHm(t.hour, t.minute)
                    showFromPicker = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { showFromPicker = false }) { Text("Отмена") } },
        )
    }
    if (showToPicker) {
        val (h, m) = parseHm(timeTo) ?: (9 to 30)
        val t = rememberTimePickerState(h, m, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showToPicker = false },
            title = { Text("Время окончания") },
            text = { TimePicker(state = t) },
            confirmButton = {
                TextButton(onClick = {
                    timeTo = formatHm(t.hour, t.minute)
                    showToPicker = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { showToPicker = false }) { Text("Отмена") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LessonEditorDialog(state: AdminCenterUiState, vm: AdminCenterViewModel) {
    val refs = state.scheduleRefs
    val ed = state.lessonEditor
    val isEdit = ed.lessonId != null
    AlertDialog(
        onDismissRequest = { vm.closeLessonEditor() },
        title = { Text(if (isEdit) "Редактирование занятия" else "Новое занятие") },
        text = {
            if (refs == null) {
                Text("Загрузите данные расписания.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RefDropdown(
                        label = "Предмет",
                        options = refs.courses.map { it.id to it.name },
                        selectedId = ed.courseId,
                        onSelect = { vm.updateLessonEditor { copy(courseId = it) } },
                    )
                    RefDropdown(
                        label = "Группа",
                        options = refs.groups.map { it.id to adminGroupLabel(it) },
                        selectedId = ed.groupId,
                        onSelect = { vm.updateLessonEditor { copy(groupId = it) } },
                    )
                    RefDropdown(
                        label = "Аудитория",
                        options = state.rooms.map { it.id to it.name },
                        selectedId = state.rooms.firstOrNull { it.name == ed.room }?.id,
                        onSelect = { id ->
                            val name = state.rooms.firstOrNull { it.id == id }?.name.orEmpty()
                            vm.updateLessonEditor { copy(room = name) }
                        },
                    )
                    RefDropdown(
                        label = "Тип занятия",
                        options = LessonKindOption.entries.map { it.ordinal.toLong() to it.label },
                        selectedId = ed.kind.ordinal.toLong(),
                        onSelect = { ord ->
                            LessonKindOption.entries.getOrNull(ord.toInt())?.let { k ->
                                vm.updateLessonEditor { copy(kind = k) }
                            }
                        },
                    )
                    AdminMonthCalendar(
                        selected = ed.lessonDate,
                        onSelect = { d -> vm.updateLessonEditor { copy(lessonDate = d) } },
                    )
                    AdminTimeRangePickers(
                        startHour = ed.startHour,
                        startMinute = ed.startMinute,
                        endHour = ed.endHour,
                        endMinute = ed.endMinute,
                        onStart = { h, m -> vm.updateLessonEditor { copy(startHour = h, startMinute = m) } },
                        onEnd = { h, m -> vm.updateLessonEditor { copy(endHour = h, endMinute = m) } },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { vm.saveLessonEditor() },
                enabled = refs != null,
            ) { Text(if (isEdit) "Сохранить" else "Создать") }
        },
        dismissButton = {
            TextButton(onClick = { vm.closeLessonEditor() }) { Text("Отмена") }
        },
    )
}

@Composable
private fun RefDropdown(
    label: String,
    options: List<Pair<Long, String>>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedId }?.second ?: "Выберите…"
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(selectedLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    },
                )
            }
        }
    }
}
