package me.nasukhov.intrakill.storage

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

internal fun readPickedMedia(
    resolver: ContentResolver,
    uri: Uri
): PickedMedia? = try {

    val name = resolver.query(uri, null, null, null, null)
        ?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else "unknown"
        } ?: "unknown"

    val mime = resolver.getType(uri) ?: "application/octet-stream"

    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: return null

    PickedMedia(
        name = name,
        bytes = bytes,
        mimeType = mime
    )

} catch (_: Exception) {
    null
}
