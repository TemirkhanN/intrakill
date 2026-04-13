package me.nasukhov.intrakill.domain.model

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.nasukhov.intrakill.storage.Content
import me.nasukhov.intrakill.storage.FileSize
import me.nasukhov.intrakill.storage.MediaKind
import me.nasukhov.intrakill.storage.mediaKind
import me.nasukhov.intrakill.ui.view.asImageBitmap
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.abs

// TODO get rid of this reference
object MimeTypes {
    const val IMAGE_JPG = "image/jpeg"
    const val IMAGE_PNG = "image/png"
    const val IMAGE_WEBP = "image/webp"
    const val VIDEO_MP4 = "video/mp4"
}

@Serializable
data class Attachment(
    val mimeType: String,
    @Transient
    val content: Content = Content.NONE,
    val preview: ByteArray,
    val size: FileSize,
    val position: Int,
    val id: String = UUID.randomUUID().toString(),
    val hashsum: ByteArray = hasher.computeHash(content),
    val isPersisted: Boolean = false,
) {
    val imageBitmap: ImageBitmap by lazy {
        check(mediaKind == MediaKind.IMAGE)
        content.readBytes().asImageBitmap()
    }

    val mediaKind: MediaKind = mimeType.mediaKind()

    companion object {
        private val hasher = MessageDigest.getInstance("SHA-256")
    }
}

fun List<Attachment>.remove(element: Attachment): List<Attachment> =
    this
        .filter { it != element }
        .mapIndexed { index, attachment ->
            attachment.copy(position = index)
        }

fun List<Attachment>.combine(withList: List<Attachment>): List<Attachment> =
    (this + withList).mapIndexed { index, attachment ->
        attachment.copy(position = index)
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

private fun List<Attachment>.swap(
    pos1: Int,
    pos2: Int,
): List<Attachment> {
    require(abs(pos1 - pos2) == 1) {
        "Currently swap is available for neighboring attachments."
    }

    val newList = this.toMutableList()

    val elem1 = newList[pos1]
    newList[pos1] = newList[pos2].copy(position = pos1)
    newList[pos2] = elem1.copy(position = pos2)

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
