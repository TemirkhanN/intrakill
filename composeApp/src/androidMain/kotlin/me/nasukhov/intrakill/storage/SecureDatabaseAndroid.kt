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

class SecureDatabaseAndroid(
    context: Context,
    private val fs: Filesystem,
) : SecureDatabase {
    companion object {
        private const val DB_NAME = "secured.db"
    }

    private val helper: DBHelper =
        DBHelper(
            context,
            { Migrator().migrate(SQLAdapterAndroid(it)) },
            DB_NAME,
        )

    private var db: SQLiteDatabase? = null

    private val conn
        get() = db!!

    private val tagRepository by lazy { TagRepository(::conn) }
    private val attachmentRepository by lazy { AttachmentRepository(::conn) }
    private val entryRepository by lazy { EntryRepository(::conn, attachmentRepository, tagRepository) }

    override fun dumpDatabase(): File {
        val db = db!!
        val tempFile = fs.getTmpFile("plain.db")
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

    override fun updateSettings(settings: Map<String, String>) {
        settings.forEach { (name, value) ->
            conn.execSQL(
                "INSERT INTO settings(name, value) VALUES(?, ?) ON CONFLICT(name) DO UPDATE SET value = excluded.value",
                arrayOf(name, value),
            )
        }
    }

    override fun getSettings(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        conn.rawQuery("SELECT `name`, `value` FROM settings", null).use { c ->
            while (c.moveToNext()) {
                result[c.getString(0)] = c.getString(1)
            }
        }

        return result
    }

    override fun importFromFile(
        file: File,
        password: String,
    ): Boolean {
        val context = helper.ctx
        SQLiteDatabase.loadLibs(context)

        val plainDb = SQLiteDatabase.openOrCreateDatabase(file.absolutePath, "", null)

        try {
            val newDb = fs.getDbFile("new_${DB_NAME}").also { if (it.exists()) it.delete() }
            val escapedTargetPath = newDb.absolutePath.replace("'", "''")
            plainDb.execSQL("ATTACH DATABASE '$escapedTargetPath' AS encrypted_db KEY '$password'")
            // extension can not be invoked by execSQL nor query and requires rawExecSQL
            plainDb.rawExecSQL("SELECT sqlcipher_export('encrypted_db')")
            plainDb.execSQL("DETACH DATABASE encrypted_db")

            return if (newDb.exists() && newDb.length() > 0) {
                val oldDb = fs.getDbFile(DB_NAME).also { if (it.exists()) it.delete() }
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

    override fun open(password: String): Boolean {
        db?.close()

        return try {
            SQLiteDatabase.loadLibs(helper.ctx)
            db = helper.getWritableDatabase(password)
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun changePassword(newPassword: String): Boolean =
        db?.let {
            val escapedPass = newPassword.replace("'", "''")
            it.rawExecSQL("PRAGMA rekey = '$escapedPass'")
            it.close()
            open(newPassword)
        } ?: false

    override fun saveEntry(entry: Entry) = entryRepository.save(entry)

    override fun countEntries(filter: EntriesFilter): Int = entryRepository.count(filter)

    override fun findEntries(filter: EntriesFilter): List<Entry> = entryRepository.findByFilter(filter)

    override fun getById(entryId: String): Entry = entryRepository.findById(entryId)!!

    override fun deleteById(entryId: String) = entryRepository.delete(entryId)

    override fun getAttachmentContent(attachmentId: String) = attachmentRepository.getContent(attachmentId)

    override fun listTags(): Set<Tag> = tagRepository.listTags()

    override fun filterMissingIds(fromIds: Set<String>) = entryRepository.findMissing(fromIds)

    override fun deleteTag(tagName: String) = tagRepository.deleteTag(tagName)

    override fun renameTag(
        oldName: String,
        newName: String,
    ) = tagRepository.renameTag(oldName, newName)
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
