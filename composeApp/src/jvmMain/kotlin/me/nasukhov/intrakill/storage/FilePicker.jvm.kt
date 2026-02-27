package me.nasukhov.intrakill.storage

import androidx.compose.runtime.Composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.nasukhov.intrakill.content.Content
import me.nasukhov.intrakill.storage.PickedMedia.Companion.PREVIEW_SIZE
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import kotlin.math.min

actual object FilePicker {

    actual suspend fun pickMultiple(): List<Result<PickedMedia>> = withContext(Dispatchers.IO) {
        val chooser = JFileChooser().apply {
            isMultiSelectionEnabled = true
            dialogTitle = "Select photos or videos"
        }

        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFiles.mapNotNull { file: File ->
                try {
                    val content = Content { file.inputStream() }
                    val mimeType = Files.probeContentType(file.toPath()) ?: "application/octet-stream"
                    Result.success(PickedMedia(
                        name = file.name,
                        content = content,
                        mimeType = mimeType,
                        size = file.length(),
                        rawPreview = generatePreviewBytes(file, mimeType)
                    ))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        } else {
            emptyList()
        }
    }
}

private fun generatePreviewBytes(
    file: File,
    mimeType: String
): ByteArray =
    when (mimeType.mediaKind()) {
        MediaKind.IMAGE, MediaKind.GIF -> generateImagePreviewSkia(file.readBytes())
        MediaKind.VIDEO -> generateVideoPreview(file)
    }


private fun generateImagePreviewSkia(
    bytes: ByteArray
): ByteArray {
    val src = Image.makeFromEncoded(bytes)
    val scale = min(
        PREVIEW_SIZE.toFloat() / src.width,
        PREVIEW_SIZE.toFloat() / src.height
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


private fun generateVideoPreview(file: File): ByteArray {
    val grabber = FFmpegFrameGrabber(file)

    return try {
        grabber.start()
        grabber.setTimestamp(3000000)
        val frame = grabber.grabImage() ?: return generateVideoPlaceholder(PREVIEW_SIZE)

        val converter = Java2DFrameConverter()
        val src = converter.convert(frame)

        val scale = min(PREVIEW_SIZE.toDouble() / src.width, PREVIEW_SIZE.toDouble() / src.height).coerceAtMost(1.0)
        val w = (src.width * scale).toInt()
        val h = (src.height * scale).toInt()

        val resized = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = resized.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(src, 0, 0, w, h, null)

        g.dispose()

        ByteArrayOutputStream().use { out ->
            ImageIO.write(resized, "jpg", out)
            out.toByteArray()
        }
    } finally {
        grabber.stop()
    }
}

private fun generateVideoPlaceholder(size: Int = PREVIEW_SIZE): ByteArray {
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