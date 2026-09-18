package xyz.mdhv.formanalyser.wellness

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The export retraction filter must cover every table that can hold a retracted row.
 *
 * `ExportViewModel.dumpTable` appends `WHERE deletedAt IS NULL` only for tables named in its
 * `RETRACTABLE` set. That set was hand-maintained and it drifted: MIGRATION_7_8 added `deletedAt`
 * to `checkin`, `pain_log` and `injury` without the set being updated, so rows an athlete had
 * retracted were still written into `.crocbak` archives. Nothing failed when that happened, which
 * is why this test exists — the set is now derived from the migrations and compared, not trusted.
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

        val withDeletedAt = (direct + looped).toSortedSet()
        assertEquals(
            setOf("checkin", "injury", "pain_log", "score_session", "sessions"),
            withDeletedAt.toSet(),
            "parsed an unexpected deletedAt table set from ${dbSrc.path}. If a migration changed " +
                "shape, fix this parser and this expectation together — do not weaken either",
        )

        val decl =
            Regex("private val RETRACTABLE: Set<String> =\\s*setOf\\(([^)]*)\\)")
                .find(exportSrc.readText())
        assertTrue(decl != null, "no RETRACTABLE declaration found in ${exportSrc.path}")

        val retractable =
            Regex("\"([a-z_]+)\"")
                .findAll(decl!!.groupValues[1])
                .map { it.groupValues[1] }
                .toSortedSet()

        assertEquals(
            withDeletedAt,
            retractable,
            "ExportViewModel.RETRACTABLE must name every table carrying a deletedAt column, or " +
                "retracted rows ride out in a .crocbak the athlete believes they cleaned up",
        )
    }
}
