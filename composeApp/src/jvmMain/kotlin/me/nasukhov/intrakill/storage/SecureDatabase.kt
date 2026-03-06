package me.nasukhov.intrakill.storage

import me.nasukhov.intrakill.content.Entry
import me.nasukhov.intrakill.content.Tag
import me.nasukhov.intrakill.storage.dao.AttachmentRepository
import me.nasukhov.intrakill.storage.dao.EntryRepository
import me.nasukhov.intrakill.storage.dao.TagRepository
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlin.use

actual object SecureDatabase {
    private var dbName = "secured.db"
        set(value) {
            if (field != value) {
                connection?.close()
                connection = null
            }
            field = value
        }

    private var connection: Connection? = null

    private val db: Connection
        get() = connection!!

    private val tagRepository by lazy { TagRepository(::db) }

    private val attachmentRepository by lazy { AttachmentRepository(::db) }
    private val entryRepository by lazy { EntryRepository(::db, attachmentRepository, tagRepository) }

    internal fun switchDb(name: String) {
        dbName = name
    }

    fun dumpDatabase(): File = connection!!.let { SqlDumpExporter.exportToPlainDatabase(it) }

    fun importFromFile(
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
            val targetFile = File(dbName)
            if (targetFile.exists()) targetFile.delete()
            file.copyTo(targetFile)

            // 3. Open the connection to the PLAINTEXT file
            // We use the configuration WITHOUT a key as per Willena's instructions
            val url = "jdbc:sqlite:$dbName"
            DriverManager.getConnection(url).apply {
                createStatement().use { stmt ->
                    val escapedPass = password.replace("'", "''")
                    stmt.execute("PRAGMA rekey = '$escapedPass'")
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
        connection = null

        return try {
            val url = "jdbc:sqlite:$dbName"
            connection =
                DriverManager.getConnection(url, null, password).apply {
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

    actual fun saveEntry(entry: Entry) = entryRepository.save(entry)

    actual fun countEntries(filter: EntriesFilter): Int = entryRepository.countByFilter(filter)

    actual fun findEntries(filter: EntriesFilter): List<Entry> = entryRepository.findByFilter(filter)

    actual fun deleteById(entryId: String) = entryRepository.deleteById(entryId)

    actual fun getById(entryId: String): Entry = entryRepository.findById(entryId)!!

    actual fun listTags(): Set<Tag> = tagRepository.findAll()
}

private object SqlDumpExporter {
    fun exportToPlainDatabase(db: Connection): File {
        val exportTo = File.createTempFile("intrakill_export_", "storage.db")

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
