package me.nasukhov.intrakill.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MigratorTest {

    /**
     * A fake SQLAdapter that tracks executed SQL and simulates version state
     * by interpreting INSERT/UPDATE statements targeting application_metadata.
     */
    private class FakeSQLAdapter(initialVersion: Version? = null) : SQLAdapter {
        val executedSql = mutableListOf<String>()
        private var version: Version? = initialVersion

        override fun exec(sql: String) {
            val trimmed = sql.trim()
            executedSql.add(trimmed)
            when {
                trimmed.startsWith("INSERT INTO application_metadata") -> {
                    if (version == null) version = Version.NONE
                }
                trimmed.startsWith("UPDATE application_metadata SET version =") -> {
                    val versionStr = trimmed.substringAfter("version = ").trim()
                    version = Version.fromString(versionStr)
                }
            }
        }

        override fun transactional(block: SQLAdapter.() -> Unit) {
            block()
        }

        override fun fetchVersion(): Version? = version
    }

    @Test
    fun migrateCreatesApplicationMetadataTableOnFreshDatabase() {
        val adapter = FakeSQLAdapter()
        Migrator().migrate(adapter)
        assertTrue(adapter.executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS `application_metadata`") })
    }

    @Test
    fun migrateInsertsNoneVersionWhenDatabaseIsFresh() {
        val adapter = FakeSQLAdapter()
        Migrator().migrate(adapter)
        assertTrue(adapter.executedSql.any { it.startsWith("INSERT INTO application_metadata") })
    }

    @Test
    fun migrateDoesNotInsertVersionRowWhenMetadataAlreadyExists() {
        val adapter = FakeSQLAdapter(initialVersion = Version.NONE)
        Migrator().migrate(adapter)
        assertTrue(adapter.executedSql.none { it.startsWith("INSERT INTO application_metadata") })
    }

    @Test
    fun migrateAppliesAll4MigrationsOnFreshDatabase() {
        val adapter = FakeSQLAdapter()
        Migrator().migrate(adapter)
        val updateCount = adapter.executedSql.count {
            it.startsWith("UPDATE application_metadata SET version =")
        }
        assertEquals(4, updateCount)
    }

    @Test
    fun migrateRunsVacuumBecauseSplitMigrationNeedsCleanup() {
        val adapter = FakeSQLAdapter()
        Migrator().migrate(adapter)
        assertTrue(adapter.executedSql.any { it == "VACUUM;" })
    }

    @Test
    fun migrateSkipsAllMigrationsWhenAlreadyAtLatestVersion() {
        val latestVersion = Version(2026, 3, 1, 1, 6)
        val adapter = FakeSQLAdapter(initialVersion = latestVersion)
        Migrator().migrate(adapter)
        val updateCount = adapter.executedSql.count {
            it.startsWith("UPDATE application_metadata SET version =")
        }
        assertEquals(0, updateCount)
    }

    @Test
    fun migrateSkipsAppliedMigrationsButRunsNewerOnes() {
        val afterFirstMigration = Version(2026, 2, 25, 17)
        val adapter = FakeSQLAdapter(initialVersion = afterFirstMigration)
        Migrator().migrate(adapter)
        val updateCount = adapter.executedSql.count {
            it.startsWith("UPDATE application_metadata SET version =")
        }
        assertEquals(3, updateCount)
    }

    @Test
    fun migrateDoesNotRunVacuumWhenNoCleanupMigrationIsApplied() {
        val afterCleanupMigration = Version(2026, 2, 25, 22)
        val adapter = FakeSQLAdapter(initialVersion = afterCleanupMigration)
        Migrator().migrate(adapter)
        assertTrue(adapter.executedSql.none { it == "VACUUM;" })
    }
}
