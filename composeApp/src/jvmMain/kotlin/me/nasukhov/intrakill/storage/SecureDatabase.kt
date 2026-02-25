package me.nasukhov.intrakill.storage

import me.nasukhov.intrakill.content.Attachment
import me.nasukhov.intrakill.content.Content
import me.nasukhov.intrakill.content.Entry
import me.nasukhov.intrakill.content.Tag
import java.io.BufferedInputStream
import java.io.File
import java.io.OutputStream
import java.sql.Connection
import java.sql.DriverManager
import kotlin.use

actual object SecureDatabase {
    private const val DB_NAME = "secured.db"

    private var connection: Connection? = null

    private val db: Connection
        get() = connection!!

    fun dumpDatabase(output: OutputStream) {
        connection?.let {
            SqlDumpExporter.dumpDatabase(it, output)
        }
    }

    fun importFromFile(file: File, password: String): Boolean {
        try {
            // 1. Close the current encrypted connection so we can swap files
            connection?.let {
                if (!it.isClosed) {
                    it.close()
                }
            }

            // 2. Replace the current DB file with the unencrypted import file
            val targetFile = File(DB_NAME)
            if (targetFile.exists()) targetFile.delete()
            file.copyTo(targetFile)

            // 3. Open the connection to the PLAINTEXT file
            // We use the configuration WITHOUT a key as per Willena's instructions
            val url = "jdbc:sqlite:${DB_NAME}"
            DriverManager.getConnection(url).apply {
                createStatement().use { stmt ->
                    val escapedPass = password.replace("'", "''")
                    stmt.execute("PRAGMA rekey = '${escapedPass}'")
                }
                close()
            }

            return open(password)
        } catch (e: Exception) {
            return false
        }
    }

    actual fun open(password: String): Boolean {
        connection?.close()

        return try {
            val url = "jdbc:sqlite:${DB_NAME}"
            connection = DriverManager.getConnection(url, null, password).apply {
                createStatement().use { stmt ->
                    stmt.execute("PRAGMA cipher_memory_security = ON;")

                    stmt.execute("PRAGMA foreign_keys = ON;")
                    // Force key validation
                    stmt.execute("SELECT count(*) FROM sqlite_master;")

                    Migrator().migrate(SQLAdapterJVM(this))
                }
            }

            true
        } catch (e: Exception) {
            false
        }
    }

    actual fun saveEntry(entry: Entry): Entry {
        db.autoCommit = false
        try {
            if (entry.isPersisted) {
                updateEntry(entry)
            } else {
                createEntry(entry)
            }

            return getById(entry.id)
        } catch (e: Exception) {
            db.rollback()
            throw e
        } finally {
            db.autoCommit = true
        }
    }

    private fun updateEntry(entry: Entry) {
        check(entry.isPersisted) { "Attempt to update non-existent entry" }
        val oldEntry = getById(entry.id)

        // NOTE: update currently doesn't mutate name, preview and other props. Only tags and attachments.
        // Just for the sake of simplicity.

        val removedTags = oldEntry.tags.minus(entry.tags)
        if (!removedTags.isEmpty()) {
            val removedTagsPlaceholder = removedTags.joinToString(",") { "?" }
            db.prepareStatement("DELETE FROM tags WHERE entry_id=? ANd tag IN ($removedTagsPlaceholder)")
                .use { stmt ->
                    var placeholderPosition = 1
                    stmt.setString(placeholderPosition++, entry.id)
                    removedTags.forEach { removedTag ->
                        stmt.setString(placeholderPosition++, removedTag)
                    }
                    stmt.executeUpdate()
                }
        }

        val addedTags = entry.tags.minus(oldEntry.tags)
        if (!addedTags.isEmpty()) {
            db.prepareStatement("INSERT INTO tags(entry_id, tag) VALUES (?, ?)").use { stmt ->
                for (tag in addedTags) {
                    stmt.setString(1, entry.id)
                    stmt.setString(2, tag)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }

        val remainingAttachmentIds = entry.attachments.joinToString(",") { "?" }
        db.prepareStatement("DELETE FROM attachment WHERE entry_id=? AND id NOT IN ($remainingAttachmentIds)").use { stmt ->
            var placeholderPosition = 1
            stmt.setString(placeholderPosition++, entry.id)
            entry.attachments.forEach { remainingAttachment ->
               stmt.setString(placeholderPosition++, remainingAttachment.id)
            }
            stmt.executeUpdate()
        }

        db.prepareStatement(
            "INSERT INTO attachment(id, entry_id, content, preview, mime_type, hashsum, `size`) VALUES (?,?,?,?,?,?,?)"
        ).use { stmt ->
            for (a in entry.attachments.filter { !it.isPersisted }) {
                stmt.setString(1, a.id)
                stmt.setString(2, entry.id)
                stmt.setBinaryStream(3, a.content.read())
                stmt.setBytes(4, a.preview)
                stmt.setString(5, a.mimeType)
                stmt.setBytes(6, a.hashsum)
                stmt.setLong(7, a.size)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    private fun createEntry(entry: Entry) {
        check(!entry.isPersisted) { "Duplicate entry creation attempt" }
        val stmt = db.prepareStatement("INSERT INTO entry(id, name, preview) VALUES(?, ?, ?)")
        stmt.use {
            stmt.setString(1, entry.id) // TODO
            stmt.setString(2, entry.name)
            stmt.setBytes(3, entry.preview) // TODO
            stmt.executeUpdate()

            // Now insert attachments
            val attachStmt = db.prepareStatement(
                "INSERT INTO attachment(id, entry_id, content, preview, mime_type, hashsum, `size`) VALUES (?,?,?,?,?,?,?)"
            )
            attachStmt.use { aStmt ->
                for (a in entry.attachments) {
                    aStmt.setString(1, a.id)
                    aStmt.setString(2, entry.id)
                    aStmt.setBinaryStream(3, a.content.read())
                    aStmt.setBytes(4, a.preview)
                    aStmt.setString(5, a.mimeType)
                    aStmt.setBytes(6, a.hashsum)
                    aStmt.setLong(7, a.size)
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
    }
    actual fun countEntries(filter: EntriesFilter): Int {
        val hasTags = filter.tags.isNotEmpty()

        val sql = if (!hasTags) {
            """
        SELECT COUNT(*)
        FROM entry e
        """.trimIndent()
        } else {
            val placeholders = filter.tags.joinToString(",") { "?" }

            """
        SELECT COUNT(DISTINCT e.id)
        FROM entry e
        JOIN (
            SELECT entry_id
            FROM tags
            WHERE tag IN ($placeholders)
            GROUP BY entry_id
            HAVING COUNT(DISTINCT tag) = ?
        ) matched ON matched.entry_id = e.id
        """.trimIndent()
        }

        db.prepareStatement(sql).use { stmt ->
            var index = 1

            if (hasTags) {
                filter.tags.forEach { tag ->
                    stmt.setString(index++, tag)
                }
                stmt.setInt(index, filter.tags.size)
            }

            stmt.executeQuery().use { rs ->
                return if (rs.next()) rs.getInt(1) else 0
            }
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
                            tags = LazySet { listTags(id) },
                            isPersisted = true,
                        )
                    )
                }
            }
        }

        return result
    }

    actual fun deleteById(entryId: String) {
        val query = "DELETE FROM entry WHERE id = ?"

        db.prepareStatement(query).use { stmt ->
            stmt.setString(1, entryId)

            stmt.executeUpdate()
        }
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
                tags = LazySet { listTags(id) },
                isPersisted = true,
            )
        }
    }

    private fun listAttachments(entryId: String): List<Attachment> {
        val sql = """
                SELECT id, mime_type, `size`, preview, hashsum FROM attachment
                WHERE entry_id = ?
        """

        val result = mutableListOf<Attachment>()

        db.prepareStatement(sql).use { stmt ->
            stmt.setString(1, entryId)
            val rs = stmt.executeQuery()
            while (rs.next()) {
                val attachmentId = rs.getString("id")
                val size = rs.getLong("size")
                result.add(
                    Attachment(
                        mimeType = rs.getString("mime_type"),
                        content = getContent(attachmentId),
                        preview = rs.getBytes("preview"),
                        size = size,
                        id = attachmentId,
                        hashsum = rs.getBytes("hashsum"),
                        isPersisted = true,
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
                ORDER BY frequency DESC
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

    private fun getContent(attachmentId: String): Content = Content {
        db.prepareStatement("SELECT content FROM attachment WHERE id = ?")
            .use { stmt ->
                stmt.setString(1, attachmentId)

                val rs = stmt.executeQuery()
                if (!rs.next()) {
                    throw IllegalStateException("Couldn't find attachment with id=$attachmentId")
                }

                BufferedInputStream(rs.getBinaryStream(1), 4 * 1024 * 1024)
            }
    }
}

private object SqlDumpExporter {
    fun dumpDatabase(conn: Connection, out: OutputStream) {
        val unencryptedFile = exportToPlainDatabase(conn)

        try {
            unencryptedFile.inputStream().use { input ->
                // standard 8KB buffer for streaming
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    out.write(buffer, 0, bytesRead)
                }
            }
            out.flush()
        } finally {
            unencryptedFile.delete()
        }
    }

    fun exportToPlainDatabase(db: Connection): File {
        val exportTo = File.createTempFile("intrakill_export_", "storage.db")
        if (exportTo.exists()) exportTo.delete()

        try {
            val absolutePath = exportTo.absolutePath.replace("'", "''")
            db.autoCommit = false

            db.createStatement().use { stmt ->
                stmt.execute("ATTACH DATABASE '$absolutePath' AS plain_db KEY ''")
                stmt.execute("PRAGMA plain_db.journal_mode = OFF")

                db.createStatement().executeQuery(
                    "SELECT name, sql FROM main.sqlite_master WHERE type='table' AND name NOT REGEXP '^sqlite_'"
                ).use { rs ->
                    while (rs.next()) {
                        val name = rs.getString("name")
                        val sql = rs.getString("sql")

                        val redirectedSql = sql.replaceFirst("(?i)CREATE\\s+TABLE\\s+(\"?)".toRegex(), "CREATE TABLE plain_db.$1")
                        stmt.execute(redirectedSql)
                        stmt.execute("INSERT INTO plain_db.\"$name\" SELECT * FROM main.\"$name\"")
                    }
                }

                db.createStatement().executeQuery(
                    "SELECT type, sql FROM main.sqlite_master WHERE type IN ('index', 'trigger', 'view') AND name NOT REGEXP '^sqlite_'"
                ).use { rs ->
                    while (rs.next()) {
                        val type = rs.getString("type").uppercase()
                        val sql = rs.getString("sql")

                        val redirectedSql = sql.replaceFirst("(?i)CREATE\\s+$type\\s+(\"?)".toRegex(), "CREATE $type plain_db.$1")
                        stmt.execute(redirectedSql)
                    }
                }

                db.commit()
                stmt.execute("DETACH DATABASE plain_db")
            }
        } catch (e: Exception) {
            db.rollback()
            throw e
        } finally {
            db.autoCommit = true
        }

        return exportTo
    }
}

private class SQLAdapterJVM(private val connection: Connection): SQLAdapter {
    override fun exec(sql: String) {
        connection.createStatement().use { stmt ->
            stmt.execute(sql)
        }
    }

    override fun transactional(block: SQLAdapter.() -> Unit) {
        connection.autoCommit = false
        try {
            block()
            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    override fun fetchVersion(): Int {
        return connection.createStatement().use { stmt ->
            stmt.executeQuery("PRAGMA user_version").use {
                if (it.next()) it.getInt(1) else 0
            }
        }
    }

    override fun setVersion(version: Int) {
        connection.createStatement().use { stmt -> stmt.execute("PRAGMA user_version = $version") }
    }
}