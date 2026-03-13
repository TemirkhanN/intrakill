package me.nasukhov.intrakill.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import me.nasukhov.intrakill.Security
import me.nasukhov.intrakill.content.Content
import me.nasukhov.intrakill.content.Entry
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

private class ApiService(
    private val source: StorageSource,
    password: String,
) {
    private val authToken = Security.hash(password)

    fun <R> getDbDump(block: HttpURLConnection.() -> R): R = request(URI("$source/dump"), block)

    fun <R> listEntriesIds(
        limit: Int = 100,
        offset: Int = 0,
        block: HttpURLConnection.() -> R,
    ): R = request(URI("$source/entriesIds?limit=$limit&offset=$offset"), block)

    @OptIn(ExperimentalSerializationApi::class)
    fun getById(id: String): Entry =
        request(URI("$source/entries/$id")) {
            check(responseCode == HttpURLConnection.HTTP_OK) {
                "Error $responseCode occurred while fetching entry $id"
            }
            val entry = Json.decodeFromStream<Entry>(inputStream)

            entry.copy(
                isPersisted = false,
                attachments = entry.attachments.map { it.copy(content = getAttachmentContent(it.id), isPersisted = false) },
            )
        }

    private fun getAttachmentContent(id: String) =
        Content {
            request(URI("$source/attachments/$id/content")) {
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    inputStream
                } else {
                    throw IllegalStateException("Server returned $responseCode: ${errorStream?.bufferedReader()?.readText()}")
                }
            }
        }

    private fun <R> request(
        uri: URI,
        block: HttpURLConnection.() -> R,
    ): R =
        (uri.toURL().openConnection() as HttpURLConnection)
            .apply {
                connectTimeout = 5000
                readTimeout = 300000
                requestMethod = "GET"
                setRequestProperty("Authorization", authToken)
            }.block()
}

actual object ExternalStorage {
    private var apiService: ApiService? = null

    actual fun resolve(
        source: StorageSource,
        password: String,
    ) {
        apiService = ApiService(source, password)
    }

    actual suspend fun downloadDump(onProgress: (Progress) -> Unit): File =
        withContext(Dispatchers.IO) {
            apiService!!.getDbDump {
                when (responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val tmpFile = Filesystem.getTmpFile("plain_db")

                        val totalBytes = contentLengthLong
                        inputStream.use { input ->
                            tmpFile.outputStream().use { output ->
                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                var totalRead = 0L

                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                    totalRead += bytesRead

                                    if (totalBytes > 0) {
                                        onProgress(Progress(totalRead, totalBytes))
                                    }
                                }
                                output.flush()
                            }
                        }
                        tmpFile
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

    @OptIn(ExperimentalSerializationApi::class)
    actual suspend fun listEntriesIds(
        offset: Int,
        limit: Int,
    ): Set<String> =
        withContext(Dispatchers.IO) {
            apiService!!.listEntriesIds(limit, offset) {
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Json.decodeFromStream(inputStream)
                } else {
                    emptySet()
                }
            }
        }

    actual suspend fun getById(id: String): Entry =
        withContext(Dispatchers.IO) {
            apiService!!.getById(id)
        }
}

actual object Filesystem {
    private val appFolder by lazy { File(System.getProperty("user.home"), ".Intrakill").also { it.mkdirs() } }

    actual fun getDbFile(dbName: String): File {
        require(dbName.matches("^[a-zA-Z0-9_]+\\.db$".toRegex())) { "Database name must follow pattern %s.db" }

        val dbDir = File(appFolder, "databases").also { it.mkdirs() }

        return File(dbDir, dbName)
    }

    actual fun getTmpFile(prefix: String): File = File.createTempFile(prefix, null, appFolder).also { it.deleteOnExit() }
}
