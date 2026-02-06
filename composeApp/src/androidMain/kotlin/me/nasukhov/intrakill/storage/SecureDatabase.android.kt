package me.nasukhov.intrakill.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper
import me.nasukhov.intrakill.content.Attachment
import me.nasukhov.intrakill.content.Entry
import me.nasukhov.intrakill.content.Tag

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

    suspend fun import(
        sqlDump: String,
        password: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val context = helper!!.ctx
        try {
            SQLiteDatabase.loadLibs(context)

            // Delete old DB
            context.deleteDatabase(DB_NAME)

            val db = SQLiteDatabase.openOrCreateDatabase(
                context.getDatabasePath(DB_NAME),
                password,
                null
            )

            db.beginTransaction()
            try {
                sqlDump
                    .split(";\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { db.execSQL(it) }

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
                db.close()
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    actual fun open(password: String): Boolean {
        if (db != null) error("Database already opened")
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

    actual fun findEntries(filter: EntriesFilter): List<Entry> {
        val db = db ?: error("DB not opened")
        val result = mutableListOf<Entry>()

        val args = mutableListOf<String>()
        val sql = if (filter.tags.isEmpty()) {
            """
            SELECT * FROM entry
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """.trimIndent().also {
                args += filter.limit.toString()
                args += filter.offset.toString()
            }
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
            ) t ON t.entry_id = e.id
            ORDER BY e.created_at DESC
            LIMIT ? OFFSET ?
            """.trimIndent().also {
                args += filter.tags
                args += filter.tags.size.toString()
                args += filter.limit.toString()
                args += filter.offset.toString()
            }
        }

        db.rawQuery(sql, args.toTypedArray()).use { c ->
            while (c.moveToNext()) {
                val id = c.getString(c.getColumnIndexOrThrow("id"))
                result += Entry(
                    id = id,
                    name = c.getString(c.getColumnIndexOrThrow("name")),
                    preview = c.getBlob(c.getColumnIndexOrThrow("preview")),
                    attachments = listAttachments(id),
                    tags = listTags(id)
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
                attachments = listAttachments(entryId),
                tags = listTags(entryId)
            )
        }
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

    actual fun listTags(): List<Tag> {
        val db = db ?: error("DB not opened")
        val result = mutableListOf<Tag>()

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

    private fun listTags(entryId: String): List<String> {
        val db = db ?: error("DB not opened")
        val result = mutableListOf<String>()

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
