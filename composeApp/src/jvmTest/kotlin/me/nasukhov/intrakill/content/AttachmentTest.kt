package me.nasukhov.intrakill.content

import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame

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

class AttachmentMoveTest {
    private fun attachment(pos: Int) =
        Attachment(
            mimeType = MimeTypes.IMAGE_PNG,
            content = Content { byteArrayOf().inputStream() },
            preview = byteArrayOf(),
            size = 0L,
            position = pos,
        )

    @Test
    fun `moveUpwards at position 0 returns same list`() {
        val a0 = attachment(0)
        val a1 = attachment(1)
        val list = listOf(a0, a1)
        val result = list.moveUpwards(a0)
        assertSame(list, result)
    }

    @Test
    fun `moveUpwards swaps attachment with previous`() {
        val a0 = attachment(0)
        val a1 = attachment(1)
        val a2 = attachment(2)
        val list = listOf(a0, a1, a2)

        val result = list.moveUpwards(a1)

        assertEquals(a1.id, result[0].id)
        assertEquals(a0.id, result[1].id)
        assertEquals(a2.id, result[2].id)
        assertEquals(0, result[0].position)
        assertEquals(1, result[1].position)
        assertEquals(2, result[2].position)
    }

    @Test
    fun `moveUpwards of first element in multi-element list is a no-op`() {
        val a0 = attachment(0)
        val a1 = attachment(1)
        val a2 = attachment(2)
        val list = listOf(a0, a1, a2)

        val result = list.moveUpwards(a0)

        assertSame(list, result)
    }

    @Test
    fun `moveDownwards at last position returns same list`() {
        val a0 = attachment(0)
        val a1 = attachment(1)
        val list = listOf(a0, a1)
        val result = list.moveDownwards(a1)
        assertSame(list, result)
    }

    @Test
    fun `moveDownwards swaps attachment with next`() {
        val a0 = attachment(0)
        val a1 = attachment(1)
        val a2 = attachment(2)
        val list = listOf(a0, a1, a2)

        val result = list.moveDownwards(a1)

        assertEquals(a0.id, result[0].id)
        assertEquals(a2.id, result[1].id)
        assertEquals(a1.id, result[2].id)
        assertEquals(1, result[1].position)
        assertEquals(2, result[2].position)
    }

    @Test
    fun `moveDownwards of last element in multi-element list is a no-op`() {
        val a0 = attachment(0)
        val a1 = attachment(1)
        val a2 = attachment(2)
        val list = listOf(a0, a1, a2)

        val result = list.moveDownwards(a2)

        assertSame(list, result)
    }

    @Test
    fun `single element list moveUpwards is a no-op`() {
        val a0 = attachment(0)
        val list = listOf(a0)
        assertSame(list, list.moveUpwards(a0))
    }

    @Test
    fun `single element list moveDownwards is a no-op`() {
        val a0 = attachment(0)
        val list = listOf(a0)
        assertSame(list, list.moveDownwards(a0))
    }

    @Test
    fun `combine adjusts positions correctly`() {
        val a0 = attachment(0)
        val a1 = attachment(1)
        val a2 = attachment(2)
        val list1 = listOf(a0, a1, a2)
        val a3 = attachment(0)
        val a4 = attachment(1)
        val a5 = attachment(2)
        val list2 = listOf(a3, a4, a5)

        val result = list1.combine(list2)

        assertEquals(a0.id, result[0].id)
        assertEquals(a1.id, result[1].id)
        assertEquals(a2.id, result[2].id)
        assertEquals(a3.id, result[3].id)
        assertEquals(a4.id, result[4].id)
        assertEquals(a5.id, result[5].id)
        assertEquals(0, result[0].position)
        assertEquals(1, result[1].position)
        assertEquals(2, result[2].position)
        assertEquals(3, result[3].position)
        assertEquals(4, result[4].position)
        assertEquals(5, result[5].position)
    }
}

class AttachmentMediaKindTest {
    @Test
    fun `image attachment has IMAGE media kind`() {
        val attachment =
            Attachment(
                mimeType = MimeTypes.IMAGE_PNG,
                content = Content { byteArrayOf().inputStream() },
                preview = byteArrayOf(),
                size = 0L,
                position = 0,
            )
        assertEquals(me.nasukhov.intrakill.storage.MediaKind.IMAGE, attachment.mediaKind)
    }

    @Test
    fun `video attachment has VIDEO media kind`() {
        val attachment =
            Attachment(
                mimeType = MimeTypes.VIDEO_MP4,
                content = Content { byteArrayOf().inputStream() },
                preview = byteArrayOf(),
                size = 0L,
                position = 0,
            )
        assertEquals(me.nasukhov.intrakill.storage.MediaKind.VIDEO, attachment.mediaKind)
    }
}
