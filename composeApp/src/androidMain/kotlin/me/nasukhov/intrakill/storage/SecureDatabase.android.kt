package me.nasukhov.intrakill.storage

import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper
import me.nasukhov.intrakill.content.Attachment
import me.nasukhov.intrakill.content.Entry
import me.nasukhov.intrakill.content.Tag
import java.io.File
import kotlin.use

private class DBHelper(
    val ctx: Context,
    private val migrate: (db: SQLiteDatabase) -> Unit,
    dbName: String,
    dbVersion: Int,
) : SQLiteOpenHelper(ctx, dbName, null, dbVersion) {

    override fun onCreate(db: SQLiteDatabase) {
        migrate(db)
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) = Unit
}

actual object SecureDatabase {

    private const val DB_NAME = "secured.db"
    private const val DB_VERSION = 1
    private const val ID_LENGTH = 36

    private var helper: DBHelper? = null
    private var db: SQLiteDatabase? = null

    /** Initialize with a context before opening the database */
    fun init(context: Context) {
        helper = DBHelper(context, this::migrate, DB_NAME, DB_VERSION)
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
            SQLiteDatabase.loadLibs(ctx.ctx) // or pass context via DBHelper
            db = ctx.getWritableDatabase(password)
            true
        } catch (e: Exception) {
            false
        }
    }

    actual fun saveEntry(entry: Entry) {
        val db = db ?: error("DB not opened")
        db.beginTransaction()
        try {
            db.execSQL(
                "INSERT INTO entry(id, name, preview) VALUES(?,?,?)",
                arrayOf(entry.id, entry.name, entry.preview)
            )

            entry.attachments.forEach { a ->
                db.execSQL(
                    """
                    INSERT INTO attachment
                    (id, entry_id, content, preview, mime_type, hashsum)
                    VALUES (?,?,?,?,?,?)
                    """.trimIndent(),
                    arrayOf(
                        a.id,
                        entry.id,
                        a.content,
                        a.preview,
                        a.mimeType,
                        a.hashsum
                    )
                )
            }

            entry.tags.forEach { tag ->
                db.execSQL(
                    "INSERT INTO tags(entry_id, tag) VALUES (?,?)",
                    arrayOf(entry.id, tag)
                )
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
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
                        tags = LazySet { listTags(id) }
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
                tags = LazySet { listTags(entryId) }
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
            "SELECT * FROM attachment WHERE entry_id = ?",
            arrayOf(entryId)
        ).use { c ->
            while (c.moveToNext()) {
                result += Attachment(
                    id = c.getString(c.getColumnIndexOrThrow("id")),
                    mimeType = c.getString(c.getColumnIndexOrThrow("mime_type")),
                    content = c.getBlob(c.getColumnIndexOrThrow("content")),
                    preview = c.getBlob(c.getColumnIndexOrThrow("preview")),
                    hashsum = c.getBlob(c.getColumnIndexOrThrow("hashsum"))
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
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS entry (
                id TEXT PRIMARY KEY CHECK(length(id) = $ID_LENGTH),
                name TEXT NOT NULL,
                preview BLOB NOT NULL,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            );
            """
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS attachment (
                id TEXT PRIMARY KEY CHECK(length(id) = $ID_LENGTH),
                entry_id TEXT NOT NULL,
                content BLOB NOT NULL,
                preview BLOB NOT NULL,
                mime_type TEXT NOT NULL,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                hashsum BLOB NOT NULL,
                FOREIGN KEY(entry_id) REFERENCES entry(id) ON DELETE CASCADE
            );
            """
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tags (
                entry_id TEXT NOT NULL,
                tag TEXT NOT NULL,
                PRIMARY KEY(entry_id, tag),
                FOREIGN KEY(entry_id) REFERENCES entry(id) ON DELETE CASCADE
            );
            """
        )
    }
}
