package me.nasukhov.intrakill.storage

import me.nasukhov.intrakill.scene.MediaEntry

data class EntryPreview(
    val entryId: Long,
    val name: String,
    val mediaType: String,   // "image", "gif", "video"
    val previewBytes: ByteArray
)

expect object SecureDatabase {
    fun open(password: ByteArray)

    fun saveEntry(entry: MediaEntry)

    fun listEntries(): List<EntryPreview>
}