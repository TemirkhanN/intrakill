package me.nasukhov.intrakill.domain.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import me.nasukhov.intrakill.Time
import java.util.UUID

@Serializable
data class Entry(
    val name: String,
    val preview: ByteArray,
    val attachments: List<Attachment>,
    val tags: Set<String> = emptySet(),
    val id: String = UUID.randomUUID().toString(),
    val isPersisted: Boolean = false,
    val createdAt: LocalDateTime = Time.getCurrent(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Entry) return false

        return calculateHash().contentEquals(other.calculateHash())
    }

    override fun hashCode(): Int = calculateHash().contentHashCode()

    private fun calculateHash(): ByteArray {
        val totalSize = attachments.sumOf { it.hashsum.size }
        val result = ByteArray(totalSize)
        var offset = 0

        for (attachment in attachments) {
            attachment.hashsum.copyInto(result, destinationOffset = offset)
            offset += attachment.hashsum.size
        }

        return result
    }
}
