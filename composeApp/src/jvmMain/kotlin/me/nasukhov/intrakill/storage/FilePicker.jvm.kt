package me.nasukhov.intrakill.storage

import androidx.compose.runtime.Composable
import me.nasukhov.intrakill.content.Content
import org.jetbrains.skia.*
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.min

actual fun generatePreviewBytes(
    content: Content,
    mimeType: String,
    previewSize: Int
): ByteArray =
    when (mimeType.mediaKind()) {
        MediaKind.IMAGE, MediaKind.GIF ->
            generateImagePreviewSkia(content.read().readBytes(), previewSize)

        MediaKind.VIDEO ->
            generateVideoPlaceholder(previewSize)
    }

private fun generateImagePreviewSkia(
    bytes: ByteArray,
    maxSize: Int
): ByteArray {
    val src = Image.makeFromEncoded(bytes)
    val scale = min(
        maxSize.toFloat() / src.width,
        maxSize.toFloat() / src.height
    ).coerceAtMost(1f)

    val w = (src.width * scale).toInt()
    val h = (src.height * scale).toInt()

    val surface = Surface.makeRasterN32Premul(w, h)
    val canvas = surface.canvas
    canvas.clear(Color.TRANSPARENT)

    canvas.drawImageRect(
        image = src,
        src = Rect.makeWH(src.width.toFloat(), src.height.toFloat()),
        dst = Rect.makeWH(w.toFloat(), h.toFloat()),
        samplingMode = SamplingMode.LINEAR,
        paint = Paint().apply { isAntiAlias = true },
        strict = true
    )

    val encoded = surface.makeImageSnapshot()
        .encodeToData(EncodedImageFormat.JPEG, 85)
        ?: error("JPEG encoding failed")

    return encoded.bytes
}

private fun generateVideoPlaceholder(size: Int): ByteArray {
    val img = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()

    g.color = java.awt.Color(30, 30, 30)
    g.fillRect(0, 0, size, size)

    g.color = java.awt.Color.WHITE
    g.fillPolygon(
        intArrayOf(size / 3, size / 3, size * 2 / 3),
        intArrayOf(size / 4, size * 3 / 4, size / 2),
        3
    )

    g.dispose()

    return ByteArrayOutputStream().also {
        ImageIO.write(img, "jpg", it)
    }.toByteArray()
}

@Composable
actual fun ProvideFilePicker(content: @Composable (() -> Unit)) {
    content()
}