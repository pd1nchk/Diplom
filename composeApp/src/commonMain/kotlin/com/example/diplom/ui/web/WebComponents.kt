package com.example.diplom.ui.web

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.diplom.ui.design.APP_PRODUCT_NAME
import com.example.diplom.ui.design.AppLogoIcon
import com.example.diplom.ui.design.DiplomColors
import com.example.diplom.ui.design.isLightUi
import com.example.diplom.ui.design.themedAccent
import com.example.diplom.ui.design.themedBorder
import com.example.diplom.ui.design.themedTextPrimary
import com.example.diplom.ui.design.themedTextSecondary

val WebBrandGradient: Brush
    get() = Brush.linearGradient(
        listOf(DiplomColors.LightPrimary, DiplomColors.LightAccent, DiplomColors.SecondaryAccent),
    )

@Composable
fun WebCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val light = isLightUi()
    val shape = RoundedCornerShape(20.dp)
    val bg = if (light) Color.White else DiplomColors.DarkElevated
    val mod = modifier
        .clip(shape)
        .background(bg)
        .border(1.dp, themedBorder().copy(alpha = if (light) 0.9f else 0.35f), shape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(20.dp)
    Box(mod) { content() }
}

@Composable
fun WebNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = themedAccent()
    val shape = RoundedCornerShape(14.dp)
    val bg = if (selected) accent.copy(alpha = if (isLightUi()) 0.12f else 0.22f) else Color.Transparent
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) accent else themedTextSecondary(),
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) themedTextPrimary() else themedTextSecondary(),
        )
    }
}

@Composable
fun WebFloatingBottomNav(
    items: List<Pair<ImageVector, String>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val light = isLightUi()
    val shape = RoundedCornerShape(28.dp)
    val container = if (light) Color.White.copy(alpha = 0.94f) else DiplomColors.DarkElevated.copy(alpha = 0.96f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(shape)
            .background(container)
            .border(1.dp, themedBorder(), shape)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, (icon, label) ->
            val selected = index == selectedIndex
            val accent = themedAccent()
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val lift by animateFloatAsState(
                targetValue = if (hovered && !selected) 1.02f else 1f,
                animationSpec = tween(180),
                label = "navLift",
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        scaleX = lift
                        scaleY = lift
                    }
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (selected) accent.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable(interactionSource = interaction, indication = null) { onSelect(index) }
                    .padding(vertical = 10.dp, horizontal = 8.dp),
            ) {
                Icon(icon, label, tint = if (selected) accent else themedTextSecondary(), modifier = Modifier.size(22.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) accent else themedTextSecondary(),
                )
            }
        }
    }
}

@Composable
fun WebUserAvatar(
    initials: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(WebBrandGradient),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
fun WebBrandLogo(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppLogoIcon(logoSize = 44.dp, animatedGlow = false)
        Text(
            text = APP_PRODUCT_NAME,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = themedTextPrimary(),
        )
    }
}
