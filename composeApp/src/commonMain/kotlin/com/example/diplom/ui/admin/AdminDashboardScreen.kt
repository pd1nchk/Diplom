package com.example.diplom.ui.admin

import androidx.compose.runtime.Composable

/** @deprecated Use [AdminDashboardRoute] from AdminRoute.kt */
@Composable
fun AdminDashboardScreen(
    onBack: () -> Unit,
    viewModel: AdminCenterViewModel,
) = AdminDashboardRoute(onBack = onBack, viewModel = viewModel)
