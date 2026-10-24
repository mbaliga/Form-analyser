package xyz.mdhv.formanalyser.app.domain

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import xyz.mdhv.formanalyser.app.awaitUntil
import xyz.mdhv.formanalyser.app.data.AppDatabase
import xyz.mdhv.formanalyser.app.data.AthleteEntity
import xyz.mdhv.formanalyser.app.data.MoodEntity
import xyz.mdhv.formanalyser.exchange.CrocbakManifest

/**
 * A `.crocbak` row is a JSON object whose keys become SQL column names. These tests build real
 * archives (zip, manifest, checksum) and push them through the real inspect and merge paths against
 * a real Room database.
 *
 * The attacks are the ones that worked against the old `ContentValues` import, where the framework
 * pasted each key into the INSERT unquoted: a key that closes the column list, opens a SELECT and
 * comments out the rest (copying a PRIVATE diary note into a new athlete row), and a key that turns
 * `INSERT OR IGNORE` into an UPSERT (overwriting an existing athlete). Both must be refused at
 * inspect, before any preview exists, and neither may touch a row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExportViewModelImportInjectionTest {

    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var vm: ExportViewModel
    private var uriCounter = 0

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        AppDatabase.resetForTests()
        app.deleteDatabase("form-analyser.db")
        db = AppDatabase.get(app)
        runBlocking {
            db.athleteDao().upsert(AthleteEntity("a1", "Real Name", 65.0))
            db.wellnessDao().insertMood(MoodEntity("m1", 1_000, 3, "[]", note = PRIVATE_NOTE))
        }
        vm = ExportViewModel(app, db)
    }

    @After
    fun tearDown() {
        AppDatabase.resetForTests()
    }

    // --- archive plumbing -----------------------------------------------------------------------

    private fun athleteRow(id: String, name: String) = buildJsonObject {
        put("id", id)
        put("displayName", name)
        put("bodyMassKg", 70.5)
        put("handedness", "LH")
        put("drawLengthMm", JsonNull)
        put("avatarSeed", 7L)
        put("club", JsonNull)
        put("pubkey", JsonNull)
    }

    private fun sessionRow(id: String, deletedAt: Long?) = buildJsonObject {
        put("id", id)
        put("athleteId", "a1")
        put("startedAtEpochMs", 1_000L)
        put("drawWeightLbs", 38.0)
        put("distanceMeters", 18L)
        put("rigId", JsonNull)
        put("handednessOverride", JsonNull)
        put("preCheckinId", JsonNull)
        put("postCheckinId", JsonNull)
        put("durationAutoS", JsonNull)
        put("durationS", JsonNull)
        put("arrowsActual", JsonNull)
        if (deletedAt == null) put("deletedAt", JsonNull) else put("deletedAt", deletedAt)
    }

    /** A valid unsigned `.crocbak`: manifest + one JSON payload per table, checksummed like the writer. */
    private fun archive(tables: Map<String, JsonArray>): ByteArray {
        val payloads = tables.toSortedMap().mapValues { it.value.toString().toByteArray() }
        val digest = MessageDigest.getInstance("SHA-256")
        payloads.values.forEach(digest::update)
        val manifest =
            CrocbakManifest(
                schemaVersion = CrocbakManifest.CURRENT_SCHEMA_VERSION,
                appVersion = "test",
                createdAtMs = 1L,
                athletePubkeyFingerprint = "test-fingerprint",
                tierName = "SHAREABLE_ONLY",
                includedTables = payloads.keys.toList(),
                contentChecksum = "sha256:" + digest.digest().joinToString("") { "%02x".format(it) },
            )
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.serialize().toByteArray())
            zip.closeEntry()
            payloads.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry("tables/$name.json"))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun offer(tables: Map<String, JsonArray>): Uri {
        val uri = Uri.parse("content://crocodyl.test/archive-${uriCounter++}.crocbak")
        shadowOf(app.contentResolver).registerInputStream(uri, ByteArrayInputStream(archive(tables)))
        return uri
    }

    private fun inspect(tables: Map<String, JsonArray>) {
        vm.inspectImport(offer(tables))
        vm.busy.awaitUntil { !it }
    }

    private fun importInspected() {
        vm.importInspected()
        vm.busy.awaitUntil { !it }
    }

    // --- the hostile archives -------------------------------------------------------------------

    /** The required columns of `athletes`, so the only thing wrong with a hostile row is one key. */
    private fun requiredAthleteColumns(id: String): List<Pair<String, JsonElement>> =
        listOf(
            "id" to JsonPrimitive(id),
            "displayName" to JsonPrimitive("Attacker"),
            "bodyMassKg" to JsonPrimitive(70.5),
            "handedness" to JsonPrimitive("RH"),
            "avatarSeed" to JsonPrimitive(1L),
        )

    /**
     * The old import put each key straight into the INSERT, in `ContentValues`' own iteration order
     * (hash order, not insertion order). The payloads below close the column list, so they only work
     * when the hostile key comes last. Everything after `--` is swallowed by the comment, so pad the
     * key there until `ContentValues` really does put it last.
     */
    private fun hostileKeyLast(realKeys: List<String>, payload: String): String {
        var pad = 0
        while (true) {
            val key = "$payload -- pad$pad"
            val order =
                ContentValues().apply { realKeys.forEach { put(it, "v") }; put(key, "v") }.valueSet().map { it.key }
            if (order.last() == key) return key
            pad++
        }
    }

    private fun hostileArchive(id: String, payload: String): Map<String, JsonArray> {
        val real = requiredAthleteColumns(id)
        val key = hostileKeyLast(real.map { it.first }, payload)
        val row = JsonObject(real.toMap() + (key to JsonPrimitive("")))
        return mapOf("athlete" to buildJsonArray { add(row) })
    }

    /** Closes the column list and copies the PRIVATE mood note into a brand-new athlete's `club`. */
    private fun exfiltrationArchive() =
        hostileArchive(
            "planted",
            "club) SELECT ?,?,?,?,?,(SELECT note FROM mood_entry LIMIT 1) || ?",
        )

    /** Turns INSERT OR IGNORE into an UPSERT that renames the existing athlete. */
    private fun overwriteArchive() =
        hostileArchive(
            "a1",
            "club) VALUES (?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET displayName='Hacked'",
        )

    /** A quote in a column name: the shape that breaks any hand-built statement. */
    private fun quoteArchive(): Map<String, JsonArray> {
        val row = JsonObject(requiredAthleteColumns("q1").toMap() + ("display'Name" to JsonPrimitive("x")))
        return mapOf("athlete" to buildJsonArray { add(row) })
    }

    private val hostileArchives: Map<String, Map<String, JsonArray>>
        get() =
            linkedMapOf(
                "exfiltration" to exfiltrationArchive(),
                "overwrite" to overwriteArchive(),
                "quote" to quoteArchive(),
            )

    private data class AthleteRow(val displayName: String, val handedness: String, val bodyMassKg: Double)

    private fun athlete(id: String): AthleteRow? =
        db.openHelper.readableDatabase
            .query("SELECT displayName, handedness, bodyMassKg FROM athletes WHERE id = ?", arrayOf<Any?>(id))
            .use { c -> if (c.moveToFirst()) AthleteRow(c.getString(0), c.getString(1), c.getDouble(2)) else null }

    private fun assertDatabaseUntouched() {
        assertEquals("Real Name", athlete("a1")?.displayName)
        assertNull("no planted row", athlete("planted"))
        assertNull("no quote row", athlete("q1"))
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM athletes").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("only the seeded athlete exists", 1, c.getInt(0))
        }
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM athletes WHERE club = ?", arrayOf<Any?>(PRIVATE_NOTE)).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("the PRIVATE note was not copied into a shareable table", 0, c.getInt(0))
        }
    }

    @Test
    fun hostileColumnArchiveIsRejectedAtInspect() {
        for ((label, tables) in hostileArchives) {
            inspect(tables)

            assertNull("$label: no preview may exist for a hostile archive", vm.importPreview.value)
            val outcome = vm.importOutcome.value
            assertNotNull("$label: the athlete is told why", outcome)
            assertFalse("$label: $outcome", outcome!!.ok)
            assertTrue("$label: ${outcome.message}", outcome.message.startsWith("Archive rejected"))
        }
        assertDatabaseUntouched()
    }

    @Test
    fun existingRowUnchangedAfterHostileImport() {
        for ((label, tables) in hostileArchives) {
            inspect(tables)
            // A rejected archive leaves nothing to import: this must be a no-op, not a second chance.
            importInspected()

            val outcome = vm.importOutcome.value
            assertNotNull("$label: outcome", outcome)
            assertTrue(
                "$label: importing a rejected archive must not even be attempted; got ${outcome!!.message}",
                outcome.message.startsWith("Archive rejected"),
            )
            assertNull("$label: still no preview", vm.importPreview.value)
        }
        assertDatabaseUntouched()
    }

    // --- legitimate archives still work ---------------------------------------------------------

    @Test
    fun legitimateArchiveStillRoundTrips() {
        val tables =
            mapOf(
                "athlete" to
                    buildJsonArray {
                        add(athleteRow("b1", "Imported Archer"))
                        // Same id as the seeded athlete, different data: existing rows win.
                        add(athleteRow("a1", "Overwritten?"))
                    },
                "session" to
                    buildJsonArray {
                        add(sessionRow("s-live", deletedAt = null))
                        // A tombstone is not history worth importing.
                        add(sessionRow("s-retracted", deletedAt = 123L))
                    },
            )

        inspect(tables)
        val preview = vm.importPreview.value
        assertNotNull("a legitimate archive previews: ${vm.importOutcome.value}", preview)
        assertEquals("b1 and the live session are new, the retracted session is not counted", 2L, preview!!.newRows)
        assertEquals("a1 already exists with different data", 1L, preview.conflictRows)

        importInspected()

        val outcome = vm.importOutcome.value
        assertTrue("import succeeded: ${outcome?.message}", outcome?.ok == true)
        assertTrue(outcome!!.message, outcome.message.contains("Imported 2 new row(s); kept 1 existing row(s)"))
        assertTrue(outcome.message, outcome.message.contains("Skipped 1 retracted row(s)"))
        val imported = athlete("b1")
        assertEquals("Imported Archer", imported?.displayName)
        assertEquals("LH", imported?.handedness)
        assertEquals(70.5, imported?.bodyMassKg ?: 0.0, 0.0)
        assertEquals("Real Name", athlete("a1")?.displayName)
        db.openHelper.readableDatabase.query("SELECT id FROM sessions ORDER BY id").use { c ->
            val ids = buildList { while (c.moveToNext()) add(c.getString(0)) }
            assertEquals(listOf("s-live"), ids)
        }
    }

    // --- values must fit the real column types --------------------------------------------------

    @Test
    fun valuesThatDoNotFitTheColumnTypeRejectTheArchive() {
        val stringInIntegerColumn =
            athleteRow("t1", "Typed").let { JsonObject(it + ("avatarSeed" to JsonPrimitive("seven"))) }
        inspect(mapOf("athlete" to buildJsonArray { add(stringInIntegerColumn) }))
        assertNull(vm.importPreview.value)
        assertTrue(vm.importOutcome.value!!.message, vm.importOutcome.value!!.message.startsWith("Archive rejected"))

        val numberInTextColumn =
            athleteRow("t2", "Typed").let { JsonObject(it + ("displayName" to JsonPrimitive(42L))) }
        inspect(mapOf("athlete" to buildJsonArray { add(numberInTextColumn) }))
        assertNull(vm.importPreview.value)
        assertTrue(vm.importOutcome.value!!.message.startsWith("Archive rejected"))

        val nullInRequiredColumn =
            athleteRow("t3", "Typed").let { JsonObject(it + ("handedness" to JsonNull)) }
        inspect(mapOf("athlete" to buildJsonArray { add(nullInRequiredColumn) }))
        assertNull(vm.importPreview.value)
        assertTrue(vm.importOutcome.value!!.message.startsWith("Archive rejected"))
    }

    @Test
    fun aRowMissingARequiredColumnRejectsTheArchive() {
        val withoutHandedness = JsonObject(athleteRow("m1", "Missing") - "handedness")
        inspect(mapOf("athlete" to buildJsonArray { add(withoutHandedness) }))

        assertNull(vm.importPreview.value)
        val message = vm.importOutcome.value!!.message
        assertTrue(message, message.startsWith("Archive rejected"))
        assertTrue(message, message.contains("handedness"))
    }

    @Test
    fun anOlderArchiveWithOnlyOptionalColumnsMissingIsAccepted() {
        // Older builds knew fewer nullable columns; the required ones are all present.
        val older = JsonObject(athleteRow("old1", "Older") - "club" - "pubkey" - "drawLengthMm")
        inspect(mapOf("athlete" to buildJsonArray { add(older) }))

        assertNotNull(vm.importOutcome.value?.message, vm.importPreview.value)
        importInspected()
        assertTrue(vm.importOutcome.value!!.message, vm.importOutcome.value!!.ok)
        assertEquals("Older", athlete("old1")?.displayName)
    }

    @Test
    fun anArchiveOverTheRowCeilingIsRejectedBeforeAnyRowIsRead() {
        val empty: JsonElement = JsonObject(emptyMap())
        val tooMany = JsonArray(List(ExportViewModel.MAX_TABLE_ROWS + 1) { empty })
        inspect(mapOf("athlete" to tooMany))

        assertNull(vm.importPreview.value)
        val message = vm.importOutcome.value!!.message
        assertTrue(message, message.startsWith("Archive rejected"))
        assertTrue(message, message.contains("too many rows"))
    }

    private companion object {
        const val PRIVATE_NOTE = "private diary entry"
    }
}
