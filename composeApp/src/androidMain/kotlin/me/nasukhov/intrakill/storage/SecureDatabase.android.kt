package me.nasukhov.intrakill.storage

import android.content.Context
import me.nasukhov.intrakill.domain.model.Entry
import me.nasukhov.intrakill.domain.model.Tag
import me.nasukhov.intrakill.storage.dao.AttachmentRepository
import me.nasukhov.intrakill.storage.dao.EntryRepository
import me.nasukhov.intrakill.storage.dao.TagRepository
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper
import java.io.File
import kotlin.use

private class DBHelper(
    val ctx: Context,
    private val migrate: (db: SQLiteDatabase) -> Unit,
    dbName: String,
) : SQLiteOpenHelper(ctx, dbName, null, 1) {
    override fun onCreate(db: SQLiteDatabase) = Unit

    // Downgrade and upgrade are entirely on migrator. We don't rely on the helper here.
    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) = Unit

    // Downgrade and upgrade are entirely on migrator. We don't rely on helper here.
    override fun onDowngrade(
        db: SQLiteDatabase?,
        oldVersion: Int,
        newVersion: Int,
    ) = Unit

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        db.execSQL("PRAGMA foreign_keys=ON;")

        migrate(db)
    }
}

actual object SecureDatabase {
    private const val DB_NAME = "secured.db"

    private var helper: DBHelper? = null
    private var db: SQLiteDatabase? = null

    private val conn
        get() = db!!

    private val tagRepository by lazy { TagRepository(::conn) }
    private val attachmentRepository by lazy { AttachmentRepository(::conn) }
    private val entryRepository by lazy { EntryRepository(::conn, attachmentRepository, tagRepository) }

    /** Initialize with a context before opening the database */
    fun init(context: Context) {
        helper = DBHelper(context, { Migrator().migrate(SQLAdapterAndroid(it)) }, DB_NAME)
    }

    actual fun dumpDatabase(): File {
        val db = db!!
        val tempFile = Filesystem.getTmpFile("plain.db")
        if (tempFile.exists()) tempFile.delete()

        val escapedPath = tempFile.absolutePath.replace("'", "''")

        // Attach a new, empty, plaintext database
        db.execSQL("ATTACH DATABASE '$escapedPath' AS plain_db KEY ''")

        // gemini keeps trying to use execSQL even though procedures like this work only with rawsql call
        db.rawExecSQL("SELECT sqlcipher_export('plain_db')")

        // Detach so the file is flushed and unlocked
        db.execSQL("DETACH DATABASE plain_db")

        return tempFile
    }

    actual fun importFromFile(
        file: File,
        password: String,
    ): Boolean {
        val context = helper!!.ctx
        SQLiteDatabase.loadLibs(context)

        val plainDb = SQLiteDatabase.openOrCreateDatabase(file.absolutePath, "", null)

        try {
            val newDb = Filesystem.getDbFile("new_${DB_NAME}").also { if (it.exists()) it.delete() }
            val escapedTargetPath = newDb.absolutePath.replace("'", "''")
            plainDb.execSQL("ATTACH DATABASE '$escapedTargetPath' AS encrypted_db KEY '$password'")
            // extension can not be invoked by execSQL nor query and requires rawExecSQL
            plainDb.rawExecSQL("SELECT sqlcipher_export('encrypted_db')")
            plainDb.execSQL("DETACH DATABASE encrypted_db")

            return if (newDb.exists() && newDb.length() > 0) {
                val oldDb = Filesystem.getDbFile(DB_NAME).also { if (it.exists()) it.delete() }
                newDb.renameTo(oldDb) && open(password)
            } else {
                false
            }
        } catch (_: Exception) {
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
        } catch (_: Exception) {
            false
        }
    }

    actual fun changePassword(newPassword: String): Boolean =
        db?.let {
            val escapedPass = newPassword.replace("'", "''")
            it.rawExecSQL("PRAGMA rekey = '$escapedPass'")
            it.close()
            open(newPassword)
        } ?: false

    actual fun saveEntry(entry: Entry) = entryRepository.save(entry)

    actual fun countEntries(filter: EntriesFilter): Int = entryRepository.count(filter)

    actual fun findEntries(filter: EntriesFilter): List<Entry> = entryRepository.findByFilter(filter)

    actual fun getById(entryId: String): Entry = entryRepository.findById(entryId)!!

    actual fun deleteById(entryId: String) = entryRepository.delete(entryId)

    actual fun getAttachmentContent(attachmentId: String) = attachmentRepository.getContent(attachmentId)

    actual fun listTags(): Set<Tag> = tagRepository.listTags()

    actual fun filterMissingIds(fromIds: Set<String>) = entryRepository.findMissing(fromIds)
}

private class SQLAdapterAndroid(
    private val db: SQLiteDatabase,
) : SQLAdapter {
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

    override fun fetchVersion(): Version? =
        db.rawQuery("SELECT version FROM application_metadata LIMIT 1", null).use { cursor ->
            val versionColumn = cursor.getColumnIndexOrThrow("version")
            if (cursor.moveToFirst()) {
                Version.fromString(cursor.getString(versionColumn))
            } else {
                null
            }
        }
}
