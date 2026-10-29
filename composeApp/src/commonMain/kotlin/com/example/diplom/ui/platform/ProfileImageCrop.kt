package com.example.diplom.ui.platform

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.roundToInt

/** Размер круга предпросмотра в диалоге обрезки (dp) — должен совпадать с [ProfilePhotoCropDialog]. */
const val PROFILE_CROP_PREVIEW_FRAME_DP = 260

/** Поворот предпросмотра (градусы, кратно 90). На Android — полноценно; на остальных платформах — без изменений. */
expect fun rotateImageBitmap(bitmap: ImageBitmap, degrees: Int): ImageBitmap

data class ProfileCropGeometry(
    val totalScale: Float,
    val left: Float,
    val top: Float,
    val drawWidth: Float,
    val drawHeight: Float,
)

/** Единая геометрия «cover» + пользовательский масштаб и сдвиг в координатах кадра. */
fun computeProfileCropGeometry(
    frameSizePx: Float,
    srcWidthPx: Float,
    srcHeightPx: Float,
    userScale: Float,
    panInFramePx: Offset,
): ProfileCropGeometry {
    val cover = max(frameSizePx / srcWidthPx, frameSizePx / srcHeightPx)
    val totalScale = cover * userScale.coerceIn(0.5f, 4f)
    val drawW = srcWidthPx * totalScale
    val drawH = srcHeightPx * totalScale
    val left = (frameSizePx - drawW) / 2f + panInFramePx.x
    val top = (frameSizePx - drawH) / 2f + panInFramePx.y
    return ProfileCropGeometry(totalScale, left, top, drawW, drawH)
}

/**
 * Рендер квадратного аватара. [pan] задан в координатах кадра предпросмотра [previewFramePx];
 * при сохранении сдвиг масштабируется под [outputPx].
 */
fun renderProfileAvatarBitmap(
    source: ImageBitmap,
    userScale: Float,
    pan: Offset,
    previewFramePx: Float,
    outputPx: Int = 512,
): ImageBitmap {
    val out = outputPx.toFloat()
    val panOut = if (previewFramePx > 0f) {
        Offset(pan.x * out / previewFramePx, pan.y * out / previewFramePx)
    } else {
        pan
    }
    val geo = computeProfileCropGeometry(
        frameSizePx = out,
        srcWidthPx = source.width.toFloat(),
        srcHeightPx = source.height.toFloat(),
        userScale = userScale,
        panInFramePx = panOut,
    )

    val result = ImageBitmap(outputPx, outputPx)
    val canvas = Canvas(result)
    canvas.drawRect(
        left = 0f,
        top = 0f,
        right = out,
        bottom = out,
        paint = Paint().apply { color = Color(0xFF1E293B) },
    )
    canvas.drawImageRect(
        image = source,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(source.width, source.height),
        dstOffset = IntOffset(geo.left.roundToInt(), geo.top.roundToInt()),
        dstSize = IntSize(geo.drawWidth.roundToInt().coerceAtLeast(1), geo.drawHeight.roundToInt().coerceAtLeast(1)),
        paint = Paint(),
    )
    return result
}

/** Рисует кадр обрезки в [DrawScope] — та же математика, что при сохранении. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawProfileCropFrame(
    image: ImageBitmap,
    userScale: Float,
    panInFramePx: Offset,
) {
    val framePx = size.width
    val geo = computeProfileCropGeometry(
        frameSizePx = framePx,
        srcWidthPx = image.width.toFloat(),
        srcHeightPx = image.height.toFloat(),
        userScale = userScale,
        panInFramePx = panInFramePx,
    )
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(geo.left.roundToInt(), geo.top.roundToInt()),
        dstSize = IntSize(geo.drawWidth.roundToInt().coerceAtLeast(1), geo.drawHeight.roundToInt().coerceAtLeast(1)),
    )
}
