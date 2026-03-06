package me.nasukhov.intrakill.storage

import androidx.compose.runtime.Composable
import me.nasukhov.intrakill.content.Content

typealias FileSize = Long

expect object FilePicker {
    suspend fun pickMultiple(): List<Result<PickedMedia>>
}

enum class MediaKind {
    IMAGE,
    GIF,
    VIDEO,
}

fun String.mediaKind() =
    when {
        startsWith("image/") && this != "image/gif" -> MediaKind.IMAGE
        this == "image/gif" -> MediaKind.GIF
        startsWith("video/") -> MediaKind.VIDEO
        else -> error("Unsupported media type: $this")
    }

data class PickedMedia(
    val name: String,
    val content: Content,
    val mimeType: String,
    val size: FileSize,
    val rawPreview: ByteArray,
) {
    companion object {
        const val PREVIEW_SIZE: Int = 512
        const val VIDEO_PREVIEW_FRAME_AT_X_SECOND = 8
    }
}

@Composable
expect fun ProvideFilePicker(content: @Composable () -> Unit)
