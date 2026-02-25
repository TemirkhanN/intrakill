package me.nasukhov.intrakill.storage

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import me.nasukhov.intrakill.content.Content

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
        size = getFileSize(uri) ?: content.calculateSize()
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