package me.nasukhov.intrakill.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class VersionTest {

    @Test
    fun toStringFormatsVersionWithLeadingZeros() {
        val version = Version(2026, 1, 5, 9, 3)
        assertEquals("202601050903", version.toString())
    }

    @Test
    fun toStringFormatsVersionWithAllTwoDigitFields() {
        val version = Version(2026, 12, 31, 23, 59)
        assertEquals("202612312359", version.toString())
    }

    @Test
    fun fromStringParsesVersionStringCorrectly() {
        val version = Version.fromString("202603051430")
        assertEquals(2026, version.year)
        assertEquals(3, version.month)
        assertEquals(5, version.day)
        assertEquals(14, version.hour)
        assertEquals(30, version.minute)
    }

    @Test
    fun fromStringIsInverseOfToString() {
        val original = Version(2026, 3, 5, 14, 30)
        val roundTripped = Version.fromString(original.toString())
        assertEquals(original, roundTripped)
    }

    @Test
    fun fromStringThrowsForStringShorterThan12Chars() {
        assertFails { Version.fromString("202603051") }
    }

    @Test
    fun fromStringThrowsForStringLongerThan12Chars() {
        assertFails { Version.fromString("2026030514301") }
    }

    @Test
    fun fromStringThrowsForEmptyString() {
        assertFails { Version.fromString("") }
    }

    @Test
    fun compareToEarlierVersionIsLessThanLater() {
        val earlier = Version(2026, 1, 1, 0)
        val later = Version(2026, 6, 1, 0)
        assertTrue(earlier < later)
    }

    @Test
    fun compareToSameVersionsAreEqual() {
        val v1 = Version(2026, 3, 5, 12, 0)
        val v2 = Version(2026, 3, 5, 12, 0)
        assertEquals(0, v1.compareTo(v2))
    }

    @Test
    fun compareToLaterVersionIsGreaterThanEarlier() {
        val earlier = Version(2025, 12, 31, 23, 59)
        val later = Version(2026, 1, 1, 0, 0)
        assertTrue(later > earlier)
    }

    @Test
    fun noneConstantIsMinimumVersion() {
        assertEquals(Version(2000, 1, 1, 0, 0), Version.NONE)
    }

    @Test
    fun noneIsLessThanAnyRealMigrationVersion() {
        val realVersion = Version(2026, 1, 1, 0)
        assertTrue(Version.NONE < realVersion)
    }

    @Test
    fun initThrowsForYearBelow2000() {
        assertFails { Version(1999, 1, 1, 0) }
    }

    @Test
    fun initThrowsForYearAbove2100() {
        assertFails { Version(2101, 1, 1, 0) }
    }

    @Test
    fun initThrowsForMonth0() {
        assertFails { Version(2026, 0, 1, 0) }
    }

    @Test
    fun initThrowsForMonth13() {
        assertFails { Version(2026, 13, 1, 0) }
    }

    @Test
    fun initThrowsForDay0() {
        assertFails { Version(2026, 3, 0, 0) }
    }

    @Test
    fun initThrowsForDay32() {
        assertFails { Version(2026, 3, 32, 0) }
    }

    @Test
    fun initThrowsForNegativeHour() {
        assertFails { Version(2026, 3, 5, -1) }
    }

    @Test
    fun initThrowsForHour24() {
        assertFails { Version(2026, 3, 5, 24) }
    }

    @Test
    fun initThrowsForNegativeMinute() {
        assertFails { Version(2026, 3, 5, 12, -1) }
    }

    @Test
    fun initThrowsForMinute60() {
        assertFails { Version(2026, 3, 5, 12, 60) }
    }

    @Test
    fun boundaryValuesAreValid() {
        Version(2000, 1, 1, 0, 0)
        Version(2100, 12, 31, 23, 59)
    }
}
