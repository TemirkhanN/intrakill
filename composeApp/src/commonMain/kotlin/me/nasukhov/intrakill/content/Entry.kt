package me.nasukhov.intrakill.content

import me.nasukhov.intrakill.storage.EntriesFilter
import me.nasukhov.intrakill.storage.MediaKind
import me.nasukhov.intrakill.storage.SecureDatabase
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

data class Entry(
    val name: String,
    val preview: ByteArray,
    val attachments: List<Attachment>,
    val tags: List<String> = emptyList(),
    val id: String = UUID.randomUUID().toString()
)

object MediaRepository {

    fun save(entry: Entry): Entry {
        SecureDatabase.saveEntry(entry)

        return entry
    }

    // TODO not really clean, but so far so good
    fun findEntries(filter: EntriesFilter): List<Entry>  = SecureDatabase.findEntries(filter)

    fun getById(entryId: String): Entry = SecureDatabase.getById(entryId)
}

data class Tag(
    val name: String,
    val frequency: Int
)