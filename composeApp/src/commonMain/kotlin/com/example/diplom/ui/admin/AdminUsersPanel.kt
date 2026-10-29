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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.diplom.api.ForgotPasswordAdminItemDto
import com.example.diplom.api.SpecialtyDto
import com.example.diplom.api.TeacherAdminDto

@Composable
fun AdminUsersPanel(state: AdminCenterUiState, vm: AdminCenterViewModel) {
    val subIndex = when (state.usersSub) {
        UsersSub.STUDENTS -> 0
        UsersSub.TEACHERS -> 1
        UsersSub.PASSWORDS -> 2
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        AdminSubTabs(
            labels = listOf("Студенты", "Преподаватели", "Сброс пароля"),
            selected = subIndex,
            onSelect = { idx ->
                vm.setUsersSub(
                    when (idx) {
                        0 -> UsersSub.STUDENTS
                        1 -> UsersSub.TEACHERS
                        else -> UsersSub.PASSWORDS
                    },
                )
            },
            modifier = Modifier.padding(bottom = 12.dp),
        )
        when (state.usersSub) {
            UsersSub.STUDENTS -> StudentsSection(state, vm)
            UsersSub.TEACHERS -> TeachersSection(state, vm)
            UsersSub.PASSWORDS -> PasswordResetSection(vm)
        }
    }
}

