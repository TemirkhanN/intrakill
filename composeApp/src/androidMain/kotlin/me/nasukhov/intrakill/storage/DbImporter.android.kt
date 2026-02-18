package me.nasukhov.intrakill.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.nasukhov.intrakill.Security
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

actual object DbImporter {
    actual suspend fun importDatabase(ip: String, port: Int, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val tempFile = File(DbFileResolver.ctx.cacheDir, "unencrypted.db")

            downloadDumpToFile(ip, port, password, tempFile)

            try {
                SecureDatabase.importFromFile(tempFile, password)
            } finally {
                tempFile.delete()
            }
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun downloadDumpToFile(
        ip: String,
        port: Int,
        password: String,
        targetFile: File
    ) = withContext(Dispatchers.IO) {
        val token = Security.hash(password)
        val url = URL("http://$ip:$port/dump")

        (url.openConnection() as HttpURLConnection).run {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 300000
            setRequestProperty("Authorization", token)
            doInput = true

            when (responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    inputStream.use { input ->
                        targetFile.outputStream().use { output ->
                            // copyTo uses an 8KB buffer internally - OOM proof!
                            input.copyTo(output)
                        }
                    }
                }
                HttpURLConnection.HTTP_FORBIDDEN -> throw IllegalStateException("Incorrect password")
                else -> throw IllegalStateException("Server error: $responseCode")
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