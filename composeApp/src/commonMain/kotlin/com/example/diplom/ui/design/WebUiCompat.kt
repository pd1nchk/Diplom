package com.example.diplom.ui.design

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.diplom.ui.platform.isWebComposeClient

/** Blur часто роняет Compose Web (Skiko/Wasm) — на вебе отключаем. */
fun Modifier.diplomBlur(radius: Dp): Modifier =
    if (isWebComposeClient()) this else blur(radius)

/** Кастомные ambient/spot shadow на Wasm дают пустой кадр — упрощаем. */
fun Modifier.diplomShadow(
    elevation: Dp,
    shape: Shape,
    ambientColor: Color = Color.Black,
    spotColor: Color = Color.Black,
): Modifier {
    if (elevation <= 0.dp) return this
    // Любой shadow на Wasm/Skiko часто даёт пустой кадр — на вебе полностью отключаем.
    if (isWebComposeClient()) return this
    return shadow(
        elevation = elevation,
        shape = shape,
        ambientColor = ambientColor,
        spotColor = spotColor,
    )
}
