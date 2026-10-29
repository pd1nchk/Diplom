package com.example.diplom.ui.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ShimmerBar(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 18.dp,
) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerPhase",
    )
    val light = isLightUi()
    val base = if (light) DiplomColors.LightSecondaryBg else Color.White.copy(alpha = 0.06f)
    val highlight = if (light) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.18f)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(DiplomDimens.radiusSmall))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        base,
                        highlight,
                        base,
                    ),
                    start = Offset(phase * 400f - 200f, 0f),
                    end = Offset(phase * 400f + 200f, 80f),
                ),
            ),
    )
}
