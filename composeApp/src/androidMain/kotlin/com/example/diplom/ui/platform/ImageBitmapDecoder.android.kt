package com.example.diplom.ui.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual fun decodeImageFromBytes(bytes: ByteArray): ImageBitmap? {
    return decodeBitmapWithExifOrientation(bytes)?.asImageBitmap()
}
