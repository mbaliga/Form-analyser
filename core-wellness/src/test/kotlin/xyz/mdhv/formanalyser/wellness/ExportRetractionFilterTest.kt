package xyz.mdhv.formanalyser.wellness

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The export retraction filter must cover every table that can hold a retracted row, and every table
 * whose rows belong to one.
 *
 * `ExportViewModel.tableDumpQuery` appends `WHERE deletedAt IS NULL` for the tables in
 * [RetractionRegistry.parents], and a `NOT EXISTS` predicate for every NOT NULL column that points at
 * one of them. The parent set was once a hand-maintained constant in `ExportViewModel` and it drifted:
 * MIGRATION_7_8 added `deletedAt` to `checkin`, `pain_log` and `injury` without the set being updated,
 * so rows an athlete had retracted were still written into `.crocbak` archives. MIGRATION_8_9 later
 * created `sport_session` with a `deletedAt` column straight from a CREATE TABLE statement. Nothing
 * failed when that happened, which is why this test exists: the set is derived from the migrations and
 * compared, not trusted.
 *
 * The same drift hid a second leak. Retracting a parent leaves its children in place, so the shots of
 * a deleted session, the arrows of a deleted scorecard and the soreness regions of a deleted check-in
 * were exported with it. Room declares no foreign keys, so the only record of who belongs to whom is
 * [RetractionRegistry.refs]; [everyReferenceToARetractableParentIsRegistered] scans the entity sources
 * and fails when a reference column to a retractable parent has no registry row.
 *
 * Reads app-android source rather than depending on :app-android: core-wellness is pure-JVM and
 * runs in ci.yml, where an Android unit test would not. Same skip-if-absent contract as
 * PrivacyRegistryTest so this module stays buildable on its own.
 *
 * Local runs need `--rerun-tasks`: core-wellness's Gradle inputs do not include app-android's
 * sources, so `./gradlew :core-wellness:test` can report UP-TO-DATE after an app-android-only
 * change and quietly not run this guard. CI is unaffected — ci.yml runs `./gradlew test` on a
 * fresh runner with no Gradle cache configured, so it always executes there. If Gradle caching is
 * ever added to CI, this guard must gain app-android's sources as a declared task input or it will
 * start passing vacuously in exactly the case it exists to catch.
 */
class ExportRetractionFilterTest {

    private fun source(vararg candidates: String): File? =
        candidates.asSequence().map(::File).firstOrNull { it.isFile }

    @Test
    fun retractableSetCoversEveryTableWithADeletedAtColumn() {
        val dbSrc =
            source(
                "../app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/data/AppDatabase.kt",
                "app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/data/AppDatabase.kt",
            ) ?: return
        val exportSrc =
            source(
                "../app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/domain/ExportViewModel.kt",
                "app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/domain/ExportViewModel.kt",
            ) ?: return

        val ddl = dbSrc.readText()

        // Shape 1 (MIGRATION_6_7): ALTER TABLE `x` ADD COLUMN `deletedAt` INTEGER
        val direct =
            Regex("ALTER TABLE `([a-z_]+)` ADD COLUMN `deletedAt`")
                .findAll(ddl)
                .map { it.groupValues[1] }
                .toSet()

        // Shape 2 (MIGRATION_7_8): listOf("a", "b", "c").forEach { ... ADD COLUMN `deletedAt` ... }
        val looped =
            Regex("listOf\\(([^)]*)\\)[\\s\\S]{0,200}?ADD COLUMN `deletedAt`")
                .findAll(ddl)
                .flatMap { m ->
                    Regex("\"([a-z_]+)\"").findAll(m.groupValues[1]).map { it.groupValues[1] }
                }
                .toSet()

        // Shape 3 (MIGRATION_8_9): CREATE TABLE IF NOT EXISTS `x` (... `deletedAt` INTEGER ...)
        val created =
            Regex("CREATE TABLE IF NOT EXISTS `([a-z_]+)` \\(([^\\n]*)")
                .findAll(ddl)
                .filter { "`deletedAt`" in it.groupValues[2] }
                .map { it.groupValues[1] }
                .toSet()

        val withDeletedAt = (direct + looped + created).toSortedSet()
        assertEquals(
            setOf("checkin", "document", "injury", "pain_log", "score_session", "sessions", "sport_session"),
            withDeletedAt.toSet(),
            "parsed an unexpected deletedAt table set from ${dbSrc.path}. If a migration changed " +
                "shape, fix this parser and this expectation together — do not weaken either",
        )

        assertEquals(
            withDeletedAt,
            RetractionRegistry.parents.toSortedSet(),
            "RetractionRegistry.parents must name every table carrying a deletedAt column, or " +
                "retracted rows ride out in a .crocbak the athlete believes they cleaned up",
        )

        val exportText = exportSrc.readText()
        assertFalse(
            "RETRACTABLE" in exportText,
            "ExportViewModel must not keep a second, hand-maintained retractable set",
        )
        assertTrue(
            "RetractionRegistry." in exportText,
            "ExportViewModel.tableDumpQuery must read the registry",
        )
    }

    /** One reference column found in an entity source: [column] on [table], nullable or not. */
    private data class Found(val table: String, val column: String, val nullable: Boolean)

