package com.example.diplom.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.diplom.ui.admin.AdminCenterViewModel
import com.example.diplom.ui.admin.AdminDashboardRoute
import com.example.diplom.ui.admin.AdminNavSection

@Composable
fun AdminCenterScreen(
    onBack: () -> Unit,
    adminTicket: String,
) {
    val vm = viewModel(key = adminTicket) { AdminCenterViewModel(adminTicket) }
    LaunchedEffect(Unit) {
        if (AppState.openAdminNfcTab) {
            vm.setNav(AdminNavSection.NFC_ROOMS)
            AppState.openAdminNfcTab = false
        }
        AppState.pendingAdminNav?.let { name ->
            vm.navigateLegacy(name)
            AppState.pendingAdminNav = null
        }
    }
    AdminDashboardRoute(onBack = onBack, viewModel = vm)
}
