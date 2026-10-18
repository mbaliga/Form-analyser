package xyz.mdhv.formanalyser.wellness

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportRetractionFilterTest {
    @Test
    fun exportRetractionFilterMatchesEveryRoomDeletedAtColumn() {
        val databaseSource = sourceFile("app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/data/AppDatabase.kt")
        val exportSource = sourceFile("app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/domain/ExportViewModel.kt")
        val database = databaseSource.readText()

        val retractableTables =
            Regex("ALTER TABLE `([a-z_]+)` ADD COLUMN `deletedAt` INTEGER")
                .findAll(database)
                .map { it.groupValues[1] }
                .toMutableSet()

        val generatedMigration =
            Regex(
                """listOf\(([^)]*)\)\.forEach\s*\{[^}]*ALTER TABLE `\${'$'}it` ADD COLUMN `deletedAt`""",
                RegexOption.DOT_MATCHES_ALL,
            ).find(database)
        if (generatedMigration != null) {
            Regex("\"([a-z_]+)\"")
                .findAll(generatedMigration.groupValues[1])
                .mapTo(retractableTables) { it.groupValues[1] }
        }

        Regex("CREATE TABLE IF NOT EXISTS `([a-z_]+)` \\(([^\\n]*)")
            .findAll(database)
            .filter { "`deletedAt`" in it.groupValues[2] }
            .mapTo(retractableTables) { it.groupValues[1] }

        assertTrue(retractableTables.isNotEmpty(), "found no Room deletedAt columns in ${databaseSource.path}")

        val export = exportSource.readText()
        val filter =
            Regex("private val RETRACTABLE\\s*:\\s*Set<String>\\s*=\\s*setOf\\(([^)]*)\\)")
                .find(export)
                ?: error("could not find ExportViewModel.RETRACTABLE in ${exportSource.path}")
        val exportedTables =
            Regex("\"([a-z_]+)\"")
                .findAll(filter.groupValues[1])
                .map { it.groupValues[1] }
                .toSet()

        assertEquals(
            retractableTables,
            exportedTables,
            "The export tombstone filter must cover every table with a Room deletedAt column, " +
                "and must not name tables without one.",
        )
    }

    private fun sourceFile(relativePath: String): File =
        sequenceOf("../$relativePath", relativePath)
            .map(::File)
            .firstOrNull { it.isFile }
            ?: error("could not find $relativePath from ${File("").absolutePath}")
}
