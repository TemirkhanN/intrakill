package me.nasukhov.intrakill.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.nasukhov.intrakill.Security
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private data class Request(private val url: URL, private val authToken: String) {
    companion object {
        fun getDbDump(ip: String, port: Int, password: String) = Request(
            URL("http://$ip:$port/dump"), Security.hash(password)
        )
    }

    fun <R> exec(block: HttpURLConnection.() -> R): R {
        return (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 300000
            requestMethod = "GET"
            setRequestProperty("Authorization", authToken)
        }.block()
    }
}

actual object DbImporter {
    private val db = SecureDatabase

    actual suspend fun importDatabase(ip: String, port: Int, password: String): Boolean =
        withContext(Dispatchers.IO) {
            val request = Request.getDbDump(ip, port, password)

            request.exec {
                when (responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val tmpFile = File.createTempFile("intrakill_", "_unencrypted.db").also { it.deleteOnExit() }
                        inputStream.use { input ->
                            tmpFile.outputStream().use { output ->
                                input.copyTo(output)
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
}

// TODO use this instead of interacting with the db directly.
actual object DbFileResolver {
    actual fun resolve(dbName: String): File {
        val dir = File(System.getProperty("user.home"), ".Intrakill")
        dir.mkdirs()

        return File(dir, dbName)
    }
}