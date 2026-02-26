package me.nasukhov.intrakill.storage

import android.content.ContentValues
import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper
import me.nasukhov.intrakill.content.Attachment
import me.nasukhov.intrakill.content.Content
import me.nasukhov.intrakill.content.Entry
import me.nasukhov.intrakill.content.Tag
import me.nasukhov.intrakill.scene.asEnumeration
import java.io.File
import java.io.FilterInputStream
import java.io.OutputStream
import java.io.SequenceInputStream
import kotlin.use

private class DBHelper(
    val ctx: Context,
    private val migrate: (db: SQLiteDatabase) -> Unit,
    dbName: String
) : SQLiteOpenHelper(ctx, dbName, null, 1) {

    override fun onCreate(db: SQLiteDatabase) = Unit

    // Downgrade and upgrade are entirely on migrator. We don't rely on the helper here.
    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) = Unit

    // Downgrade and upgrade are entirely on migrator. We don't rely on helper here.
    override fun onDowngrade(
        db: SQLiteDatabase?,
        oldVersion: Int,
        newVersion: Int
    ) = Unit

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)

        migrate(db)
    }
}

actual object SecureDatabase {

    private const val DB_NAME = "secured.db"

    private var helper: DBHelper? = null
    private var db: SQLiteDatabase? = null

    /** Initialize with a context before opening the database */
    fun init(context: Context) {
        helper = DBHelper(context, this::migrate, DB_NAME)
    }

    fun dumpDatabase(output: OutputStream) {
        val context = helper!!.ctx
        val db = db!!
        val tempFile = File(context.cacheDir, "temp_unsecured.db")
        if (tempFile.exists()) tempFile.delete()

        try {
            // 2. Export the encrypted database to the plain temp file
            // We use the same 'sqlcipher_export' logic we discussed
            val escapedPath = tempFile.absolutePath.replace("'", "''")

            // Attach a new, empty, plaintext database
            db.execSQL("ATTACH DATABASE '$escapedPath' AS plain_db KEY ''")

            // gemini keeps trying to use execSQL even though procedures like this work only with rawsql call
            db.rawExecSQL("SELECT sqlcipher_export('plain_db')")

            // Detach so the file is flushed and unlocked
            db.execSQL("DETACH DATABASE plain_db")

            // 3. Stream the raw bytes of the unencrypted file to the output
            tempFile.inputStream().use { input ->
                val buffer = ByteArray(8192) // 8KB buffer
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
            }
            output.flush()
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        } finally {
            // 4. Cleanup: Never leave the unencrypted file on the device
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    fun importFromFile(file: File, password: String): Boolean {
        val context = helper!!.ctx
        SQLiteDatabase.loadLibs(context)

        val dbPath = context.getDatabasePath(DB_NAME)
        if (dbPath.exists()) dbPath.delete()

        val plainDb = SQLiteDatabase.openOrCreateDatabase(file.absolutePath, "", null)

        try {
            val escapedTargetPath = dbPath.absolutePath.replace("'", "''")
            plainDb.execSQL("ATTACH DATABASE '$escapedTargetPath' AS encrypted_db KEY '$password'")
            // extension can not be invoked by execSQL nor query and requires rawExecSQL
            plainDb.rawExecSQL("SELECT sqlcipher_export('encrypted_db')")
            plainDb.execSQL("DETACH DATABASE encrypted_db")

            return dbPath.length() > 0
        } catch (e: Exception) {
            return false
        } finally {
            plainDb.close()
        }
    }

    actual fun open(password: String): Boolean {
        db?.close()
        val ctx = helper ?: error("SecureDatabase not initialized. Call SecureDatabase.init(context) first.")

        return try {
            SQLiteDatabase.loadLibs(ctx.ctx)
            db = ctx.getWritableDatabase(password)
            true
        } catch (e: Exception) {
            false
        }
    }

    actual fun saveEntry(entry: Entry): Entry {
        val db = db ?: error("DB not opened")
        db.beginTransaction()
        try {
            if (entry.isPersisted) {
                updateEntry(entry)
            } else {
                createEntry(entry)
            }

            db.setTransactionSuccessful()

            return getById(entry.id)
        } finally {
            db.endTransaction()
        }
    }

    private fun createEntry(entry: Entry) {
        val db = db ?: error("DB not opened")
        check(!entry.isPersisted) { "Duplicate entry creation attempt" }

        db.execSQL(
            "INSERT INTO entry(id, name, preview) VALUES(?,?,?)",
            arrayOf(entry.id, entry.name, entry.preview)
        )

        entry.attachments.forEach { a ->
            db.execSQL(
                """
                    INSERT INTO attachment
                    (id, entry_id, preview, mime_type, hashsum)
                    VALUES (?,?,?,?,?)
                    """.trimIndent(),
                arrayOf(
                    a.id,
                    entry.id,
                    a.preview,
                    a.mimeType,
                    a.hashsum
                )
            )
            writeContent(a.id, a.content)
        }

        entry.tags.forEach { tag ->
            db.execSQL(
                "INSERT INTO tags(entry_id, tag) VALUES (?,?)",
                arrayOf(entry.id, tag)
            )
        }
    }

    private fun updateEntry(entry: Entry) {
        val db = db ?: error("DB not opened")
        check(entry.isPersisted) { "Attempt to update non-existent entry" }
        val oldEntry = getById(entry.id)

        val removedTags = oldEntry.tags.minus(entry.tags)
        if (!removedTags.isEmpty()) {
            val placeholders = removedTags.joinToString(",") { "?" }
            val args = arrayOf(entry.id, *removedTags.toTypedArray())
            db.execSQL("DELETE FROM tags WHERE entry_id = ? AND tag IN ($placeholders)", args)
        }

        val addedTags = entry.tags.minus(oldEntry.tags)
        addedTags.forEach { tag ->
            db.execSQL("INSERT INTO tags (entry_id, tag) VALUES (?, ?)", arrayOf(entry.id, tag))
        }

        val remainingIds = entry.attachments.map { it.id }
        val deletedIds = oldEntry.attachments.map { it.id }.filter { it !in remainingIds }

        if (!deletedIds.isEmpty()) {
            val placeholders = deletedIds.joinToString(",") { "?" }
            db.execSQL("DELETE FROM attachment WHERE id IN ($placeholders)", deletedIds.toTypedArray())
        }

        entry.attachments.filter { !it.isPersisted }.forEach { a ->
            // Using insertOrThrow or explicit params for BLOB safety
            db.execSQL("""
            INSERT INTO attachment (id, entry_id, preview, mime_type, hashsum, size)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent(), arrayOf(a.id, entry.id, a.preview, a.mimeType, a.hashsum, a.size))

            // Write chunks (using the 1MB split logic)
            writeContent(a.id, a.content)
        }
    }

    actual fun countEntries(filter: EntriesFilter): Int {
        val db = db ?: error("DB not opened")
        val args = mutableListOf<String>()

        val sql = if (filter.tags.isEmpty()) {
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

    actual fun findEntries(filter: EntriesFilter): List<Entry> {
        val db = db ?: error("DB not opened")
        val result = mutableListOf<Entry>()
        val args = mutableListOf<String>()

        val sql = if (filter.tags.isEmpty()) {
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
            val placeholders = filter.tags.joinToString(",") { "?" }
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
                        attachments = LazyList { listAttachments(id) },
                        tags = LazySet { listTags(id) },
                        isPersisted = true,
                    )
                )
            }
        }

        return result
    }


    actual fun getById(entryId: String): Entry {
        val db = db ?: error("DB not opened")

        db.rawQuery(
            "SELECT * FROM entry WHERE id = ?",
            arrayOf(entryId)
        ).use { c ->
            c.moveToFirst()
            return Entry(
                id = entryId,
                name = c.getString(c.getColumnIndexOrThrow("name")),
                preview = c.getBlob(c.getColumnIndexOrThrow("preview")),
                attachments = LazyList { listAttachments(entryId) },
                tags = LazySet { listTags(entryId) },
                isPersisted = true,
            )
        }
    }

    actual fun deleteById(entryId: String) {
        val db = db ?: error("DB not opened")
        val query = "DELETE FROM entry WHERE id = ?"

        db.execSQL(query, arrayOf(entryId))
    }

    private fun listAttachments(entryId: String): List<Attachment> {
        val db = db ?: error("DB not opened")
        val result = mutableListOf<Attachment>()

        db.rawQuery(
            "SELECT id, mime_type, preview, `size`, hashsum FROM attachment WHERE entry_id = ?",
            arrayOf(entryId)
        ).use { c ->
            while (c.moveToNext()) {
                val attachmentId = c.getString(c.getColumnIndexOrThrow("id"))
                result += Attachment(
                    id = attachmentId,
                    mimeType = c.getString(c.getColumnIndexOrThrow("mime_type")),
                    content = getContent(attachmentId),
                    preview = c.getBlob(c.getColumnIndexOrThrow("preview")),
                    size = c.getLong(c.getColumnIndexOrThrow("size")),
                    hashsum = c.getBlob(c.getColumnIndexOrThrow("hashsum")),
                    isPersisted = true,
                )
            }
        }

        return result
    }

    actual fun listTags(): Set<Tag> {
        val db = db ?: error("DB not opened")
        val result = mutableSetOf<Tag>()

        db.rawQuery(
            """
            SELECT tag, COUNT(*) as frequency
            FROM tags
            GROUP BY tag
            ORDER BY frequency DESC
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                result += Tag(
                    c.getString(0),
                    c.getInt(1)
                )
            }
        }

        return result
    }

    private fun listTags(entryId: String): Set<String> {
        val db = db ?: error("DB not opened")
        val result = mutableSetOf<String>()

        db.rawQuery(
            "SELECT tag FROM tags WHERE entry_id = ?",
            arrayOf(entryId)
        ).use { c ->
            while (c.moveToNext()) {
                result += c.getString(0)
            }
        }

        return result
    }

    private fun migrate(db: SQLiteDatabase) {
        Migrator().migrate(SQLAdapterAndroid(db))
    }

    private fun getContent(attachmentId: String): Content = Content {
        val db = db ?: error("DB not opened")

        val cursor = db.rawQuery(
            "SELECT data FROM attachment_chunk WHERE attachment_id = ? ORDER BY sequence_number ASC",
            arrayOf(attachmentId)
        )

        val streamSequence = sequence {
            try {
                if (cursor.count == 0) {
                    throw IllegalStateException("Attachment $attachmentId is missing content entirely")
                }

                while (cursor.moveToNext()) {
                    val blob = cursor.getBlob(0)
                    yield(blob.inputStream())
                }
            } catch (e: Exception) {
                cursor.close()
                throw e
            }
        }

        val mergedStream = SequenceInputStream(streamSequence.asEnumeration())

        object : FilterInputStream(mergedStream) {
            private var isClosed = false

            override fun close() {
                if (isClosed) return
                try {
                    super.close()
                } finally {
                    cursor.close()
                    isClosed = true
                }
            }
        }
    }

    private fun writeContent(attachmentId: String, content: Content) {
        val db = db ?: error("DB not opened")

        val buffer = ByteArray(MAX_CHUNK_SIZE)

        content.use { input ->
            var sequenceNumber = 0
            var totalBytesWritten = 0L

            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead == -1) break

                val dataToInsert = if (bytesRead == MAX_CHUNK_SIZE) {
                    buffer
                } else {
                    buffer.copyOf(bytesRead)
                }

                val values = ContentValues().apply {
                    put("attachment_id", attachmentId)
                    put("sequence_number", sequenceNumber)
                    put("data", dataToInsert)
                }

                val rowId = db.insertOrThrow("attachment_chunk", null, values)
                if (rowId == -1L) throw IllegalStateException("Failed to insert chunk $sequenceNumber")

                sequenceNumber++
                totalBytesWritten += bytesRead
            }
        }
    }
}

private class SQLAdapterAndroid(private val db: SQLiteDatabase) : SQLAdapter {

    override fun exec(sql: String) {
        db.execSQL(sql)
    }

    override fun transactional(block: SQLAdapter.() -> Unit) {
        db.beginTransaction()
        try {
            block()
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            throw e
        } finally {
            db.endTransaction()
        }
    }

    override fun fetchVersion(): Version? {
        return db.rawQuery("SELECT version FROM application_metadata LIMIT 1", null).use { cursor ->
            val versionColumn = cursor.getColumnIndexOrThrow("version")
            if (cursor.moveToFirst()) {
                Version.fromString(cursor.getString(versionColumn))
            } else {
                null
            }
        }
    }
}