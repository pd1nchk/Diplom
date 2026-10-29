package com.example.diplom.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.document
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader

@Composable
actual fun rememberImagePicker(onImagePicked: (ByteArray) -> Unit): () -> Unit {
    val handler = remember(onImagePicked) { onImagePicked }
    return remember(handler) {
        { openWebPhotoPicker(handler) }
    }
}

private fun openWebPhotoPicker(onImagePicked: (ByteArray) -> Unit) {
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"
    input.accept = "image/*"
    input.onchange = {
        val file = input.files?.item(0)
        if (file != null) {
            val reader = FileReader()
            reader.onload = {
                val buffer = reader.result as? ArrayBuffer
                if (buffer != null) {
                    val view = Int8Array(buffer)
                    onImagePicked(ByteArray(view.length) { index -> view[index] })
                }
            }
            reader.readAsArrayBuffer(file)
        }
    }
    input.click()
}
