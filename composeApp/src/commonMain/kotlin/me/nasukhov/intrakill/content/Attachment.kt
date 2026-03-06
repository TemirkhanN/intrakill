package me.nasukhov.intrakill.content

import me.nasukhov.intrakill.storage.FileSize
import me.nasukhov.intrakill.storage.MediaKind
import me.nasukhov.intrakill.storage.mediaKind
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.abs

class Content {
    private val resolver: () -> InputStream

    constructor(resolver: () -> InputStream) {
        this.resolver = resolver
    }

    fun <R> use(handler: (InputStream) -> R) = resolver().use(handler)

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

object MimeTypes {
    const val IMAGE_JPG = "image/jpeg"
    const val IMAGE_PNG = "image/png"
    const val IMAGE_WEBP = "image/webp"
    const val VIDEO_MP4 = "video/mp4"
}

data class Attachment(
    val mimeType: String,
    val content: Content,
    val preview: ByteArray,
    val size: FileSize,
    var position: Int,
    val id: String = UUID.randomUUID().toString(),
    val hashsum: ByteArray = hasher.computeHash(content),
    val isPersisted: Boolean = false,
) {
    val mediaKind: MediaKind = mimeType.mediaKind()

    companion object {
        private val hasher = MessageDigest.getInstance("SHA-256")
    }
    // TODO position for non persisted entry SELECT IFNULL(MAX(position), -1) + 1 FROM attachment WHERE entry_id = ?)
}

fun List<Attachment>.moveUpwards(attachment: Attachment): List<Attachment> {
    val from = attachment.position
    if (from == 0) return this

    val to = from - 1

    return swap(from, to)
}

fun List<Attachment>.moveDownwards(attachment: Attachment): List<Attachment> {
    val from = attachment.position
    if (from >= this.lastIndex) return this

    val to = from + 1

    return swap(from, to)
}

private fun List<Attachment>.swap(pos1: Int, pos2: Int): List<Attachment> {
    require(abs(pos1 - pos2) == 1) {
        "Currently swap is available for neighboring attachments."
    }

    val newList = this.toMutableList()

    val elem1 = newList[pos1]
    val elem2 = newList[pos2]
    newList[pos1] = elem2
    elem1.position = pos2
    newList[pos2] = elem1
    newList[pos1].position = pos1

    return newList
}

private fun MessageDigest.computeHash(source: Content): ByteArray {
    val chunkSizeKB = 64 * 1024
    source.use { input ->
        val buffer = ByteArray(chunkSizeKB)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            this.update(buffer, 0, read)
        }
    }

    return this.digest()
}