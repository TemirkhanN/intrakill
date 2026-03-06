package me.nasukhov.intrakill.storage

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class MediaKindTest {
    @Test
    fun `image jpeg maps to IMAGE`() {
        assertEquals(MediaKind.IMAGE, "image/jpeg".mediaKind())
    }

    @Test
    fun `image png maps to IMAGE`() {
        assertEquals(MediaKind.IMAGE, "image/png".mediaKind())
    }

    @Test
    fun `image webp maps to IMAGE`() {
        assertEquals(MediaKind.IMAGE, "image/webp".mediaKind())
    }

    @Test
    fun `image gif maps to GIF`() {
        assertEquals(MediaKind.GIF, "image/gif".mediaKind())
    }

    @Test
    fun `video mp4 maps to VIDEO`() {
        assertEquals(MediaKind.VIDEO, "video/mp4".mediaKind())
    }

    @Test
    fun `video webm maps to VIDEO`() {
        assertEquals(MediaKind.VIDEO, "video/webm".mediaKind())
    }

    @Test
    fun `unsupported mime type throws`() {
        assertFails { "application/pdf".mediaKind() }
    }

    @Test
    fun `text plain throws`() {
        assertFails { "text/plain".mediaKind() }
    }

    @Test
    fun `empty string throws`() {
        assertFails { "".mediaKind() }
    }
}
