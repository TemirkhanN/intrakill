package me.nasukhov.intrakill.storage

interface SQLAdapter {
    fun exec(sql: String)

    fun transactional(block: SQLAdapter.() -> Unit)

    // TODO keeping versioning here is wrong and should reside in migration package itself
    // The reason why I stuffed it here is just I don't want to provide layer for fetching data from db
    fun fetchVersion(): Version?
}

data class Version(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int = 0,
) : Comparable<Version> {
    init {
        require(year in 2000..2100)
        require(month in 1..12)
        require(day in 1..31)
        require(hour in 0..23)
        require(minute in 0..59)
    }

    override fun toString(): String = "%04d%02d%02d%02d%02d".format(year, month, day, hour, minute)
    override fun compareTo(other: Version): Int = toString().compareTo(other.toString())

    companion object {
        fun fromString(version: String): Version {
            check(version.length == 12) {
                "Invalid version string length: ${version.length}. Expected 12 digits (YYYYMMDDHHmm)."
            }

            return Version(
                year = version.substring(0, 4).toInt(),
                month = version.substring(4, 6).toInt(),
                day = version.substring(6, 8).toInt(),
                hour = version.substring(8, 10).toInt(),
                minute = version.substring(10, 12).toInt()
            )
        }

        val NONE = Version(2000, 1,1, 0, 0)
    }
}

private interface Migration {
    val version: Version
    fun execute(adapter: SQLAdapter)
}

internal class Migrator {
    private val migrations = listOf(
        InitialMigration(),
        MigrationAddContentSizeInAttachments(),
        MigrationSplitAttachmentContentIntoChunks(),
    ).sortedBy { it.version }

    init {
        check(migrations.size == migrations.associateBy { it.version }.size) {
            "There are duplicate versions in migrations list"
        }
    }

    fun migrate(adapter: SQLAdapter) {
        // Migration's system info required for the entire thing to function
        adapter.exec("""
            CREATE TABLE IF NOT EXISTS `application_metadata` (
                version TEXT NOT NULL
            );
        """.trimIndent())

        val versionSanityCheck = adapter.fetchVersion()
        if (versionSanityCheck == null) {
            adapter.exec("INSERT INTO application_metadata(version) VALUES ('${Version.NONE}')")
        }
        val currentVersion = adapter.fetchVersion()!!

        val newMigrations = migrations.filter { it.version > currentVersion }
        for (migration in newMigrations) {
            adapter.transactional {
                migration.execute(adapter)

                exec("UPDATE application_metadata SET version = ${migration.version}")
            }
        }

        // Rebuild and optimize database. Helps to clean up ghost data after deletions.
        if (!newMigrations.isEmpty()) {
            adapter.exec("VACUUM;")
        }
    }
}

private class InitialMigration : Migration {
    override val version: Version = Version(2026, 2, 25, 17)

    override fun execute(adapter: SQLAdapter) {
        val idLength = 36
        adapter.exec(
            """
            CREATE TABLE IF NOT EXISTS entry (
                id TEXT PRIMARY KEY CHECK(length(id) = $idLength),
                `name` TEXT NOT NULL,
                preview BLOB NOT NULL,
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
            CREATE INDEX IF NOT EXISTS idx_entry_created_at ON entry(created_at);
            CREATE INDEX IF NOT EXISTS idx_entry_name ON entry(`name`);
            """
        )

        adapter.exec(
        """
            CREATE TABLE IF NOT EXISTS attachment (
                id TEXT PRIMARY KEY CHECK(length(id) = $idLength),
                entry_id TEXT NOT NULL CHECK(length(entry_id) = $idLength),
                content BLOB NOT NULL,
                preview BLOB NOT NULL,
                mime_type TEXT NOT NULL CHECK(length(mime_type) > 5),
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                hashsum BLOB NOT NULL,
                FOREIGN KEY (entry_id) REFERENCES entry(id) ON DELETE CASCADE
            );
            CREATE INDEX IF NOT EXISTS idx_attachment_entry ON attachment(entry_id);
            CREATE INDEX IF NOT EXISTS idx_attachment_entry_created ON attachment(entry_id, created_at);
            CREATE INDEX IF NOT EXISTS idx_attachment_hashsum ON attachment(hashsum);
            """
        )

        adapter.exec(
        """
                CREATE TABLE IF NOT EXISTS tags (
                    entry_id TEXT NOT NULL CHECK(length(entry_id) = $idLength),
                    tag TEXT NOT NULL CHECK(length(tag) <= 32),
                    PRIMARY KEY (entry_id, tag),
                    FOREIGN KEY (entry_id) REFERENCES entry(id) ON DELETE CASCADE
                );
                CREATE INDEX IF NOT EXISTS idx_tags_tag ON tags(tag);
                CREATE INDEX IF NOT EXISTS idx_tags_entry ON tags(entry_id);
            """
        )
    }
}

