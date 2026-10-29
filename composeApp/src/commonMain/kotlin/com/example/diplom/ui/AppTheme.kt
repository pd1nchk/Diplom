package com.example.diplom.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.example.diplom.ui.design.DiplomColors

private val DarkScheme = darkColorScheme(
    primary = DiplomColors.Primary,
    onPrimary = DiplomColors.TextPrimary,
    primaryContainer = DiplomColors.DarkElevated,
    onPrimaryContainer = DiplomColors.TextPrimary,
    secondary = DiplomColors.SecondaryAccent,
    onSecondary = DiplomColors.TextPrimary,
    secondaryContainer = DiplomColors.DarkElevated,
    onSecondaryContainer = DiplomColors.TextPrimary,
    tertiary = DiplomColors.Cyan,
    onTertiary = DiplomColors.DarkBg,
    background = DiplomColors.DarkBg,
    onBackground = DiplomColors.TextPrimary,
    surface = DiplomColors.DarkSurface,
    onSurface = DiplomColors.TextPrimary,
    surfaceVariant = DiplomColors.DarkElevated,
    onSurfaceVariant = DiplomColors.TextSecondary,
    surfaceContainerHigh = DiplomColors.DarkElevated,
    error = DiplomColors.ErrorSoft,
    onError = DiplomColors.TextPrimary,
    outline = DiplomColors.GlassBorder,
    outlineVariant = DiplomColors.TextSecondary.copy(alpha = 0.35f),
)

private val LightScheme = lightColorScheme(
    primary = DiplomColors.LightPrimary,
    onPrimary = Color.White,
    primaryContainer = DiplomColors.LightSecondaryBg,
    onPrimaryContainer = DiplomColors.LightText,
    secondary = DiplomColors.LightAccent,
    onSecondary = Color.White,
    secondaryContainer = DiplomColors.LightElevated,
    onSecondaryContainer = DiplomColors.LightText,
    tertiary = DiplomColors.LightCyan,
    onTertiary = Color.White,
    background = DiplomColors.LightBg,
    onBackground = DiplomColors.LightText,
    surface = DiplomColors.LightSurface,
    onSurface = DiplomColors.LightText,
    surfaceVariant = DiplomColors.LightSecondaryBg,
    onSurfaceVariant = DiplomColors.LightTextSecondary,
    surfaceContainerHigh = DiplomColors.LightElevated,
    error = DiplomColors.LightError,
    onError = Color.White,
    outline = DiplomColors.LightBorder,
    outlineVariant = DiplomColors.LightTextMuted,
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
)

@Composable
fun DiplomTheme(
    useDarkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val colors: ColorScheme = if (useDarkTheme) DarkScheme else LightScheme
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content,
    )
}
