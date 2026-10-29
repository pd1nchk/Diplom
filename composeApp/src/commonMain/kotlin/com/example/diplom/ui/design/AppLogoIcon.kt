package com.example.diplom.ui.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import diplom.composeapp.generated.resources.Res
import diplom.composeapp.generated.resources.app_logo
import org.jetbrains.compose.resources.painterResource

@Composable
fun AppLogoIcon(
    modifier: Modifier = Modifier,
    logoSize: Dp = 88.dp,
    @Suppress("UNUSED_PARAMETER") animatedGlow: Boolean = false,
) {
    Image(
        painter = painterResource(Res.drawable.app_logo),
        contentDescription = APP_PRODUCT_NAME,
        modifier = modifier.size(logoSize),
        contentScale = ContentScale.Fit,
    )
}
