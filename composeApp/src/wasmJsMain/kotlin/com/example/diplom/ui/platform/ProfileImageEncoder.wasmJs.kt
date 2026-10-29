package com.example.diplom.ui.platform

import androidx.compose.ui.graphics.ImageBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

actual fun encodeProfilePhotoJpeg(image: ImageBitmap, quality: Int): ByteArray {
    val w = image.width
    val h = image.height
    if (w <= 0 || h <= 0) return ByteArray(0)

    val pixels = IntArray(w * h)
    image.readPixels(pixels)

    val rgba = ByteArray(w * h * 4)
    for (i in pixels.indices) {
        val color = pixels[i]
        val base = i * 4
        rgba[base] = ((color shr 16) and 0xFF).toByte()
        rgba[base + 1] = ((color shr 8) and 0xFF).toByte()
        rgba[base + 2] = (color and 0xFF).toByte()
        rgba[base + 3] = ((color shr 24) and 0xFF).toByte()
    }

    val info = ImageInfo.makeN32Premul(w, h)
    return Image.makeRaster(info, rgba, w * 4)
        .encodeToData(EncodedImageFormat.JPEG, quality.coerceIn(50, 100))
        ?.bytes
        ?: ByteArray(0)
}
