package com.example.diplom.ui.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/** Маркетинговое имя продукта в UI. */
const val APP_PRODUCT_NAME = "Учёт посещаемости"

const val APP_DISPLAY_VERSION = "1.0"

object DiplomColors {
    val DarkBg = Color(0xFF0F172A)
    val DarkSurface = Color(0xFF111827)
    val DarkElevated = Color(0xFF1E293B)
    val Primary = Color(0xFF6366F1)
    val SecondaryAccent = Color(0xFF8B5CF6)
    val Cyan = Color(0xFF00D1FF)
    val Success = Color(0xFF2ED47A)
    val ErrorSoft = Color(0xFFFF5A7A)
    val TextPrimary = Color(0xFFF4F7FF)
    val TextSecondary = Color(0xFFA8B3CF)
    val GlassFill = Color.White.copy(alpha = 0.06f)
    val GlassBorder = Color.White.copy(alpha = 0.08f)
    val GlowPrimary = Primary.copy(alpha = 0.45f)

    val LightBg = Color(0xFFF5F7FB)
    val LightSecondaryBg = Color(0xFFEEF2FF)
    val LightSurface = Color(0xFFFFFFFF)
    val LightElevated = Color(0xFFFCFDFF)
    val LightPrimary = Color(0xFF4F46E5)
    val LightAccent = Color(0xFF6366F1)
    val LightCyan = Color(0xFF00B8FF)
    val LightText = Color(0xFF101828)
    val LightTextSecondary = Color(0xFF667085)
    val LightTextMuted = Color(0xFF98A2B3)
    val LightBorder = Color(0xFFE4E7EC)
    val Warning = Color(0xFFF59E0B)
    val LightError = Color(0xFFEF4444)
    val LightGlow = LightPrimary.copy(alpha = 0.24f)
}

object DiplomGradients {
    val brandHorizontal: Brush
        get() = Brush.horizontalGradient(
            listOf(DiplomColors.Primary, DiplomColors.SecondaryAccent),
        )
    val brandVertical: Brush
        get() = Brush.verticalGradient(
            listOf(DiplomColors.Primary, DiplomColors.SecondaryAccent, DiplomColors.Cyan.copy(alpha = 0.6f)),
        )
    val meshBluePurple: Brush
        get() = Brush.radialGradient(
            colors = listOf(DiplomColors.Primary.copy(alpha = 0.35f), Color.Transparent),
        )
    val meshPurpleCyan: Brush
        get() = Brush.radialGradient(
            colors = listOf(DiplomColors.SecondaryAccent.copy(alpha = 0.28f), Color.Transparent),
        )
    val lightPrimary: Brush
        get() = Brush.linearGradient(
            listOf(DiplomColors.LightPrimary, DiplomColors.LightAccent),
        )
    val lightAccent: Brush
        get() = Brush.linearGradient(
            listOf(DiplomColors.LightAccent, DiplomColors.LightCyan),
        )
    val lightSurface: Brush
        get() = Brush.verticalGradient(
            listOf(DiplomColors.LightBg, DiplomColors.LightSecondaryBg, DiplomColors.LightBg),
        )

    /** Активный (успешный) внешний вид кнопки/элемента. */
    val lightSuccess: Brush
        get() = Brush.linearGradient(
            listOf(DiplomColors.Success, DiplomColors.LightAccent),
        )

    /** Активный (успешный) внешний вид для тёмной темы. */
    val successHorizontal: Brush
        get() = Brush.horizontalGradient(
            listOf(DiplomColors.Success, DiplomColors.SecondaryAccent),
        )
}

object DiplomDimens {
    val radiusSmall = 12.dp
    val radiusMedium = 18.dp
    val radiusLarge = 28.dp
    val space4 = 4.dp
    val space8 = 8.dp
    val space12 = 12.dp
    val space16 = 16.dp
    val space24 = 24.dp
    val space32 = 32.dp
    val buttonMinHeight = 52.dp
    val touchMin = 48.dp
}

@Composable
fun isLightUi(): Boolean = MaterialTheme.colorScheme.background.luminance() > 0.5f

@Composable
fun themedTextPrimary(): Color = if (isLightUi()) DiplomColors.LightText else DiplomColors.TextPrimary

@Composable
fun themedTextSecondary(): Color = if (isLightUi()) DiplomColors.LightTextSecondary else DiplomColors.TextSecondary

@Composable
fun themedMutedText(): Color = if (isLightUi()) DiplomColors.LightTextMuted else DiplomColors.TextSecondary.copy(alpha = 0.7f)

@Composable
fun themedAccent(): Color = if (isLightUi()) DiplomColors.LightPrimary else DiplomColors.Primary

@Composable
fun themedCyan(): Color = if (isLightUi()) DiplomColors.LightCyan else DiplomColors.Cyan

@Composable
fun themedError(): Color = if (isLightUi()) DiplomColors.LightError else DiplomColors.ErrorSoft

@Composable
fun themedBorder(): Color = if (isLightUi()) DiplomColors.LightBorder else DiplomColors.GlassBorder

@Composable
fun themedScreenBrush(): Brush = if (isLightUi()) DiplomGradients.lightSurface else {
    Brush.verticalGradient(listOf(DiplomColors.DarkBg, DiplomColors.DarkSurface, DiplomColors.DarkBg))
}
