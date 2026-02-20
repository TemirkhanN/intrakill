package me.nasukhov.intrakill.storage

import java.io.File

// TODO rename into FileResolver and adjust usages accordingly
expect object DbFileResolver {
    fun resolve(dbName: String): File
}

expect object DbImporter {
    /**
     * Imports a remote database and saves it locally.
     * Returns true if file was successfully downloaded.
     */
    suspend fun importDatabase(ip: String, port: Int = 8080, password: String): Boolean
}
