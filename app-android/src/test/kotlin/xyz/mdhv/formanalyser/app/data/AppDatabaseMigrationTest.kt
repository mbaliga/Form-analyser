package xyz.mdhv.formanalyser.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
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

    @After
    fun tearDown() {
        AppDatabase.resetForTests()
    }

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
            db.execSQL(
                "INSERT INTO shots (id, sessionId, athleteId, indexInSession, featuresJson, score, isBaseline) VALUES " +
                    "('shot1', 's1', 'a1', 1, '{}', 9.0, 1), " +
                    "('shot2', 's1', 'a1', 2, '{}', 10.0, 1), " +
                    "('shot3', 's1', 'a1', 3, '{}', 8.0, 1)"
            )

            AppDatabase.MIGRATION_1_2.migrate(db)
            AppDatabase.MIGRATION_2_3.migrate(db)
            AppDatabase.MIGRATION_3_4.migrate(db)
            AppDatabase.MIGRATION_4_5.migrate(db)
            AppDatabase.MIGRATION_5_6.migrate(db)
            AppDatabase.MIGRATION_6_7.migrate(db)
            AppDatabase.MIGRATION_7_8.migrate(db)
            AppDatabase.MIGRATION_8_9.migrate(db)
            AppDatabase.MIGRATION_9_10.migrate(db)
            AppDatabase.MIGRATION_10_11.migrate(db)

            assertEquals("Legacy", scalar(db, "SELECT displayName FROM athletes WHERE id = 'a1'"))
            assertEquals("38.5", scalar(db, "SELECT drawWeightLbs FROM sessions WHERE id = 's1'"))
            assertEquals("rig_a1", scalar(db, "SELECT rigId FROM sessions WHERE id = 's1'"))
            assertEquals("3", scalar(db, "SELECT COUNT(*) FROM shots WHERE sessionId = 's1'"))
            assertEquals("27", scalar(db, "SELECT SUM(score) FROM shots WHERE sessionId = 's1'"))
            assertEquals("RH", scalar(db, "SELECT handedness FROM athletes WHERE id = 'a1'"))
            assertEquals("My bow", scalar(db, "SELECT name FROM rig WHERE id = 'rig_a1'"))

            assertTrue(tableExists(db, "checkin"))
            assertTrue(tableExists(db, "pain_log"))
            assertTrue(tableExists(db, "score_session"))
            assertTrue(tableExists(db, "training_plan"))
            assertTrue(tableExists(db, "score_candidate"))
            assertTrue(tableExists(db, "sport_session"))
            assertTrue(columnExists(db, "sport_session", "deletedAt"))
            assertTrue(tableExists(db, "capture_media"))
            assertTrue(columnExists(db, "shots", "drawStartS"))
            assertTrue(columnExists(db, "shots", "releaseS"))
            assertTrue(columnExists(db, "shots", "captureMediaId"))
            assertTrue(columnExists(db, "sessions", "deletedAt"))
            assertTrue(columnExists(db, "score_session", "deletedAt"))
            assertTrue(columnExists(db, "checkin", "deletedAt"))
            assertTrue(columnExists(db, "pain_log", "deletedAt"))
            assertTrue(columnExists(db, "injury", "deletedAt"))
            assertTrue(columnExists(db, "score_candidate", "detectorVersion"))
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
            assertTrue(DbRecovery.isUsableBackup(context, backupPath, name))
            val backup = File(requireNotNull(backupPath))
            assertTrue(File(backup, "backup.sha256").isFile)
            assertArrayEquals(live.readBytes(), File(backup, name).readBytes())
            assertArrayEquals(wal.readBytes(), File(backup, "$name-wal").readBytes())
            assertArrayEquals(shm.readBytes(), File(backup, "$name-shm").readBytes())

            val outsideRoot = context.filesDir.resolve("not-db-recovery-${UUID.randomUUID()}")
            backup.copyRecursively(outsideRoot)
            assertTrue(!DbRecovery.isUsableBackup(context, outsideRoot.absolutePath, name))

            File(backup, name).appendBytes(byteArrayOf(9))
            assertTrue(!DbRecovery.isUsableBackup(context, backupPath, name))
            outsideRoot.deleteRecursively()
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

    /**
     * Build a v1 database (raw [SQLiteDatabase], not Room) at [name]: the three tables
     * [MIGRATION_1_2][AppDatabase.MIGRATION_1_2] assumes already exist, in the shape they had
     * before any Phase-1 column was added. `shots` is included for completeness even though no
     * migration ever touches it — its schema has been constant since v1.
     */
    private fun createV1Database(name: String): SQLiteDatabase {
        context.deleteDatabase(name)
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null)
        db.execSQL(
            "CREATE TABLE `athletes` (`id` TEXT NOT NULL, `displayName` TEXT NOT NULL, `bodyMassKg` REAL NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE TABLE `sessions` (`id` TEXT NOT NULL, `athleteId` TEXT NOT NULL, `startedAtEpochMs` INTEGER NOT NULL, `drawWeightLbs` REAL NOT NULL, `distanceMeters` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX `index_sessions_athleteId` ON `sessions` (`athleteId`)")
        db.execSQL(
            "CREATE TABLE `shots` (`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `athleteId` TEXT NOT NULL, `indexInSession` INTEGER NOT NULL, `featuresJson` TEXT NOT NULL, `score` REAL, `isBaseline` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX `index_shots_sessionId` ON `shots` (`sessionId`)")
        db.execSQL("CREATE INDEX `index_shots_athleteId` ON `shots` (`athleteId`)")
        db.version = 1
        return db
    }

    /** Open a raw [SupportSQLiteDatabase] whose schema [createDdl] builds, for a migrate()-only test. */
    private fun openRaw(name: String, createDdl: (SupportSQLiteDatabase) -> Unit): SupportSQLiteDatabase {
        context.deleteDatabase(name)
        val factory = FrameworkSQLiteOpenHelperFactory()
        val callback =
            object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) = createDdl(db)

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            }
        val config =
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build()
        return factory.create(config).writableDatabase
    }

    // --- the whole chain, validated by Room itself --------------------------------------------

    /**
     * The strongest check this file can make: build a genuine v1 database, hand it to the SAME
     * `Room.databaseBuilder` + `addMigrations` shape [AppDatabase.build] uses, and force the open
     * (mirroring [AppDatabase.openResilient]'s `db.openHelper.writableDatabase` trick). If any
     * migration in the chain diverges from what today's `@Entity` definitions expect — a column
     * this file forgot, a type Room would not derive, an index left behind — Room throws here with
     * a schema diff, not a hand-rolled assertion pretending to know what Room would say.
     */
    @Test
    fun fullChain_v1ToV11_opensCleanlyAndPassesRoomsOwnSchemaValidation() {
        val name = "full-chain-test.db"
        createV1Database(name).apply {
            execSQL("INSERT INTO athletes (id, displayName, bodyMassKg) VALUES ('a1','Test',70.0)")
            execSQL(
                "INSERT INTO sessions (id, athleteId, startedAtEpochMs, drawWeightLbs, distanceMeters) VALUES ('s1','a1',1000,32.5,70)"
            )
            close()
        }

        val db =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(
                    AppDatabase.MIGRATION_1_2,
                    AppDatabase.MIGRATION_2_3,
                    AppDatabase.MIGRATION_3_4,
                    AppDatabase.MIGRATION_4_5,
                    AppDatabase.MIGRATION_5_6,
                    AppDatabase.MIGRATION_6_7,
                    AppDatabase.MIGRATION_7_8,
                    AppDatabase.MIGRATION_8_9,
                    AppDatabase.MIGRATION_9_10,
                    AppDatabase.MIGRATION_10_11,
                )
                // Test-only: Robolectric runs this test body ON what Room sees as the main thread
                // (there is no separate real UI thread to hop to), so without this Room's own
                // main-thread guard would refuse the very `db.query(...)` calls below that check the
                // migration actually ran. Production's AppDatabase.build() does not call this and is
                // unaffected — it is only ever opened from a background context.
                .allowMainThreadQueries()
                .build()

        // Triggers open + every migration + Room's own post-migration schema validation, exactly
        // as AppDatabase.openResilient does for a real install upgrading across every release.
        db.openHelper.writableDatabase

        // The chain runs data through, not just DDL: MIGRATION_1_2's backfill must have given the
        // pre-existing athlete a rig and repointed their session to it.
        db.query("SELECT rigId FROM sessions WHERE id = 's1'", emptyArray()).use { c ->
            assertTrue(c.moveToFirst())
            assertNotNull("MIGRATION_1_2 should have repointed the session to a backfilled rig", c.getString(0))
        }
        db.close()
    }

    // --- MIGRATION_1_2: the Phase-1 backfill ----------------------------------------------------

    @Test
    fun migration1To2_addsAthleteAndSessionColumnsAndBackfillsOneRigPerAthlete() {
        val name = "mig-1-2.db"
        val db =
            openRaw(name) { d ->
                d.execSQL(
                    "CREATE TABLE `athletes` (`id` TEXT NOT NULL, `displayName` TEXT NOT NULL, `bodyMassKg` REAL NOT NULL, PRIMARY KEY(`id`))"
                )
                d.execSQL(
                    "CREATE TABLE `sessions` (`id` TEXT NOT NULL, `athleteId` TEXT NOT NULL, `startedAtEpochMs` INTEGER NOT NULL, `drawWeightLbs` REAL NOT NULL, `distanceMeters` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        db.execSQL("INSERT INTO athletes (id, displayName, bodyMassKg) VALUES ('a1','Test',70.0)")
        db.execSQL(
            "INSERT INTO sessions (id, athleteId, startedAtEpochMs, drawWeightLbs, distanceMeters) VALUES ('s1','a1',1000,32.5,70)"
        )
        // A second athlete with no sessions at all: the backfill must not choke on a null "most
        // recent session" and must still hand them a rig with no marked poundage.
        db.execSQL("INSERT INTO athletes (id, displayName, bodyMassKg) VALUES ('a2','NoSessions',60.0)")

        AppDatabase.MIGRATION_1_2.migrate(db)

        db.query("SELECT handedness, avatarSeed, drawLengthMm, club, pubkey FROM athletes WHERE id = 'a1'", emptyArray())
            .use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("RH", c.getString(0))
                assertTrue("avatarSeed should be backfilled to a positive value", c.getLong(1) > 0)
                assertTrue("drawLengthMm should be NULL, not defaulted", c.isNull(2))
                assertTrue(c.isNull(3))
                assertTrue(c.isNull(4))
            }

        var rigIdForA1: String? = null
        db.query("SELECT id, active, tuningJson, bowType FROM rig WHERE athleteId = 'a1'", emptyArray()).use { c ->
            assertTrue(c.moveToFirst())
            rigIdForA1 = c.getString(0)
            assertEquals(1, c.getInt(1))
            assertTrue(
                "tuningJson should carry the marked poundage from the athlete's most recent session",
                c.getString(2)?.contains("32.5") == true,
            )
            assertEquals("RECURVE", c.getString(3))
            assertTrue(c.isLast)
        }
        db.query("SELECT rigId FROM sessions WHERE id = 's1'", emptyArray()).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(rigIdForA1, c.getString(0))
        }

        db.query("SELECT tuningJson FROM rig WHERE athleteId = 'a2'", emptyArray()).use { c ->
            assertTrue("an athlete with no sessions still gets a rig", c.moveToFirst())
            assertNull("no session to mark a poundage from means null tuning, not a guessed one", c.getString(0))
        }
        db.close()
    }

    // --- MIGRATION_6_7 / MIGRATION_7_8: retraction columns, never a hard delete -----------------

    @Test
    fun migration6To7_addsNullableDeletedAtWithoutTouchingExistingRows() {
        val name = "mig-6-7.db"
        val db =
            openRaw(name) { d ->
                d.execSQL(
                    "CREATE TABLE `sessions` (`id` TEXT NOT NULL, `athleteId` TEXT NOT NULL, `startedAtEpochMs` INTEGER NOT NULL, `drawWeightLbs` REAL NOT NULL, `distanceMeters` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                d.execSQL(
                    "CREATE TABLE `score_session` (`id` TEXT NOT NULL, `total` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        db.execSQL(
            "INSERT INTO sessions (id, athleteId, startedAtEpochMs, drawWeightLbs, distanceMeters) VALUES ('s1','a1',1000,32.0,70)"
        )
        db.execSQL("INSERT INTO score_session (id, total) VALUES ('sc1', 342)")

        AppDatabase.MIGRATION_6_7.migrate(db)

        db.query("SELECT deletedAt FROM sessions WHERE id = 's1'", emptyArray()).use { c ->
            assertTrue(c.moveToFirst())
            assertTrue("a pre-existing session must not be retracted by the migration itself", c.isNull(0))
        }
        db.query("SELECT total, deletedAt FROM score_session WHERE id = 'sc1'", emptyArray()).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(342, c.getInt(0))
            assertTrue(c.isNull(1))
        }
        db.close()
    }

    @Test
    fun migration7To8_addsDeletedAtToTheThreeRemainingTables() {
        val name = "mig-7-8.db"
        val db =
            openRaw(name) { d ->
                d.execSQL("CREATE TABLE `checkin` (`id` TEXT NOT NULL, PRIMARY KEY(`id`))")
                d.execSQL("CREATE TABLE `pain_log` (`id` TEXT NOT NULL, PRIMARY KEY(`id`))")
                d.execSQL("CREATE TABLE `injury` (`id` TEXT NOT NULL, PRIMARY KEY(`id`))")
            }
        db.execSQL("INSERT INTO checkin (id) VALUES ('c1')")
        db.execSQL("INSERT INTO pain_log (id) VALUES ('p1')")
        db.execSQL("INSERT INTO injury (id) VALUES ('i1')")

        AppDatabase.MIGRATION_7_8.migrate(db)

        for ((table, id) in listOf("checkin" to "c1", "pain_log" to "p1", "injury" to "i1")) {
            db.query("SELECT deletedAt FROM `$table` WHERE id = ?", arrayOf<Any?>(id)).use { c ->
                assertTrue("$table row should survive the migration", c.moveToFirst())
                assertTrue("$table.deletedAt should default to NULL, not 0/false", c.isNull(0))
            }
        }
        db.close()
    }

    // --- MIGRATION_10_11: detector provenance, never backfilled ---------------------------------

    @Test
    fun migration10To11_addsDetectorVersionAndNeverBackfillsExistingCandidates() {
        val name = "mig-10-11.db"
        val db =
            openRaw(name) { d ->
                d.execSQL(
                    "CREATE TABLE `score_candidate` (`id` TEXT NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        // A candidate that predates the detector-version column, exactly the case the migration's
        // own KDoc says must stay untouched -- inventing a version for it would be a provenance claim
        // no one made, not a record of one.
        db.execSQL("INSERT INTO score_candidate (id, status) VALUES ('cand1', 'PROPOSED')")

        AppDatabase.MIGRATION_10_11.migrate(db)

        db.query("SELECT status, detectorVersion FROM score_candidate WHERE id = 'cand1'", emptyArray()).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("PROPOSED", c.getString(0))
            assertTrue("a pre-existing candidate must not be assigned a detector version it never had", c.isNull(1))
        }
        db.close()
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
