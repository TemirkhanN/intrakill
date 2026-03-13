package me.nasukhov.intrakill.storage

import android.content.Context
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
import java.net.URL

actual object ExternalStorage {
    private var source: StorageSource? = null
    private var token: String? = null

    actual fun resolve(
        source: StorageSource,
        password: String,
    ) {
        this.source = source
        token = Security.hash(password)
    }

    actual suspend fun downloadDump(onProgress: (Progress) -> Unit) =
        withContext(Dispatchers.IO) {
            val targetFile = Filesystem.getTmpFile("plain.db")

            val url = URL("$source/dump")

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
                                        onProgress(Progress(totalRead, totalBytes))
                                    }
                                }
                                output.flush()
                            }
                        }

                        targetFile
                    }
                    HttpURLConnection.HTTP_FORBIDDEN -> throw IllegalStateException("Incorrect password")
                    else -> throw IllegalStateException("Server error: $responseCode")
                }
            }
        }

    @OptIn(ExperimentalSerializationApi::class)
    actual suspend fun listEntriesIds(
        offset: Int,
        limit: Int,
    ): Set<String> =
        withContext(Dispatchers.IO) {
            val url = URL("$source/entriesIds?limit=$limit&offset=$offset")

            (url.openConnection() as HttpURLConnection).run {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 300000
                setRequestProperty("Authorization", token)
                doInput = true

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Json.decodeFromStream(inputStream)
                } else {
                    emptySet()
                }
            }
        }

    @OptIn(ExperimentalSerializationApi::class)
    actual suspend fun getById(id: String): Entry =
        withContext(Dispatchers.IO) {
            val url = URL("$source/entries/$id")

            (url.openConnection() as HttpURLConnection).run {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 300000
                setRequestProperty("Authorization", token)
                doInput = true

                check(responseCode == HttpURLConnection.HTTP_OK) {
                    "Error $responseCode occurred while fetchind entry $id"
                }
                val entry = Json.decodeFromStream<Entry>(inputStream)

                entry.copy(
                    isPersisted = false,
                    attachments = entry.attachments.map { it.copy(content = getAttachmentContent(it.id), isPersisted = false) },
                )
            }
        }

    private fun getAttachmentContent(id: String) =
        Content {
            val url = URL("$source/attachments/$id/content")

            (url.openConnection() as HttpURLConnection).run {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 300000
                setRequestProperty("Authorization", token)

                inputStream
            }
        }
}

actual object Filesystem {
    private lateinit var ctx: Context

    fun init(context: Context) {
        ctx = context.applicationContext
    }

    actual fun getDbFile(dbName: String): File {
        require(dbName.matches("^[a-zA-Z0-9_]+\\.db$".toRegex())) { "Database name must follow pattern %s.db" }

        val file = ctx.getDatabasePath(dbName)
        file.parentFile?.mkdirs()

        return file
    }

    actual fun getTmpFile(prefix: String): File = File.createTempFile(prefix, null, ctx.cacheDir).also { it.deleteOnExit() }
}
