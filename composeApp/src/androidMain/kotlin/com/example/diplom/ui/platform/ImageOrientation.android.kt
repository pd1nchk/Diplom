package com.example.diplom.ui.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Декодирует JPEG/PNG и применяет EXIF-ориентацию (фото с камеры не «лёгут» набок). */
fun decodeBitmapWithExifOrientation(bytes: ByteArray): Bitmap? {
    val orientation = runCatching {
        ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_UNDEFINED)

    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val matrix = matrixForExifOrientation(orientation) ?: return bitmap
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated != bitmap) bitmap.recycle()
    return rotated
}

/** Перекодирует в JPEG без EXIF — дальше ориентация только из пикселей. */
fun normalizeImageBytes(bytes: ByteArray): ByteArray {
    val bitmap = decodeBitmapWithExifOrientation(bytes) ?: return bytes
    return encodeJpeg(bitmap, 92).also { if (!bitmap.isRecycled) bitmap.recycle() }
}

fun encodeJpeg(bitmap: Bitmap, quality: Int = 92): ByteArray =
    ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(50, 100), out)
        out.toByteArray()
    }

fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
    val d = ((degrees % 360) + 360) % 360
    if (d == 0) return bitmap
    val matrix = Matrix().apply { postRotate(d.toFloat()) }
    val rect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
    matrix.mapRect(rect)
    val outW = rect.width().toInt().coerceAtLeast(1)
    val outH = rect.height().toInt().coerceAtLeast(1)
    val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.translate(outW / 2f, outH / 2f)
    canvas.rotate(d.toFloat())
    canvas.drawBitmap(bitmap, -bitmap.width / 2f, -bitmap.height / 2f, null)
    return out
}

actual fun rotateImageBitmap(bitmap: ImageBitmap, degrees: Int): ImageBitmap {
    val d = ((degrees % 360) + 360) % 360
    if (d == 0) return bitmap
    val src = bitmap.asAndroidBitmap()
    val rotated = rotateBitmap(src, d)
    return rotated.asImageBitmap()
}

private fun matrixForExifOrientation(orientation: Int): Matrix? {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_NORMAL,
        ExifInterface.ORIENTATION_UNDEFINED,
        -> return null
        else -> return null
    }
    return matrix
}
