package me.nasukhov.intrakill.storage

import me.nasukhov.intrakill.domain.model.Entry
import java.io.File

expect object ExternalStorage {
    suspend fun <R> open(
        source: StorageSource,
        password: String,
        then: suspend ExternalStorage.() -> R,
    ): R

    suspend fun downloadDump(onProgress: (Progress) -> Unit): File

    suspend fun listEntriesIds(
        offset: Int = 0,
        limit: Int = Int.MAX_VALUE,
    ): Set<String>

    suspend fun getById(id: String): Entry
}
