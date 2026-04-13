package me.nasukhov.intrakill.storage

import java.io.InputStream

class Content {
    companion object {
        val NONE = Content { "".byteInputStream() }
    }

    private val resolver: () -> InputStream

    private var byteCache: ByteArray? = null

    constructor(resolver: () -> InputStream) {
        this.resolver = resolver
    }

    fun <R> use(handler: (InputStream) -> R) = resolver().use(handler)

    fun readBytes(): ByteArray = byteCache ?: resolver().use { it.readBytes() }.also { byteCache = it }

    fun calculateSize(): Long =
        resolver().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            var read: Int

            while (input.read(buffer).also { read = it } != -1) {
                total += read
            }

            total
        }
}