private class MigrationAddContentSizeInAttachments: Migration {
    override val version: Version = Version(2026, 2, 25, 18)

    override fun execute(adapter: SQLAdapter) {
        adapter.exec("ALTER TABLE attachment ADD COLUMN `size` INTEGER NOT NULL DEFAULT 0;")
        adapter.exec("UPDATE attachment SET `size` = length(content);")
    }
}

private class MigrationSplitAttachmentContentIntoChunks : Migration {
    override val version: Version = Version(2026, 2, 25, 22)

    override fun execute(adapter: SQLAdapter) {
        val idLength = 36
        val chunkSize = MAX_CHUNK_SIZE

        adapter.exec("""
            CREATE TABLE attachment_new (
                id TEXT PRIMARY KEY CHECK(length(id) = $idLength),
                entry_id TEXT NOT NULL CHECK(length(entry_id) = $idLength),
                preview BLOB NOT NULL,
                size INTEGER NOT NULL DEFAULT 0,
                mime_type TEXT NOT NULL CHECK(length(mime_type) > 5),
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                hashsum BLOB NOT NULL,
                FOREIGN KEY (entry_id) REFERENCES entry(id) ON DELETE CASCADE
            )
        """.trimIndent())

        adapter.exec("""
            CREATE TABLE attachment_chunk (
                attachment_id TEXT NOT NULL,
                sequence_number INTEGER NOT NULL,
                data BLOB NOT NULL,
                PRIMARY KEY (attachment_id, sequence_number),
                FOREIGN KEY (attachment_id) REFERENCES attachment_new(id) ON DELETE CASCADE
            )
        """.trimIndent())

        adapter.exec("""
            INSERT INTO attachment_new (id, entry_id, preview, size, mime_type, created_at, hashsum)
            SELECT id, entry_id, preview, size, mime_type, created_at, hashsum FROM attachment
        """.trimIndent())

        adapter.exec("""
            INSERT INTO attachment_chunk (attachment_id, sequence_number, data)
            WITH RECURSIVE splitter(att_id, offset, seq) AS (
                SELECT id, 1, 0 FROM attachment WHERE content IS NOT NULL
                UNION ALL
                SELECT att_id, offset + $chunkSize, seq + 1
                FROM splitter
                WHERE offset + $chunkSize <= (SELECT length(content) FROM attachment WHERE id = att_id)
            )
            SELECT 
                s.att_id, 
                s.seq, 
                substr(a.content, s.offset, $chunkSize)
            FROM splitter s
            JOIN attachment a ON s.att_id = a.id
        """.trimIndent())

        adapter.exec("DROP TABLE attachment")
        adapter.exec("ALTER TABLE attachment_new RENAME TO attachment")

        adapter.exec("CREATE INDEX idx_attachment_entry ON attachment(entry_id)")
        adapter.exec("CREATE INDEX idx_attachment_entry_created ON attachment(entry_id, created_at)")
        adapter.exec("CREATE INDEX idx_attachment_hashsum ON attachment(hashsum)")
        adapter.exec("CREATE INDEX idx_attachment_chunk_id ON attachment_chunk(attachment_id)")
    }
}