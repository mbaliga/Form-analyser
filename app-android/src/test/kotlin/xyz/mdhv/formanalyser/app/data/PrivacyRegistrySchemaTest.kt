package xyz.mdhv.formanalyser.app.data

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.mdhv.formanalyser.wellness.PrivacyRegistry

/**
 * Checks the privacy contract against the schema Room actually generated, including the three
 * historical plural table names. The pure-JVM registry test protects migration DDL; this test
 * protects the entity/schema/export boundary that a source regex cannot fully represent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrivacyRegistrySchemaTest {
    @Test
    fun `every generated Room table has exactly one privacy classification`() {
        val schema =
            listOf(
                    File("schemas/xyz.mdhv.formanalyser.app.data.AppDatabase/8.json"),
                    File("app-android/schemas/xyz.mdhv.formanalyser.app.data.AppDatabase/8.json"),
                )
                .firstOrNull(File::isFile)
        assertTrue("Room schema snapshot is missing", schema != null)

        val entities =
            JSONObject(requireNotNull(schema).readText())
                .getJSONObject("database")
                .getJSONArray("entities")
        val logicalNames = buildSet {
            for (i in 0 until entities.length()) {
                val actual = entities.getJSONObject(i).getString("tableName")
                add(
                    when (actual) {
                        "athletes" -> "athlete"
                        "sessions" -> "session"
                        "shots" -> "shot"
                        else -> actual
                    }
                )
            }
        }

        assertEquals(PrivacyRegistry.byTable.keys, logicalNames)
        logicalNames.forEach { name ->
            assertTrue("$name has no privacy classification", PrivacyRegistry.classOf(name) != null)
        }
    }
}
