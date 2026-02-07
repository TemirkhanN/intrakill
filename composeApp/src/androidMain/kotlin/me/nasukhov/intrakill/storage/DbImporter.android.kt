package me.nasukhov.intrakill.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

actual object DbImporter {
    actual suspend fun importDatabase(ip: String, port: Int, password: String): Boolean {
        return downloadDump(ip, port).let {
            SecureDatabase.import(it, password)
        }
    }

    private suspend fun downloadDump(ip: String, port: Int): String =
        withContext(Dispatchers.IO) {
            val url = URL("http://$ip:$port/dump")
            (url.openConnection() as HttpURLConnection).run {
                connectTimeout = 5000
                readTimeout = 300000
                inputStream.bufferedReader().use { it.readText() }
            }
        }
}

actual object DbFileResolver {
    lateinit var ctx: Context // TODO
    fun init(context: Context) {
        ctx = context.applicationContext
    }

    actual fun resolve(dbName: String): File {
        val file = ctx.getDatabasePath(dbName)
        file.parentFile?.mkdirs()

        return file
    }
}