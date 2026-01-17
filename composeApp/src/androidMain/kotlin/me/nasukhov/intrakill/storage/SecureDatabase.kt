package me.nasukhov.intrakill.storage

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import net.sqlcipher.database.SupportFactory
/*
actual object SecureDatabase {

    // TODO memory leak enjoyer chatgpt
    private lateinit var context: Context
    private var db: SupportSQLiteDatabase? = null

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    actual fun open(password: ByteArray) {
        if (db != null) {
            throw RuntimeException("Database is already open")
        }

        try {
            val passphrase = password

            val factory = SupportFactory(passphrase)

            val helper = FrameworkSQLiteOpenHelperFactory()
                .create(
                    SupportSQLiteOpenHelper.Configuration.builder(context)
                        .name("secure.db")
                        .callback(object : SupportSQLiteOpenHelper.Callback(1) {

                            override fun onCreate(db: SupportSQLiteDatabase) {
                                db.execSQL(
                                    """
                                    CREATE TABLE entries (
                                        id TEXT PRIMARY KEY,
                                        content BLOB NOT NULL
                                    );
                                    """
                                )
                                db.execSQL(
                                    """
                                    CREATE TABLE tags (
                                        entry_id TEXT NOT NULL,
                                        tag TEXT NOT NULL
                                    );
                                    """
                                )
                                db.execSQL(
                                    "CREATE INDEX idx_tags_tag ON tags(tag);"
                                )
                            }

                            override fun onUpgrade(
                                db: SupportSQLiteDatabase,
                                oldVersion: Int,
                                newVersion: Int
                            ) = Unit
                        })
                        .build()
                )

            db = helper.writableDatabase
        } catch (e: Exception) {
            throw IllegalStateException(
                "Failed to open encrypted database",
                e
            )
        }
    }
}
*/