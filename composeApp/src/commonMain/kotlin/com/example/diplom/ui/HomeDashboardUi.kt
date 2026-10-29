package com.example.diplom.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.example.diplom.ui.design.diplomBlur
import com.example.diplom.ui.design.diplomShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diplom.ui.admin.AdminNavSection
import androidx.compose.ui.unit.sp
import com.example.diplom.data.UpcomingEvent
import com.example.diplom.domain.UserRole
import com.example.diplom.ui.design.DiplomColors
import com.example.diplom.ui.design.DiplomGradients
import com.example.diplom.ui.design.isLightUi
import com.example.diplom.ui.design.themedAccent
import com.example.diplom.ui.design.themedCyan
import com.example.diplom.ui.design.themedMutedText
import com.example.diplom.ui.design.themedTextPrimary
import com.example.diplom.ui.design.themedTextSecondary
import com.example.diplom.ui.platform.isWebComposeClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.example.diplom.time.AppTimeZone
import com.example.diplom.time.nowEpochMillis
import com.example.diplom.time.epochMillisToAppZoneHour
import kotlinx.datetime.toLocalDateTime

fun russianGreeting(): String {
    val hour = epochMillisToAppZoneHour(nowEpochMillis())
    return when (hour) {
        in 5..10 -> "Доброе утро"
        in 11..16 -> "Добрый день"
        in 17..22 -> "Добрый вечер"
        else -> "Доброй ночи"
    }
}

