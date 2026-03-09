package me.nasukhov.intrakill.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.nasukhov.intrakill.content.Entry
import java.io.File

// TODO rename into FileResolver and adjust usages accordingly
expect object DbFileResolver {
    fun resolve(dbName: String): File
}

data class Progress(
    val current: Long,
    val outOf: Long,
) {
    val percent: Float = if (outOf == 0L) 0f else (current.toFloat() / outOf) * 100

    init {
        check(current >= 0 && outOf >= 0) { "Progress indicators can not be negative." }
        check(current <= outOf) { "Progress must be within 0% and 100%" }
    }

    constructor(current: Int, outOf: Int) : this(current.toLong(), outOf.toLong())

    companion object {
        val EMPTY = Progress(0, 0)
    }

    fun isEmpty() = this == EMPTY
}

@JvmInline
value class StorageSource(
    val value: String,
) {
    init {
        check(value.matches("""http(s?)://(127|192)\.\d{1,3}\.\d{1,3}\.\d{1,3}:[1-9]\d{3}$""".toRegex()))
    }

    constructor(ip: String, port: Int) : this("http://$ip:$port")

    override fun toString() = value
}

object DbImporter {
    private val db = SecureDatabase

    /**
     * Imports a remote database and saves it locally.
     * Returns true if file was successfully downloaded.
     */
    suspend fun importDatabase(
        source: StorageSource,
        password: String,
        onProgress: (Progress) -> Unit = {},
    ): Boolean =
        withContext(Dispatchers.IO) {
            ExternalStorage.resolve(source, password)
            val dump = ExternalStorage.downloadDump(onProgress)
            dump.deleteOnExit()

            try {
                db.importFromFile(dump, password)
            } finally {
                dump.delete()
            }
        }

    suspend fun syncEntries(
        source: StorageSource,
        password: String,
        onProgress: (Progress) -> Unit,
    ): Unit =
        withContext(Dispatchers.IO) {
            val apiService = ExternalStorage.apply { resolve(source, password) }

            val idsToSync = mutableSetOf<String>()
            var offset = 0
            val limit = 1000
            var hasMore = true

            while (hasMore) {
                val fetchedIds = apiService.listEntriesIds(offset, limit)
                if (!fetchedIds.isEmpty()) {
                    val missing = db.filterMissingIds(fetchedIds)
                    idsToSync.addAll(missing)
                    offset += limit
                } else {
                    hasMore = false
                }
            }

            val successfullySynced = 0
            idsToSync.forEach { id ->
                try {
                    val saved = db.saveEntry(apiService.getById(id))
                    onProgress(Progress(successfullySynced, idsToSync.size))
                } catch (_: Exception) {
                }
            }
        }
}

expect object ExternalStorage {
    // TODO DI is the way. This is rather meh
    fun resolve(
        source: StorageSource,
        password: String,
    )

    suspend fun downloadDump(onProgress: (Progress) -> Unit): File

    suspend fun listEntriesIds(
        offset: Int = 0,
        limit: Int = Int.MAX_VALUE,
    ): Set<String>

    suspend fun getById(id: String): Entry
}
