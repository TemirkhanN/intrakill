package me.nasukhov.intrakill.content

import me.nasukhov.intrakill.storage.MediaKind
import me.nasukhov.intrakill.storage.mediaKind
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

class Content {

    private val resolver: () -> InputStream

    constructor(content: ByteArray) {
        resolver = { content.inputStream() }
    }

    constructor(stream: InputStream) {
        resolver = { stream }
    }

    constructor(resolver: () -> InputStream) {
        this.resolver = resolver
    }

    fun read(): InputStream = resolver()

    fun readBytes(): ByteArray = resolver().readBytes()
}

data class Attachment(
    val mimeType: String,
    val content: Content,
    val preview: ByteArray,
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