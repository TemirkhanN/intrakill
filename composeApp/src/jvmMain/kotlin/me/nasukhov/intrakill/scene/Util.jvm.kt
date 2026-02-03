package me.nasukhov.intrakill.scene

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

actual fun ByteArray.asImageBitmap(): ImageBitmap =
    Image.makeFromEncoded(this).toComposeImageBitmap()
