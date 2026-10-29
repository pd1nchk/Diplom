package com.example.diplom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.diplom.ui.design.themedAccent
import com.example.diplom.ui.design.themedTextPrimary
import com.example.diplom.ui.design.themedTextSecondary
import com.example.diplom.ui.platform.PROFILE_CROP_PREVIEW_FRAME_DP
import com.example.diplom.ui.platform.decodeImageFromBytes
import com.example.diplom.ui.platform.drawProfileCropFrame
import com.example.diplom.ui.platform.encodeProfilePhotoJpeg
import com.example.diplom.ui.platform.renderProfileAvatarBitmap
import com.example.diplom.ui.platform.rotateImageBitmap

@Composable
fun ProfilePhotoCropDialog(
    sourceBytes: ByteArray,
    onDismiss: () -> Unit,
    onConfirm: (ByteArray) -> Unit,
) {
    val sourceBitmap = remember(sourceBytes) { decodeImageFromBytes(sourceBytes) }
    var rotationDegrees by remember { mutableIntStateOf(0) }
    val displayBitmap = remember(sourceBitmap, rotationDegrees) {
        sourceBitmap?.let { rotateImageBitmap(it, rotationDegrees) }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val previewFramePx = remember(density) {
        with(density) { PROFILE_CROP_PREVIEW_FRAME_DP.dp.toPx() }
    }

    if (displayBitmap == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Не удалось открыть фото") },
            text = { Text("Выберите другой файл (JPG или PNG).", color = themedTextSecondary()) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Фото профиля", fontWeight = FontWeight.Bold, color = themedTextPrimary())
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Сдвиньте и увеличьте фото. Круг — как в профиле.",
                    style = MaterialTheme.typography.bodySmall,
                    color = themedTextSecondary(),
                )
                ProfileCropEditor(
                    bitmap = displayBitmap,
                    scale = scale,
                    pan = pan,
                    onScaleChange = { scale = it },
                    onPanChange = { pan = it },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            rotationDegrees = (rotationDegrees - 90 + 360) % 360
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.RotateLeft,
                            contentDescription = "Повернуть влево",
                            tint = themedAccent(),
                        )
                        Text(" Влево", color = themedAccent())
                    }
                    TextButton(
                        onClick = {
                            rotationDegrees = (rotationDegrees + 90) % 360
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.RotateRight,
                            contentDescription = "Повернуть вправо",
                            tint = themedAccent(),
                        )
                        Text(" Вправо", color = themedAccent())
                    }
                }
                Column(Modifier.fillMaxWidth()) {
                    Text("Масштаб", style = MaterialTheme.typography.labelMedium, color = themedTextSecondary())
                    Slider(
                        value = scale,
                        onValueChange = { scale = it },
                        valueRange = 0.5f..4f,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cropped = renderProfileAvatarBitmap(
                        source = displayBitmap,
                        userScale = scale,
                        pan = pan,
                        previewFramePx = previewFramePx,
                    )
                    val jpeg = encodeProfilePhotoJpeg(cropped)
                    if (jpeg.isNotEmpty()) onConfirm(jpeg)
                },
            ) {
                Text("Сохранить", color = themedAccent(), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@Composable
private fun ProfileCropEditor(
    bitmap: androidx.compose.ui.graphics.ImageBitmap,
    scale: Float,
    pan: Offset,
    onScaleChange: (Float) -> Unit,
    onPanChange: (Offset) -> Unit,
) {
    val frameDp = PROFILE_CROP_PREVIEW_FRAME_DP.dp
    Box(
        modifier = Modifier
            .size(frameDp)
            .clip(CircleShape)
            .background(Color(0xFF0F172A))
            .border(2.dp, themedAccent().copy(alpha = 0.45f), CircleShape)
            .pointerInput(bitmap, scale, pan) {
                detectTransformGestures { _, panChange, zoom, _ ->
                    onScaleChange((scale * zoom).coerceIn(0.5f, 4f))
                    onPanChange(pan + panChange)
                }
            }
            .pointerInput(bitmap, scale, pan) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onPanChange(pan + dragAmount)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawProfileCropFrame(bitmap, scale, pan)
        }
    }
}
