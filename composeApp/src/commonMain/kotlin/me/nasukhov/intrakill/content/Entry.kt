package me.nasukhov.intrakill.content

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val tags: Set<String> = emptySet(),
    val id: String = UUID.randomUUID().toString()
)

data class EntriesSearchResult(val entries: List<Entry>, val outOfTotal: Int)

object MediaRepository {
    suspend fun unlock(password: String): Boolean = withContext(Dispatchers.IO) {
        SecureDatabase.open(password)
    }

    suspend fun save(entry: Entry): Entry = withContext(Dispatchers.IO) {
        SecureDatabase.saveEntry(entry)

        entry
    }

    suspend fun listTags(): Set<Tag> = withContext(Dispatchers.IO) {
        SecureDatabase.listTags()
    }

    // TODO not really clean, but so far so good
    suspend fun findEntries(filter: EntriesFilter): EntriesSearchResult = withContext(Dispatchers.IO) {
        EntriesSearchResult(
            SecureDatabase.findEntries(filter),
            SecureDatabase.countEntries(filter),
        )
    }

    suspend fun getById(entryId: String): Entry  = withContext(Dispatchers.IO) {SecureDatabase.getById(entryId)}

    suspend fun deleteById(entryId: String): Unit = withContext(Dispatchers.IO) { SecureDatabase.deleteById(entryId) }
}

data class Tag(
    val name: String,
    val frequency: Int
)