@Composable
private fun StudentsSection(state: AdminCenterUiState, vm: AdminCenterViewModel) {
    val u = state.userInputs
    var listQuery by remember { mutableStateOf("") }
    val q = listQuery.trim().lowercase()
    val students = state.catalog.students.filter { s ->
        q.isEmpty() || s.fullName.lowercase().contains(q) ||
            "${s.course}${s.groupNumber}".contains(q)
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = listQuery,
            onValueChange = { listQuery = it },
            label = { Text("Поиск студентов") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 720.dp
            if (wide) {
                AdminTableHeader(listOf("ФИО", "Курс", "Группа", ""))
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(students, key = { it.id }) { s ->
                    AdminSurfaceCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (wide) {
                                Text(s.fullName, Modifier.weight(1.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${s.course}", Modifier.weight(0.5f), style = MaterialTheme.typography.bodySmall)
                                Text("гр. ${s.groupNumber}", Modifier.weight(0.5f), style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text(
                                    "${s.fullName} · ${s.course} к., гр.${s.groupNumber}",
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            TextButton(onClick = { vm.deleteStudent(s.id) }) { Text("Удалить") }
                        }
                    }
                }
            }
        }
        AdminListDivider()
        Text("Добавить студента", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = u.fullName,
            onValueChange = { vm.setUserInputs { copy(fullName = it) } },
            label = { Text("ФИО") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = u.ticket,
            onValueChange = { vm.setUserInputs { copy(ticket = it.filter { ch -> ch.isLetterOrDigit() }.take(32)) } },
            label = { Text("Билет (логин и пароль)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = u.course,
                onValueChange = { vm.setUserInputs { copy(course = it) } },
                label = { Text("Курс") },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = u.groupNum,
                onValueChange = { vm.setUserInputs { copy(groupNum = it) } },
                label = { Text("Группа") },
                modifier = Modifier.weight(1f),
            )
        }
        SpecialtySearchDropdown(
            specialties = state.catalog.specialties,
            selectedId = u.specialtyId,
            onSelect = { vm.setUserInputs { copy(specialtyId = it) } },
        )
        val specId = u.specialtyId ?: state.catalog.specialties.firstOrNull()?.id
        Button(
            onClick = {
                if (specId != null && u.fullName.isNotBlank() && u.ticket.isNotBlank()) {
                    vm.addStudent(
                        u.fullName,
                        u.ticket,
                        u.ticket,
                        u.course.toIntOrNull() ?: 1,
                        u.groupNum.toIntOrNull() ?: 1,
                        specId,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = specId != null && u.fullName.isNotBlank() && u.ticket.isNotBlank(),
        ) { Text("Добавить студента") }
    }
}

@Composable
private fun SpecialtySearchDropdown(
    specialties: List<SpecialtyDto>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val selected = specialties.firstOrNull { it.id == selectedId }
    val label = selected?.let { "${it.code} — ${it.name}" } ?: "Специальность"
    val filtered = specialties.filter { s ->
        val q = query.trim().lowercase()
        q.isEmpty() ||
            s.name.lowercase().contains(q) ||
            s.code.lowercase().contains(q) ||
            s.profile.lowercase().contains(q)
    }
    Column {
        Text("Специальность", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = query.ifEmpty { label },
            onValueChange = {
                query = it
                expanded = true
            },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            singleLine = true,
            label = { Text("Поиск специальности") },
        )
        DropdownMenu(
            expanded = expanded && filtered.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            filtered.take(30).forEach { s ->
                DropdownMenuItem(
                    text = { Text("${s.code} — ${s.name}", maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        onSelect(s.id)
                        query = ""
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun TeachersSection(state: AdminCenterUiState, vm: AdminCenterViewModel) {
    val u = state.userInputs
    var listQuery by remember { mutableStateOf("") }
    val qq = listQuery.trim().lowercase()
    val teachers = state.catalog.teachers.filter { t ->
        qq.isEmpty() || t.fullName.lowercase().contains(qq) || t.degree.lowercase().contains(qq)
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = listQuery,
            onValueChange = { listQuery = it },
            label = { Text("Поиск преподавателей") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Text(
            "Новый преподаватель: билет tchdir<id>, пароль 1234.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 720.dp
            if (wide) AdminTableHeader(listOf("ФИО", "Степень", ""))
            LazyColumn(contentPadding = PaddingValues(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(teachers, key = { it.id }) { t ->
                    TeacherRow(t, wide, vm)
                }
            }
        }
        AdminListDivider()
        Text("Добавить преподавателя", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = u.teacherName,
            onValueChange = { vm.setUserInputs { copy(teacherName = it) } },
            label = { Text("ФИО") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = u.teacherDegree,
            onValueChange = { vm.setUserInputs { copy(teacherDegree = it) } },
            label = { Text("Степень / должность") },
            modifier = Modifier.fillMaxWidth(),
        )
        DepartmentDropdown(
            departments = state.catalog.departments,
            selectedId = u.teacherDepartmentId,
            onSelect = { vm.setUserInputs { copy(teacherDepartmentId = it) } },
        )
        val depId = u.teacherDepartmentId ?: state.catalog.departments.firstOrNull()?.id
        Button(
            onClick = {
                if (depId != null && u.teacherName.isNotBlank()) {
                    vm.addTeacher(u.teacherName, u.teacherDegree, depId)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = depId != null && u.teacherName.isNotBlank(),
        ) { Text("Добавить преподавателя") }
    }
}

@Composable
private fun TeacherRow(t: TeacherAdminDto, wide: Boolean, vm: AdminCenterViewModel) {
    AdminSurfaceCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (wide) {
                Text(t.fullName, Modifier.weight(1f), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.degree, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Column(Modifier.weight(1f)) {
                    Text(t.fullName, fontWeight = FontWeight.Medium)
                    Text(t.degree, style = MaterialTheme.typography.bodySmall)
                }
            }
            TextButton(onClick = { vm.deleteTeacher(t.id) }) { Text("Удалить") }
        }
    }
}

@Composable
private fun DepartmentDropdown(
    departments: List<com.example.diplom.api.DepartmentDto>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = departments.firstOrNull { it.id == selectedId }?.name ?: "Кафедра"
    Column {
        Text("Кафедра", style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            departments.forEach { d ->
                DropdownMenuItem(
                    text = { Text(d.name) },
                    onClick = {
                        onSelect(d.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PasswordResetSection(vm: AdminCenterViewModel) {
    var items by remember { mutableStateOf<List<ForgotPasswordAdminItemDto>>(emptyList()) }
    LaunchedEffect(Unit) { vm.loadForgot { items = it } }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { vm.loadForgot { items = it } }, modifier = Modifier.fillMaxWidth()) {
            Text("Обновить заявки")
        }
        if (items.isEmpty()) {
            AdminEmptyState("Нет заявок", "Ожидающие сброса пароля отсутствуют.")
        } else {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val wide = maxWidth >= 720.dp
                if (wide) AdminTableHeader(listOf("Билет", "Контакт", ""))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(items, key = { it.id }) { req ->
                        AdminSurfaceCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (wide) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(req.studentTicket, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                        Text(req.contact, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                        Button(onClick = {
                                            vm.resolveForgot(req.id) { vm.loadForgot { items = it } }
                                        }) { Text("Сбросить") }
                                    }
                                } else {
                                    Text(req.studentTicket, fontWeight = FontWeight.Medium)
                                    Text(req.contact, style = MaterialTheme.typography.bodySmall)
                                    Button(onClick = {
                                        vm.resolveForgot(req.id) { vm.loadForgot { items = it } }
                                    }) { Text("Сбросить пароль") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
