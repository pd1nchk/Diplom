package com.example.diplom.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Room
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.diplom.api.LessonJournalEntryDto
import com.example.diplom.time.nowEpochMillis
import com.example.diplom.ui.design.DiplomColors
import com.example.diplom.ui.design.GhostTextButton
import com.example.diplom.ui.design.GradientPrimaryButton
import com.example.diplom.ui.design.diplomShadow
import com.example.diplom.ui.design.isLightUi
import com.example.diplom.ui.design.themedAccent
import com.example.diplom.ui.design.themedBorder
import com.example.diplom.ui.design.themedError
import com.example.diplom.ui.design.themedMutedText
import com.example.diplom.ui.design.themedTextPrimary
import com.example.diplom.ui.design.themedTextSecondary
import kotlinx.coroutines.delay

@Composable
fun JournalLessonDetailDialog(
    lesson: LessonJournalEntryDto,
    groupLabel: String?,
    isSessionForThisLesson: Boolean,
    hasOtherActiveSession: Boolean = false,
    otherActiveSessionHint: String? = null,
    showReschedule: Boolean = true,
    onDismiss: () -> Unit,
    onStartLesson: () -> Unit,
    onEndLesson: () -> Unit = {},
    onReschedule: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    var nowTick by remember { mutableStateOf(nowEpochMillis()) }
    LaunchedEffect(lesson.lessonId) {
        while (true) {
            nowTick = nowEpochMillis()
            delay(30_000)
        }
    }

    val startUi = computeLessonStartUiState(
        dateIso = lesson.dateIso,
        timeRange = lesson.timeRange,
        roomNfcUid = lesson.roomNfcUid,
        isSessionForThisLesson = isSessionForThisLesson,
        hasOtherActiveSession = hasOtherActiveSession,
        otherActiveSessionHint = otherActiveSessionHint,
        nowMs = nowTick,
    )
    val accent = subjectAccentColor(lesson.subject)
    val dateLabel = lesson.journalColumnLabel ?: formatLessonDateRu(lesson.dateIso)
    val nfcBound = !lesson.roomNfcUid.isNullOrBlank()
    val kindRu = lessonKindLabelRu(lesson.lessonKind)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compact = maxWidth < 640.dp
            val cardWidth = if (compact) maxWidth - 8.dp else 540.dp.coerceAtMost(maxWidth - 40.dp)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = if (isLightUi()) 0.50f else 0.62f),
                                Color.Black.copy(alpha = if (isLightUi()) 0.42f else 0.55f),
                            ),
                        ),
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss,
                    ),
                contentAlignment = if (compact) Alignment.BottomCenter else Alignment.Center,
            ) {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                        scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = 0.78f)) +
                        slideInVertically(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            initialOffsetY = { if (compact) it else it / 4 },
                        ),
                    exit = fadeOut() + scaleOut(targetScale = 0.95f),
                ) {
                    PremiumLessonCard(
                        modifier = Modifier
                            .width(cardWidth)
                            .padding(
                                horizontal = if (compact) 4.dp else 0.dp,
                                vertical = if (compact) 6.dp else 0.dp,
                            )
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = {},
                            ),
                        compact = compact,
                        lesson = lesson,
                        accent = accent,
                        kindRu = kindRu,
                        dateLabel = dateLabel,
                        groupLabel = groupLabel,
                        nfcBound = nfcBound,
                        startUi = startUi,
                        showReschedule = showReschedule,
                        onDismiss = onDismiss,
                        onStartLesson = {
                            if (startUi.canStart) {
                                onDismiss()
                                onStartLesson()
                            }
                        },
                        onEndLesson = onEndLesson,
                        onReschedule = {
                            onDismiss()
                            onReschedule()
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PremiumLessonCard(
    modifier: Modifier,
    compact: Boolean,
    lesson: LessonJournalEntryDto,
    accent: Color,
    kindRu: String,
    dateLabel: String,
    groupLabel: String?,
    nfcBound: Boolean,
    startUi: LessonStartUiState,
    showReschedule: Boolean,
    onDismiss: () -> Unit,
    onStartLesson: () -> Unit,
    onEndLesson: () -> Unit,
    onReschedule: () -> Unit,
) {
    val shape = RoundedCornerShape(
        topStart = 28.dp,
        topEnd = 28.dp,
        bottomStart = if (compact) 10.dp else 28.dp,
        bottomEnd = if (compact) 10.dp else 28.dp,
    )
    val bodyBg = if (isLightUi()) Color(0xFFFCFDFF) else DiplomColors.DarkElevated
    val heroBrush = Brush.linearGradient(
        colors = listOf(
            accent,
            accent.copy(alpha = 0.85f),
            Color(0xFF8B5CF6).copy(alpha = 0.9f),
        ),
    )

    Surface(
        modifier = modifier
            .diplomShadow(28.dp, shape, spotColor = accent.copy(alpha = 0.35f))
            .clip(shape)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.55f), themedBorder().copy(alpha = 0.25f)),
                ),
                shape = shape,
            ),
        shape = shape,
        color = bodyBg,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (compact) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.35f)),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 128.dp else 140.dp)
                    .background(heroBrush),
            ) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 40.dp, y = (-30).dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f)),
                )
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .align(Alignment.BottomStart)
                        .offset(x = (-20).dp, y = 20.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f)),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.22f))
                            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            subjectIconVector(lesson.subject),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            lesson.subject,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            lineHeight = 26.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            kindRu,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(alpha = 0.22f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Закрыть", tint = Color.White.copy(alpha = 0.92f))
                    }
                }
            }

            Column(
                modifier = Modifier
                    .offset(y = (-18).dp)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (isLightUi()) Color.White else DiplomColors.DarkSurface,
                        )
                        .border(1.dp, themedBorder().copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                        .diplomShadow(12.dp, RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("Время", style = MaterialTheme.typography.labelMedium, color = themedMutedText())
                            Text(
                                formatLessonTimeRangeDisplay(lesson.timeRange),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = accent,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Дата", style = MaterialTheme.typography.labelMedium, color = themedMutedText())
                            Text(
                                dateLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = themedTextPrimary(),
                            )
                        }
                    }
                }

                startUi.statusMessage?.let { LessonStatusBanner(it, startUi.statusTone) }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    maxItemsInEachRow = if (compact) 1 else 2,
                ) {
                    LessonInfoChip(Icons.Default.Room, "Аудитория", lesson.room, accent)
                    LessonInfoChip(Icons.Default.MenuBook, "Тип", kindRu, accent)
                    if (!groupLabel.isNullOrBlank()) {
                        LessonInfoChip(Icons.Default.Groups, "Группа", groupLabel, accent)
                    }
                    LessonInfoChip(Icons.Default.CalendarMonth, "Календарь", dateLabel, accent)
                }

                NfcStatusSection(bound = nfcBound)

                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (startUi.showEndLessonButton) {
                            GradientPrimaryButton(
                                text = "Завершить лекцию",
                                onClick = onEndLesson,
                                enabled = true,
                                fullWidth = true,
                            )
                        }
                        GradientPrimaryButton(
                            text = startUi.primaryButtonText,
                            onClick = onStartLesson,
                            enabled = startUi.canStart,
                            fullWidth = true,
                        )
                        if (showReschedule) {
                            OutlineActionChip(
                                text = "Запросить перенос",
                                onClick = onReschedule,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        GhostTextButton(text = "Закрыть", onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    ) {
                        GhostTextButton(text = "Закрыть", onClick = onDismiss)
                        if (showReschedule) {
                            OutlineActionChip(text = "Запросить перенос", onClick = onReschedule)
                        }
                        if (startUi.showEndLessonButton) {
                            GradientPrimaryButton(
                                text = "Завершить лекцию",
                                onClick = onEndLesson,
                                enabled = true,
                                fullWidth = false,
                                modifier = Modifier.width(200.dp),
                            )
                        }
                        GradientPrimaryButton(
                            text = startUi.primaryButtonText,
                            onClick = onStartLesson,
                            enabled = startUi.canStart,
                            fullWidth = false,
                            modifier = Modifier.width(240.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun LessonInfoChip(icon: ImageVector, label: String, value: String, accent: Color) {
    val chipShape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(chipShape)
            .background(if (isLightUi()) Color(0xFFF1F5F9) else Color.White.copy(alpha = 0.05f))
            .border(1.dp, themedBorder().copy(alpha = 0.28f), chipShape)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
        }
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = themedMutedText())
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = themedTextPrimary())
        }
    }
}

