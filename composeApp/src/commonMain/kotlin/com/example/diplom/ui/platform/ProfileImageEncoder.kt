package com.example.diplom.ui.platform

import androidx.compose.ui.graphics.ImageBitmap

expect fun encodeProfilePhotoJpeg(image: ImageBitmap, quality: Int = 90): ByteArray
