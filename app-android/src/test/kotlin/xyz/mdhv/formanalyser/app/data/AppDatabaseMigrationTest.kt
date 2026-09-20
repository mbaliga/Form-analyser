package xyz.mdhv.formanalyser.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the real Android SQLite migration DDL without depending on a device or a hand-written
 * Room schema fixture. The old database is the exact V1 shape shipped by the original app; each
 * production migration is then applied in order and the resulting tables/columns are inspected.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `v1 history survives the complete migration chain`() {
        val name = "migration-${UUID.randomUUID()}.db"
        val helper = legacyV1Helper(name)
        try {
            val db = helper.writableDatabase
            db.execSQL("INSERT INTO athletes (id, displayName, bodyMassKg) VALUES ('a1', 'Legacy', 70.0)")
            db.execSQL(
                "INSERT INTO sessions (id, athleteId, startedAtEpochMs, drawWeightLbs, distanceMeters) " +
                    "VALUES ('s1', 'a1', 1234, 38.5, 70)"
            )

            AppDatabase.MIGRATION_1_2.migrate(db)
            AppDatabase.MIGRATION_2_3.migrate(db)
            AppDatabase.MIGRATION_3_4.migrate(db)
            AppDatabase.MIGRATION_4_5.migrate(db)
            AppDatabase.MIGRATION_5_6.migrate(db)
            AppDatabase.MIGRATION_6_7.migrate(db)
            AppDatabase.MIGRATION_7_8.migrate(db)

            assertEquals("Legacy", scalar(db, "SELECT displayName FROM athletes WHERE id = 'a1'"))
            assertEquals("38.5", scalar(db, "SELECT drawWeightLbs FROM sessions WHERE id = 's1'"))
            assertEquals("rig_a1", scalar(db, "SELECT rigId FROM sessions WHERE id = 's1'"))
            assertEquals("RH", scalar(db, "SELECT handedness FROM athletes WHERE id = 'a1'"))
            assertEquals("My bow", scalar(db, "SELECT name FROM rig WHERE id = 'rig_a1'"))

            assertTrue(tableExists(db, "checkin"))
            assertTrue(tableExists(db, "pain_log"))
            assertTrue(tableExists(db, "score_session"))
            assertTrue(tableExists(db, "training_plan"))
            assertTrue(tableExists(db, "score_candidate"))
            assertTrue(columnExists(db, "sessions", "deletedAt"))
            assertTrue(columnExists(db, "score_session", "deletedAt"))
            assertTrue(columnExists(db, "checkin", "deletedAt"))
            assertTrue(columnExists(db, "pain_log", "deletedAt"))
            assertTrue(columnExists(db, "injury", "deletedAt"))
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun `recovery backup is complete and usable before any reset`() {
        val name = "recovery-${UUID.randomUUID()}.db"
        val live = context.getDatabasePath(name).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        val wal = File(live.parentFile, "$name-wal").apply { writeBytes(byteArrayOf(5, 6)) }
        val shm = File(live.parentFile, "$name-shm").apply { writeBytes(byteArrayOf(7, 8)) }
        var backupPath: String? = null
        try {
            backupPath = DbRecovery.backUpBeforeReset(context, name)
            assertNotNull(backupPath)
            assertTrue(DbRecovery.isUsableBackup(backupPath, name))
            val backup = File(requireNotNull(backupPath))
            assertArrayEquals(live.readBytes(), File(backup, name).readBytes())
            assertArrayEquals(wal.readBytes(), File(backup, "$name-wal").readBytes())
            assertArrayEquals(shm.readBytes(), File(backup, "$name-shm").readBytes())
        } finally {
            backupPath?.let { File(it).deleteRecursively() }
            context.deleteDatabase(name)
        }
    }

    @Test
    fun resetNoticeIsDurableUntilExplicitlyDismissed() {
        val backupPath = context.filesDir.resolve("db-recovery/test-backup").absolutePath
        try {
            DbRecovery.dismissNotice(context)
            assertTrue(DbRecovery.pendingNotice(context) == null)

            DbRecovery.markReset(context, backupPath)
            val pending = DbRecovery.pendingNotice(context)
            assertNotNull(pending)
            assertTrue(requireNotNull(pending).resetAtMs > 0L)
            assertEquals(backupPath, pending.backupPath)

            DbRecovery.dismissNotice(context)
            assertTrue(DbRecovery.pendingNotice(context) == null)
        } finally {
            DbRecovery.dismissNotice(context)
        }
    }

    private fun legacyV1Helper(name: String): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                "CREATE TABLE athletes (id TEXT NOT NULL, displayName TEXT NOT NULL, " +
                                    "bodyMassKg REAL NOT NULL, PRIMARY KEY(id))"
                            )
                            db.execSQL(
                                "CREATE TABLE sessions (id TEXT NOT NULL, athleteId TEXT NOT NULL, " +
                                    "startedAtEpochMs INTEGER NOT NULL, drawWeightLbs REAL NOT NULL, " +
                                    "distanceMeters INTEGER NOT NULL, PRIMARY KEY(id))"
                            )
                            db.execSQL("CREATE INDEX index_sessions_athleteId ON sessions (athleteId)")
                            db.execSQL(
                                "CREATE TABLE shots (id TEXT NOT NULL, sessionId TEXT NOT NULL, " +
                                    "athleteId TEXT NOT NULL, indexInSession INTEGER NOT NULL, " +
                                    "featuresJson TEXT NOT NULL, score REAL, isBaseline INTEGER NOT NULL, " +
                                    "PRIMARY KEY(id))"
                            )
                            db.execSQL("CREATE INDEX index_shots_sessionId ON shots (sessionId)")
                            db.execSQL("CREATE INDEX index_shots_athleteId ON shots (athleteId)")
                        }

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                            error("The test database is migrated explicitly")
                    }
                )
                .build()
        )

    private fun scalar(db: SupportSQLiteDatabase, sql: String): String? =
        db.query(sql).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean =
        scalar(db, "SELECT name FROM sqlite_master WHERE type = 'table' AND name = '$table'") != null

    private fun columnExists(db: SupportSQLiteDatabase, table: String, column: String): Boolean =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) if (cursor.getString(nameIndex) == column) return true
            false
        }
}
