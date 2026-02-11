package me.nasukhov.intrakill.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.nasukhov.intrakill.Security
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

actual object DbImporter {

    actual suspend fun importDatabase(ip: String, port: Int, password: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("http://$ip:$port/dump")

                val token = Security.hash(password)

                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 300000
                    requestMethod = "GET"

                    setRequestProperty("Authorization", token)
                }

                when (connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val targetFile = DbFileResolver.resolve("secured.db")
                        connection.inputStream.use { input ->
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        true
                    }
                    HttpURLConnection.HTTP_FORBIDDEN -> {
                        println("Import failed: Incorrect password/token rejected by server.")
                        false
                    }
                    else -> {
                        println("Import failed: Server returned ${connection.responseCode}")
                        false
                    }
                }
            } catch (e: Exception) {
                false
            }
        }
}

actual object DbFileResolver {
    actual fun resolve(dbName: String): File {
        val dir = File(System.getProperty("user.home"), ".Intrakill")
        dir.mkdirs()

        return File(dir, dbName)
    }
}