package com.example.diplom.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.diplom.ui.design.themedTextSecondary

@Composable
fun AdminDashboardPanel(state: AdminCenterUiState, vm: AdminCenterViewModel) {
  val q = state.globalSearch.trim().lowercase()
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      AdminSectionTitle(
        title = "Обзор",
        subtitle = "Сводка и быстрые действия",
      )
      OutlinedTextField(
        value = state.globalSearch,
        onValueChange = vm::setGlobalSearch,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Поиск по разделам") },
        singleLine = true,
      )
    }
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
          AdminKpiTile(
            "Студентов",
            state.catalog.students.size.toString(),
            modifier = Modifier.weight(1f),
            onClick = { vm.setNav(AdminNavSection.USERS); vm.setUsersSub(UsersSub.STUDENTS) },
          )
          AdminKpiTile(
            "Преподавателей",
            state.catalog.teachers.size.toString(),
            modifier = Modifier.weight(1f),
            onClick = { vm.setNav(AdminNavSection.USERS); vm.setUsersSub(UsersSub.TEACHERS) },
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
          AdminKpiTile(
            "Сегодня занятий",
            vm.lessonsTodayCount().toString(),
            modifier = Modifier.weight(1f),
            onClick = { vm.setNav(AdminNavSection.SCHEDULE); vm.setScheduleSub(ScheduleSub.LESSONS) },
          )
          AdminKpiTile(
            "Заявок",
            (state.forgotPasswordPending + state.reschedulePending).toString(),
            modifier = Modifier.weight(1f),
            onClick = { vm.setNav(AdminNavSection.SCHEDULE); vm.setScheduleSub(ScheduleSub.RESCHEDULES) },
          )
        }
      }
    }
  if (state.forgotPasswordPending > 0 || state.reschedulePending > 0 || vm.unlinkedRoomsCount() > 0) {
    item {
      Text("Требует внимания", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
        if (state.forgotPasswordPending > 0) {
          AdminSurfaceCard(onClick = { vm.setNav(AdminNavSection.USERS); vm.setUsersSub(UsersSub.PASSWORDS) }) {
            Text(
              "Сброс пароля: ${state.forgotPasswordPending}",
              modifier = Modifier.padding(12.dp),
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
        if (state.reschedulePending > 0) {
          AdminSurfaceCard(onClick = { vm.setNav(AdminNavSection.SCHEDULE); vm.setScheduleSub(ScheduleSub.RESCHEDULES) }) {
            Text(
              "Переносы: ${state.reschedulePending}",
              modifier = Modifier.padding(12.dp),
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
        if (vm.unlinkedRoomsCount() > 0) {
          AdminSurfaceCard(onClick = { vm.setNav(AdminNavSection.NFC_ROOMS) }) {
            Text(
              "Аудитории без NFC: ${vm.unlinkedRoomsCount()}",
              modifier = Modifier.padding(12.dp),
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
      }
    }
  }
    item {
      Text("Ближайшие занятия", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      val upcoming = state.lessons
        .filter { l -> q.isEmpty() || l.courseName.lowercase().contains(q) || l.groupDisplayName.lowercase().contains(q) }
        .take(6)
      if (upcoming.isEmpty()) {
        AdminEmptyState(
          title = "Нет занятий",
          description = "Создайте занятие в разделе «Расписание»",
          actionLabel = "Расписание",
          onAction = {
            vm.setNav(AdminNavSection.SCHEDULE)
            vm.openNewLessonEditor()
          },
        )
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
          upcoming.forEach { l ->
            AdminSurfaceCard {
              Column(Modifier.padding(12.dp)) {
                Text(l.courseName, fontWeight = FontWeight.Medium)
                Text(
                  "${l.groupDisplayName} · ${formatInstantRu(l.startIso)}",
                  style = MaterialTheme.typography.bodySmall,
                  color = themedTextSecondary(),
                )
              }
            }
          }
        }
      }
    }
    item {
      Button(
        onClick = { vm.setNav(AdminNavSection.SCHEDULE); vm.openNewLessonEditor() },
        modifier = Modifier.fillMaxWidth(),
      ) { Text("Создать занятие") }
    }
  }
}
