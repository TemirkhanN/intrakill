package me.nasukhov.intrakill.storage

import android.content.Context
import java.io.File

class FilesystemAndroid(
    private val context: Context,
) : Filesystem {
    init {
        // TODO it's a hack to overcome crippled deleteOnExit on android. Also, bad since it's called on the main thread
        context.cacheDir.listFiles()?.forEach { runCatching { it.delete() } }
    }

    override fun getDbFile(dbName: String): File {
        require(dbName.matches("^[a-zA-Z0-9_]+\\.db$".toRegex())) { "Database name must follow pattern %s.db" }

        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()

        return file
    }

    override fun getTmpFile(prefix: String): File = File.createTempFile(prefix, null, context.cacheDir).also { it.deleteOnExit() }
}
