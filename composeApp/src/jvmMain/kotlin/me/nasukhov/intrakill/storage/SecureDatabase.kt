package me.nasukhov.intrakill.storage

import me.nasukhov.intrakill.scene.MediaEntry
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.util.UUID

actual object SecureDatabase {

    private var connection: Connection? = null

    actual fun open(password: ByteArray) {
        if (connection != null) {
            throw RuntimeException("Cannot open connection")
        }

        try {
            Class.forName("org.sqlite.JDBC")

            val url = "jdbc:sqlite:secure.db"
            val conn = DriverManager.getConnection(url)

            conn.createStatement().use { stmt ->
                stmt.execute("PRAGMA key = '${String(password)}';")
                stmt.execute("PRAGMA cipher_memory_security = ON;")

                // Force key validation
                stmt.execute("SELECT count(*) FROM sqlite_master;")

                // First launch → create tables
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS entry (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        title TEXT NOT NULL CHECK(length(title) <= 255),
                        preview BLOB NOT NULL,
                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                    );
                    CREATE INDEX IF NOT EXISTS idx_entry_title ON entry(title);
                    CREATE INDEX IF NOT EXISTS idx_entry_created_at ON entry(created_at);
                    """
                )

                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS attachment (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        entry_id INTEGER NOT NULL,
                        content BLOB NOT NULL,
                        preview BLOB NOT NULL,
                        media_type TEXT NOT NULL CHECK (
                            media_type IN ('image', 'gif', 'video')
                        ),
                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (entry_id) REFERENCES entry(id) ON DELETE CASCADE
                    );
                    CREATE INDEX IF NOT EXISTS idx_attachment_entry ON attachment(entry_id);
                    CREATE INDEX idx_attachment_entry_created ON attachment(entry_id, created_at);
                    """
                )

                stmt.execute(
                    """
                        CREATE TABLE IF NOT EXISTS tags (
                            entry_id INTEGER NOT NULL,
                            tag TEXT NOT NULL CHECK(length(tag) <= 32),
                            PRIMARY KEY (entry_id, tag),
                            FOREIGN KEY (entry_id) REFERENCES entry(id) ON DELETE CASCADE
                        );
                        CREATE INDEX IF NOT EXISTS idx_tags_tag ON tags(tag);
                        CREATE INDEX IF NOT EXISTS idx_tags_entry ON tags(entry_id);
                    """
                )
            }

            connection = conn
        } catch (e: Exception) {
            throw IllegalStateException(
                "Failed to open encrypted database",
                e
            )
        }
    }

    actual fun saveEntry(entry: MediaEntry) {
        connection!!.use { db ->
            db.autoCommit = false
            try {
                val stmt = db.prepareStatement(
                    "INSERT INTO entry(title,preview) VALUES(?,?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS
                )
                stmt.use {
                    stmt.setString(1, UUID.randomUUID().toString()) // TODO
                    stmt.setBytes(2, entry.media.first().generateImagePreview()) // TODO
                    stmt.executeUpdate()

                    val rs = stmt.generatedKeys
                    val entryId = if (rs.next()) rs.getLong(1) else error("Failed to get entry id")

                    // Now insert attachments
                    val attachStmt = db.prepareStatement(
                        "INSERT INTO attachment(entry_id, content, preview, media_type) VALUES (?, ?, ?, ?)"
                    )
                    attachStmt.use { a ->
                        for (m in entry.media) {
                            a.setLong(1, entryId)
                            a.setBytes(2, m.bytes)
                            a.setBytes(3, m.generateImagePreview())
                            a.setString(4, m.mediaType)
                            a.addBatch()
                        }
                        a.executeBatch()
                    }

                    // Insert tags
                    val tagStmt = db.prepareStatement(
                        "INSERT INTO tags(entry_id, tag) VALUES (?, ?)"
                    )
                    tagStmt.use { t ->
                        for (tag in entry.tags) {
                            t.setLong(1, entryId)
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
    }

    actual fun listEntries(): List<EntryPreview> {
        connection!!.use { db ->
            val sql = """
                SELECT
                    e.id      AS entry_id,
                    e.title   AS entry_name,
                    'image'   AS media_type,
                    e.preview AS preview
                FROM entry e
                ORDER BY e.created_at DESC
                LIMIT 20
            """

            val result = mutableListOf<EntryPreview>()

            db.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    result.add(rs.toEntryPreview())
                }
            }

            return result
        }
    }

    private fun ResultSet.toEntryPreview(): EntryPreview =
        EntryPreview(
            entryId = getLong("entry_id"),
            name = getString("entry_name"),
            mediaType = getString("media_type"),
            previewBytes = getBytes("preview")
        )
}
