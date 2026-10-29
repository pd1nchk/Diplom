package com.example.diplom.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(DiplomDimens.radiusMedium),
    borderAlpha: Float = 0.08f,
    fillAlpha: Float = 0.06f,
    contentPadding: Dp = DiplomDimens.space16,
    content: @Composable ColumnScope.() -> Unit,
) {
    val light = isLightUi()
    val fill = if (light) Color.White.copy(alpha = 0.72f) else Color.White.copy(alpha = fillAlpha)
    val border = if (light) Color.White.copy(alpha = 0.64f) else Color.White.copy(alpha = borderAlpha)
    val shadow = if (light) 18.dp else 0.dp
    Column(
        modifier = modifier
            .diplomShadow(
                elevation = shadow,
                shape = shape,
                ambientColor = DiplomColors.LightPrimary.copy(alpha = 0.10f),
                spotColor = DiplomColors.LightAccent.copy(alpha = 0.12f),
            )
            .clip(shape)
            .background(fill)
            .border(BorderStroke(1.dp, border), shape)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun GradientStrokeCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(DiplomDimens.radiusLarge),
    brush: Brush = DiplomGradients.brandHorizontal,
    content: @Composable ColumnScope.() -> Unit,
) {
    val fill = if (isLightUi()) DiplomColors.LightElevated.copy(alpha = 0.84f) else DiplomColors.GlassFill.copy(alpha = 0.12f)
    Column(
        modifier = modifier
            .diplomShadow(
                elevation = if (isLightUi()) 16.dp else 0.dp,
                shape = shape,
                ambientColor = DiplomColors.LightPrimary.copy(alpha = 0.10f),
                spotColor = DiplomColors.LightAccent.copy(alpha = 0.10f),
            )
            .clip(shape)
            .border(BorderStroke(1.dp, brush), shape)
            .background(fill, shape)
            .padding(DiplomDimens.space16),
        content = content,
    )
}
