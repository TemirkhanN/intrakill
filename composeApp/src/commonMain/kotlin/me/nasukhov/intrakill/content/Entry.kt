package me.nasukhov.intrakill.content

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import me.nasukhov.intrakill.appTimezone
import java.util.UUID
import kotlin.time.Clock

@Serializable
data class Entry(
    val name: String,
    val preview: ByteArray,
    val attachments: List<Attachment>,
    val tags: Set<String> = emptySet(),
    val id: String = UUID.randomUUID().toString(),
    val isPersisted: Boolean = false,
    val createdAt: LocalDateTime = Clock.System.now().toLocalDateTime(appTimezone),
)
