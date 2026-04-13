package me.nasukhov.intrakill.storage.dao

import me.nasukhov.intrakill.domain.model.Tag
import net.sqlcipher.database.SQLiteDatabase
import kotlin.use

class TagRepository(
    private val dbResolver: () -> SQLiteDatabase,
) {
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
}
