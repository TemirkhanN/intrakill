package me.nasukhov.intrakill.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TagTest {
    @Test
    fun tagCreatedSuccessfullyWithValidName() {
        val tag = Tag("validtag", 5)
        assertEquals("validtag", tag.name)
        assertEquals(5, tag.frequency)
    }

    @Test
    fun tagCreatedSuccessfullyWithNameAtMaxLength() {
        val name = "a".repeat(Tag.MAX_LENGTH)
        val tag = Tag(name, 1)
        assertEquals(name, tag.name)
    }

    @Test
    fun tagThrowsWhenNameExceedsMaxLength() {
        val longName = "a".repeat(Tag.MAX_LENGTH + 1)
        assertFailsWith<IllegalStateException> {
            Tag(longName, 1)
        }
    }

    @Test
    fun maxLengthConstantIs32() {
        assertEquals(32, Tag.MAX_LENGTH)
    }

    @Test
    fun tagWithZeroFrequencyIsValid() {
        val tag = Tag("newtag", 0)
        assertEquals(0, tag.frequency)
    }

    @Test
    fun tagsWithSameNameAndFrequencyAreEqual() {
        val tag1 = Tag("same", 3)
        val tag2 = Tag("same", 3)
        assertEquals(tag1, tag2)
    }
}
