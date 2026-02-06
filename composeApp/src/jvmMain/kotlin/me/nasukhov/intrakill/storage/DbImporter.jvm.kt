package me.nasukhov.intrakill.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

actual object DbImporter {

    actual suspend fun importDatabase(ip: String, port: Int, password: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("http://$ip:$port/db")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 15000
                connection.requestMethod = "GET"

                if (connection.responseCode != 200) return@withContext false

                val input = connection.inputStream
                val targetFile = DbFileResolver.resolve("secured.db")
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }

                true
            } catch (e: Exception) {
                e.printStackTrace()
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