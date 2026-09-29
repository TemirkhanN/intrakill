package me.nasukhov.intrakill.storage.dao

import me.nasukhov.intrakill.domain.model.Tag
import java.sql.Connection
import kotlin.use

class TagRepository(
    private val dbResolver: () -> Connection,
) {
    private val db: Connection
        get() = dbResolver()

    private companion object {
        const val SELECT_ALL_TAGS = """
                SELECT 
                    tag as name,
                    COUNT(*) as frequency
                FROM tags
                GROUP BY tag
                ORDER BY frequency DESC
        """
        const val ADD_ENTRY_TAGS = "INSERT OR IGNORE INTO tags(entry_id, tag) VALUES (?, ?)"
        const val SELECT_ENTRY_TAGS = "SELECT * FROM tags WHERE entry_id = ?"
        const val DELETE_ENTRY_TAGS = "DELETE FROM tags WHERE entry_id=? AND tag IN (%s)"
        const val DELETE_TAG = "DELETE FROM tags WHERE tag = ?"

        const val DELETE_TAG_FROM_ENTRIES_THAT_HAS_PARTICULAR_TAG = """
                DELETE
                FROM tags
                WHERE tag = ?
                    AND entry_id = (SELECT entry_id FROM tags WHERE tag = ?)
        """

        const val RENAME_TAG = "UPDATE tags SET tag = ? WHERE tag = ?"
    }

    fun findAll(): Set<Tag> {
        val result = mutableSetOf<Tag>()

        db.prepareStatement(SELECT_ALL_TAGS).use { stmt ->
            val rs = stmt.executeQuery()
            while (rs.next()) {
                result.add(
                    Tag(
                        rs.getString("name"),
                        rs.getInt("frequency"),
                    ),
                )
            }
        }

        return result
    }

    fun getByEntryId(entryId: String): Set<String> {
        val result = mutableSetOf<String>()

        db.prepareStatement(SELECT_ENTRY_TAGS).use { stmt ->
            stmt.setString(1, entryId)
            val rs = stmt.executeQuery()
            while (rs.next()) {
                result.add(rs.getString("tag"))
            }
        }

        return result
    }

    fun addToEntry(
        entryId: String,
        tags: Set<String>,
    ) {
        if (tags.isEmpty()) return

        db.prepareStatement(ADD_ENTRY_TAGS).use { stmt ->
            tags.forEach {
                stmt.setString(1, entryId)
                stmt.setString(2, it)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    fun removeFromEntry(
        entryId: String,
        tags: Set<String>,
    ) {
        if (tags.isEmpty()) return

        db.prepareStatement(DELETE_ENTRY_TAGS.format(tags.placeholders())).use { stmt ->
            stmt.setString(1, entryId)
            tags.bind(stmt, 2)
            stmt.executeUpdate()
        }
    }

    fun deleteTag(tagName: String) {
        db.prepareStatement(DELETE_TAG).use { stmt ->
            stmt.setString(1, tagName)
            stmt.executeUpdate()
        }
    }

    fun renameTag(
        oldName: String,
        newName: String,
    ) {
        if (oldName == newName) return

        val autoCommitState = db.autoCommit
        db.autoCommit = false
        try {
            db
                .prepareStatement(DELETE_TAG_FROM_ENTRIES_THAT_HAS_PARTICULAR_TAG)
                .use { stmt ->
                    stmt.setString(1, oldName)
                    stmt.setString(2, newName)
                    stmt.executeUpdate()
                }

            db.prepareStatement(RENAME_TAG).use { stmt ->
                stmt.setString(1, newName)
                stmt.setString(2, oldName)
                stmt.executeUpdate()
            }
            db.commit()
        } catch (e: Exception) {
            db.rollback()
            throw e
        } finally {
            db.autoCommit = autoCommitState
        }
    }
}
