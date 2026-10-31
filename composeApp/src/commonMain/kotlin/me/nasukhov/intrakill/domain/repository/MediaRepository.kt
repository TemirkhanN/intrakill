package me.nasukhov.intrakill.domain.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import me.nasukhov.intrakill.domain.model.Entry
import me.nasukhov.intrakill.domain.model.Tag
import me.nasukhov.intrakill.storage.EntriesFilter
import me.nasukhov.intrakill.storage.SecureDatabase

data class EntriesSearchResult(
    val entries: List<Entry>,
    val outOfTotal: Int,
)

class MediaRepository(
    private val secureDatabase: SecureDatabase,
) {
    private var knownTagsCache: Set<Tag>? = null

    // A shared signal that fires whenever entries change
    // replay = 1 ensures new subscribers get the latest update immediately
    private val _updates = MutableSharedFlow<Unit>(replay = 1)
    val updates: Flow<Unit> = _updates

    private fun notifyChanged() {
        _updates.tryEmit(Unit)
    }

    suspend fun unlock(password: String): Boolean =
        withContext(Dispatchers.IO) {
            secureDatabase.open(password)
        }

    suspend fun changePassword(newPassword: String): Boolean =
        withContext(Dispatchers.IO) {
            secureDatabase.changePassword(newPassword)
        }

    suspend fun save(entry: Entry): Entry =
        withContext(Dispatchers.IO) {
            val result = secureDatabase.saveEntry(entry)
            clearCache()
            notifyChanged()
            result
        }

    suspend fun listTags(): Set<Tag> =
        withContext(Dispatchers.IO) {
            knownTagsCache ?: secureDatabase.listTags().also { knownTagsCache = it }
        }

    suspend fun findEntries(filter: EntriesFilter): EntriesSearchResult =
        withContext(Dispatchers.IO) {
            EntriesSearchResult(
                secureDatabase.findEntries(filter),
                secureDatabase.countEntries(filter),
            )
        }

    suspend fun findById(entryId: String): Entry? =
        withContext(Dispatchers.IO) {
            try {
                secureDatabase.getById(entryId)
            } catch (_: Exception) {
                null
            }
        }

    suspend fun deleteById(entryId: String): Unit =
        withContext(Dispatchers.IO) {
            secureDatabase.deleteById(entryId)
            clearCache()
            notifyChanged()
        }

    suspend fun renameTag(
        oldName: String,
        newName: String,
    ): Tag? =
        withContext(Dispatchers.IO) {
            if (oldName != newName) {
                secureDatabase.renameTag(oldName, newName)
                clearCache()
            }

            listTags().find { it.name == newName }
        }

    suspend fun deleteTag(tag: Tag): Unit =
        withContext(Dispatchers.IO) {
            secureDatabase.deleteTag(tag.name)
            clearCache()
            notifyChanged()
        }

    private fun clearCache() {
        knownTagsCache = null
    }
}
