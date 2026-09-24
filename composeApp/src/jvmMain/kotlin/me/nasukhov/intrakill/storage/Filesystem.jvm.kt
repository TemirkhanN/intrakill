package me.nasukhov.intrakill.storage

import java.io.File

actual object Filesystem {
    private val appFolder by lazy { File(System.getProperty("user.home"), ".Intrakill").also { it.mkdirs() } }

    actual fun getDbFile(dbName: String): File {
        require(dbName.matches("^[a-zA-Z0-9_]+\\.db$".toRegex())) { "Database name must follow pattern %s.db" }

        val dbDir = File(appFolder, "databases").also { it.mkdirs() }

        return File(dbDir, dbName)
    }

    actual fun getTmpFile(prefix: String): File = File.createTempFile(prefix, null, appFolder).also { it.deleteOnExit() }
}
