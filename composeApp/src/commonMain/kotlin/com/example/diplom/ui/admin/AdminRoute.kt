package com.example.diplom.ui.admin

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
@Composable
fun AdminDashboardRoute(
    onBack: () -> Unit,
    viewModel: AdminCenterViewModel,
) {
    val state by viewModel.ui.collectAsState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wideLayout = maxWidth >= 900.dp
        AdminShell(
            onBack = onBack,
            viewModel = viewModel,
            wideLayout = wideLayout,
        ) { uiState, vm ->
            AnimatedContent(
                targetState = uiState.nav,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "admin_nav",
            ) { nav ->
                when (nav) {
                    AdminNavSection.DASHBOARD -> AdminDashboardPanel(uiState, vm)
                    AdminNavSection.SCHEDULE -> AdminSchedulePanel(uiState, vm)
                    AdminNavSection.USERS -> AdminUsersPanel(uiState, vm)
                    AdminNavSection.CATALOG -> AdminCatalogPanel(uiState, vm)
                    AdminNavSection.NFC_ROOMS -> AdminNfcPanel(uiState, vm)
                }
            }
        }
        AdminDialogs(state, viewModel)
    }
}

@Composable
private fun AdminDialogs(
    state: AdminCenterUiState,
    viewModel: AdminCenterViewModel,
) {
    state.pendingDeleteCourseId?.let {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeleteCourse() },
            title = { Text("Удалить предмет?") },
            text = {
                Text("Все связанные занятия и привязки групп будут удалены. Действие необратимо.")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDeleteCourse() }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDeleteCourse() }) { Text("Отмена") }
            },
        )
    }

    if (state.showNewCourseDialog) {
        val refs = state.scheduleRefs
        AlertDialog(
            onDismissRequest = { viewModel.showNewCourse(false) },
            title = { Text("Новый предмет") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.newCourseName,
                        onValueChange = { viewModel.setNewCourseDraft(it, state.newCourseTeacherId) },
                        label = { Text("Название") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Преподаватель", style = MaterialTheme.typography.labelLarge)
                    refs?.teachers?.forEach { t ->
                        FilterChip(
                            selected = state.newCourseTeacherId == t.userId,
                            onClick = { viewModel.setNewCourseDraft(state.newCourseName, t.userId) },
                            label = { Text(t.fullName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.submitNewCourse() }) { Text("Создать") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showNewCourse(false) }) { Text("Отмена") }
            },
        )
    }

    state.editingCourseId?.let {
        val refs = state.scheduleRefs ?: return@let
        AlertDialog(
            onDismissRequest = { viewModel.closeEditCourse() },
            title = { Text("Редактирование предмета") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    OutlinedTextField(
                        value = state.editCourseNameDraft,
                        onValueChange = {
                            viewModel.setEditCourseDraft(it, state.editCourseTeacherId, state.editCourseGroupIds)
                        },
                        label = { Text("Название") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Преподаватель", style = MaterialTheme.typography.labelLarge)
                    refs.teachers.forEach { t ->
                        FilterChip(
                            selected = state.editCourseTeacherId == t.userId,
                            onClick = {
                                viewModel.setEditCourseDraft(
                                    state.editCourseNameDraft,
                                    t.userId,
                                    state.editCourseGroupIds,
                                )
                            },
                            label = { Text(t.fullName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                    Text("Группы", style = MaterialTheme.typography.labelLarge)
                    refs.groups.forEach { g ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                adminGroupLabel(g),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = g.id in state.editCourseGroupIds,
                                onCheckedChange = { on ->
                                    val next = if (on) state.editCourseGroupIds + g.id else state.editCourseGroupIds - g.id
                                    viewModel.setEditCourseDraft(state.editCourseNameDraft, state.editCourseTeacherId, next)
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.saveEditedCourse { viewModel.closeEditCourse() } }) {
                    Text("Сохранить")
                }
            },
            dismissButton = { TextButton(onClick = { viewModel.closeEditCourse() }) { Text("Закрыть") } },
        )
    }
}