    /**
     * Every `val xxxId: String` / `String?` declared in a Room entity under app/data, with the table
     * it lives in. Each file is cut into chunks at every `@Entity(`, and each chunk stops where the
     * next class, interface or object starts, so a projection class declared after the last entity of
     * a file is not read as part of that entity.
     */
    private fun entityIdColumns(dataDir: File): List<Found> {
        val found = ArrayList<Found>()
        val tableName = Regex("tableName\\s*=\\s*\"([a-z_]+)\"")
        val idColumn = Regex("val\\s+(\\w+Id)\\s*:\\s*String(\\?)?")
        val nextDeclaration = Regex("\\n(?:(?:data|abstract|sealed|open|internal|private)\\s+)*(?:class|interface|object)\\s")
        val files = dataDir.listFiles { f -> f.isFile && f.name.endsWith(".kt") }.orEmpty().sortedBy { it.name }
        for (file in files) {
            val chunks = file.readText().split("@Entity(").drop(1)
            for (chunk in chunks) {
                val table = tableName.find(chunk)?.groupValues?.get(1) ?: continue
                // Skip the entity's own declaration, then cut at whatever class follows it.
                val ownDeclaration = Regex("(?:data\\s+)?class\\s+\\w+").find(chunk)
                val body =
                    if (ownDeclaration == null) chunk
                    else {
                        val rest = chunk.substring(ownDeclaration.range.last + 1)
                        val cut = nextDeclaration.find(rest)?.range?.first ?: rest.length
                        chunk.substring(0, ownDeclaration.range.last + 1) + rest.substring(0, cut)
                    }
                idColumn.findAll(body).forEach { m ->
                    found += Found(table, m.groupValues[1], nullable = m.groupValues[2].isNotEmpty())
                }
            }
        }
        return found
    }

    @Test
    fun everyReferenceToARetractableParentIsRegistered() {
        val dataDir =
            sequenceOf(
                    "../app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/data",
                    "app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/data",
                )
                .map(::File)
                .firstOrNull { it.isDirectory } ?: return

        // Longest case-insensitive suffix of the column's base name (`scoreSessionId` -> `scoreSession`)
        // among the parents' suffixes: `scoreSession` must win over `session`.
        val suffixes = RetractionRegistry.parentRefSuffix.entries.sortedByDescending { it.value.length }
        val discovered =
            entityIdColumns(dataDir)
                .mapNotNull { f ->
                    val base = f.column.removeSuffix("Id")
                    val parent =
                        suffixes.firstOrNull { base.endsWith(it.value, ignoreCase = true) }?.key
                            ?: return@mapNotNull null
                    RetractionRegistry.Ref(f.table, f.column, parent, owned = !f.nullable)
                }
                .toSet()

        val registered = RetractionRegistry.refs.toSet()
        assertEquals(
            emptySet(),
            discovered - registered,
            "an entity column points at a retractable parent but has no RetractionRegistry.refs row " +
                "(or is registered with the wrong parent / owned flag). Add it: NOT NULL means the child " +
                "is hidden with its parent, nullable means it is a link and still exports",
        )
        assertEquals(
            emptySet(),
            registered - discovered,
            "RetractionRegistry.refs names a column that no entity declares; remove or fix the row",
        )

        // The registry is also pinned to the thirteen references decided on, so that the scan above
        // cannot quietly agree with a registry that lost a row at the same moment the scan lost it.
        val expected =
            setOf(
                "shots.sessionId>sessions:owned",
                "session_context.sessionId>sessions:owned",
                "capture_media.sessionId>sessions:owned",
                "score_arrow.scoreSessionId>score_session:owned",
                "score_opponent_end.scoreSessionId>score_session:owned",
                "score_candidate.scoreSessionId>score_session:owned",
                "observer_score_event.scoreSessionId>score_session:owned",
                "soreness.checkinId>checkin:owned",
                "document.injuryId>injury:link",
                "pain_log.injuryId>injury:link",
                "score_session.linkedFormSessionId>sessions:link",
                "sessions.preCheckinId>checkin:link",
                "sessions.postCheckinId>checkin:link",
            )
        assertEquals(
            expected,
            discovered.map { "${it.table}.${it.column}>${it.parent}:${if (it.owned) "owned" else "link"}" }.toSet(),
            "the discovered reference set changed; if a table or column was added or renamed on " +
                "purpose, update RetractionRegistry.refs and this expectation together",
        )
    }

    @Test
    fun ownedRefsAreOnlyPrivateOrExportable() {
        // capture_media is the one owned child that is PRIVATE (raw video, never exported). Every
        // other owned child must be a table the export can actually write, i.e. not PRIVATE, so that
        // its NOT EXISTS predicate is what keeps it clean. Registry names are SQL names; the three
        // incumbent plural tables map to their logical PrivacyRegistry names.
        val logical = mapOf("sessions" to "session", "shots" to "shot")
        val ownedTables = RetractionRegistry.refs.filter { it.owned }.map { it.table }.toSet()
        val privateOwned =
            ownedTables.filter { PrivacyRegistry.classOf(logical[it] ?: it) == PrivacyClass.PRIVATE }
        assertEquals(
            listOf("capture_media"),
            privateOwned.sorted(),
            "capture_media is the only owned child that never leaves the device",
        )
    }
}
