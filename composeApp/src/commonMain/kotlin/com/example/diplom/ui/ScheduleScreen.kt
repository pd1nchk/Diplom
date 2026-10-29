package com.example.diplom.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.diplom.api.WeekScheduleDto
import com.example.diplom.domain.UserRole
import com.example.diplom.schedule.ScheduleRepository

@Composable
fun ScheduleScreen(
    onBack: () -> Unit,
    userRole: UserRole,
    studentTicket: String,
    userName: String = "",
) {
    val repo = remember { ScheduleRepository() }
    var week by remember { mutableStateOf<WeekScheduleDto?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(studentTicket, reloadKey) {
        loading = true
        error = null
        runCatching { repo.weekForUser(studentTicket) }
            .onSuccess {
                week = it
                loading = false
            }
            .onFailure {
                error = it.message ?: "Не удалось загрузить расписание"
                loading = false
            }
    }

    SchedulePlannerContent(
        onBack = onBack,
        userRole = userRole,
        userName = userName,
        week = week,
        loading = loading,
        error = error,
        onToday = { reloadKey++ },
        modifier = Modifier,
    )
}
