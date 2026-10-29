package com.example.diplom.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.diplom.ui.design.FitBrandMark
import com.example.diplom.ui.design.MeshGradientBackground
import kotlinx.coroutines.delay

@Composable
fun SplashIntro(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(900)
        onFinished()
    }
    Box(modifier = Modifier.fillMaxSize()) {
        MeshGradientBackground(Modifier.fillMaxSize())
        FitBrandMark(
            modifier = Modifier.align(Alignment.Center),
            showTagline = false,
        )
    }
}
