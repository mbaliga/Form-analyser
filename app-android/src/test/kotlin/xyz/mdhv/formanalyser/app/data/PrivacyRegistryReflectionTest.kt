package xyz.mdhv.formanalyser.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import xyz.mdhv.formanalyser.wellness.PrivacyRegistry

/**
 * The reflection-based half of the completeness guard [PrivacyRegistry] has long promised.
 *
 * `core-wellness`'s own `PrivacyRegistryTest.everyAndroidTableIsClassified` already checks this by
 * regexing `CREATE TABLE` statements out of `AppDatabase.kt`'s migration source text — deliberately,
 * because `core-wellness` is pure-JVM and must not depend on `:app-android`, and that test needs to
 * run in `ci.yml` on every push, where an Android/Robolectric unit test would not (see that test's
 * KDoc). `CROCODYL_BUILD_NOTES.md`'s recon notes flagged the gap this file closes: "the app-layer
 * reflection test reconciles [PrivacyRegistry's logical names] with the actual Room `tableName`s".
 *
 * That reconciliation is NOT done by reading `@Database`/`@Entity` off `AppDatabase::class.java`
 * with `Class.getAnnotation()`, tempting as that looks — both annotations are declared
 * `@Retention(AnnotationRetention.BINARY)` (Java `RetentionPolicy.CLASS`), which the compiler emits
 * as `RuntimeInvisibleAnnotations`. KSP reads them at compile time, from the source/class file, but
 * plain JVM reflection at test runtime cannot see them at all — `getAnnotation()` silently returns
 * `null`. [realTableNames] instead builds a genuine in-memory Room database from this same
 * `AppDatabase` class (the one KSP also compiles) and reads back the table names SQLite actually
 * created — Room's generated `createAllTables()` is what turns `@Entity` into real tables, so this
 * is still asking the compiled schema itself, just through the one channel that is runtime-visible,
 * rather than through a second, independently-maintained table-name list that could silently drift.
 *
 * This runs under Robolectric — like every test in this module — and, unlike the annotation
 * reflection this file used to attempt, DOES exercise real (if in-memory) Room/SQLite machinery:
 * building the database, forcing its creation, and querying `sqlite_master`.
 */
@RunWith(RobolectricTestRunner::class)
class PrivacyRegistryReflectionTest {

    /**
     * athlete/session/shot/rig are [PrivacyRegistry]'s canonical (singular) spec names for the
     * historically-plural Room tables athletes/sessions/shots/rig — see `PrivacyRegistry.kt`'s own
     * KDoc ("the app-layer reflection test reconciles these with the actual Room tableNames and any
     * historical plural names"). Every other [PrivacyRegistry] entry already matches its real Room
     * tableName verbatim. Hoisted to a single class-level map so both tests below reconcile off the
     * same three names rather than two independently-maintained copies that could drift apart.
     */
    private val canonicalToReal =
        mapOf("athlete" to "athletes", "session" to "sessions", "shot" to "shots", "rig" to "rig")

    /**
     * Every table Room actually creates for [AppDatabase], read off a genuine in-memory instance
     * rather than off `@Database`/`@Entity` annotations `Class.getAnnotation()` cannot see (see
     * class KDoc). No migrations are involved — an in-memory database has no prior version to
     * migrate from, so Room calls its generated `createAllTables()` straight from today's `@Entity`
     * definitions, which is exactly the "real Room tableNames" this guard needs.
     */
    private fun realTableNames(): List<String> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            // Force the open now (Room otherwise defers it to first use) — this is what actually
            // runs createAllTables() and populates sqlite_master.
            val supportDb = db.openHelper.writableDatabase
            val tableNames = mutableListOf<String>()
            supportDb.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
                while (cursor.moveToNext()) tableNames.add(cursor.getString(0))
            }
            // sqlite_master also lists bookkeeping tables no @Entity ever declared: SQLite's own
            // (sqlite_sequence, sqlite_stat*, ...) and Room's identity-hash ledger
            // (room_master_table, androidx.room.RoomMasterTable.TABLE_NAME — kept as a literal here
            // rather than a dependency on that internal, @RestrictTo class). Counting either as an
            // unclassified Room table would be a false positive, not a real privacy gap.
            return tableNames.filterNot { it == "room_master_table" || it.startsWith("sqlite_") }
        } finally {
            db.close()
        }
    }

    /**
     * The completeness guard: an unclassified table has no [PrivacyRegistry] entry, so nothing
     * downstream — `core-exchange`'s `ConsentFilter`, `core-coach`'s `Redaction` — has a class to
     * check it against. Whether such a table leaks into a cloud prompt or an export then depends on
     * how each caller happens to treat a null lookup rather than on a decision anyone made, which is
     * exactly the gap [PrivacyRegistry]'s own KDoc calls out.
     */
    @Test
    fun everyRoomEntityHasAPrivacyRegistryEntry() {
        val tableNames = realTableNames()
        assertTrue(
            "AppDatabase declares no entities — the real in-memory database has nothing to check",
            tableNames.isNotEmpty(),
        )

        // realTableNames() returns real Room tableNames (athletes/sessions/shots/...), but
        // PrivacyRegistry.byTable keys athlete/session/shot by their canonical singular spec name
        // (see canonicalToReal's KDoc above). Reconcile real -> canonical before the classOf lookup,
        // the same reconciliation everyClassifiedTableIsARealRoomTable already does in the opposite
        // direction — otherwise those three real names would look unclassified even though
        // PrivacyRegistry does cover them, just under a different spelling.
        val realToCanonical =
            canonicalToReal.entries.associate { (canonical, real) -> real to canonical }
        val unclassified =
            tableNames.filter { table ->
                val canonical = realToCanonical[table] ?: table
                PrivacyRegistry.classOf(canonical) == null
            }
        assertTrue(
            "these Room tables (found in a real in-memory AppDatabase built from its real @Entity " +
                "classes) have no PrivacyRegistry entry, so no export tier or coach redaction rule " +
                "applies to them: $unclassified",
            unclassified.isEmpty(),
        )
    }

    /**
     * A duplicate table name would mean two entities silently collide in SQLite (Room would refuse
     * to compile that, in practice) or, more subtly, that this test's own name-derivation logic
     * mismatched one of them against the wrong [PrivacyRegistry] entry without anyone noticing.
     */
    @Test
    fun noTwoEntitiesClaimTheSameTableName() {
        val tableNames = realTableNames()
        assertEquals(
            "a table name was read twice off two different @Entity classes",
            tableNames.size,
            tableNames.toSet().size,
        )
    }

    /**
     * A regression guard for the reconciliation itself: every logical name
     * `PrivacyRegistryTest.everyAndroidTableIsClassified` already checks via DDL text-scraping in
     * `core-wellness` must also appear among the REAL Room table names read here. If it did not,
     * the two tests would be quietly checking two different sets of tables and neither would notice
     * the other had drifted.
     */
    @Test
    fun everyClassifiedTableIsARealRoomTable() {
        val realTables = realTableNames().toSet()
        val unmatched =
            PrivacyRegistry.byTable.keys.filter { canonical ->
                val real = canonicalToReal[canonical] ?: canonical
                real !in realTables
            }
        assertTrue(
            "PrivacyRegistry classifies these names, but no real Room @Entity produces them: $unmatched",
            unmatched.isEmpty(),
        )
    }
}