@Composable
fun DashboardHeroSection(
    greeting: String,
    firstName: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val textPrimary = themedTextPrimary()
    val textSecondary = themedTextSecondary()
    val accent = themedAccent()
    val light = isLightUi()
    val pulse = if (isWebComposeClient()) {
        1f
    } else {
        val mesh = rememberInfiniteTransition(label = "hero")
        mesh.animateFloat(
            initialValue = 0.92f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "heroPulse",
        ).value
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accent.copy(alpha = if (light) 0.14f else 0.22f),
                            DiplomColors.SecondaryAccent.copy(alpha = if (light) 0.08f else 0.18f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(140.dp * pulse)
                .diplomBlur(48.dp)
                .background(
                    Brush.radialGradient(
                        listOf(DiplomColors.Cyan.copy(alpha = 0.35f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .size(120.dp)
                .diplomBlur(40.dp)
                .background(
                    Brush.radialGradient(
                        listOf(DiplomColors.SecondaryAccent.copy(alpha = 0.28f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 28.dp),
        ) {
            Text(
                text = greeting,
                style = MaterialTheme.typography.labelLarge,
                color = textSecondary,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = firstName,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = themedMutedText(),
                lineHeight = 22.sp,
            )
        }
    }
}

@Composable
fun FeaturedLessonGlassCard(
    event: UpcomingEvent,
    loading: Boolean,
    countdownLabel: String,
    statusLabel: String,
    onOpenSchedule: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textPrimary = themedTextPrimary()
    val textSecondary = themedTextSecondary()
    val accent = themedAccent()
    val cyan = themedCyan()
    val light = isLightUi()
    val shape = RoundedCornerShape(28.dp)
    val borderBrush = Brush.linearGradient(
        listOf(
            accent.copy(alpha = 0.55f),
            DiplomColors.SecondaryAccent.copy(alpha = 0.45f),
            cyan.copy(alpha = 0.4f),
        ),
    )
    val fill = if (light) Color.White.copy(alpha = 0.78f) else Color.White.copy(alpha = 0.07f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .diplomShadow(
                elevation = 28.dp,
                shape = shape,
                ambientColor = accent.copy(alpha = 0.25f),
                spotColor = cyan.copy(alpha = 0.22f),
            )
            .clip(shape)
            .border(1.2.dp, borderBrush, shape)
            .background(fill)
            .clickable(onClick = onOpenSchedule)
            .padding(20.dp),
    ) {
        if (loading) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth(0.45f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(textSecondary.copy(alpha = 0.2f)),
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.85f)
                        .height(26.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(textSecondary.copy(alpha = 0.18f)),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Главная пара",
                        style = MaterialTheme.typography.labelLarge,
                        color = cyan,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = event.title.ifBlank { "Нет данных" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(
                            event.dayLabel.takeIf { it.isNotBlank() },
                            event.startTime.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = textSecondary,
                    )
                    if (event.room.isNotBlank()) {
                        Text(
                            text = "Ауд. ${event.room}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = textSecondary,
                        )
                    }
                    if (event.teacher.isNotBlank()) {
                        Text(
                            text = event.teacher,
                            style = MaterialTheme.typography.bodySmall,
                            color = themedMutedText(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (event.isOngoing) DiplomColors.Success.copy(alpha = 0.2f)
                                else accent.copy(alpha = 0.18f),
                            )
                            .border(
                                1.dp,
                                if (event.isOngoing) DiplomColors.Success.copy(alpha = 0.45f)
                                else accent.copy(alpha = 0.35f),
                                RoundedCornerShape(14.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (event.isOngoing) DiplomColors.Success else accent,
                        )
                    }
                    Text(
                        text = countdownLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary,
                    )
                    Text(
                        text = "Расписание →",
                        style = MaterialTheme.typography.labelMedium,
                        color = cyan,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
fun rememberLessonCountdown(event: UpcomingEvent): String {
    var nowMs by remember { mutableStateOf(nowEpochMillis()) }
    LaunchedEffect(event.id, event.startEpochMillis, event.endEpochMillis) {
        while (isActive) {
            delay(1000)
            nowMs = nowEpochMillis()
        }
    }
    val start = event.startEpochMillis
    val end = event.endEpochMillis
    if (start <= 0L) return event.startTime.ifBlank { "—" }
    return when {
        nowMs >= start && nowMs < end -> {
            val left = ((end - nowMs) / 60_000).toInt().coerceAtLeast(0)
            "ещё ${left} мин"
        }
        nowMs < start -> {
            val mins = ((start - nowMs) / 60_000).toInt()
            when {
                mins >= 1440 -> "через ${mins / 1440} д"
                mins >= 60 -> "через ${mins / 60} ч ${mins % 60} мин"
                else -> "через $mins мин"
            }
        }
        else -> "завершена"
    }
}

@Composable
fun DashboardStatPill(label: String, value: String, modifier: Modifier = Modifier) {
    val textPrimary = themedTextPrimary()
    val textSecondary = themedTextSecondary()
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = if (isLightUi()) 0.55f else 0.08f))
            .border(1.dp, Color.White.copy(alpha = if (isLightUi()) 0.5f else 0.1f), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = textSecondary,
            )
        }
    }
}

@Composable
fun QuickGradientActionCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Компактная плитка (правая колонка у преподавателя) — меньше иконка, подпись всегда видна. */
    compact: Boolean = false,
) {
    val textPrimary = themedTextPrimary()
    val shape = RoundedCornerShape(22.dp)
    val iconBox = if (compact) 34.dp else 40.dp
    val iconGlyph = if (compact) 18.dp else 22.dp
    Column(
        modifier = modifier
            .heightIn(min = if (compact) 88.dp else 72.dp)
            .diplomShadow(16.dp, shape, ambientColor = DiplomColors.Primary.copy(alpha = 0.2f), spotColor = DiplomColors.Cyan.copy(alpha = 0.15f))
            .clip(shape)
            .background(Color.White.copy(alpha = if (isLightUi()) 0.72f else 0.08f))
            .border(1.dp, Color.White.copy(alpha = if (isLightUi()) 0.45f else 0.12f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(iconBox)
                .clip(CircleShape)
                .background(DiplomGradients.brandHorizontal),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(iconGlyph))
        }
        Text(
            text = title,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = if (compact) 15.sp else 16.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun HomeQuickActionsDashboard(
    role: UserRole,
    onCalendar: () -> Unit,
    onSchedule: () -> Unit,
    onAdminCenter: () -> Unit,
    onAttendance: () -> Unit,
    onNotifications: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Разделы",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = themedTextPrimary(),
        )
        when (role) {
            UserRole.STUDENT -> {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    QuickGradientActionCard(
                        title = "Расписание",
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        onClick = onSchedule,
                        modifier = Modifier.weight(1.55f).height(132.dp),
                    )
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        QuickGradientActionCard(
                            title = "Уведомления",
                            icon = Icons.Default.Notifications,
                            onClick = onNotifications,
                            modifier = Modifier.fillMaxWidth().height(72.dp),
                        )
                        QuickGradientActionCard(
                            title = "NFC",
                            icon = Icons.Default.Cameraswitch,
                            onClick = onScan,
                            modifier = Modifier.fillMaxWidth().height(72.dp),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickGradientActionCard(
                        title = "Посещаемость",
                        icon = Icons.Default.EventNote,
                        onClick = onAttendance,
                        modifier = Modifier.fillMaxWidth().height(108.dp),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickGradientActionCard(
                        title = "Календарь",
                        icon = Icons.Default.CalendarMonth,
                        onClick = onCalendar,
                        modifier = Modifier.weight(1f).height(96.dp),
                    )
                }
            }
            UserRole.TEACHER -> TeacherHomeQuickActions(
                onCalendar = onCalendar,
                onSchedule = onSchedule,
                onAttendance = onAttendance,
                onNotifications = onNotifications,
            )
            UserRole.ADMIN -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickGradientActionCard(
                        title = "Админ-центр",
                        icon = Icons.Default.AdminPanelSettings,
                        onClick = onAdminCenter,
                        modifier = Modifier.weight(1.5f).height(136.dp),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        QuickGradientActionCard(
                            title = "Заявки на перенос",
                            icon = Icons.Default.SwapHoriz,
                            onClick = {
                                AppState.pendingAdminNav = "RESCHEDULE_REQUESTS"
                                onAdminCenter()
                            },
                            modifier = Modifier.fillMaxWidth().height(62.dp),
                        )
                        QuickGradientActionCard(
                            title = "Сброс пароля",
                            icon = Icons.Default.ManageAccounts,
                            onClick = {
                                AppState.pendingAdminNav = "PASSWORDS"
                                onAdminCenter()
                            },
                            modifier = Modifier.fillMaxWidth().height(62.dp),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickGradientActionCard(
                        title = "Календарь",
                        icon = Icons.Default.CalendarMonth,
                        onClick = onCalendar,
                        modifier = Modifier.weight(1f).height(96.dp),
                    )
                    QuickGradientActionCard(
                        title = "NFC",
                        icon = Icons.Default.Cameraswitch,
                        onClick = onScan,
                        modifier = Modifier.weight(1f).height(96.dp),
                    )
                }
            }
        }
    }
}
