package me.nasukhov.intrakill.storage

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ContentTest {
    @Test
    fun `readBytes returns correct content`() {
        val expected = "hello world".toByteArray()
        val content = Content { expected.inputStream() }
        assertContentEquals(expected, content.readBytes())
    }

    @Test
    fun `calculateSize returns correct byte count`() {
        val data = "hello world"
        val content = Content { data.toByteArray().inputStream() }
        assertEquals(data.toByteArray().size.toLong(), content.calculateSize())
    }

    @Test
    fun `calculateSize returns zero for empty content`() {
        val content = Content { byteArrayOf().inputStream() }
        assertEquals(0L, content.calculateSize())
    }

    @Test
    fun `use provides InputStream and reads data correctly`() {
        val data = "test data"
        val content = Content { data.toByteArray().inputStream() }
        var readData = ""
        content.use { stream ->
            readData = stream.readBytes().toString(Charsets.UTF_8)
        }
        assertEquals(data, readData)
    }

    @Test
    fun `readBytes can be called multiple times`() {
        val data = "repeatable".toByteArray()
        val content = Content { data.inputStream() }
        assertContentEquals(data, content.readBytes())
        assertContentEquals(data, content.readBytes())
    }
}
