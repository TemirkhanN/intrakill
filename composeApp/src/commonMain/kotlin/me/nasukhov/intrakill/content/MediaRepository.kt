package me.nasukhov.intrakill.content

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import me.nasukhov.intrakill.storage.EntriesFilter
import me.nasukhov.intrakill.storage.SecureDatabase

data class EntriesSearchResult(val entries: List<Entry>, val outOfTotal: Int)

object MediaRepository {
    private var knownTagsCache: Set<Tag>? = null

    // A shared signal that fires whenever entries change
    // replay = 1 ensures new subscribers get the latest update immediately
    private val _updates = MutableSharedFlow<Unit>(replay = 1).apply {
        tryEmit(Unit)
    }
    val updates: Flow<Unit> = _updates

    private fun notifyChanged() {
        _updates.tryEmit(Unit)
    }

    suspend fun unlock(password: String): Boolean = withContext(Dispatchers.IO) {
        SecureDatabase.open(password)
    }

    suspend fun save(entry: Entry): Entry = withContext(Dispatchers.IO) {
        val result = SecureDatabase.saveEntry(entry)
        clearCache()
        notifyChanged()
        result
    }

    suspend fun listTags(): Set<Tag> = withContext(Dispatchers.IO) {
        knownTagsCache ?: SecureDatabase.listTags().also { knownTagsCache = it }
    }

    suspend fun findEntries(filter: EntriesFilter): EntriesSearchResult = withContext(Dispatchers.IO) {
        EntriesSearchResult(
            SecureDatabase.findEntries(filter),
            SecureDatabase.countEntries(filter),
        )
    }

    suspend fun findById(entryId: String): Entry?  = withContext(Dispatchers.IO) {
        try {
            SecureDatabase.getById(entryId)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun deleteById(entryId: String): Unit = withContext(Dispatchers.IO) {
        SecureDatabase.deleteById(entryId)
        clearCache()
        notifyChanged()
    }

    private fun clearCache() {
        knownTagsCache = null
    }
}
