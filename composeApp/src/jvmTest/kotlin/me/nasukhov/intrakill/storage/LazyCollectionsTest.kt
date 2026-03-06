package me.nasukhov.intrakill.storage

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LazyListTest {
    @Test
    fun `loader is not called until first access`() {
        var callCount = 0

        @Suppress("UNUSED_VARIABLE")
        val lazyList =
            LazyList {
                callCount++
                listOf("a", "b", "c")
            }
        assertEquals(0, callCount)
    }

    @Test
    fun `loader is called on first size access`() {
        var callCount = 0
        val lazyList =
            LazyList {
                callCount++
                listOf("a", "b", "c")
            }
        @Suppress("UNUSED_EXPRESSION")
        lazyList.size
        assertEquals(1, callCount)
    }

    @Test
    fun `loader is called only once across multiple accesses`() {
        var callCount = 0
        val lazyList =
            LazyList {
                callCount++
                listOf("a", "b", "c")
            }
        lazyList.size
        lazyList.size
        lazyList[0]
        lazyList[2]
        assertEquals(1, callCount)
    }

    @Test
    fun `size returns correct count`() {
        val lazyList = LazyList { listOf(1, 2, 3) }
        assertEquals(3, lazyList.size)
    }

    @Test
    fun `get returns correct element at each index`() {
        val lazyList = LazyList { listOf("x", "y", "z") }
        assertEquals("x", lazyList[0])
        assertEquals("y", lazyList[1])
        assertEquals("z", lazyList[2])
    }

    @Test
    fun `empty list has size zero`() {
        val lazyList = LazyList<String> { emptyList() }
        assertEquals(0, lazyList.size)
    }

    @Test
    fun `iteration returns all elements in order`() {
        val expected = listOf(10, 20, 30)
        val lazyList = LazyList { expected }
        assertEquals(expected, lazyList.toList())
    }
}

class LazySetTest {
    @Test
    fun `loader is not called until first access`() {
        var callCount = 0

        @Suppress("UNUSED_VARIABLE")
        val lazySet =
            LazySet {
                callCount++
                setOf("a", "b", "c")
            }
        assertEquals(0, callCount)
    }

    @Test
    fun `loader is called on first size access`() {
        var callCount = 0
        val lazySet =
            LazySet {
                callCount++
                setOf("a", "b", "c")
            }
        @Suppress("UNUSED_EXPRESSION")
        lazySet.size
        assertEquals(1, callCount)
    }

    @Test
    fun `loader is called only once across multiple accesses`() {
        var callCount = 0
        val lazySet =
            LazySet {
                callCount++
                setOf("a", "b", "c")
            }
        lazySet.size
        lazySet.size
        lazySet.iterator()
        assertEquals(1, callCount)
    }

    @Test
    fun `size returns correct count`() {
        val lazySet = LazySet { setOf(1, 2, 3) }
        assertEquals(3, lazySet.size)
    }

    @Test
    fun `iterator traverses all elements`() {
        val expected = setOf("a", "b", "c")
        val lazySet = LazySet { expected }
        assertEquals(expected, lazySet.toSet())
    }

    @Test
    fun `empty set has size zero`() {
        val lazySet = LazySet<String> { emptySet() }
        assertEquals(0, lazySet.size)
    }

    @Test
    fun `contains works correctly`() {
        val lazySet = LazySet { setOf("apple", "banana") }
        assertTrue(lazySet.contains("apple"))
        assertFalse(lazySet.contains("cherry"))
    }
}

class AsEnumerationTest {
    @Test
    fun `asEnumeration converts non-empty sequence`() {
        val items = listOf(1, 2, 3)
        val enumeration = items.asSequence().asEnumeration()
        val result = mutableListOf<Int>()
        while (enumeration.hasMoreElements()) {
            result.add(enumeration.nextElement())
        }
        assertEquals(items, result)
    }

    @Test
    fun `asEnumeration on empty sequence has no elements`() {
        val enumeration = emptySequence<String>().asEnumeration()
        assertFalse(enumeration.hasMoreElements())
    }

    @Test
    fun `hasMoreElements returns false after all elements consumed`() {
        val enumeration = sequenceOf("only").asEnumeration()
        assertTrue(enumeration.hasMoreElements())
        enumeration.nextElement()
        assertFalse(enumeration.hasMoreElements())
    }

    @Test
    fun `asEnumeration preserves order`() {
        val items = listOf("first", "second", "third")
        val enumeration = items.asSequence().asEnumeration()
        assertEquals("first", enumeration.nextElement())
        assertEquals("second", enumeration.nextElement())
        assertEquals("third", enumeration.nextElement())
    }
}
