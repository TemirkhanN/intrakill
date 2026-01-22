package me.nasukhov.intrakill.storage

import me.nasukhov.intrakill.content.Entry
import me.nasukhov.intrakill.content.Tag

data class EntriesFilter(
    val limit: Int,
    val offset: Int = 0,
    val tags: List<String> = emptyList()
)

expect object SecureDatabase {
    fun open(password: String): Boolean

    fun saveEntry(entry: Entry)

    fun findEntries(filter: EntriesFilter): List<Entry>

    fun listTags(): List<Tag>

    fun getById(entryId: String): Entry
}