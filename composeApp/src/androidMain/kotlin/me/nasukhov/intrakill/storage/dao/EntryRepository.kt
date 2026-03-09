package me.nasukhov.intrakill.storage.dao

import me.nasukhov.intrakill.content.Entry
import me.nasukhov.intrakill.storage.EntriesFilter
import me.nasukhov.intrakill.storage.LazyList
import me.nasukhov.intrakill.storage.LazySet
import net.sqlcipher.database.SQLiteDatabase
import kotlin.use

class EntryRepository(
    private val dbResolver: () -> SQLiteDatabase,
    private val attachmentRepository: AttachmentRepository,
    private val tagRepository: TagRepository,
) {
    private val db: SQLiteDatabase
        get() = dbResolver()

    fun findById(id: String): Entry? {
        db
            .rawQuery(
                "SELECT * FROM entry WHERE id = ?",
                arrayOf(id),
            ).use { c ->
                if (!c.moveToFirst()) return null

                return Entry(
                    id = id,
                    name = c.getString(c.getColumnIndexOrThrow("name")),
                    preview = c.getBlob(c.getColumnIndexOrThrow("preview")),
                    attachments = LazyList { attachmentRepository.listAttachments(id) },
                    tags = LazySet { tagRepository.listEntryTags(id) },
                    isPersisted = true,
                )
            }
    }

    fun findByFilter(filter: EntriesFilter): List<Entry> {
        val result = mutableListOf<Entry>()
        val args = mutableListOf<String>()

        val sql =
            if (filter.tags.isEmpty()) {
                """
                SELECT id AS entry_id, name, preview 
                FROM entry 
                ORDER BY created_at DESC 
                LIMIT ? OFFSET ?
                """.trimIndent().also {
                    args.add(filter.limit.toString())
                    args.add(filter.offset.toString())
                }
            } else {
                // Since tags is a Set, we don't need .distinct()
                val placeholders = filter.tags.placeholders()
                val tagCount = filter.tags.size

                """
                SELECT 
                    e.id AS entry_id, 
                    e.name, 
                    e.preview
                FROM entry e
                WHERE (
                    SELECT COUNT(DISTINCT t.tag)
                    FROM tags t
                    WHERE t.entry_id = e.id AND t.tag IN ($placeholders)
                ) = $tagCount
                ORDER BY e.created_at DESC
                LIMIT ? OFFSET ?
                """.trimIndent().also {
                    // Add tags for the IN clause
                    filter.tags.forEach { args.add(it) }
                    // Add pagination
                    args.add(filter.limit.toString())
                    args.add(filter.offset.toString())
                }
            }

        // rawQuery returns a cursor that must be closed (handled by .use)
        db.rawQuery(sql, args.toTypedArray()).use { c ->
            val idCol = c.getColumnIndexOrThrow("entry_id")
            val nameCol = c.getColumnIndexOrThrow("name")
            val previewCol = c.getColumnIndexOrThrow("preview")

            while (c.moveToNext()) {
                val id = c.getString(idCol)

                result.add(
                    Entry(
                        id = id,
                        name = c.getString(nameCol),
                        preview = c.getBlob(previewCol),
                        attachments = LazyList { attachmentRepository.listAttachments(id) },
                        tags = LazySet { tagRepository.listEntryTags(id) },
                        isPersisted = true,
                    ),
                )
            }
        }

        return result
    }

    fun findMissing(ids: Set<String>): Set<String> {
        val result = mutableSetOf<String>()
        db.rawQuery("SELECT id FROM entry WHERE id IN (%s)".format(ids.placeholders()), ids.toTypedArray()).use { c ->
            val idCol = c.getColumnIndexOrThrow("id")

            while (c.moveToNext()) {
                val id = c.getString(idCol)
                result.add(id)
            }
        }

        return ids - result
    }

    fun count(filter: EntriesFilter): Int {
        val args = mutableListOf<String>()

        val sql =
            if (filter.tags.isEmpty()) {
                "SELECT COUNT(*) FROM entry"
            } else {
                val placeholders = filter.tags.joinToString(",") { "?" }
                val tagCount = filter.tags.size

                """
                SELECT COUNT(*) 
                FROM entry e
                WHERE (
                    SELECT COUNT(DISTINCT t.tag)
                    FROM tags t
                    WHERE t.entry_id = e.id AND t.tag IN ($placeholders)
                ) = $tagCount
                """.trimIndent().also {
                    filter.tags.forEach { args.add(it) }
                }
            }

        return db.rawQuery(sql, args.toTypedArray()).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun delete(id: String) {
        val query = "DELETE FROM entry WHERE id = ?"

        db.execSQL(query, arrayOf(id))
    }

    fun save(entry: Entry): Entry {
        db.beginTransaction()
        try {
            if (entry.isPersisted) {
                updateEntry(entry)
            } else {
                createEntry(entry)
            }

            db.setTransactionSuccessful()

            return findById(entry.id)!!
        } finally {
            db.endTransaction()
        }
    }

    private fun createEntry(entry: Entry) {
        check(!entry.isPersisted) { "Duplicate entry creation attempt" }

        db.execSQL(
            "INSERT INTO entry(id, name, preview) VALUES(?,?,?)",
            arrayOf(entry.id, entry.name, entry.preview),
        )

        attachmentRepository.addToEntry(entry.id, entry.attachments)

        tagRepository.addToEntry(entry.id, entry.tags)
    }

    private fun updateEntry(entry: Entry) {
        check(entry.isPersisted) { "Attempt to update non-existent entry" }
        val oldEntry = findById(entry.id)!!

        val removedTags = oldEntry.tags.minus(entry.tags)
        if (!removedTags.isEmpty()) {
            tagRepository.removeFromEntry(entry.id, removedTags)
        }

        val addedTags = entry.tags.minus(oldEntry.tags)
        if (!addedTags.isEmpty()) {
            tagRepository.addToEntry(entry.id, addedTags)
        }

        val remainingIds = entry.attachments.map { it.id }
        val deletedIds = oldEntry.attachments.map { it.id }.filter { it !in remainingIds }
        if (!deletedIds.isEmpty()) {
            attachmentRepository.deleteAttachments(deletedIds)
        }

        val newAttachments = entry.attachments.filter { !it.isPersisted }
        if (!newAttachments.isEmpty()) {
            attachmentRepository.addToEntry(entry.id, newAttachments)
        }

        entry.attachments.filter { it.isPersisted }.forEach { currentVersion ->
            val previousVersion = oldEntry.attachments.first { it.id == currentVersion.id }
            if (previousVersion.position != currentVersion.position) {
                db.execSQL("UPDATE attachment SET position = ? WHERE id = ?", arrayOf<Any>(currentVersion.position, currentVersion.id))
            }
        }

        // normalize
        db.execSQL(
            """
            UPDATE attachment 
            SET position = (
                SELECT COUNT(*) 
                FROM attachment AS a2 
                WHERE a2.entry_id = attachment.entry_id 
                  AND a2.position < attachment.position
            )
            WHERE entry_id = ?
        """,
            arrayOf(entry.id),
        )

        // Refresh preview
        db.execSQL(
            """
            UPDATE entry 
            SET preview = (
                SELECT preview 
                FROM attachment 
                WHERE entry_id = entry.id AND position = 0
            ) 
            WHERE id = ? 
              AND preview IS NOT (
                  SELECT preview 
                  FROM attachment 
                  WHERE entry_id = entry.id AND position = 0
              )
        """,
        )
    }
}
