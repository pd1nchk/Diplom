package com.example.diplom.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.diplom.api.AdminCourseRefDto
import com.example.diplom.api.AdminGroupRefDto

@Composable
fun AdminCatalogPanel(state: AdminCenterUiState, vm: AdminCenterViewModel) {
    val labels = listOf("Предметы", "Группы", "Факультеты", "Кафедры", "Специальности")
    val subIndex = CatalogSub.entries.indexOf(state.catalogSub).coerceAtLeast(0)
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        AdminSubTabs(
            labels = labels,
            selected = subIndex,
            onSelect = { idx -> vm.setCatalogSub(CatalogSub.entries[idx]) },
            modifier = Modifier.padding(bottom = 12.dp),
        )
        when (state.catalogSub) {
            CatalogSub.SUBJECTS -> SubjectsTab(state, vm)
            CatalogSub.GROUPS -> GroupsTab(state, vm)
            CatalogSub.FACULTIES -> FacultiesTab(state, vm)
            CatalogSub.DEPARTMENTS -> DepartmentsTab(state, vm)
            CatalogSub.SPECIALTIES -> SpecialtiesTab(state, vm)
        }
    }
}

@Composable
private fun SubjectsTab(state: AdminCenterUiState, vm: AdminCenterViewModel) {
    val refs = state.scheduleRefs
    if (refs == null) {
        AdminEmptyState("Нет данных", "Обновите данные.", "Обновить", { vm.refreshAll() })
        return
    }
    val q = state.subjectSearch.trim().lowercase()
    val courses = refs.courses.filter { c -> q.isEmpty() || c.name.lowercase().contains(q) }
    val counts = state.lessons.groupingBy { it.courseId }.eachCount()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.subjectSearch,
            onValueChange = vm::setSubjectSearch,
            label = { Text("Поиск предмета") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(onClick = { vm.showNewCourse(true) }, modifier = Modifier.fillMaxWidth()) {
            Text("+ Создать предмет")
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 720.dp
            if (wide) AdminTableHeader(listOf("Название", "Преподаватель", "Групп", "Занятий", ""))
            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(courses, key = { it.id }) { c ->
                    SubjectRow(c, counts[c.id] ?: 0, wide, vm)
                }
            }
        }
    }
}

@Composable
private fun SubjectRow(
    c: AdminCourseRefDto,
    lessonCount: Int,
    wide: Boolean,
    vm: AdminCenterViewModel,
) {
    AdminSurfaceCard(Modifier.fillMaxWidth()) {
        if (wide) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(c.name, Modifier.weight(1.2f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(c.teacherFullName, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${c.linkedGroupIds.size}", Modifier.weight(0.4f), style = MaterialTheme.typography.bodySmall)
                Text("$lessonCount", Modifier.weight(0.4f), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.weight(1.2f), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { vm.openEditCourse(c.id) }) { Text("Изменить") }
                    TextButton(onClick = {
                        vm.setScheduleFilterCourse(c.id)
                        vm.setNav(AdminNavSection.SCHEDULE)
                    }) { Text("Расписание") }
                    TextButton(onClick = { vm.requestDeleteCourse(c.id) }) { Text("Удалить") }
                }
            }
        } else {
            Column(Modifier.padding(12.dp)) {
                Text(c.name, fontWeight = FontWeight.SemiBold)
                Text("Преподаватель: ${c.teacherFullName}", style = MaterialTheme.typography.bodySmall)
                Text("Групп: ${c.linkedGroupIds.size} · Занятий: $lessonCount", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { vm.openEditCourse(c.id) }) { Text("Изменить") }
                    TextButton(onClick = {
                        vm.setScheduleFilterCourse(c.id)
                        vm.setNav(AdminNavSection.SCHEDULE)
                    }) { Text("Расписание") }
                    TextButton(onClick = { vm.requestDeleteCourse(c.id) }) { Text("Удалить") }
                }
            }
        }
    }
}

@Composable
private fun GroupsTab(state: AdminCenterUiState, vm: AdminCenterViewModel) {
    val refs = state.scheduleRefs
    if (refs == null) {
        AdminEmptyState("Нет данных", "Обновите данные.")
        return
    }
    var groupFilter by remember { mutableStateOf("") }
    val q = groupFilter.trim().lowercase()
    val groups = refs.groups.filter { g -> q.isEmpty() || adminGroupLabel(g).lowercase().contains(q) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = groupFilter,
            onValueChange = { groupFilter = it },
            label = { Text("Поиск группы") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 720.dp
            if (wide) AdminTableHeader(listOf("Группа", "ID"))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(groups, key = { it.id }) { g ->
                    GroupRow(g, wide)
                }
            }
        }
    }
}

