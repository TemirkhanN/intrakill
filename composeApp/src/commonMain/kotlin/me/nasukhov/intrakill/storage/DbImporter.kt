package me.nasukhov.intrakill.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.net.URI

data class Progress(
    val current: Long,
    val outOf: Long,
) {
    val percent: Float = if (outOf == 0L) 0f else ((current.toDouble() / outOf) * 100).toFloat()

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
@Serializable
value class StorageSource(
    val value: String,
) {
    init {
        check(value.matches("""http(s?)://(127|192)\.\d{1,3}\.\d{1,3}\.\d{1,3}:[1-9]\d{3}$""".toRegex()))
    }

    constructor(ip: String, port: Int) : this("http://$ip:$port")

    fun urlTo(destination: String) = URI("$this/${destination.trimStart('/')}")

    override fun toString() = value
}

object DbImporter {
    private val db = SecureDatabase
    private val sourceStorage = ExternalStorage

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
            val dump =
                sourceStorage.open(source, password) {
                    downloadDump(onProgress)
                }

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
            sourceStorage.open(source, password) {
                val idsToSync = mutableSetOf<String>()
                var offset = 0
                val limit = 1000
                var hasMore = true

                while (hasMore) {
                    val fetchedIds = listEntriesIds(offset, limit)
                    if (!fetchedIds.isEmpty()) {
                        val missing = db.filterMissingIds(fetchedIds)
                        idsToSync.addAll(missing)
                        offset += limit
                    } else {
                        hasMore = false
                    }
                }

                var successfullySynced = 0
                idsToSync.forEach { id ->
                    try {
                        db.saveEntry(getById(id))
                        onProgress(Progress(++successfullySynced, idsToSync.size))
                    } catch (_: Exception) {
                    }
                }
            }
        }
}
