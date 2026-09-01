package xyz.mdhv.formanalyser.wellness

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrivacyRegistryTest {
    @Test
    fun privateTablesAreExactlyCycleMoodLifeEvent() {
        assertEquals(
            setOf("mood_entry", "life_event", "cycle_entry"),
            PrivacyRegistry.privateTables(),
        )
    }

    @Test
    fun medicationIsMedical() {
        assertEquals(PrivacyClass.MEDICAL, PrivacyRegistry.classOf("medication_entry"))
    }

    @Test
    fun coreTablesShareable() {
        listOf("athlete", "session", "shot", "rig", "checkin", "soreness").forEach {
            assertEquals(
                PrivacyClass.SHAREABLE,
                PrivacyRegistry.classOf(it),
                "$it should be shareable",
            )
        }
    }

    @Test
    fun everyRegisteredTableHasAClass() {
        assertTrue(PrivacyRegistry.byTable.values.all { it in PrivacyClass.entries })
    }

    /**
     * Every table the Android database creates must be classified here.
     *
     * PrivacyRegistry's own KDoc has long claimed a test enforced this; none existed, and the v4→v6
     * migrations added ten tables on trust alone. An unclassified table is not a neutral omission —
     * `classOf` returns null for it, so the `core-exchange` consent filter and the coach redactor
     * have no class to check it against, and whether it leaks depends on how each caller treats
     * null rather than on a decision anyone made.
     *
     * Reads the migration DDL rather than reflecting over Room: core-wellness is pure-JVM and must
     * not depend on :app-android, and this runs on every push in ci.yml, where an Android unit test
     * would not. If the file cannot be found the test skips instead of failing, so this module
     * stays buildable on its own.
     */
    @Test
    fun everyAndroidTableIsClassified() {
        val source =
            sequenceOf(
                    "../app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/data/AppDatabase.kt",
                    "app-android/src/main/kotlin/xyz/mdhv/formanalyser/app/data/AppDatabase.kt",
                )
                .map(::File)
                .firstOrNull { it.isFile } ?: return

        val created =
            Regex("CREATE TABLE IF NOT EXISTS `([a-z_]+)`")
                .findAll(source.readText())
                .map { it.groupValues[1] }
                .toSortedSet()
        assertTrue(created.isNotEmpty(), "found no CREATE TABLE statements in ${source.path}")

        val unclassified = created.filter { PrivacyRegistry.classOf(it) == null }
        assertTrue(
            unclassified.isEmpty(),
            "these Room tables have no PrivacyRegistry entry, so no export tier or coach " +
                "redaction rule applies to them: $unclassified",
        )
    }
}
