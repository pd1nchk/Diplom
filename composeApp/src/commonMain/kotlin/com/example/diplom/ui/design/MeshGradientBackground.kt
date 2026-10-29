package com.example.diplom.ui.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import com.example.diplom.ui.platform.isWebComposeClient

@Composable
fun MeshGradientBackground(modifier: Modifier = Modifier) {
    if (isWebComposeClient()) {
        Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    val transition = rememberInfiniteTransition(label = "mesh")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(10_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "meshShift",
    )
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val scheme = MaterialTheme.colorScheme
        val dark = scheme.background.red < 0.2f && scheme.background.green < 0.2f
        val p1 = if (dark) DiplomColors.Primary else DiplomColors.LightPrimary
        val p2 = if (dark) DiplomColors.SecondaryAccent else DiplomColors.LightAccent
        val p3 = if (dark) DiplomColors.Cyan else DiplomColors.LightPrimary
        Box(Modifier.fillMaxSize().background(scheme.background))
        Box(
            Modifier
                .size(w * 0.85f)
                .offset(x = w * (-0.15f + shift * 0.1f), y = h * -0.1f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(p1.copy(alpha = if (dark) 0.42f else 0.2f), Color.Transparent),
                        radius = w.value * 0.42f,
                    ),
                    CircleShape,
                ),
        )
        Box(
            Modifier
                .size(w * 0.75f)
                .offset(x = w * (0.35f - shift * 0.08f), y = h * (0.35f + shift * 0.05f))
                .background(
                    Brush.radialGradient(
                        colors = listOf(p2.copy(alpha = if (dark) 0.35f else 0.18f), Color.Transparent),
                        radius = w.value * 0.38f,
                    ),
                    CircleShape,
                ),
        )
        Box(
            Modifier
                .size(w * 0.55f)
                .offset(x = w * 0.1f, y = h * (0.55f - shift * 0.06f))
                .background(
                    Brush.radialGradient(
                        colors = listOf(p3.copy(alpha = if (dark) 0.28f else 0.14f), Color.Transparent),
                        radius = w.value * 0.32f,
                    ),
                    CircleShape,
                ),
        )
    }
}
