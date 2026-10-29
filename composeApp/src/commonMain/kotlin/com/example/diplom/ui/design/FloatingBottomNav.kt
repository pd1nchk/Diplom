package com.example.diplom.ui.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.diplom.ui.platform.isWebComposeClient
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DiplomCapsuleNavBar(
    items: List<Pair<ImageVector, String>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(32.dp)
    val light = isLightUi()
    val container = if (light) Color.White.copy(alpha = 0.82f) else DiplomColors.DarkElevated.copy(alpha = 0.94f)
    val border = if (light) Color.White.copy(alpha = 0.72f) else DiplomColors.GlassBorder
    val selectedText = if (light) DiplomColors.LightText else DiplomColors.TextPrimary
    val unselectedText = if (light) DiplomColors.LightTextSecondary else DiplomColors.TextSecondary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DiplomDimens.space16, vertical = DiplomDimens.space8)
            .diplomShadow(
                elevation = 22.dp,
                shape = shape,
                ambientColor = if (light) DiplomColors.LightPrimary.copy(alpha = 0.14f) else DiplomColors.Primary.copy(alpha = 0.25f),
                spotColor = if (light) DiplomColors.LightAccent.copy(alpha = 0.18f) else DiplomColors.Primary.copy(alpha = 0.25f),
            )
            .clip(shape)
            .background(container)
            .border(1.dp, border, shape)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, (icon, label) ->
            val selected = index == selectedIndex
            val scale by animateFloatAsState(
                targetValue = if (selected) 1.06f else 1f,
                animationSpec = tween(220),
                label = "navScale",
            )
            val navScale = if (isWebComposeClient()) 1f else scale
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        scaleX = navScale
                        scaleY = navScale
                    }
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(index) }
                    .background(
                        if (selected) {
                            Brush.horizontalGradient(
                                listOf(
                                    (if (light) DiplomColors.LightPrimary else DiplomColors.Primary).copy(alpha = if (light) 0.16f else 0.35f),
                                    (if (light) DiplomColors.LightAccent else DiplomColors.SecondaryAccent).copy(alpha = if (light) 0.14f else 0.25f),
                                ),
                            )
                        } else {
                            Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                        },
                        RoundedCornerShape(22.dp),
                    )
                    .padding(vertical = 10.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (selected) selectedText else unselectedText,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) selectedText else unselectedText,
                )
            }
        }
    }
}
