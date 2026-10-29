package com.example.diplom.ui.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun FitBrandMark(
    modifier: Modifier = Modifier,
    showTagline: Boolean = true,
    animated: Boolean = false,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AppLogoIcon(
            logoSize = 104.dp,
            animatedGlow = animated,
        )
        if (showTagline) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = APP_PRODUCT_NAME,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = themedTextPrimary(),
            )
        }
    }
}
