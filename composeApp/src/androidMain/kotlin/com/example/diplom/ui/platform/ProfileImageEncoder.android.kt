package com.example.diplom.ui.platform

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.ByteArrayOutputStream

actual fun encodeProfilePhotoJpeg(image: ImageBitmap, quality: Int): ByteArray {
    val stream = ByteArrayOutputStream()
    image.asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(50, 100), stream)
    return stream.toByteArray()
}
