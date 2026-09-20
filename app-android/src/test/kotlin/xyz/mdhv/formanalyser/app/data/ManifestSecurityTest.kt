package xyz.mdhv.formanalyser.app.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ManifestSecurityTest {
    @Test
    fun applicationDisablesBackupAndCleartextTraffic() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:allowBackup=" + '"' + "false" + '"'))
        assertTrue(manifest.contains("android:usesCleartextTraffic=" + '"' + "false" + '"'))
    }

    @Test
    fun fileProviderIsPrivateAndBackupRulesExcludeAppRoot() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("androidx.core.content.FileProvider"))
        assertTrue(manifest.contains("android:exported=" + '"' + "false" + '"'))
        assertTrue(manifest.contains("android:grantUriPermissions=" + '"' + "true" + '"'))

        val backupRules = readXmlResource("backup_rules")
        val extractionRules = readXmlResource("data_extraction_rules")
        assertTrue(backupRules.contains("""domain="root""""))
        assertTrue(backupRules.contains("""path="." """.trim()))
        assertTrue(extractionRules.contains("<cloud-backup>"))
        assertTrue(extractionRules.contains("<device-transfer>"))
        assertTrue(extractionRules.count { it == '<' } >= 5)
    }

    private fun readXmlResource(name: String): String {
        val file =
            when (name) {
                "backup_rules" -> File("src/main/res/xml/backup_rules.xml")
                "data_extraction_rules" -> File("src/main/res/xml/data_extraction_rules.xml")
                else -> error("Unknown XML resource: $name")
            }
        assertTrue("Missing XML source: $name", file.isFile)
        return file.readText()
    }
}
