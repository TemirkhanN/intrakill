package me.nasukhov.intrakill.storage.dao

import me.nasukhov.intrakill.content.Attachment
import me.nasukhov.intrakill.content.Entry
import me.nasukhov.intrakill.storage.EntriesFilter
import me.nasukhov.intrakill.storage.LazyList
import me.nasukhov.intrakill.storage.LazySet
import java.sql.Connection
import kotlin.use

class EntryRepository(
    private val dbResolver: () -> Connection,
    private val attachmentRepository: AttachmentRepository,
    private val tagRepository: TagRepository,
) {
    private val db: Connection
        get() = dbResolver()

    private companion object {
        const val FIND_BY_ID = "SELECT * FROM entry WHERE id = ?"
        const val DELETE_BY_ID = "DELETE FROM entry WHERE id = ?"
        const val CREATE_ENTRY = "INSERT INTO entry(id, name, preview) VALUES(?, ?, ?)"
        const val FIND_LATEST_ENTRIES = """
            SELECT e.*
            FROM entry e
            ORDER BY e.created_at DESC
            LIMIT ? OFFSET ?
        """
        const val FIND_LATEST_ENTRIES_FILTERED = """
            SELECT e.*
            FROM entry e
            JOIN (
                SELECT entry_id
                FROM tags
                WHERE tag IN (%s)
                GROUP BY entry_id
                HAVING COUNT(DISTINCT tag) = ?
            ) matched ON matched.entry_id = e.id
            ORDER BY e.created_at DESC
            LIMIT ? OFFSET ?
        """
        const val COUNT_ALL_ENTRIES = "SELECT COUNT(*) FROM entry e"
        const val COUNT_ALL_ENTRIES_FILTERED = """
            SELECT COUNT(DISTINCT e.id)
            FROM entry e
            JOIN (
                SELECT entry_id
                FROM tags
                WHERE tag IN (%s)
                GROUP BY entry_id
                HAVING COUNT(DISTINCT tag) = ?
            ) matched ON matched.entry_id = e.id
        """
    }

    fun save(entry: Entry): Entry {
        db.autoCommit = false
        try {
            if (entry.isPersisted) {
                updateEntry(entry)
            } else {
                createEntry(entry)
            }

            db.commit()

            return findById(entry.id)!!
        } catch (e: Exception) {
            db.rollback()
            throw e
        } finally {
            db.autoCommit = true
        }
    }

    private fun updateEntry(entry: Entry) {
        check(entry.isPersisted) { "Attempt to update non-existent entry" }
        val oldEntry = findById(entry.id)
        check(oldEntry != null) { "Entry with id ${entry.id} does not exist even though claims to be persisted!" }

        // NOTE: update currently doesn't mutate name, preview and other props. Only tags and attachments.
        // Just for the sake of simplicity.

        val removedTags = oldEntry.tags.minus(entry.tags)
        tagRepository.removeFromEntry(entry.id, removedTags)

        val addedTags = entry.tags.minus(oldEntry.tags)
        if (!addedTags.isEmpty()) {
            tagRepository.addToEntry(entry.id, addedTags)
        }

        val newAttachments = mutableListOf<Attachment>()
        val existingAttachments = mutableListOf<Attachment>()
        entry.attachments.forEach { attachment ->
            if (attachment.isPersisted) {
                existingAttachments.add(attachment)
            } else {
                newAttachments.add(attachment)
            }
        }
        attachmentRepository.deleteExcluding(entry.id, existingAttachments.map { it.id }.toSet())
        attachmentRepository.addToEntry(entry.id, newAttachments)

        val oldById = oldEntry.attachments.associateBy { it.id }
        val switched = entry.attachments
            .mapNotNull { current ->
                val prev = oldById[current.id] ?: return@mapNotNull null
                if (prev.position != current.position) current else null
            }
        if (!switched.isEmpty()) {
            attachmentRepository.updatePositions(switched)
        }

        attachmentRepository.normalizePositions(entry.id)
    }

    private fun createEntry(entry: Entry) {
        check(!entry.isPersisted) { "Duplicate entry creation attempt" }
        db.prepareStatement(CREATE_ENTRY).use { stmt ->
            stmt.setString(1, entry.id)
            stmt.setString(2, entry.name)
            stmt.setBytes(3, entry.preview)
            stmt.executeUpdate()
        }

        attachmentRepository.addToEntry(entry.id, entry.attachments)

        tagRepository.addToEntry(entry.id, entry.tags)
    }

    fun findById(id: String): Entry? {
        db.prepareStatement(FIND_BY_ID).use { stmt ->
            stmt.setString(1, id)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) {
                    return null
                }

                val id = rs.getString("id")
                return Entry(
                    id = id,
                    name = rs.getString("name"),
                    preview = rs.getBytes("preview"),
                    attachments = LazyList { attachmentRepository.getByEntryId(id) },
                    tags = LazySet { tagRepository.getByEntryId(id) },
                    isPersisted = true,
                )
            }
        }
    }

    fun findByFilter(filter: EntriesFilter): List<Entry> {
        val result = mutableListOf<Entry>()

        val hasTags = filter.tags.isNotEmpty()

        val query = if (!hasTags) {
            FIND_LATEST_ENTRIES
        } else {
            FIND_LATEST_ENTRIES_FILTERED.format(filter.tags.placeholders())
        }

        db.prepareStatement(query).use { stmt ->
            var nextIndex = 1
            if (!filter.tags.isEmpty()) {
                nextIndex = filter.tags.bind(stmt, 1)
                stmt.setInt(nextIndex++, filter.tags.size)
            }

            stmt.setInt(nextIndex++, filter.limit)
            stmt.setInt(nextIndex, filter.offset)

            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    val id = rs.getString("id")

                    result.add(
                        Entry(
                            id = id,
                            name = rs.getString("name"),
                            preview = rs.getBytes("preview"),
                            attachments = LazyList { attachmentRepository.getByEntryId(id) },
                            tags = LazySet { tagRepository.getByEntryId(id) },
                            isPersisted = true,
                        )
                    )
                }
            }
        }

        return result
    }

    fun countByFilter(filter: EntriesFilter): Int {
        val hasTags = filter.tags.isNotEmpty()

        val sql = if (!hasTags) {
            COUNT_ALL_ENTRIES
        } else {
            COUNT_ALL_ENTRIES_FILTERED.format(filter.tags.placeholders())
        }

        db.prepareStatement(sql).use { stmt ->
            if (hasTags) {
                val nextIndex = filter.tags.bind(stmt, 1)
                stmt.setInt(nextIndex, filter.tags.size)
            }

            stmt.executeQuery().use { rs ->
                return if (rs.next()) rs.getInt(1) else 0
            }
        }
    }

    fun deleteById(id: String) {
        db.prepareStatement(DELETE_BY_ID).use { stmt ->
            stmt.setString(1, id)
            stmt.executeUpdate()
        }
    }
}
