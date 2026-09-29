package me.nasukhov.intrakill.storage.dao

import me.nasukhov.intrakill.domain.model.Tag
import net.sqlcipher.database.SQLiteDatabase
import kotlin.use

class TagRepository(
    private val dbResolver: () -> SQLiteDatabase,
) {
    // TODO other inline queries must to const values too
    private companion object {
        const val DELETE_TAG_FROM_ENTRIES_THAT_HAS_PARTICULAR_TAG = """
                DELETE
                FROM tags
                WHERE tag = ?
                    AND entry_id = (SELECT entry_id FROM tags WHERE tag = ?)
        """

        const val RENAME_TAG = "UPDATE tags SET tag = ? WHERE tag = ?"

        const val DELETE_TAG = "DELETE FROM tags WHERE tag = ?"
    }

    private val db: SQLiteDatabase
        get() = dbResolver()

    fun addToEntry(
        entryId: String,
        tags: Set<String>,
    ) {
        // TODO single insert?
        tags.forEach { tag ->
            db.execSQL(
                "INSERT INTO tags(entry_id, tag) VALUES (?,?)",
                arrayOf(entryId, tag),
            )
        }
    }

    fun removeFromEntry(
        entryId: String,
        tags: Set<String>,
    ) {
        val bindings = arrayOf(entryId, *tags.toTypedArray())
        db.execSQL("DELETE FROM tags WHERE entry_id = ? AND tag IN (${tags.placeholders()})", bindings)
    }

    fun listEntryTags(entryId: String): Set<String> {
        val result = mutableSetOf<String>()

        db
            .rawQuery(
                "SELECT tag FROM tags WHERE entry_id = ?",
                arrayOf(entryId),
            ).use { c ->
                while (c.moveToNext()) {
                    result += c.getString(0)
                }
            }

        return result
    }

    fun listTags(): Set<Tag> {
        val result = mutableSetOf<Tag>()

        db
            .rawQuery(
                """
                SELECT tag, COUNT(*) as frequency
                FROM tags
                GROUP BY tag
                ORDER BY frequency DESC
                """.trimIndent(),
                null,
            ).use { c ->
                while (c.moveToNext()) {
                    result +=
                        Tag(
                            c.getString(0),
                            c.getInt(1),
                        )
                }
            }

        return result
    }

    fun deleteTag(tagName: String) {
        db.execSQL(DELETE_TAG.query(), arrayOf(tagName))
    }

    fun renameTag(
        oldName: String,
        newName: String,
    ) {
        if (oldName == newName) return

        db.beginTransaction()
        try {
            db.execSQL(DELETE_TAG_FROM_ENTRIES_THAT_HAS_PARTICULAR_TAG.query(), arrayOf(oldName, newName))
            db.execSQL(RENAME_TAG.query(), arrayOf(newName, oldName))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}

private fun String.query() = this.trimIndent()
