package com.example.diplom.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class AdminNavItem(
    val section: AdminNavSection,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val navItems = listOf(
    AdminNavItem(AdminNavSection.DASHBOARD, Icons.Default.Dashboard),
    AdminNavItem(AdminNavSection.SCHEDULE, Icons.Default.CalendarMonth),
    AdminNavItem(AdminNavSection.USERS, Icons.Default.People),
    AdminNavItem(AdminNavSection.CATALOG, Icons.Default.MenuBook),
    AdminNavItem(AdminNavSection.NFC_ROOMS, Icons.Default.Nfc),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminShell(
    onBack: () -> Unit,
    viewModel: AdminCenterViewModel,
    wideLayout: Boolean,
    content: @Composable (AdminCenterUiState, AdminCenterViewModel) -> Unit,
) {
    val state by viewModel.ui.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    LaunchedEffect(state.snackbar) {
        val m = state.snackbar ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(m)
        viewModel.dismissSnackbar()
    }

    val shellScaffold: @Composable (Modifier) -> Unit = { contentModifier ->
        Scaffold(
            modifier = contentModifier,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            state.nav.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    actions = {
                        if (!wideLayout) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Разделы")
                            }
                        }
                        TextButton(
                            onClick = { viewModel.refreshAll() },
                            enabled = !state.globalRefreshing,
                        ) { Text("Обновить") }
                    },
                )
            },
        ) { pad ->
            Column(Modifier.fillMaxSize().padding(pad)) {
                if (state.globalRefreshing) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                AdminLoadErrors(state, onRetry = { viewModel.refreshAll() })
                Box(Modifier.weight(1f).fillMaxSize()) {
                    content(state, viewModel)
                }
            }
        }
    }

    if (wideLayout) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail(modifier = Modifier.fillMaxHeight()) {
                navItems.forEach { item ->
                    NavigationRailItem(
                        selected = state.nav == item.section,
                        onClick = { viewModel.setNav(item.section) },
                        icon = { Icon(item.icon, contentDescription = item.section.label) },
                        label = { Text(item.section.label, maxLines = 1) },
                    )
                }
            }
            shellScaffold(Modifier.weight(1f))
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                Column(Modifier.width(280.dp).padding(vertical = 16.dp)) {
                    Text(
                        "Админ-панель",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                    navItems.forEach { item ->
                        val selected = state.nav == item.section
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setNav(item.section)
                                    scope.launch { drawerState.close() }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = item.section.label,
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                item.section.label,
                                modifier = Modifier.padding(start = 16.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            },
        ) {
            shellScaffold(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun AdminLoadErrors(state: AdminCenterUiState, onRetry: () -> Unit) {
    val errors = listOfNotNull(
        state.scheduleRefsBanner,
        state.lessonsBanner,
        state.catalogError,
        state.roomsError,
    ).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    errors.forEach { err ->
        AdminAlertBanner(message = err, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), onRetry = onRetry)
    }
}
