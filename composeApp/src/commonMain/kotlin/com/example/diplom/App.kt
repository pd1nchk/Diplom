package com.example.diplom

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.diplom.auth.AuthViewModel
import com.example.diplom.ui.platform.isWebComposeClient
import com.example.diplom.ui.AttendanceScreen
import com.example.diplom.ui.AdminCenterScreen
import com.example.diplom.ui.AuthScreen
import com.example.diplom.ui.CalendarScreen
import com.example.diplom.ui.DiplomTheme
import com.example.diplom.ui.MainScreen
import com.example.diplom.ui.web.WebMainScreen
import com.example.diplom.ui.NotificationsScreen
import com.example.diplom.ui.OverlayScreen
import com.example.diplom.ui.ScanScreen
import com.example.diplom.ui.ScheduleScreen
import com.example.diplom.ui.SplashIntro
@Composable
fun App() {
    remember {
        com.example.diplom.ui.AppState.initStorage()
        true
    }
    var showSplash by remember { mutableStateOf(!isWebComposeClient()) }
    val viewModel = remember { AuthViewModel() }
    val state by viewModel.state.collectAsState()
    val useDarkTheme = com.example.diplom.ui.AppState.isDarkTheme

    DiplomTheme(useDarkTheme = useDarkTheme) {
        when {
            showSplash -> SplashIntro { showSplash = false }
            state.authResponse == null -> AuthScreen(
                state = state,
                onLogin = { ticket, password -> viewModel.login(ticket, password) },
                onForgotPassword = { ticket, contact, comment ->
                    viewModel.forgotPassword(ticket, contact, comment)
                },
            )
            else -> {
                val user = state.authResponse!!.user
                when (com.example.diplom.ui.AppState.overlayScreen) {
                    OverlayScreen.NONE -> {
                        val auth = state.authResponse!!
                        val onLogout = {
                            com.example.diplom.ui.AppState.overlayScreen = OverlayScreen.NONE
                            viewModel.logout()
                        }
                        val onCalendar = { com.example.diplom.ui.AppState.overlayScreen = OverlayScreen.CALENDAR }
                        val onSchedule = { com.example.diplom.ui.AppState.overlayScreen = OverlayScreen.SCHEDULE }
                        val onAdminCenter = { com.example.diplom.ui.AppState.overlayScreen = OverlayScreen.ADMIN_CENTER }
                        val onAttendance = { com.example.diplom.ui.AppState.overlayScreen = OverlayScreen.ATTENDANCE }
                        val onNotifications = { com.example.diplom.ui.AppState.overlayScreen = OverlayScreen.NOTIFICATIONS }
                        val onScan = { com.example.diplom.ui.AppState.overlayScreen = OverlayScreen.SCAN }
                        if (isWebComposeClient()) {
                            WebMainScreen(
                                authResponse = auth,
                                onLogout = onLogout,
                                onNavigateToCalendar = onCalendar,
                                onNavigateToSchedule = onSchedule,
                                onNavigateToAdminCenter = onAdminCenter,
                                onNavigateToAttendance = onAttendance,
                                onNavigateToNotifications = onNotifications,
                                onNavigateToScan = onScan,
                            )
                        } else {
                            MainScreen(
                                authResponse = auth,
                                onLogout = onLogout,
                                onNavigateToCalendar = onCalendar,
                                onNavigateToSchedule = onSchedule,
                                onNavigateToAdminCenter = onAdminCenter,
                                onNavigateToAttendance = onAttendance,
                                onNavigateToNotifications = onNotifications,
                                onNavigateToScan = onScan,
                            )
                        }
                    }
                    OverlayScreen.SCAN -> ScanScreen(
                        onBack = { com.example.diplom.ui.AppState.overlayScreen = OverlayScreen.NONE },
                        studentTicket = user.studentTicket,
                        userRole = user.role,
                    )
                    OverlayScreen.CALENDAR -> CalendarScreen(
                        onBack = { com.example.diplom.ui.AppState.overlayScreen = OverlayScreen.NONE },
                        userRole = user.role,
                        userTicket = user.studentTicket,
                    )
                    OverlayScreen.SCHEDULE -> ScheduleScreen(
                        onBack = { com.example.diplom.ui.AppState.overlayScreen = OverlayScreen.NONE },
                        userRole = user.role,
                        studentTicket = user.studentTicket,
                        userName = user.fullName,
                    )
                    OverlayScreen.ADMIN_CENTER -> AdminCenterScreen(
                        onBack = { com.example.diplom.ui.AppState.overlayScreen = OverlayScreen.NONE },
                        adminTicket = user.studentTicket,
                    )
                    OverlayScreen.ATTENDANCE -> AttendanceScreen(
                        onBack = { com.example.diplom.ui.AppState.overlayScreen = OverlayScreen.NONE },
                        userRole = user.role,
                        userTicket = user.studentTicket,
                    )
                    OverlayScreen.NOTIFICATIONS -> NotificationsScreen(
                        onBack = { com.example.diplom.ui.AppState.overlayScreen = OverlayScreen.NONE },
                        studentTicket = user.studentTicket,
                    )
                }
            }
        }
    }
}
