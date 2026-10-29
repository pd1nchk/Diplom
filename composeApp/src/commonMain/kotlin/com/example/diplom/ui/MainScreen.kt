package com.example.diplom.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.example.diplom.ui.design.DiplomCapsuleNavBar
import com.example.diplom.ui.design.DiplomColors
import com.example.diplom.ui.design.GlassCard
import com.example.diplom.ui.design.GradientPrimaryButton
import com.example.diplom.ui.design.MeshGradientBackground
import com.example.diplom.ui.design.ShimmerBar
import com.example.diplom.ui.design.isLightUi
import com.example.diplom.ui.design.themedAccent
import com.example.diplom.ui.design.themedBorder
import com.example.diplom.ui.design.themedCyan
import com.example.diplom.ui.design.themedError
import com.example.diplom.ui.design.themedTextPrimary
import com.example.diplom.ui.design.themedTextSecondary
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.diplom.admin.AdminCatalogRepository
import com.example.diplom.api.AuthResponse
import com.example.diplom.api.ChangePasswordRequest
import com.example.diplom.api.NotificationDto
import com.example.diplom.api.UserStateDto
import com.example.diplom.api.WeekScheduleDto
import com.example.diplom.api.emptyWeekSchedule
import com.example.diplom.attendance.AttendanceRepository
import com.example.diplom.auth.AuthRepository
import com.example.diplom.data.UpcomingEvent
import com.example.diplom.domain.UserRole
import com.example.diplom.notifications.NotificationsApiRepository
import com.example.diplom.schedule.ScheduleRepository
import com.example.diplom.state.UserStateRepository
import com.example.diplom.ui.platform.decodeImageFromBytes
import com.example.diplom.ui.web.pageVerticalScroll
import com.example.diplom.ui.platform.rememberImagePicker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.diplom.time.AppTimeZone
import com.example.diplom.time.nowEpochMillis
import com.example.diplom.time.epochMillisToAppZoneYmd
import kotlinx.datetime.toLocalDateTime

enum class MainTab {
    HOME,
    PROFILE,
    SETTINGS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    authResponse: AuthResponse,
    onLogout: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToSchedule: () -> Unit,
    onNavigateToAdminCenter: () -> Unit,
    onNavigateToAttendance: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToScan: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf(
        MainTab.HOME to Strings.home(),
        MainTab.PROFILE to Strings.profile(),
        MainTab.SETTINGS to Strings.settings(),
    )

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            DiplomCapsuleNavBar(
                items = listOf(
                    Icons.Default.Home to tabs[0].second,
                    Icons.Default.Person to tabs[1].second,
                    Icons.Default.Settings to tabs[2].second,
                ),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            MeshGradientBackground(Modifier.fillMaxSize())
            when (tabs[selectedTab].first) {
                MainTab.HOME -> HomeTabContent(
                    userName = authResponse.user.fullName,
                    studentTicket = authResponse.user.studentTicket,
                    role = authResponse.user.role,
                    onCalendar = onNavigateToCalendar,
                    onSchedule = onNavigateToSchedule,
                    onAdminCenter = onNavigateToAdminCenter,
                    onAttendance = onNavigateToAttendance,
                    onNotifications = onNavigateToNotifications,
                    onScan = onNavigateToScan,
                )
                MainTab.PROFILE -> ProfileTabContent(
                    role = authResponse.user.role,
                    fullName = authResponse.user.fullName,
                    studentTicket = authResponse.user.studentTicket,
                )
                MainTab.SETTINGS -> SettingsTabContent(
                    userTicket = authResponse.user.studentTicket,
                    onLogout = onLogout,
                )
            }
        }
    }
}

