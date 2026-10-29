package com.example.diplom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.diplom.api.StudentAbsenceDto
import com.example.diplom.api.TeacherGroupCourseDto
import com.example.diplom.attendance.AttendanceRepository
import com.example.diplom.domain.UserRole
import com.example.diplom.ui.design.GlassCard
import com.example.diplom.ui.design.ShimmerBar
import com.example.diplom.ui.design.themedAccent
import com.example.diplom.ui.design.themedError
import com.example.diplom.ui.design.themedScreenBrush
import com.example.diplom.ui.design.themedTextPrimary
import com.example.diplom.ui.design.themedTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    onBack: () -> Unit,
    userRole: UserRole,
    userTicket: String,
) {
    val repo = remember { AttendanceRepository() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var teacherNav by remember { mutableStateOf<TeacherNav>(TeacherNav.Courses) }

    LaunchedEffect(userTicket) {
        teacherNav = TeacherNav.Courses
    }

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var absences by remember { mutableStateOf<List<StudentAbsenceDto>>(emptyList()) }
    var teacherGroups by remember { mutableStateOf<List<TeacherGroupCourseDto>>(emptyList()) }
    val textPrimary = themedTextPrimary()
    val textSecondary = themedTextSecondary()
    val errorColor = themedError()

    val teacherTitle = when (val n = teacherNav) {
        is TeacherNav.Courses -> "Посещаемость"
        is TeacherNav.Subjects -> "${n.year} курс"
        is TeacherNav.Groups -> n.courseName
        is TeacherNav.Journal -> "Журнал · ${n.group.groupName}"
    }

    LaunchedEffect(userTicket, userRole) {
        loading = true
        while (true) {
            error = null
            when (userRole) {
                UserRole.STUDENT -> {
                    runCatching { repo.studentAbsences(userTicket) }
                        .onSuccess { absences = it }
                        .onFailure { error = it.message ?: "Ошибка загрузки" }
                }
                UserRole.TEACHER, UserRole.ADMIN -> {
                    runCatching { repo.teacherGroups(userTicket) }
                        .onSuccess { teacherGroups = it }
                        .onFailure { error = it.message ?: "Ошибка загрузки" }
                }
            }
            loading = false
            delay(60_000)
        }
    }

    fun snack(msg: String) {
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (userRole) {
                            UserRole.STUDENT -> "Посещаемость"
                            UserRole.TEACHER, UserRole.ADMIN -> teacherTitle
                        },
                        color = textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when (userRole) {
                                UserRole.STUDENT -> onBack()
                                UserRole.TEACHER, UserRole.ADMIN -> {
                                    if (teacherNav is TeacherNav.Courses) {
                                        onBack()
                                    } else {
                                        teacherNav = popTeacherNav(teacherNav)
                                    }
                                }
                            }
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                    titleContentColor = textPrimary,
                    navigationIconContentColor = textPrimary,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(themedScreenBrush())
                .padding(padding),
        ) {
            when {
                loading -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    repeat(8) { ShimmerBar(Modifier.fillMaxWidth(), height = 64.dp) }
                }

                error != null -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    GlassCard(modifier = Modifier.fillMaxWidth(), fillAlpha = 0.1f, borderAlpha = 0.14f) {
                        Text(text = error!!, color = errorColor, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                userRole == UserRole.STUDENT -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            text = "Пропуски и отметки",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary,
                        )
                    }
                    item {
                        AnalyticsStrip(
                            title = "Всего записей",
                            value = absences.size.toString(),
                            subtitle = if (absences.isEmpty()) "Отлично, пропусков нет" else "Есть отметки для просмотра",
                        )
                    }
                    if (absences.isEmpty()) {
                        item {
                            EmptyAttendanceState("Нет записей", "Когда появятся отметки посещаемости, они будут показаны карточками.")
                        }
                    } else {
                        items(absences, key = { "${it.lessonId}_${it.dateIso}" }) { a ->
                            AbsenceCard(a)
                        }
                    }
                }

                else -> TeacherAttendanceHost(
                    userTicket = userTicket,
                    teacherGroups = teacherGroups,
                    repo = repo,
                    snackMessage = { snack(it) },
                    nav = teacherNav,
                    onNavChange = { teacherNav = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun AbsenceCard(a: StudentAbsenceDto) {
    val textPrimary = themedTextPrimary()
    val textSecondary = themedTextSecondary()
    val accentCol = themedAccent()
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        fillAlpha = 0.08f,
        borderAlpha = 0.12f,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(accentCol.copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(a.subject.take(1), color = accentCol, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(a.subject, fontWeight = FontWeight.SemiBold, color = textPrimary)
                Text("${a.dateIso} · ${a.timeRange}", style = MaterialTheme.typography.bodySmall, color = textSecondary)
            }
        }
        Text("Причина: ${a.reason}", style = MaterialTheme.typography.bodyMedium, color = textPrimary)
        Text("Преподаватель: ${a.teacher}", style = MaterialTheme.typography.bodySmall, color = textSecondary)
    }
}

@Composable
private fun AnalyticsStrip(title: String, value: String, subtitle: String) {
    GlassCard(modifier = Modifier.fillMaxWidth(), fillAlpha = 0.12f, borderAlpha = 0.16f) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = themedTextSecondary())
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = themedTextPrimary())
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = themedTextSecondary())
    }
}

@Composable
private fun EmptyAttendanceState(title: String, subtitle: String) {
    GlassCard(modifier = Modifier.fillMaxWidth(), fillAlpha = 0.1f, borderAlpha = 0.14f) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = themedTextPrimary())
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = themedTextSecondary())
    }
}
