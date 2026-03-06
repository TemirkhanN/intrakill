package me.nasukhov.intrakill.storage

import kotlinx.datetime.toLocalDateTime
import me.nasukhov.intrakill.appTimezone
import me.nasukhov.intrakill.content.Attachment
import me.nasukhov.intrakill.content.Content
import me.nasukhov.intrakill.content.Entry
import me.nasukhov.intrakill.content.MimeTypes
import me.nasukhov.intrakill.content.Tag
import me.nasukhov.intrakill.storage.dao.bind
import me.nasukhov.intrakill.storage.dao.placeholders
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.sql.DriverManager
import kotlin.math.min
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecureDatabaseTest {
    private val dbName = "testDatabaseSecured.db"

    private val password = "test-password"

    private val db = SecureDatabase

    private val currentTime = Clock.System.now()

    private val connection by lazy {
        DriverManager.getConnection("jdbc:sqlite:$dbName", null, password)
    }

    @BeforeEach
    fun setUp() {
        deleteDatabase()
        db.switchDb(dbName)
        db.open(password)
    }

    @AfterEach
    fun tearDown() {
        deleteDatabase()
    }

    @Test
    fun `open should create and initialize database on the first run`() {
        deleteDatabase()
        assertTrue(db.open("LiterallyAnyPassword"))
    }

    @Test
    fun `open should reopen database with the valid password`() {
        // Ensure that it's possible to save data into database
        val entry = createEntry()

        assertTrue(db.open(password))

        // Ensure that data is persisting after opening database with valid password
        assertEntryIsStillThere(entry)
    }

    @Test
    fun `open should fail with invalid password`() {
        val entry = createEntry()
        assertFalse(db.open("wrong password"))

        assertFails { assertEntryIsStillThere(entry) }

        assertTrue(db.open(password))
        assertEntryIsStillThere(entry)
    }

    @Test
    fun `test deleteById removes entry and all relevant data`() {
        val entry = createEntry()
        assertEntryIsStillThere(entry)

        db.deleteById(entry.id)

        assertAllDataIsDeleted(entry)
    }

    @Test
    fun `test listTags returns all known tags data`() {
        assertTrue(db.listTags().isEmpty())

        createEntry { it.copy(tags = setOf("tag4", "tag1", "tag one")) }
        createEntry { it.copy(tags = setOf("tag1", "tag one")) }
        createEntry { it.copy(tags = setOf("tag4", "tag four")) }
        createEntry { it.copy(tags = setOf("tag1", "tag four", "tag-five-05")) }

        val expectedTags =
            setOf(
                Tag("tag4", 2),
                Tag("tag1", 3),
                Tag("tag one", 2),
                Tag("tag four", 2),
                Tag("tag-five-05", 1),
            )

        val actualTags = db.listTags()
        assertEquals(actualTags.size, actualTags.size)
        expectedTags.forEach {
            assertContains(actualTags, it, "${it.name}, ${it.frequency} is not in the actual list")
        }
    }

    @Test
    fun `test findEntries in empty db`() {
        val entries = db.findEntries(EntriesFilter(5))
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `test findEntries`() {
        val passedSeconds = { seconds: Int -> currentTime.plus(seconds.seconds).toLocalDateTime(appTimezone) }
        val entry1 = createEntry { it.copy(tags = setOf("tag1", "tag2", "tag3"), createdAt = passedSeconds(0)) }
        val entry2 = createEntry { it.copy(tags = setOf("tag1", "tag3", "tag4"), createdAt = passedSeconds(1)) }
        val entry3 = createEntry { it.copy(tags = setOf("tag5"), createdAt = passedSeconds(2)) }
        val entry4 = createEntry { it.copy(tags = setOf("tag3", "tag6"), createdAt = passedSeconds(3)) }
        val entry5 = createEntry { it.copy(tags = setOf("tag2", "tag6"), createdAt = passedSeconds(4)) }

        // Entries are expected to be returned from the latest to the earliest
        val allExistingEntries = listOf(entry5, entry4, entry3, entry2, entry1)

        mapOf(
            "case1" to Pair(EntriesFilter(limit = 10), allExistingEntries.size),
            "case2" to Pair(EntriesFilter(limit = 10, tags = setOf("tag1")), 2),
            "case3" to Pair(EntriesFilter(limit = 10, tags = setOf("tag1", "tag2")), 1),
            "case4" to Pair(EntriesFilter(limit = 10, tags = setOf("tag3")), 3),
            "case5" to Pair(EntriesFilter(limit = 3), allExistingEntries.size),
            "case6" to Pair(EntriesFilter(limit = 3, tags = setOf("tag1")), 2),
            "case7" to Pair(EntriesFilter(limit = 3, tags = setOf("tag1", "tag2")), 1),
            "case8" to Pair(EntriesFilter(limit = 3, tags = setOf("tag3")), 3),
            "case9" to Pair(EntriesFilter(limit = 3, offset = 2), allExistingEntries.size),
            "case6" to Pair(EntriesFilter(limit = 3, offset = 2, tags = setOf("tag1")), 2),
            "case7" to Pair(EntriesFilter(limit = 3, offset = 2, tags = setOf("tag1", "tag2")), 1),
            "case8" to Pair(EntriesFilter(limit = 3, offset = 2, tags = setOf("tag3")), 3),
            "case10" to Pair(EntriesFilter(limit = 10, offset = 4), allExistingEntries.size),
        ).forEach {
            val (filter, totalEntriesMatchingFilter) = it.value
            assertEntriesEquals(allExistingEntries.select(filter), db.findEntries(filter), it.key)
            assertEquals(totalEntriesMatchingFilter, db.countEntries(filter), it.key)
        }
    }

    private fun assertEntriesEquals(
        expectedEntries: List<Entry>,
        actualEntries: List<Entry>,
        msg: String? = null,
    ) {
        assertEquals(expectedEntries.size, actualEntries.size, msg)

        expectedEntries.forEachIndexed { index, expectedEntry ->
            val storedEntry = actualEntries.getOrNull(index)
            assertNotNull(storedEntry, msg)
            assertEntriesAreEqual(expectedEntry, storedEntry, msg)
        }
    }

    private fun createEntry(modify: (it: Entry) -> Entry = { it }): Entry {
        val newEntry =
            modify(
                Entry(
                    name = "SomeName",
                    preview = "SomeAttachmentPreview".toByteArray(),
                    attachments =
                        listOf(
                            Attachment(
                                mimeType = MimeTypes.IMAGE_PNG,
                                content = Content { "SomeAttachmentContent".byteInputStream() },
                                preview = "SomeAttachmentPreview".toByteArray(),
                                size = "SomeAttachmentContent".toByteArray().size.toLong(),
                                position = 0,
                            ),
                            Attachment(
                                mimeType = MimeTypes.VIDEO_MP4,
                                content = Content { "AnotherAttachmentContent".byteInputStream() },
                                preview = "AnotherAttachmentPreview".toByteArray(),
                                size = "AnotherAttachmentContent".toByteArray().size.toLong(),
                                position = 1,
                            ),
                        ),
                    tags = setOf("tag1", "tag two", "3rd tag"),
                    createdAt = currentTime.toLocalDateTime(appTimezone),
                ),
            )

        return db.saveEntry(newEntry)
    }

    private fun assertEntryIsStillThere(entry: Entry) {
        val storedEntry = db.getById(entry.id)
        assertEntriesAreEqual(entry, storedEntry)
    }

    private fun assertEntriesAreEqual(
        entry1: Entry,
        entry2: Entry,
        msg: String? = null,
    ) {
        assertEquals(entry1.id, entry2.id, msg)
        assertEquals(entry1.tags, entry2.tags, msg)
        assertContentEquals(entry1.preview, entry2.preview, msg)
        assertEquals(entry1.name, entry2.name, msg)
        assertEquals(entry1.attachments.size, entry2.attachments.size, msg)
        entry2.attachments.forEachIndexed { index, it ->
            val expectedAttachment = entry1.attachments[index]
            assertEquals(expectedAttachment.id, it.id, msg)
            assertEquals(expectedAttachment.mimeType, it.mimeType, msg)
            assertContentEquals(expectedAttachment.content.readBytes(), it.content.readBytes(), msg)
            assertContentEquals(expectedAttachment.preview, it.preview, msg)
            assertEquals(expectedAttachment.size, it.size, msg)
            assertEquals(expectedAttachment.position, it.position, msg)
        }
    }

    private fun assertAllDataIsDeleted(entry: Entry) {
        // No entry itself
        connection.prepareStatement("SELECT COUNT(*) FROM entry WHERE id = ?").use { stmt ->
            stmt.setString(1, entry.id)
            val totalRows =
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getInt(1) else 0
                }
            assertEquals(0, totalRows)
        }

        // No tags
        connection.prepareStatement("SELECT COUNT(*) FROM tags WHERE entry_id = ?").use { stmt ->
            stmt.setString(1, entry.id)
            val totalRows =
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getInt(1) else 0
                }
            assertEquals(0, totalRows)
        }

        // No attachments
        val attachmentsIds = entry.attachments.map { it.id }
        connection
            .prepareStatement(
                "SELECT COUNT(*) FROM attachment WHERE (entry_id = ? OR id IN (${attachmentsIds.placeholders()}))",
            ).use { stmt ->
                stmt.setString(1, entry.id)
                attachmentsIds.bind(stmt, 2)
                val totalRows =
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) rs.getInt(1) else 0
                    }
                assertEquals(0, totalRows)
            }

        connection
            .prepareStatement(
                "SELECT COUNT(*) FROM attachment_chunk WHERE attachment_id IN(${attachmentsIds.placeholders()})",
            ).use { stmt ->
                attachmentsIds.bind(stmt)
                val totalRows =
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) rs.getInt(1) else 0
                    }
                assertEquals(0, totalRows)
            }
    }

    private fun deleteDatabase(name: String = dbName) {
        File(name).delete()
    }
}

private fun List<Entry>.select(filter: EntriesFilter): List<Entry> {
    val from = filter.offset
    val filteredByTags = this.filter { it.tags.containsAll(filter.tags) }
    return if (from >= filteredByTags.size || filteredByTags.isEmpty()) {
        emptyList()
    } else {
        val to = min(from + filter.limit - 1, filteredByTags.size - 1)
        filteredByTags.slice(from..to)
    }
}
