package me.nasukhov.intrakill.storage

import me.nasukhov.intrakill.content.Entry
import me.nasukhov.intrakill.content.Tag
import java.util.Enumeration

data class EntriesFilter(
    val limit: Int,
    val offset: Int = 0,
    val tags: Set<String> = emptySet(),
)

// Chunk size in application_chunk.data column is no more than this value across all devices.
internal const val MAX_CHUNK_SIZE = 1 * 1024 * 1024

expect object SecureDatabase {
    fun open(password: String): Boolean

    fun saveEntry(entry: Entry): Entry

    fun findEntries(filter: EntriesFilter): List<Entry>

    fun countEntries(filter: EntriesFilter): Int

    fun listTags(): Set<Tag>

    fun getById(entryId: String): Entry

    fun deleteById(entryId: String)
}

class LazyList<T>(
    loader: () -> List<T>,
) : AbstractList<T>() {
    private val delegate: List<T> by lazy(loader)

    override val size: Int
        get() = delegate.size

    override fun get(index: Int): T = delegate[index]
}

class LazySet<T>(
    loader: () -> Set<T>,
) : AbstractSet<T>() {
    private val delegate: Set<T> by lazy(loader)

    override val size: Int
        get() = delegate.size

    override fun iterator(): Iterator<T> = delegate.iterator()
}

fun <T> Sequence<T>.asEnumeration(): Enumeration<T> =
    object : Enumeration<T> {
        val iterator = this@asEnumeration.iterator()

        override fun hasMoreElements(): Boolean = iterator.hasNext()

        override fun nextElement(): T = iterator.next()
    }
