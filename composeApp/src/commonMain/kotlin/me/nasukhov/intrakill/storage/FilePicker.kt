package me.nasukhov.intrakill.storage

import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

expect object FilePicker {
    suspend fun pickMultiple(): List<PickedMedia>
}

data class PickedMedia(
    val name: String,
    val bytes: ByteArray,
    val mimeType: String,
) {
    val preview by lazy {
        Image.makeFromEncoded(generateImagePreview()).toComposeImageBitmap()
    }

    private val mediaType = when {
        mimeType.startsWith("image/") && mimeType != "image/gif" -> "image"
        mimeType == "image/gif" -> "gif"
        mimeType.startsWith("video/") -> "video"
        else -> error("Unsupported media type: $mimeType")
    }

    fun generateImagePreview(maxSize: Int = 256): ByteArray {
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
}