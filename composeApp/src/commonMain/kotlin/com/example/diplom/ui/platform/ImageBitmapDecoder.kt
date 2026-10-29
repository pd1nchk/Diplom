package com.example.diplom.ui.platform

import androidx.compose.ui.graphics.ImageBitmap

expect fun decodeImageFromBytes(bytes: ByteArray): ImageBitmap?
