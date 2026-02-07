package me.nasukhov.intrakill.storage

import me.nasukhov.intrakill.content.Attachment
import me.nasukhov.intrakill.content.Entry
import me.nasukhov.intrakill.content.Tag
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement

actual object SecureDatabase {
    private const val ID_LENGTH = 36

    private var connection: Connection? = null

    private val db: Connection
        get() = connection!!

    fun dumpDatabase(): String {
        return connection?.let {
            SqlDumpExporter.dumpDatabase(it)
        } ?: ""
    }

    actual fun open(password: String): Boolean {
        if (connection != null) {
            throw RuntimeException("Cannot open connection")
        }

        return try {
            val url = "jdbc:sqlite:secured.db"
            connection = DriverManager.getConnection(url, null, password).apply {
                createStatement().use { stmt ->
                    stmt.execute("PRAGMA cipher_memory_security = ON;")

                    stmt.execute("PRAGMA foreign_keys = ON;")
                    // Force key validation
                    stmt.execute("SELECT count(*) FROM sqlite_master;")

                    migrate(stmt)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    actual fun saveEntry(entry: Entry) {
        db.autoCommit = false
        try {
            val stmt = db.prepareStatement("INSERT INTO entry(id, name, preview) VALUES(?, ?, ?)")
            stmt.use {
                stmt.setString(1, entry.id) // TODO
                stmt.setString(2, entry.name)
                stmt.setBytes(3, entry.preview) // TODO
                stmt.executeUpdate()

                // Now insert attachments
                val attachStmt = db.prepareStatement(
                    "INSERT INTO attachment(id, entry_id, content, preview, mime_type, hashsum) VALUES (?,?,?,?,?,?)"
                )
                attachStmt.use { aStmt ->
                    for (a in entry.attachments) {
                        aStmt.setString(1, a.id)
                        aStmt.setString(2, entry.id)
                        aStmt.setBytes(3, a.content)
                        aStmt.setBytes(4, a.preview)
                        aStmt.setString(5, a.mimeType)
                        aStmt.setBytes(6, a.hashsum)
                        aStmt.addBatch()
                    }
                    aStmt.executeBatch()
                }

                // Insert tags
                val tagStmt = db.prepareStatement(
                    "INSERT INTO tags(entry_id, tag) VALUES (?, ?)"
                )
                tagStmt.use { t ->
                    for (tag in entry.tags) {
                        t.setString(1, entry.id)
                        t.setString(2, tag)
                        t.addBatch()
                    }
                    t.executeBatch()
                }
            }

            db.commit()
        } catch (e: Exception) {
            db.rollback()
            throw e
        } finally {
            db.autoCommit = true
        }
    }

    actual fun findEntries(filter: EntriesFilter): List<Entry> {
        val result = mutableListOf<Entry>()

        val hasTags = filter.tags.isNotEmpty()

        val sql = if (!hasTags) {
            """
            SELECT e.*
            FROM entry e
            ORDER BY e.created_at DESC
            LIMIT ? OFFSET ?
        """.trimIndent()
        } else {
            val placeholders = filter.tags.joinToString(",") { "?" }

            """
            SELECT e.*
            FROM entry e
            JOIN (
                SELECT entry_id
                FROM tags
                WHERE tag IN ($placeholders)
                GROUP BY entry_id
                HAVING COUNT(DISTINCT tag) = ?
            ) matched ON matched.entry_id = e.id
            ORDER BY e.created_at DESC
            LIMIT ? OFFSET ?
        """.trimIndent()
        }

        db.prepareStatement(sql).use { stmt ->
            var index = 1

            if (hasTags) {
                filter.tags.forEach { tag ->
                    stmt.setString(index++, tag)
                }
                stmt.setInt(index++, filter.tags.size)
            }

            stmt.setInt(index++, filter.limit)
            stmt.setInt(index, filter.offset)

            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    val id = rs.getString("id")

                    result.add(
                        Entry(
                            id = id,
                            name = rs.getString("name"),
                            preview = rs.getBytes("preview"),
                            attachments = LazyList { listAttachments(id) },
                            tags = LazySet { listTags(id) }
                        )
                    )
                }
            }
        }

        return result
    }


    actual fun getById(entryId: String): Entry {
        val sql = """
                SELECT
                    e.*
                FROM entry e
                WHERE e.id = ?
            """

        db.prepareStatement(sql).use { stmt ->
            stmt.setString(1, entryId)
            val rs = stmt.executeQuery()
            rs.next()

            val id = rs.getString("id")
            return Entry(
                id = id,
                name = rs.getString("name"),
                preview = rs.getBytes("preview"),
                attachments = LazyList { listAttachments(id) },
                tags = LazySet { listTags(id) }
            )
        }
    }

    private fun listAttachments(entryId: String): List<Attachment> {
        val sql = """
                SELECT * FROM attachment
                WHERE entry_id = ?
        """

        val result = mutableListOf<Attachment>()

        db.prepareStatement(sql).use { stmt ->
            stmt.setString(1, entryId)
            val rs = stmt.executeQuery()
            while (rs.next()) {
                result.add(
                    Attachment(
                        mimeType = rs.getString("mime_type"),
                        content = rs.getBytes("content"),
                        preview = rs.getBytes("preview"),
                        id = rs.getString("id"),
                    )
                )
            }
        }

        return result
    }

    actual fun listTags(): Set<Tag> {
        val sql = """
                SELECT 
                    tag as name,
                    COUNT(*) as frequency
                FROM tags
                GROUP BY tag
        """

        val result = mutableSetOf<Tag>()

        db.prepareStatement(sql).use { stmt ->
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

    private fun listTags(entryId: String): Set<String> {
        val sql = """
                SELECT * FROM tags
                WHERE entry_id = ?
        """

        val result = mutableSetOf<String>()

        db.prepareStatement(sql).use { stmt ->
            stmt.setString(1, entryId)
            val rs = stmt.executeQuery()
            while (rs.next()) {
                result.add(rs.getString("tag"))
            }
        }

        return result
    }

    private fun migrate(stmt: Statement) {
        // First launch → create tables
        stmt.execute(
            """
                    CREATE TABLE IF NOT EXISTS entry (
                        id TEXT PRIMARY KEY CHECK(length(id) = $ID_LENGTH),
                        `name` TEXT NOT NULL,
                        preview BLOB NOT NULL,
                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                    );
                    CREATE INDEX IF NOT EXISTS idx_entry_created_at ON entry(created_at);
                    CREATE INDEX IF NOT EXISTS idx_entry_name ON entry(`name`);
                    """
        )

        stmt.execute(
            """
                    CREATE TABLE IF NOT EXISTS attachment (
                        id TEXT PRIMARY KEY CHECK(length(entry_id) = $ID_LENGTH),
                        entry_id INTEGER NOT NULL,
                        content BLOB NOT NULL,
                        preview BLOB NOT NULL,
                        mime_type TEXT NOT NULL CHECK(length(mime_type) > 5),
                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        hashsum BLOB NOT NULL,
                        FOREIGN KEY (entry_id) REFERENCES entry(id) ON DELETE CASCADE
                    );
                    CREATE INDEX IF NOT EXISTS idx_attachment_entry ON attachment(entry_id);
                    CREATE INDEX IF NOT EXISTS idx_attachment_entry_created ON attachment(entry_id, created_at);
                    CREATE INDEX IF NOT EXISTS idx_attachment_hashsum ON attachment(hashsum);
                    """
        )

        stmt.execute(
            """
                        CREATE TABLE IF NOT EXISTS tags (
                            entry_id TEXT NOT NULL CHECK(length(entry_id) = $ID_LENGTH),
                            tag TEXT NOT NULL CHECK(length(tag) <= 32),
                            PRIMARY KEY (entry_id, tag),
                            FOREIGN KEY (entry_id) REFERENCES entry(id) ON DELETE CASCADE
                        );
                        CREATE INDEX IF NOT EXISTS idx_tags_tag ON tags(tag);
                        CREATE INDEX IF NOT EXISTS idx_tags_entry ON tags(entry_id);
                    """
        )
    }
}

private object SqlDumpExporter {
    fun dumpDatabase(conn: Connection): String {
        val sb = StringBuilder()

        conn.createStatement().use { stmt ->
            // schema
            stmt.executeQuery(
                """
                SELECT sql FROM sqlite_master
                WHERE sql IS NOT NULL
                  AND type IN ('table','index','trigger')
                  AND name NOT LIKE 'sqlite_%'
                ORDER BY type='table' DESC
            """
            ).use { rs ->
                while (rs.next()) {
                    sb.append(rs.getString(1)).append(";\n")
                }
            }

            // data
            stmt.executeQuery(
                """
                SELECT name FROM sqlite_master
                WHERE type='table'
                  AND name NOT LIKE 'sqlite_%'
            """
            ).use { tables ->
                while (tables.next()) {
                    val table = tables.getString(1)
                    dumpTable(conn, table, sb)
                }
            }
        }

        return sb.toString()
    }

    private fun dumpTable(conn: Connection, table: String, sb: StringBuilder) {
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT * FROM \"$table\"").use { rs ->
                val meta = rs.metaData
                val cols = meta.columnCount

                while (rs.next()) {
                    sb.append("INSERT INTO \"$table\" VALUES (")
                    for (i in 1..cols) {
                        if (i > 1) sb.append(",")
                        val v = rs.getObject(i)
                        sb.append(
                            when (v) {
                                null -> "NULL"
                                is ByteArray -> "X'${v.joinToString("") { "%02x".format(it) }}'"
                                is Number -> v.toString()
                                else -> "'${v.toString().replace("'", "''")}'"
                            }
                        )
                    }
                    sb.append(");\n")
                }
            }
        }
    }
}
