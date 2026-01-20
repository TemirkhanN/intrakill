package me.nasukhov.intrakill.content

import me.nasukhov.intrakill.storage.SecureDatabase
import java.util.UUID

data class Attachment(
    val mimeType: String,
    val content: ByteArray,
    val preview: ByteArray,
    val id: String = UUID.randomUUID().toString()
)

data class Entry(
    val preview: ByteArray,
    val attachments: List<Attachment>,
    val tags: List<String> = emptyList(),
    val id: String = UUID.randomUUID().toString()
)

object MediaRepository {

    fun save(entry: Entry) {
        SecureDatabase.saveEntry(entry)
    }

    fun listEntries(): List<Entry>  = SecureDatabase.listEntries()

    fun getById(entryId: String): Entry = SecureDatabase.getById(entryId)
}