package me.nasukhov.intrakill.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.nasukhov.intrakill.Security
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

actual object DbImporter {
    actual suspend fun importDatabase(
        ip: String,
        port: Int,
        password: String,
        onProgress: (Int) -> Unit,
    ): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val tempFile = File(DbFileResolver.ctx.cacheDir, "unencrypted.db")

                downloadDumpToFile(ip, port, password, tempFile, onProgress)

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
        targetFile: File,
        onProgress: (Int) -> Unit,
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
                    val totalBytes = contentLength.toLong()

                    inputStream.use { input ->
                        targetFile.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var totalRead = 0L

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalRead += bytesRead

                                if (totalBytes > 0) {
                                    onProgress(progress(totalRead, totalBytes))
                                }
                            }
                            output.flush()
                        }
                    }
                }
                HttpURLConnection.HTTP_FORBIDDEN -> throw IllegalStateException("Incorrect password")
                else -> throw IllegalStateException("Server error: $responseCode")
            }
        }
    }

    private fun progress(
        bytes: Long,
        outOf: Long,
    ): Int = ((bytes.toFloat() / outOf) * 100).toInt()
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