@Composable
private fun GroupRow(g: AdminGroupRefDto, wide: Boolean) {
    AdminSurfaceCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (wide) {
                Text(adminGroupLabel(g), Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Text("id=${g.id}", Modifier.weight(0.4f), style = MaterialTheme.typography.labelSmall)
            } else {
                Column {
                    Text(adminGroupLabel(g), fontWeight = FontWeight.Medium)
                    Text("id=${g.id}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun FacultiesTab(state: AdminCenterUiState, vm: AdminCenterViewModel) {
    val u = state.userInputs
    CatalogListSection(
        title = "Факультеты",
        rows = state.catalog.faculties.map { it.id to it.name },
        onDelete = { vm.deleteFaculty(it) },
        addLabel = "Новый факультет",
        addValue = u.facultyName,
        onAddValueChange = { vm.setUserInputs { copy(facultyName = it) } },
        onAdd = {
            vm.addFaculty(u.facultyName)
            vm.setUserInputs { copy(facultyName = "") }
        },
    )
}

@Composable
private fun DepartmentsTab(state: AdminCenterUiState, vm: AdminCenterViewModel) {
    val u = state.userInputs
    val fId = u.deptFacultyId ?: state.catalog.faculties.firstOrNull()?.id
    CatalogListSection(
        title = "Кафедры",
        rows = state.catalog.departments.map { it.id to it.name },
        onDelete = { vm.deleteDepartment(it) },
        addLabel = "Новая кафедра",
        addValue = u.deptName,
        onAddValueChange = { vm.setUserInputs { copy(deptName = it) } },
        onAdd = { if (fId != null) vm.addDepartment(fId, u.deptName) },
        addEnabled = fId != null && u.deptName.isNotBlank(),
    )
}

@Composable
private fun SpecialtiesTab(state: AdminCenterUiState, vm: AdminCenterViewModel) {
    val u = state.userInputs
    val fId = u.specFacultyId ?: state.catalog.faculties.firstOrNull()?.id
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 720.dp
            if (wide) AdminTableHeader(listOf("Код", "Название", "Профиль", ""))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(state.catalog.specialties, key = { it.id }) { s ->
                    AdminSurfaceCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (wide) {
                                Text(s.code, Modifier.weight(0.5f), style = MaterialTheme.typography.bodySmall)
                                Text(s.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(s.profile, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            } else {
                                Text("${s.code} ${s.name}", Modifier.weight(1f))
                            }
                            TextButton(onClick = { vm.deleteSpecialty(s.id) }) { Text("Удалить") }
                        }
                    }
                }
            }
        }
        OutlinedTextField(u.specCode, { vm.setUserInputs { copy(specCode = it) } }, label = { Text("Код") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(u.specName, { vm.setUserInputs { copy(specName = it) } }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(u.specProfile, { vm.setUserInputs { copy(specProfile = it) } }, label = { Text("Профиль") }, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = { if (fId != null) vm.addSpecialty(fId, u.specCode, u.specName, u.specProfile) },
            enabled = fId != null && u.specCode.isNotBlank() && u.specName.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Добавить специальность") }
    }
}

@Composable
private fun CatalogListSection(
    title: String,
    rows: List<Pair<Long, String>>,
    onDelete: (Long) -> Unit,
    addLabel: String,
    addValue: String,
    onAddValueChange: (String) -> Unit,
    onAdd: () -> Unit,
    addEnabled: Boolean = addValue.isNotBlank(),
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 720.dp
            if (wide) AdminTableHeader(listOf("Название", ""))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(rows, key = { it.first }) { (id, name) ->
                    AdminSurfaceCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(name, Modifier.weight(1f))
                            TextButton(onClick = { onDelete(id) }) { Text("Удалить") }
                        }
                    }
                }
            }
        }
        OutlinedTextField(addValue, onAddValueChange, label = { Text(addLabel) }, modifier = Modifier.fillMaxWidth())
        Button(onClick = onAdd, enabled = addEnabled, modifier = Modifier.fillMaxWidth()) { Text("Добавить") }
    }
}