@Composable
fun HomeTabContent(
    userName: String,
    studentTicket: String,
    role: UserRole,
    onCalendar: () -> Unit,
    onSchedule: () -> Unit,
    onAdminCenter: () -> Unit,
    onAttendance: () -> Unit,
    onNotifications: () -> Unit,
    onScan: () -> Unit,
) {
    val scheduleRepo = remember { ScheduleRepository() }
    val notificationsRepo = remember { NotificationsApiRepository() }
    val attendanceRepo = remember { AttendanceRepository() }
    val adminRepo = remember { AdminCatalogRepository() }

    var scheduleLoading by remember(studentTicket) { mutableStateOf(true) }
    var weekModel by remember(studentTicket) { mutableStateOf(emptyWeekSchedule()) }
    var notifs by remember(studentTicket) { mutableStateOf<List<NotificationDto>>(emptyList()) }
    var studentUnexcused by remember(studentTicket) { mutableStateOf(0) }
    var teacherGroupCount by remember(studentTicket) { mutableStateOf(0) }
    var nfcSessionActive by remember(studentTicket) { mutableStateOf(false) }
    var adminStudents by remember(studentTicket) { mutableStateOf(0) }
    var adminTeachers by remember(studentTicket) { mutableStateOf(0) }
    var adminPwdPending by remember(studentTicket) { mutableStateOf(0) }

    var nearestEvent by remember(studentTicket) {
        mutableStateOf(
            UpcomingEvent(
                id = 0L,
                title = "",
                subtitle = "",
                startTime = "",
            ),
        )
    }

    LaunchedEffect(studentTicket, role) {
        scheduleLoading = true
        while (true) {
            runCatching {
                val week = scheduleRepo.weekForUser(studentTicket)
                weekModel = week
                notifs = runCatching { notificationsRepo.listForUser(studentTicket) }.getOrElse { emptyList() }
                when (role) {
                    UserRole.STUDENT -> {
                        studentUnexcused = runCatching {
                            attendanceRepo.studentAbsences(studentTicket).count { it.reason == "UNEXCUSED" }
                        }.getOrElse { 0 }
                    }
                    UserRole.TEACHER -> {
                        teacherGroupCount = runCatching { attendanceRepo.teacherGroups(studentTicket).size }.getOrElse { 0 }
                        nfcSessionActive = runCatching {
                            attendanceRepo.activeSessionForTeacher(studentTicket) != null
                        }.getOrElse { false }
                    }
                    UserRole.ADMIN -> {
                        val snap = runCatching { adminRepo.snapshot(studentTicket) }.getOrNull()
                        adminStudents = snap?.students?.size ?: 0
                        adminTeachers = snap?.teachers?.size ?: 0
                        adminPwdPending = runCatching { adminRepo.forgotPasswordRequests("PENDING").size }.getOrElse { 0 }
                    }
                }
                nearestEvent = resolveNearestEvent(week, notifs)
            }.onFailure {
                nearestEvent = UpcomingEvent(
                    id = 0L,
                    title = "Нет связи с сервером",
                    subtitle = it.message.orEmpty(),
                    startTime = "",
                )
            }
            scheduleLoading = false
            delay(60_000)
        }
    }

    val firstName = userName.split(" ").firstOrNull().orEmpty()
    val lessonsToday = remember(weekModel) { countLessonsTodayFromWeek(weekModel) }
    val subtitle = remember(role, lessonsToday, notifs, studentUnexcused, teacherGroupCount, nfcSessionActive, adminStudents, adminTeachers, adminPwdPending) {
        buildHomeSubtitle(
            role = role,
            lessonsToday = lessonsToday,
            notifCount = notifs.size,
            studentUnexcused = studentUnexcused,
            teacherGroupCount = teacherGroupCount,
            nfcActive = nfcSessionActive,
            adminStudents = adminStudents,
            adminTeachers = adminTeachers,
            adminPwdPending = adminPwdPending,
        )
    }
    val countdown = rememberLessonCountdown(nearestEvent)
    val nowMs = nowEpochMillis()
    val statusLabel = when {
        nearestEvent.isOngoing -> "Идёт сейчас"
        nearestEvent.startEpochMillis > 0L && nowMs < nearestEvent.startEpochMillis -> "Скоро"
        nearestEvent.startEpochMillis > 0L && nowMs >= nearestEvent.endEpochMillis -> "Завершено"
        else -> "План"
    }
    val pillScroll = rememberScrollState()
    val totalWeekLessons = remember(weekModel) { totalLessonsInWeek(weekModel).coerceAtLeast(1) }
    val attendancePct = remember(studentUnexcused, totalWeekLessons) {
        ((1f - studentUnexcused.toFloat() / totalWeekLessons) * 100f).toInt().coerceIn(0, 100)
    }

    Column(
        modifier = Modifier.pageVerticalScroll(),
    ) {
        DashboardHeroSection(
            greeting = russianGreeting(),
            firstName = firstName,
            subtitle = subtitle,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            FeaturedLessonGlassCard(
                event = nearestEvent,
                loading = scheduleLoading,
                countdownLabel = countdown,
                statusLabel = statusLabel,
                onOpenSchedule = onSchedule,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(pillScroll),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DashboardStatPill("Сегодня пар", lessonsToday.toString())
                DashboardStatPill("Уведомлений", notifs.size.toString())
                when (role) {
                    UserRole.STUDENT -> {
                        DashboardStatPill("Пропуски", studentUnexcused.toString())
                        DashboardStatPill("Оценка посещ.", "$attendancePct%")
                    }
                    UserRole.TEACHER -> {
                        DashboardStatPill("Групп", teacherGroupCount.toString())
                        DashboardStatPill("NFC", if (nfcSessionActive) "ON" else "—")
                    }
                    UserRole.ADMIN -> {
                        DashboardStatPill("Студентов", adminStudents.toString())
                        DashboardStatPill("Препод.", adminTeachers.toString())
                        if (adminPwdPending > 0) {
                            DashboardStatPill("Сброс пароля", adminPwdPending.toString())
                        }
                    }
                }
            }
            HomeQuickActionsDashboard(
                role = role,
                onCalendar = onCalendar,
                onSchedule = onSchedule,
                onAdminCenter = onAdminCenter,
                onAttendance = onAttendance,
                onNotifications = onNotifications,
                onScan = onScan,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTabContent(
    userTicket: String,
    onLogout: () -> Unit,
) {
    val userStateRepository = remember { UserStateRepository() }
    val authRepository = remember { AuthRepository() }
    val scope = rememberCoroutineScope()
    var languageExpanded by remember { mutableStateOf(false) }
    var profilePhone by remember(userTicket) { mutableStateOf("") }
    var phoneHint by remember { mutableStateOf<String?>(null) }
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newPassword2 by remember { mutableStateOf("") }
    var pwdHint by remember { mutableStateOf<String?>(null) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val textPrimary = themedTextPrimary()
    val textSecondary = themedTextSecondary()
    val accent = themedAccent()
    val cyan = themedCyan()
    val border = themedBorder()
    val error = themedError()
    val light = isLightUi()

    LaunchedEffect(userTicket) {
        val state = userStateRepository.load(userTicket) ?: UserStateDto()
        profilePhone = state.profilePhone
        phoneHint = null
    }

    val settingsFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = textPrimary,
        unfocusedTextColor = textPrimary,
        focusedBorderColor = accent,
        unfocusedBorderColor = border,
        focusedLabelColor = cyan,
        unfocusedLabelColor = textSecondary,
        cursorColor = cyan,
        disabledTextColor = textSecondary,
        disabledBorderColor = border,
        disabledLabelColor = textSecondary,
        focusedContainerColor = if (light) Color.White.copy(alpha = 0.42f) else Color.Transparent,
        unfocusedContainerColor = if (light) Color.White.copy(alpha = 0.30f) else Color.Transparent,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .pageVerticalScroll()
                .padding(20.dp),
        ) {
        Text(
            text = Strings.settings(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = textPrimary,
        )
        Spacer(modifier = Modifier.height(24.dp))
        GlassCard(modifier = Modifier.fillMaxWidth(), fillAlpha = 0.08f, borderAlpha = 0.12f) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    Strings.darkTheme(),
                    color = textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = AppState.isDarkTheme,
                    onCheckedChange = { AppState.isDarkTheme = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accent,
                        uncheckedThumbColor = textSecondary,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = border,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { languageExpanded = true },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        Strings.language(),
                        color = textSecondary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        AppState.language.displayName,
                        color = textPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Text("›", color = accent, style = MaterialTheme.typography.headlineSmall)
            }
        }
        if (languageExpanded) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { languageExpanded = false },
                title = { Text(Strings.language()) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Language.entries.forEach { lang ->
                            TextButton(
                                onClick = {
                                    AppState.language = lang
                                    languageExpanded = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(lang.displayName)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { languageExpanded = false }) {
                        Text("Отмена")
                    }
                },
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "Контакты",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = textPrimary,
        )
        OutlinedTextField(
            value = profilePhone,
            onValueChange = { profilePhone = it },
            label = { Text("Мобильный телефон", color = textSecondary) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            singleLine = true,
            colors = settingsFieldColors,
        )
        Spacer(modifier = Modifier.height(8.dp))
        GradientPrimaryButton(
            text = "Сохранить телефон",
            onClick = {
                scope.launch {
                    phoneHint = null
                    val existing = userStateRepository.load(userTicket) ?: UserStateDto()
                    val ok = userStateRepository.save(
                        userTicket,
                        existing.copy(profilePhone = profilePhone.trim()),
                    )
                    phoneHint = if (ok) "Телефон сохранён" else "Не удалось сохранить телефон"
                }
            },
            fullWidth = false,
        )
        phoneHint?.let {
            Text(
                text = it,
                color = if ("сохран" in it.lowercase()) DiplomColors.Success else error,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Смена пароля",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = textPrimary,
        )
        OutlinedTextField(
            value = oldPassword,
            onValueChange = { oldPassword = it },
            label = { Text("Текущий пароль", color = textSecondary) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            singleLine = true,
            colors = settingsFieldColors,
        )
        OutlinedTextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            label = { Text("Новый пароль", color = textSecondary) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = settingsFieldColors,
        )
        OutlinedTextField(
            value = newPassword2,
            onValueChange = { newPassword2 = it },
            label = { Text("Повтор нового пароля", color = textSecondary) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = settingsFieldColors,
        )
        pwdHint?.let {
            Text(
                it,
                color = if ("обновлён" in it.lowercase()) DiplomColors.Success else error,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        GradientPrimaryButton(
            text = "Сменить пароль",
            onClick = {
                pwdHint = null
                when {
                    oldPassword.isBlank() -> pwdHint = "Введите текущий пароль"
                    newPassword.isBlank() -> pwdHint = "Введите новый пароль"
                    newPassword.length < 4 -> pwdHint = "Новый пароль — не короче 4 символов"
                    newPassword != newPassword2 -> pwdHint = "Новые пароли не совпадают"
                    else -> {
                        scope.launch {
                            val r = authRepository.changePassword(
                                ChangePasswordRequest(
                                    studentTicket = userTicket,
                                    oldPassword = oldPassword,
                                    newPassword = newPassword,
                                ),
                            )
                            r.fold(
                                onSuccess = {
                                    oldPassword = ""
                                    newPassword = ""
                                    newPassword2 = ""
                                    pwdHint = "Пароль обновлён"
                                },
                                onFailure = { pwdHint = it.message ?: "Не удалось сменить пароль" },
                            )
                        }
                    }
                }
            },
            fullWidth = false,
        )
        Spacer(modifier = Modifier.height(28.dp))
        HorizontalDivider(color = border)
        Spacer(modifier = Modifier.height(20.dp))
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            fillAlpha = 0.1f,
            borderAlpha = 0.14f,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Сессия",
                    style = MaterialTheme.typography.labelLarge,
                    color = cyan,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Завершить работу в приложении",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                )
                Text(
                    text = "Сессия на этом устройстве закроется: снова понадобятся билет и пароль. Данные на сервере (расписание, журнал) не удаляются.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textSecondary,
                )
                OutlinedButton(
                    onClick = { showLogoutConfirm = true },
                    modifier = Modifier.wrapContentWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = error,
                    ),
                    border = BorderStroke(1.5.dp, error),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.size(10.dp))
                    Text("Выйти из аккаунта", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        }
        if (showLogoutConfirm) {
            val err = MaterialTheme.colorScheme.error
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showLogoutConfirm = false },
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = err,
                    )
                },
                title = {
                    Text(
                        "Выйти из аккаунта?",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                text = {
                    Text(
                        "Несохранённые поля на этом экране (телефон, пароль) отправьте кнопками «Сохранить» / «Сменить пароль» до выхода.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLogoutConfirm = false
                            onLogout()
                        },
                    ) {
                        Text("Выйти", color = err, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutConfirm = false }) {
                        Text("Остаться")
                    }
                },
            )
        }
    }
}

private fun countLessonsTodayFromWeek(week: WeekScheduleDto): Int {
    val today = epochMillisToAppZoneYmd(nowEpochMillis())
    return week.columns.sumOf { col ->
        col.lessons.count { lesson ->
            lesson.startTimeEpochMillis > 0L &&
                epochMillisToAppZoneYmd(lesson.startTimeEpochMillis) == today
        }
    }
}

private fun totalLessonsInWeek(week: WeekScheduleDto): Int =
    week.columns.sumOf { col -> col.lessons.count { it.startTimeEpochMillis > 0L } }

private fun buildHomeSubtitle(
    role: UserRole,
    lessonsToday: Int,
    notifCount: Int,
    studentUnexcused: Int,
    teacherGroupCount: Int,
    nfcActive: Boolean,
    adminStudents: Int,
    adminTeachers: Int,
    adminPwdPending: Int,
): String {
    val parts = mutableListOf<String>()
    parts.add("Сегодня пар: $lessonsToday")
    parts.add("уведомлений: $notifCount")
    when (role) {
        UserRole.STUDENT -> if (studentUnexcused > 0) parts.add("неуваж. пропусков: $studentUnexcused")
        UserRole.TEACHER -> {
            parts.add("активных групп: $teacherGroupCount")
            parts.add(if (nfcActive) "NFC-сессия открыта" else "NFC-сессия закрыта")
        }
        UserRole.ADMIN -> {
            parts.add("студентов в каталоге: $adminStudents")
            parts.add("преподавателей: $adminTeachers")
            if (adminPwdPending > 0) parts.add("заявок на сброс пароля: $adminPwdPending")
        }
    }
    return parts.joinToString(" · ")
}

private fun resolveNearestEvent(week: WeekScheduleDto, notifs: List<NotificationDto>): UpcomingEvent {
    val now = nowEpochMillis()
    data class Flat(val day: String, val lesson: com.example.diplom.api.ScheduledLessonDto)
    val flat = week.columns.flatMap { col ->
        col.lessons.map { Flat(col.dayOfWeek, it) }
    }.filter { it.lesson.startTimeEpochMillis > 0L && it.lesson.endTimeEpochMillis > 0L }

    val ongoing = flat.firstOrNull { now >= it.lesson.startTimeEpochMillis && now < it.lesson.endTimeEpochMillis }
    if (ongoing != null) {
        val l = ongoing.lesson
        return UpcomingEvent(
            id = l.id,
            title = l.subject,
            subtitle = "${ongoing.day}, ауд. ${l.room} · сейчас",
            startTime = l.timeRange,
            teacher = l.teacher,
            room = l.room,
            startEpochMillis = l.startTimeEpochMillis,
            endEpochMillis = l.endTimeEpochMillis,
            dayLabel = ongoing.day,
            isOngoing = true,
        )
    }
    val upcoming = flat.filter { it.lesson.startTimeEpochMillis >= now }.minByOrNull { it.lesson.startTimeEpochMillis }
    if (upcoming != null) {
        val l = upcoming.lesson
        return UpcomingEvent(
            id = l.id,
            title = l.subject,
            subtitle = "${upcoming.day}, ауд. ${l.room}",
            startTime = l.timeRange,
            teacher = l.teacher,
            room = l.room,
            startEpochMillis = l.startTimeEpochMillis,
            endEpochMillis = l.endTimeEpochMillis,
            dayLabel = upcoming.day,
            isOngoing = false,
        )
    }
    val past = flat.filter { it.lesson.endTimeEpochMillis <= now }.maxByOrNull { it.lesson.endTimeEpochMillis }
    if (past != null) {
        val l = past.lesson
        return UpcomingEvent(
            id = l.id,
            title = l.subject,
            subtitle = "${past.day}, ауд. ${l.room} · последнее на неделе",
            startTime = l.timeRange,
            teacher = l.teacher,
            room = l.room,
            startEpochMillis = l.startTimeEpochMillis,
            endEpochMillis = l.endTimeEpochMillis,
            dayLabel = past.day,
            isOngoing = false,
        )
    }
    val n = notifs.firstOrNull()
    if (n != null) {
        val time = n.createdAtIso.removeSuffix("Z").split("T").let { parts ->
            if (parts.size == 2) "${parts[0]} ${parts[1].take(5)}" else n.createdAtIso
        }
        return UpcomingEvent(
            id = n.id,
            title = n.title,
            subtitle = n.message,
            startTime = time,
        )
    }
    return UpcomingEvent(
        id = 0L,
        title = "Нет занятий на неделю",
        subtitle = "Откройте раздел «Расписание»",
        startTime = "",
    )
}

private fun parseCourseAndIndex(groupLabel: String): Pair<Int, Int>? {
    val head = groupLabel.trim().split(" ").firstOrNull() ?: return null
    val m = Regex("""^(\d+)-(\d+)$""").find(head) ?: return null
    return m.groupValues[1].toInt() to m.groupValues[2].toInt()
}

private fun ByteArray.toHexString(): String = joinToString("") { byte ->
    byte.toInt().and(0xFF).toString(16).padStart(2, '0')
}

private fun String.hexToByteArray(): ByteArray {
    if (length % 2 != 0) return ByteArray(0)
    return ByteArray(length / 2) { index ->
        substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

