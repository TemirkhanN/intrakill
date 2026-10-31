package me.nasukhov.intrakill.storage

import java.io.File

interface Filesystem {
    fun getDbFile(dbName: String): File

    fun getTmpFile(prefix: String): File
}
