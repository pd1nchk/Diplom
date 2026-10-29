package com.example.diplom.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember

@Composable
fun GradientPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Зелёный «активный» вид (например «Сессия идёт»), даже если нажатие отключено. */
    activeAppearance: Boolean = false,
    loading: Boolean = false,
    fullWidth: Boolean = true,
) {
    val shape = RoundedCornerShape(DiplomDimens.radiusMedium)
    val light = isLightUi()
    val enabledBrush = if (light) DiplomGradients.lightPrimary else DiplomGradients.brandHorizontal
    val activeBrush = if (light) DiplomGradients.lightSuccess else DiplomGradients.successHorizontal
    val disabledBrush = Brush.horizontalGradient(
        listOf(
            if (light) DiplomColors.LightTextMuted.copy(alpha = 0.75f) else Color.Gray,
            if (light) DiplomColors.LightTextMuted.copy(alpha = 0.75f) else Color.Gray,
        ),
    )
    val highlighted = enabled || activeAppearance
    val backgroundBrush = when {
        enabled -> enabledBrush
        activeAppearance -> activeBrush
        else -> disabledBrush
    }
    Box(
        modifier = modifier
            .then(
                if (fullWidth) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.wrapContentWidth().padding(horizontal = 24.dp)
                },
            )
            .height(54.dp)
            .diplomShadow(
                elevation = if (highlighted) 16.dp else 0.dp,
                shape = shape,
                spotColor = when {
                    activeAppearance && !enabled -> DiplomColors.Success.copy(alpha = 0.45f)
                    light -> DiplomColors.LightGlow
                    else -> DiplomColors.GlowPrimary
                },
            )
            .clip(shape)
            .background(backgroundBrush)
            .clickable(
                enabled = enabled && !loading,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = Color.White,
                modifier = Modifier.size(28.dp),
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}

@Composable
fun GhostTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val color = if (isLightUi()) DiplomColors.LightCyan else DiplomColors.Cyan
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(DiplomDimens.radiusSmall))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = DiplomDimens.space12, horizontal = DiplomDimens.space8),
        color = color,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
    )
}
