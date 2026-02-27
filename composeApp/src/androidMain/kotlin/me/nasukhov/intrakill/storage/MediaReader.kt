package me.nasukhov.intrakill.storage

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.graphics.createBitmap
import me.nasukhov.intrakill.content.Content
import java.io.ByteArrayOutputStream
import kotlin.math.min

internal fun ContentResolver.readPickedMedia(uri: Uri): Result<PickedMedia> = try {
    val name = this.query(uri, null, null, null, null)
        ?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else "unknown"
        } ?: "unknown"

    val mime = this.getType(uri) ?: "application/octet-stream"
    val content = Content { this.openInputStream(uri) ?: throw Exception("Can't read selected file ${uri.getFileName()}") }

    Result.success(PickedMedia(
        name = name,
        content = content,
        mimeType = mime,
        size = getFileSize(uri) ?: content.calculateSize(),
        rawPreview = generatePreview(content, uri, mime)
    ))
} catch (e: Exception) {
    Result.failure(e)
}

private fun Uri.getFileName(): String? = this.path?.let {
    val path = this.path ?: throw Exception("Selected resource doesn't seem to be a file")

    val cut: Int = path.lastIndexOf("/")
    if (cut != -1) {
        path.substring(cut + 1)
    } else {
        throw Exception("Selected resource is a directory")
    }
}

internal fun ContentResolver.getFileSize(uri: Uri): FileSize? {
    this.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
        ?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex >= 0 && cursor.moveToFirst()) {
                return if (!cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
            }
        }

    return null
}

internal fun FileSize.MB(): String = "${this/(1024*1024)}MB"

// TODO messy signatures
private fun generatePreview(content: Content, uri: Uri, mimeType: String): ByteArray =  when (mimeType.mediaKind()) {
    MediaKind.IMAGE, MediaKind.GIF -> generateImagePreviewAndroid(content)
    MediaKind.VIDEO -> generateVideoPreview(uri.path!!)
}

private fun generateImagePreviewAndroid(
    content: Content
): ByteArray = content.read().use {
    val maxSize = PickedMedia.PREVIEW_SIZE
    val src = BitmapFactory.decodeStream(it)

    val scale = min(
        maxSize.toFloat() / src.width,
        maxSize.toFloat() / src.height
    ).coerceAtMost(1f)

    val w = (src.width * scale).toInt()
    val h = (src.height * scale).toInt()

    val resized = createBitmap(w, h)
    val canvas = Canvas(resized)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    canvas.drawBitmap(
        src,
        null,
        android.graphics.Rect(0, 0, w, h),
        paint
    )

    return ByteArrayOutputStream().also { output ->
        resized.compress(Bitmap.CompressFormat.JPEG, 85, output)
    }.toByteArray()
}

private fun generateVideoPreview(path: String): ByteArray {
    val retriever = MediaMetadataRetriever()
    val maxSize = PickedMedia.PREVIEW_SIZE.toFloat()

    return try {
        retriever.setDataSource(path)
        // Grab frame at 1 second
        val rawBitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: return generateVideoPlaceholderStub(maxSize.toInt())

        val scale = min(maxSize / rawBitmap.width, maxSize / rawBitmap.height).coerceAtMost(1f)
        val outWidth = (rawBitmap.width * scale).toInt()
        val outHeight = (rawBitmap.height * scale).toInt()

        val resized = Bitmap.createScaledBitmap(rawBitmap, outWidth, outHeight, true)

        ByteArrayOutputStream().use { out ->
            resized.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
    } finally {
        retriever.release()
    }
}

private fun generateVideoPlaceholderStub(size: Int): ByteArray {
    val bmp = createBitmap(size, size)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    canvas.drawRGB(30, 30, 30)

    paint.color = android.graphics.Color.WHITE
    canvas.drawPath(
        android.graphics.Path().apply {
            moveTo(size / 3f, size / 4f)
            lineTo(size / 3f, size * 3 / 4f)
            lineTo(size * 2 / 3f, size / 2f)
            close()
        },
        paint
    )

    return ByteArrayOutputStream().also {
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, it)
    }.toByteArray()
}