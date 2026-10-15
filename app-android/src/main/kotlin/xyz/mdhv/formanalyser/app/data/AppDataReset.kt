package xyz.mdhv.formanalyser.app.data

import android.content.Context
import java.io.File
import java.security.KeyStore
import kotlinx.coroutines.CancellationException
import xyz.mdhv.formanalyser.app.ai.AiSettings
import xyz.mdhv.formanalyser.app.exchange.AndroidKeyProvider

/**
 * Erases every app-owned athlete-data store for the explicit Settings → Data → Wipe flow.
 * Call from a background dispatcher. Work is best-effort across stores: a failure in one store
 * must not prevent cleanup of the others, and the caller is told if any store could not be erased.
 */
internal object AppDataReset {
    private val sensitiveDirectories = listOf("vault", "on-device-models", "db-recovery")
    private val privatePreferenceFiles =
        listOf(
            "crocodyl_byok_keys",
            "crocodyl_byok_prefs",
            "crocodyl_vault_prefs",
            "crocodyl_db_recovery",
        )
    private val keystoreAliases =
        setOf(
            "crocodyl_byok_master",
            "crocodyl_vault_master",
            AndroidKeyProvider.ALIAS,
        )

    suspend fun wipe(
        context: Context,
        deleteAliases: (Set<String>) -> Unit = ::deleteAndroidKeyAliases,
    ) {
        val app = context.applicationContext
        val failures = mutableListOf<Exception>()

        suspend fun attempt(label: String, action: suspend () -> Unit) {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failures += IllegalStateException("Could not erase $label", error)
            }
        }

        attempt("Room database and SQLite sidecars") { AppDatabase.deleteForUserWipe(app) }
        sensitiveDirectories.forEach { name ->
            attempt("$name files") {
                val directory = File(app.filesDir, name)
                check(directory.deleteRecursively() && !directory.exists())
            }
        }
        attempt("temporary and cached files") {
            val children = app.cacheDir.listFiles() ?: error("Could not list the app cache")
            children.forEach { child ->
                check(child.deleteRecursively() && !child.exists()) { "Could not remove ${child.name}" }
            }
        }
        privatePreferenceFiles.forEach { name ->
            attempt("$name preferences") {
                check(app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit())
            }
        }
        attempt("app preferences") { AppPrefs(app).clearAll() }
        attempt("AI coach preferences") { AiSettings(app).clearAll() }
        attempt("recovery notice") { DbRecovery.dismissNotice(app) }
        attempt("Android Keystore aliases") { deleteAliases(keystoreAliases) }

        if (failures.isNotEmpty()) {
            throw IllegalStateException(
                "The app could not completely erase local data (${failures.size} store(s) failed). Retry the wipe.",
                failures.first(),
            ).also { aggregate -> failures.drop(1).forEach(aggregate::addSuppressed) }
        }
    }

    private fun deleteAndroidKeyAliases(aliases: Set<String>) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        aliases.forEach(keyStore::deleteEntry)
    }
}
