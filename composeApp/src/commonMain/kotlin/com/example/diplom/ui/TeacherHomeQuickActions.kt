package com.example.diplom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diplom.ui.design.DiplomGradients
import com.example.diplom.ui.design.diplomShadow
import com.example.diplom.ui.design.isLightUi
import com.example.diplom.ui.design.themedTextPrimary

private val TeacherTileHeight = 112.dp
private val TeacherQuickGridMaxWidth = 520.dp

/** Разделы на главной преподавателя: ровная сетка 2×2. */
@Composable
fun TeacherHomeQuickActions(
    onCalendar: () -> Unit,
    onSchedule: () -> Unit,
    onAttendance: () -> Unit,
    onNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = TeacherQuickGridMaxWidth)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TeacherHomeTile(
                    title = "Журнал NFC",
                    icon = Icons.Default.EventNote,
                    onClick = onAttendance,
                    modifier = Modifier.weight(1f).height(TeacherTileHeight),
                )
                TeacherHomeTile(
                    title = "Расписание",
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    onClick = onSchedule,
                    modifier = Modifier.weight(1f).height(TeacherTileHeight),
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TeacherHomeTile(
                    title = "Уведомления",
                    icon = Icons.Default.Notifications,
                    onClick = onNotifications,
                    modifier = Modifier.weight(1f).height(TeacherTileHeight),
                )
                TeacherHomeTile(
                    title = "Календарь",
                    icon = Icons.Default.CalendarMonth,
                    onClick = onCalendar,
                    modifier = Modifier.weight(1f).height(TeacherTileHeight),
                )
            }
        }
    }
}

@Composable
private fun TeacherHomeTile(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .diplomShadow(12.dp, shape)
            .clip(shape)
            .background(Color.White.copy(alpha = if (isLightUi()) 0.82f else 0.1f))
            .border(1.dp, Color.White.copy(alpha = if (isLightUi()) 0.55f else 0.15f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(DiplomGradients.brandHorizontal),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Text(
            text = title,
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = themedTextPrimary(),
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 18.sp,
        )
    }
}
