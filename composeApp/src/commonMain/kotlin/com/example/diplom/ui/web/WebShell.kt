package com.example.diplom.ui.web

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.diplom.api.AuthResponse
import com.example.diplom.domain.UserRole
import com.example.diplom.ui.HomeTabContent
import com.example.diplom.ui.ProfileTabContent
import com.example.diplom.ui.SettingsTabContent
import com.example.diplom.ui.Strings
import com.example.diplom.ui.design.themedAccent
import com.example.diplom.ui.design.themedBorder
import com.example.diplom.ui.design.themedTextPrimary
import com.example.diplom.ui.design.themedTextSecondary

private val WebSidebarBreakpoint = 960.dp
private val WebSidebarWidth = 272.dp
private val WebContentMax = 1280.dp

/**
 * Полнофункциональная веб-оболочка: те же вкладки и контент, что в [com.example.diplom.ui.MainScreen],
 * с адаптивным layout (sidebar / floating nav).
 */
@Composable
fun WebShell(
    authResponse: AuthResponse,
    onLogout: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToSchedule: () -> Unit,
    onNavigateToAdminCenter: () -> Unit,
    onNavigateToAttendance: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToScan: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val user = authResponse.user
    val navItems = listOf(
        Triple(Icons.Default.Home, Strings.home(), 0),
        Triple(Icons.Default.Person, Strings.profile(), 1),
        Triple(Icons.Default.Settings, Strings.settings(), 2),
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val wide = maxWidth >= WebSidebarBreakpoint

        if (wide) {
            Row(Modifier.fillMaxSize()) {
                WebSidebar(
                    userName = user.fullName,
                    userTicket = user.studentTicket,
                    role = user.role,
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it },
                    onLogout = onLogout,
                    modifier = Modifier
                        .width(WebSidebarWidth)
                        .fillMaxHeight(),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 28.dp, vertical = 20.dp),
                ) {
                    WebTabContent(
                        selectedTab = selectedTab,
                        authResponse = authResponse,
                        onLogout = onLogout,
                        onCalendar = onNavigateToCalendar,
                        onSchedule = onNavigateToSchedule,
                        onAdminCenter = onNavigateToAdminCenter,
                        onAttendance = onNavigateToAttendance,
                        onNotifications = onNavigateToNotifications,
                        onScan = onNavigateToScan,
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                WebMobileTopBar(
                    userName = user.fullName,
                    role = user.role,
                    onLogout = onLogout,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    WebTabContent(
                        selectedTab = selectedTab,
                        authResponse = authResponse,
                        onLogout = onLogout,
                        onCalendar = onNavigateToCalendar,
                        onSchedule = onNavigateToSchedule,
                        onAdminCenter = onNavigateToAdminCenter,
                        onAttendance = onNavigateToAttendance,
                        onNotifications = onNavigateToNotifications,
                        onScan = onNavigateToScan,
                    )
                }
                WebFloatingBottomNav(
                    items = navItems.map { it.first to it.second },
                    selectedIndex = selectedTab,
                    onSelect = { selectedTab = it },
                )
            }
        }
    }
}

@Composable
private fun WebTabContent(
    selectedTab: Int,
    authResponse: AuthResponse,
    onLogout: () -> Unit,
    onCalendar: () -> Unit,
    onSchedule: () -> Unit,
    onAdminCenter: () -> Unit,
    onAttendance: () -> Unit,
    onNotifications: () -> Unit,
    onScan: () -> Unit,
) {
    val user = authResponse.user
    WebScrollHost(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = WebContentMax),
    ) {
        when (selectedTab) {
            0 -> HomeTabContent(
                userName = user.fullName,
                studentTicket = user.studentTicket,
                role = user.role,
                onCalendar = onCalendar,
                onSchedule = onSchedule,
                onAdminCenter = onAdminCenter,
                onAttendance = onAttendance,
                onNotifications = onNotifications,
                onScan = onScan,
            )
            1 -> ProfileTabContent(
                role = user.role,
                fullName = user.fullName,
                studentTicket = user.studentTicket,
            )
            2 -> SettingsTabContent(
                userTicket = user.studentTicket,
                onLogout = onLogout,
            )
        }
    }
}

@Composable
private fun WebSidebar(
    userName: String,
    userTicket: String,
    role: UserRole,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initials = userName.split(" ")
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .take(2)
        .joinToString("")
        .ifBlank { "?" }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            WebBrandLogo()
            Spacer(Modifier.height(28.dp))
            WebNavItem(Icons.Default.Home, Strings.home(), selectedTab == 0, onClick = { onSelectTab(0) })
            WebNavItem(Icons.Default.Person, Strings.profile(), selectedTab == 1, onClick = { onSelectTab(1) })
            WebNavItem(Icons.Default.Settings, Strings.settings(), selectedTab == 2, onClick = { onSelectTab(2) })
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = themedBorder())
        Spacer(Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WebUserAvatar(initials)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    userName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = themedTextPrimary(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    webRoleLabel(role),
                    style = MaterialTheme.typography.labelMedium,
                    color = themedTextSecondary(),
                )
                Text(
                    userTicket,
                    style = MaterialTheme.typography.labelMedium,
                    color = themedTextSecondary(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.Logout, null, tint = themedAccent())
            Text("  Выйти", color = themedAccent(), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun WebMobileTopBar(
    userName: String,
    role: UserRole,
    onLogout: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                userName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = themedTextPrimary(),
                maxLines = 1,
            )
            Text(
                webRoleLabel(role),
                style = MaterialTheme.typography.bodySmall,
                color = themedTextSecondary(),
            )
        }
        TextButton(onClick = onLogout) {
            Text("Выйти", color = themedAccent(), fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun webRoleLabel(role: UserRole): String = when (role) {
    UserRole.STUDENT -> "Студент"
    UserRole.TEACHER -> "Преподаватель"
    UserRole.ADMIN -> "Администратор"
}

/** Точка входа для [App] — то же имя, что раньше. */
@Composable
fun WebMainScreen(
    authResponse: AuthResponse,
    onLogout: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToSchedule: () -> Unit,
    onNavigateToAdminCenter: () -> Unit,
    onNavigateToAttendance: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToScan: () -> Unit,
) = WebShell(
    authResponse = authResponse,
    onLogout = onLogout,
    onNavigateToCalendar = onNavigateToCalendar,
    onNavigateToSchedule = onNavigateToSchedule,
    onNavigateToAdminCenter = onNavigateToAdminCenter,
    onNavigateToAttendance = onNavigateToAttendance,
    onNavigateToNotifications = onNavigateToNotifications,
    onNavigateToScan = onNavigateToScan,
)
