package me.nasukhov.intrakill.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.nasukhov.intrakill.Security
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

actual object DbImporter {
    actual suspend fun importDatabase(ip: String, port: Int, password: String): Boolean {
        return downloadDump(ip, port, password).let {
            SecureDatabase.import(it, password)
        }
    }

    private suspend fun downloadDump(
        ip: String,
        port: Int,
        password: String
    ): String = withContext(Dispatchers.IO) {
        val token = Security.hash(password)

        val url = URL("http://$ip:$port/dump")
        (url.openConnection() as HttpURLConnection).run {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 300000

            setRequestProperty("Authorization", token)

            when (responseCode) {
                HttpURLConnection.HTTP_OK -> inputStream.bufferedReader().use { it.readText() }
                HttpURLConnection.HTTP_FORBIDDEN -> throw IllegalStateException("Incorrect password")
                else -> throw IllegalStateException("Server returned error code: $responseCode")
            }
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