package me.nasukhov.intrakill.storage

import java.io.File

expect object Filesystem {
    fun getDbFile(dbName: String): File

    fun getTmpFile(prefix: String): File
}
