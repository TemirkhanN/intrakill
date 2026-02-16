package me.nasukhov.intrakill.content

import me.nasukhov.intrakill.storage.MediaKind
import me.nasukhov.intrakill.storage.mediaKind
import java.security.MessageDigest
import java.util.UUID

data class Attachment(
    val mimeType: String,
    val content: ByteArray,
    val preview: ByteArray,
    val id: String = UUID.randomUUID().toString(),
    val hashsum: ByteArray = hasher.digest(content)
) {
    val mediaKind: MediaKind = mimeType.mediaKind()

    companion object {
        private val hasher = MessageDigest.getInstance("SHA-256")
    }
}