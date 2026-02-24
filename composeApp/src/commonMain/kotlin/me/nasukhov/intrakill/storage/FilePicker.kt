package me.nasukhov.intrakill.storage

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import me.nasukhov.intrakill.content.Content
import me.nasukhov.intrakill.scene.asImageBitmap

expect object FilePicker {
    suspend fun pickMultiple(): List<Result<PickedMedia>>
}

enum class MediaKind {
    IMAGE,
    GIF,
    VIDEO,
}

fun String.mediaKind() = when {
    startsWith("image/") && this != "image/gif" -> MediaKind.IMAGE
    this == "image/gif" -> MediaKind.GIF
    startsWith("video/") -> MediaKind.VIDEO
    else -> error("Unsupported media type: $this")
}

expect fun generatePreviewBytes(
    content: Content,
    mimeType: String,
    previewSize: Int
): ByteArray

data class PickedMedia(
    val name: String,
    val content: Content,
    val mimeType: String,
    val previewSize: Int = 512
) {
    val rawPreview by lazy {
        generatePreviewBytes(content, mimeType, previewSize)
    }

    val preview: ImageBitmap by lazy {
        rawPreview.asImageBitmap()
    }
}

@Composable
expect fun ProvideFilePicker(content: @Composable () -> Unit)