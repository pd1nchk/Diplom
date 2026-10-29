package com.example.diplom.ui.platform

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@androidx.compose.runtime.Composable
actual fun rememberImagePicker(onImagePicked: (ByteArray) -> Unit): () -> Unit {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val raw = input.readBytes()
                    onImagePicked(normalizeImageBytes(raw))
                }
            } catch (_: Exception) {}
        }
    }

    return {
        launcher.launch("image/*")
    }
}
