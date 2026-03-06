package me.nasukhov.intrakill.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.nasukhov.intrakill.Security
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

private data class Request(private val url: URI, private val authToken: String) {
    companion object {
        fun getDbDump(ip: String, port: Int, password: String) = Request(
            URI("http://$ip:$port/dump"), Security.hash(password)
        )
    }

    fun <R> exec(block: HttpURLConnection.() -> R): R {
        return (url.toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 300000
            requestMethod = "GET"
            setRequestProperty("Authorization", authToken)
        }.block()
    }
}

actual object DbImporter {
    private val db = SecureDatabase

    actual suspend fun importDatabase(ip: String, port: Int, password: String, onProgress: (Int) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            val request = Request.getDbDump(ip, port, password)

            request.exec {
                when (responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val tmpFile = File.createTempFile("intrakill_", "_unencrypted.db").also { it.deleteOnExit() }

                        val totalBytes = contentLength.toLong()
                        inputStream.use { input ->
                            tmpFile.outputStream().use { output ->
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

                        db.importFromFile(tmpFile, password).also { tmpFile.delete() }
                    }
                    HttpURLConnection.HTTP_FORBIDDEN -> {
                        throw IllegalStateException("Import failed: Incorrect password/token rejected by server.")
                    }
                    else -> {
                        throw IllegalStateException("Import failed: Server returned $responseCode")
                    }
                }
            }
        }

    private fun progress(bytes: Long, outOf: Long): Int = ((bytes.toFloat()/outOf) * 100).toInt()
}

// TODO use this instead of interacting with the db directly.
actual object DbFileResolver {
    actual fun resolve(dbName: String): File {
        val dir = File(System.getProperty("user.home"), ".Intrakill")
        dir.mkdirs()

        return File(dir, dbName)
    }
}