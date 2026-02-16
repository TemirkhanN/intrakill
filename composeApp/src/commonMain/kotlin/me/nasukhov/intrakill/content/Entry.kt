package me.nasukhov.intrakill.content

import java.util.UUID

data class Entry(
    val name: String,
    val preview: ByteArray,
    val attachments: List<Attachment>,
    val tags: Set<String> = emptySet(),
    val id: String = UUID.randomUUID().toString()
)