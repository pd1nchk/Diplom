package com.example.diplom.ui.platform

import androidx.compose.runtime.Composable

@Composable
actual fun rememberImagePicker(onImagePicked: (ByteArray) -> Unit): () -> Unit {
    return { }
}
