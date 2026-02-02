package me.nasukhov.intrakill.storage

import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

expect object FilePicker {
    suspend fun pickMultiple(): List<PickedMedia>
}

enum class MediaKind {
    IMAGE,
    GIF,
    VIDEO,
}

fun String.mediaKind() = when {
    this.startsWith("image/") && this != "image/gif" -> MediaKind.IMAGE
    this == "image/gif" -> MediaKind.GIF
    this.startsWith("video/") -> MediaKind.VIDEO
    else -> error("Unsupported media type: $this")
}

data class PickedMedia(
    val name: String,
    val bytes: ByteArray,
    val mimeType: String,
    val previewSize: Int = 512
) {
    val rawPreview by lazy {
        when (mimeType.mediaKind()) {
            MediaKind.IMAGE -> generateImagePreview(bytes, previewSize)
            MediaKind.GIF -> generateGifPreview(bytes, previewSize)
            MediaKind.VIDEO -> generateVideoPlaceholder(previewSize)
        }
    }

    val preview by lazy {
        Image.makeFromEncoded(rawPreview).toComposeImageBitmap()
    }

    private fun generateImagePreview(
        bytes: ByteArray,
        maxSize: Int
    ): ByteArray {
        val img = ImageIO.read(ByteArrayInputStream(bytes))

        val scale = minOf(
            maxSize.toDouble() / img.width,
            maxSize.toDouble() / img.height,
            1.0
        )

        val w = (img.width * scale).toInt()
        val h = (img.height * scale).toInt()

        val resized = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = resized.createGraphics()
        g.drawImage(img, 0, 0, w, h, null)
        g.dispose()

        val out = ByteArrayOutputStream()
        ImageIO.write(resized, "jpg", out)
        return out.toByteArray()
    }

    private fun generateGifPreview(
        bytes: ByteArray,
        maxSize: Int
    ): ByteArray {
        // ImageIO returns first frame only
        return generateImagePreview(bytes, maxSize)
    }

    private fun generateVideoPlaceholder(size: Int): ByteArray {
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()

        // background
        g.color = Color(30, 30, 30)
        g.fillRect(0, 0, size, size)

        // play triangle
        g.color = Color.WHITE
        val triangle = intArrayOf(
            size / 3, size / 4,
            size / 3, size * 3 / 4,
            size * 2 / 3, size / 2
        )
        g.fillPolygon(
            intArrayOf(triangle[0], triangle[2], triangle[4]),
            intArrayOf(triangle[1], triangle[3], triangle[5]),
            3
        )

        g.dispose()

        val out = ByteArrayOutputStream()
        ImageIO.write(img, "jpg", out)
        return out.toByteArray()
    }
}