@Composable
private fun LessonStatusBanner(message: String, tone: LessonStatusTone) {
    val (bg, fg) = when (tone) {
        LessonStatusTone.Success -> DiplomColors.Success.copy(alpha = 0.14f) to DiplomColors.Success
        LessonStatusTone.Warning -> DiplomColors.Warning.copy(alpha = 0.18f) to DiplomColors.Warning
        LessonStatusTone.Error -> themedError().copy(alpha = 0.12f) to themedError()
        LessonStatusTone.Info -> themedAccent().copy(alpha = 0.12f) to themedAccent()
        LessonStatusTone.Neutral -> themedMutedText().copy(alpha = 0.12f) to themedTextSecondary()
    }
    Text(
        message,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = fg,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun NfcStatusSection(bound: Boolean) {
    val bg = if (bound) DiplomColors.Success.copy(alpha = if (isLightUi()) 0.12f else 0.2f)
    else themedError().copy(alpha = if (isLightUi()) 0.1f else 0.18f)
    val dot = if (bound) DiplomColors.Success else themedError()
    val title = if (bound) "NFC аудитории подключён" else "NFC не привязан"
    val subtitle = when {
        bound -> "Сессию можно открыть по расписанию; студенты — по карте NFC"
        else -> "Админ-центр → NFC аудитории"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Icon(Icons.Default.Nfc, null, tint = dot, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = themedTextPrimary())
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = themedTextSecondary())
        }
    }
}

@Composable
private fun OutlineActionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, Brush.horizontalGradient(listOf(Color(0xFF4F46E5), Color(0xFF8B5CF6))), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.EventRepeat, null, tint = Color(0xFF6366F1), modifier = Modifier.size(18.dp))
            Text(text, color = Color(0xFF4F46E5), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
        }
    }
}

internal fun formatLessonDateOnly(dateIso: String): String = formatLessonDateRu(dateIso)
