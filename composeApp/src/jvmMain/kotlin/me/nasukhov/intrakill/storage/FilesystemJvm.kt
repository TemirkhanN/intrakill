package me.nasukhov.intrakill.storage

import java.io.File

class FilesystemJvm(
    rootDir: String = System.getProperty("user.home"),
) : Filesystem {
    private val appFolder by lazy { File(rootDir, ".Intrakill").also { it.mkdirs() } }

    override fun getDbFile(dbName: String): File {
        require(dbName.matches("^[a-zA-Z0-9_]+\\.db$".toRegex())) { "Database name must follow pattern %s.db" }

        val dbDir = File(appFolder, "databases").also { it.mkdirs() }

        return File(dbDir, dbName)
    }

    override fun getTmpFile(prefix: String): File = File.createTempFile(prefix, null, appFolder).also { it.deleteOnExit() }
}
