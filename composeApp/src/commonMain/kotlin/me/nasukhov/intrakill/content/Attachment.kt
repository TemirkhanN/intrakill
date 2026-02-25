package me.nasukhov.intrakill.content

import me.nasukhov.intrakill.storage.FileSize
import me.nasukhov.intrakill.storage.MediaKind
import me.nasukhov.intrakill.storage.mediaKind
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

class Content {
    private val resolver: () -> InputStream

    constructor(resolver: () -> InputStream) {
        this.resolver = resolver
    }

    fun read(): InputStream = resolver()

    fun readBytes(): ByteArray = resolver().use { it.readBytes() }

    fun calculateSize(): Long =
        resolver().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            var read: Int

            while (input.read(buffer).also { read = it } != -1) {
                total += read
            }

            total
        }
}

data class Attachment(
    val mimeType: String,
    val content: Content,
    val preview: ByteArray,
    val size: FileSize,
    val id: String = UUID.randomUUID().toString(),
    val hashsum: ByteArray = hasher.computeHash(content),
    val isPersisted: Boolean = false,
) {
    val mediaKind: MediaKind = mimeType.mediaKind()

    companion object {
        private val hasher = MessageDigest.getInstance("SHA-256")
    }
}

private fun MessageDigest.computeHash(source: Content): ByteArray {
    val chunkSizeKB = 64 * 1024
    source.read().use { input ->
        val buffer = ByteArray(chunkSizeKB)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            this.update(buffer, 0, read)
        }
    }

    return this.digest()
}