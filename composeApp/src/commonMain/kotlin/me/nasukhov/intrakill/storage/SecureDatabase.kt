package me.nasukhov.intrakill.storage

import me.nasukhov.intrakill.content.Entry

expect object SecureDatabase {
    fun open(password: String): Boolean

    fun saveEntry(entry: Entry)

    fun listEntries(): List<Entry>

    fun getById(entryId: String): Entry
}