package me.nasukhov.intrakill.storage

interface SQLAdapter {
    fun exec(sql: String)

    fun transactional(block: SQLAdapter.() -> Unit)

    fun fetchVersion(): Int

    fun setVersion(version: Int)
}

private data class Version(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
) : Comparable<Version> {
    init {
        require(year in 2020..2099)
        require(month in 1..12)
        require(day in 1..31)
        require(hour in 0..23)
    }

    fun toInt(): Int = "%02d%02d%02d%02d".format(year-2000, month, day, hour).toInt()
    override fun toString(): String = "%04d%02d%02d%02d".format(year, month, day, hour)
    override fun compareTo(other: Version): Int = toInt().compareTo(other.toInt())
}

private interface Migration {
    val version: Version
    fun execute(adapter: SQLAdapter)
}

internal class Migrator {
    private val migrations = listOf(
        InitialMigration(),
        MigrationAddContentSizeInAttachments()

    ).sortedBy { it.version }

    init {
        check(migrations.size == migrations.associateBy { it.version }.size) {
            "There are duplicate versions in migrations list"
        }
    }

    fun migrate(adapter: SQLAdapter) {
        val currentVersion = adapter.fetchVersion()
        val newMigrations = migrations.filter { it.version.toInt() > currentVersion }
        for (migration in newMigrations) {
            adapter.transactional { migration.execute(adapter) }
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
                id TEXT PRIMARY KEY CHECK(length(entry_id) = $idLength),
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