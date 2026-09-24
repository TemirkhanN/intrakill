package me.nasukhov.intrakill.storage

import android.content.Context
import java.io.File

actual object Filesystem {
    private lateinit var ctx: Context

    fun init(context: Context) {
        ctx = context.applicationContext

        // TODO it's a hack to overcome crippled deleteOnExit on android. Also, bad since it's called on the main thread
        context.cacheDir.listFiles().forEach { runCatching { it.delete() } }
    }

    actual fun getDbFile(dbName: String): File {
        require(dbName.matches("^[a-zA-Z0-9_]+\\.db$".toRegex())) { "Database name must follow pattern %s.db" }

        val file = ctx.getDatabasePath(dbName)
        file.parentFile?.mkdirs()

        return file
    }

    actual fun getTmpFile(prefix: String): File = File.createTempFile(prefix, null, ctx.cacheDir).also { it.deleteOnExit() }
}
