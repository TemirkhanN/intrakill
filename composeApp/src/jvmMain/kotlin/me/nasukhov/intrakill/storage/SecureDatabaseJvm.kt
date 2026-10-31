package me.nasukhov.intrakill.storage

import me.nasukhov.intrakill.domain.model.Entry
import me.nasukhov.intrakill.domain.model.Tag
import me.nasukhov.intrakill.storage.dao.AttachmentRepository
import me.nasukhov.intrakill.storage.dao.EntryRepository
import me.nasukhov.intrakill.storage.dao.TagRepository
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlin.use

class SecureDatabaseJvm(
    private val filesystem: Filesystem,
) : SecureDatabase {
    internal val dbName = "secured.db"

    private var connection: Connection? = null

    private val db: Connection
        get() = connection!!

    private val tagRepository by lazy { TagRepository(::db) }

    private val attachmentRepository by lazy { AttachmentRepository(::db) }
    private val entryRepository by lazy { EntryRepository(::db, attachmentRepository, tagRepository) }

    override fun dumpDatabase(): File = connection!!.let { SqlDumpExporter.exportToPlainDatabase(it, filesystem) }

    override fun updateSettings(settings: Map<String, String>) {
        db
            .prepareStatement(
                "INSERT INTO settings(`name`, `value`) VALUES(?, ?) " +
                    "ON CONFLICT(`name`) " +
                    "DO UPDATE SET `value` = ?",
            ).use { stmt ->
                settings.forEach { (name, value) ->
                    stmt.setString(1, name)
                    stmt.setString(2, value)
                    stmt.setString(3, value)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
    }

    override fun getSettings(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        db.prepareStatement("SELECT `name`, `value` FROM settings").use { stmt ->
            val rs = stmt.executeQuery()
            while (rs.next()) {
                result[rs.getString("name")] = rs.getString("value")
            }
        }

        return result
    }

    override fun importFromFile(
        file: File,
        password: String,
    ): Boolean {
        try {
            // 1. Close the current encrypted connection so we can swap files
            connection?.let {
                if (!it.isClosed) {
                    it.close()
                }
            }

            // 2. Replace the current DB file with the unencrypted import file
            val newDb = filesystem.getDbFile("new_$dbName")
            if (newDb.exists()) newDb.delete()
            file.let {
                it.copyTo(newDb)
                it.delete()
            }

            // 3. Open the connection to the PLAINTEXT file
            // We use the configuration WITHOUT a key as per Willena's instructions
            newDb.openSqliteConnection().apply {
                createStatement().use { stmt ->
                    val escapedPass = password.replace("'", "''")
                    stmt.execute("PRAGMA rekey = '$escapedPass'")
                }
                close()
            }

            val oldDb = filesystem.getDbFile(dbName).also { if (it.exists()) it.delete() }
            return newDb.renameTo(oldDb) && open(password)
        } catch (_: Exception) {
            return false
        }
    }

    override fun open(password: String): Boolean {
        connection?.close()
        connection = null

        return try {
            connection =
                filesystem.getDbFile(dbName).openSqliteConnection(password = password).apply {
                    createStatement().use { stmt ->
                        stmt.execute("PRAGMA cipher_memory_security = ON;")

                        stmt.execute("PRAGMA foreign_keys = ON;")
                        // Force key validation
                        stmt.execute("SELECT count(*) FROM sqlite_master;")
                    }

                    Migrator().migrate(SQLAdapterJVM(this))
                }

            true
        } catch (e: Exception) {
            false
        }
    }

    override fun changePassword(newPassword: String): Boolean =
        connection?.let {
            it.createStatement().use { stmt ->
                val escapedPass = newPassword.replace("'", "''")
                stmt.execute("PRAGMA rekey = '$escapedPass'")
            }
            it.close()
            open(newPassword)
        } ?: false

    override fun saveEntry(entry: Entry) = entryRepository.save(entry)

    override fun countEntries(filter: EntriesFilter): Int = entryRepository.countByFilter(filter)

    override fun findEntries(filter: EntriesFilter): List<Entry> = entryRepository.findByFilter(filter)

    override fun deleteById(entryId: String) = entryRepository.deleteById(entryId)

    override fun getById(entryId: String): Entry = entryRepository.findById(entryId)!!

    override fun getAttachmentContent(attachmentId: String) = attachmentRepository.getContent(attachmentId)

    override fun listTags(): Set<Tag> = tagRepository.findAll()

    override fun filterMissingIds(fromIds: Set<String>) = entryRepository.findMissing(fromIds)

    override fun deleteTag(tagName: String) = tagRepository.deleteTag(tagName)

    override fun renameTag(
        oldName: String,
        newName: String,
    ) = tagRepository.renameTag(oldName, newName)
}

// TODO there is no point in having the object. Could be just the function
private object SqlDumpExporter {
    fun exportToPlainDatabase(
        db: Connection,
        filesystem: Filesystem,
    ): File {
        val exportTo = filesystem.getTmpFile("export_storage")

        try {
            val absolutePath = exportTo.absolutePath.replace("'", "''")
            db.autoCommit = false

            db.createStatement().use { stmt ->
                stmt.execute("ATTACH DATABASE '$absolutePath' AS plain_db KEY ''")
                stmt.execute("PRAGMA plain_db.journal_mode = OFF")

                db
                    .createStatement()
                    .executeQuery(
                        "SELECT name, sql FROM main.sqlite_master WHERE type='table' AND name NOT REGEXP '^sqlite_'",
                    ).use { rs ->
                        while (rs.next()) {
                            val name = rs.getString("name")
                            val sql = rs.getString("sql")

                            val redirectedSql = sql.replaceFirst("(?i)CREATE\\s+TABLE\\s+(\"?)".toRegex(), "CREATE TABLE plain_db.$1")
                            stmt.execute(redirectedSql)
                            stmt.execute("INSERT INTO plain_db.\"$name\" SELECT * FROM main.\"$name\"")
                        }
                    }

                db
                    .createStatement()
                    .executeQuery(
                        "SELECT type, sql FROM main.sqlite_master WHERE type IN ('index', 'trigger', 'view') AND name NOT REGEXP '^sqlite_'",
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
            exportTo.delete()
            throw e
        } finally {
            db.autoCommit = true
        }

        return exportTo
    }
}

private class SQLAdapterJVM(
    private val connection: Connection,
) : SQLAdapter {
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

    override fun fetchVersion(): Version? =
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT version FROM application_metadata LIMIT 1").use {
                if (it.next()) Version.fromString(it.getString(1)) else null
            }
        }
}

internal fun File.openSqliteConnection(
    password: String? = null,
    user: String? = null,
): Connection {
    val dbPath = this.absolutePath.replace("\\", "/")

    return DriverManager.getConnection("jdbc:sqlite:$dbPath", user, password)
}
