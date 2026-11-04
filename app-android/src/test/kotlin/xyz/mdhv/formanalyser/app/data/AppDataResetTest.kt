package xyz.mdhv.formanalyser.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.mdhv.formanalyser.app.ai.AiSettings
import xyz.mdhv.formanalyser.app.exchange.AndroidKeyProvider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppDataResetTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `full wipe erases database files caches preferences and signing identity`() = runBlocking {
        val database =
            withContext(Dispatchers.IO) {
                AppDatabase.get(context).also { it.clearAllTables() }
            }
        withContext(Dispatchers.IO) {
            database.athleteDao().upsert(AthleteEntity("wipe-athlete", "Wipe me", 70.0))
        }

        val preferences = AppPrefs(context)
        preferences.setOnboarded(true)
        preferences.setThemeMode("DARK")
        preferences.setKeepRawVideo(true)
        val aiSettings = AiSettings(context)
        aiSettings.setSelectedModelId("wipe-test-model")
        aiSettings.setMedicalGrantDefault(true)
        aiSettings.setKeepPrivate(false)
        aiSettings.setOnDeviceModelPath("/private/model.task")

        val privateDirectories = listOf("vault", "on-device-models", "db-recovery")
        privateDirectories.forEach { name ->
            File(context.filesDir, "$name/wipe-marker").apply {
                parentFile?.mkdirs()
                writeText("sensitive")
            }
        }
        val captureMarkers =
            listOfNotNull(context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES), context.filesDir)
                .map { root ->
                    File(root, "captures/wipe-clip.mp4").apply {
                        parentFile?.mkdirs()
                        writeText("raw video")
                    }
                }
        val cachedExport = File(context.cacheDir, ".croc-export-wipe.partial").apply {
            parentFile?.mkdirs()
            writeText("private export")
        }
        val preferenceNames =
            listOf(
                "crocodyl_byok_keys",
                "crocodyl_byok_prefs",
                "crocodyl_vault_prefs",
                "crocodyl_db_recovery",
            )
        preferenceNames.forEach { name ->
            assertTrue(
                context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
                    .putString("wipe-marker", "secret")
                    .commit(),
            )
        }
        DbRecovery.markReset(context, "recovery-backup")

        var deletedAliases: Set<String>? = null
        withContext(Dispatchers.IO) { AppDataReset.wipe(context) { deletedAliases = it } }

        assertFalse(context.getDatabasePath("form-analyser.db").exists())
        val freshDatabase = withContext(Dispatchers.IO) { AppDatabase.get(context) }
        assertNull(withContext(Dispatchers.IO) { freshDatabase.athleteDao().firstOrNull() })
        assertFalse(preferences.onboarded.first())
        assertEquals("SYSTEM", preferences.themeMode.first())
        assertFalse(preferences.keepRawVideo.first())
        assertEquals(AiSettings.DEFAULT_MODEL_ID, aiSettings.selectedModelId.first())
        assertFalse(aiSettings.medicalGrantDefault.first())
        assertTrue(aiSettings.keepPrivate.first())
        assertNull(aiSettings.onDeviceModelPath.first())
        privateDirectories.forEach { assertFalse(File(context.filesDir, it).exists()) }
        captureMarkers.forEach { assertFalse(it.exists()) }
        assertFalse(cachedExport.exists())
        preferenceNames.forEach { name ->
            assertTrue(context.getSharedPreferences(name, Context.MODE_PRIVATE).all.isEmpty())
        }
        assertNull(DbRecovery.pendingNotice(context))
        assertEquals(
            setOf(
                "crocodyl_byok_master",
                "crocodyl_vault_master",
                AndroidKeyProvider.ALIAS,
            ),
            deletedAliases,
        )
    }
}
