package me.nasukhov.intrakill.storage

import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.min

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
        val srcImage = Image.makeFromEncoded(bytes)
        val scale = min(
            maxSize.toFloat() / srcImage.width,
            maxSize.toFloat() / srcImage.height
        ).coerceAtMost(1f)

        val targetWidth = (srcImage.width * scale).toInt()
        val targetHeight = (srcImage.height * scale).toInt()

        val surface = Surface.makeRasterN32Premul(
            targetWidth,
            targetHeight,
        )

        val canvas = surface.canvas
        canvas.clear(Color.TRANSPARENT)

        val paint = Paint().apply {
            isAntiAlias = true
        }

        canvas.drawImageRect(
            image = srcImage,
            src = Rect.makeWH(srcImage.width.toFloat(), srcImage.height.toFloat()),
            dst = Rect.makeWH(targetWidth.toFloat(), targetHeight.toFloat()),
            samplingMode = SamplingMode.LINEAR,
            paint = paint,
            strict = true,
        )

        val resizedImage = surface.makeImageSnapshot()

        val encoded = resizedImage.encodeToData(
            EncodedImageFormat.JPEG,
            85
        ) ?: error("Skia JPEG encoding failed")

        return encoded.bytes
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
        g.color = java.awt.Color(30, 30, 30)
        g.fillRect(0, 0, size, size)

        // play triangle
        g.color = java.awt.Color.WHITE
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