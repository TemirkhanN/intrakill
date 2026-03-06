package me.nasukhov.intrakill.storage.dao

import me.nasukhov.intrakill.content.Tag
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
}
