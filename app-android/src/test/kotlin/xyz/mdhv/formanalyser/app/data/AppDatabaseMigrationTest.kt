package xyz.mdhv.formanalyser.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Exercises every `MIGRATION_n_(n+1)` in [AppDatabase] against real (if hand-built) schema
 * fixtures, plus the whole v1 -> v9 chain end to end.
 *
 * ## Why these fixtures are hand-built rather than Room's exported schema JSON
 * `AppDatabase` only just turned `exportSchema = true` on (this pass), with
 * `ksp { arg("room.schemaLocation", ...) }` pointed at `app-android/schemas/`. That mechanism
 * writes out the schema for whatever version is CURRENTLY declared (`9`) the next time a real
 * Android/KSP build runs — it cannot retroactively produce `1.json` through `8.json` for versions
 * that were never "current" while export was on, and no such build is possible in this environment
 * (no Android SDK; CI is the first build that will ever run `kspDebugKotlin` here). So
 * `MigrationTestHelper.createDatabase(name, oldVersion)` — which needs exactly those historical
 * JSON files as test assets — has nothing to read for any version before the very first CI run
 * populates `schemas/`, and even then only for `9`.
 *
 * Every fact this file needs about the pre-migration schema is nonetheless already recorded, in the
 * literal DDL each `MIGRATION_n_(n+1)` executes: an `ALTER TABLE x ADD COLUMN y` names a column that
 * must NOT have existed at `x`'s prior version, and a `CREATE TABLE` names one that must not have.
 * [createV1Database] and [openRaw] below replay that same source of truth forward — first as the
 * literal "before" fixture for v1 (the one version with no migration to derive it from: `athletes`/
 * `sessions`/`shots`, the three entities untouched since `MIGRATION_1_2`'s preconditions and
 * `ShotEntity` respectively), then by calling each real `AppDatabase.MIGRATION_n_(n+1)` object's
 * `migrate()` directly against a raw [SupportSQLiteDatabase] — no Room schema bundle involved, so
 * there is no derived-JSON accuracy to get wrong. What Room's own (KSP-generated, freshly compiled
 * by whichever CI run reads this) schema validator DOES get to check is exercised separately, in
 * [fullChain_v1ToV9_opensCleanlyAndPassesRoomsOwnSchemaValidation]: that test hands a v1 fixture to
 * the REAL `Room.databaseBuilder(...).addMigrations(...)`, so if any migration here drifts from what
 * the current `@Entity` definitions expect, Room itself throws — not a copy of Room's judgement this
 * file made up.
 *
 * Once a real CI build has run at least once and `app-android/schemas/.../9.json` exists and is
 * committed, a later pass can grow this file (or add a sibling) using `MigrationTestHelper` and
 * genuine per-version fixtures for `2` through `9` the same way; `1` will always need a hand fixture,
 * since no `MIGRATION_0_1` — and so no schema-export trigger for a "version 1" — exists.
 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        AppDatabase.resetForTests()
    }

    // --- helpers -------------------------------------------------------------------------------

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
    fun fullChain_v1ToV9_opensCleanlyAndPassesRoomsOwnSchemaValidation() {
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

    // --- MIGRATION_8_9: detector provenance, never backfilled -----------------------------------

    @Test
    fun migration8To9_addsDetectorVersionAndNeverBackfillsExistingCandidates() {
        val name = "mig-8-9.db"
        val db =
            openRaw(name) { d ->
                d.execSQL(
                    "CREATE TABLE `score_candidate` (`id` TEXT NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        // A candidate that predates the detector-version column, exactly the case the migration's
        // own KDoc says must stay untouched — inventing a version for it would be a provenance claim
        // no one made, not a record of one.
        db.execSQL("INSERT INTO score_candidate (id, status) VALUES ('cand1', 'PROPOSED')")

        AppDatabase.MIGRATION_8_9.migrate(db)

        db.query("SELECT status, detectorVersion FROM score_candidate WHERE id = 'cand1'", emptyArray()).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("PROPOSED", c.getString(0))
            assertTrue("a pre-existing candidate must not be assigned a detector version it never had", c.isNull(1))
        }
        db.close()
    }
}
