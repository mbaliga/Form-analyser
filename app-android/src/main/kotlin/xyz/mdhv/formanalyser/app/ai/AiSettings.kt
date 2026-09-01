package xyz.mdhv.formanalyser.app.ai

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import xyz.mdhv.formanalyser.coach.ModelRegistry

private val Context.aiDataStore by preferencesDataStore(name = "crocodyl_ai_prefs")

/**
 * AI coach preferences (Phase 3 coach). Mirrors [xyz.mdhv.formanalyser.app.data.AppPrefs]: a
 * DataStore-backed object of Flows + suspend setters. Redaction inputs (medicalGrant / keepPrivate)
 * default to the privacy-preserving choice. The selected model defaults to the first BYOK cloud
 * model in the registry — never a free hosted tier, since none exists.
 *
 * API keys are NOT stored here; they live encrypted in [KeyVault]. On-device model weights live on
 * the filesystem; [onDeviceModelPath] records where.
 */
class AiSettings(private val context: Context) {

    private object Keys {
        val SELECTED_MODEL_ID = stringPreferencesKey("selected_model_id")
        val MEDICAL_GRANT_DEFAULT = booleanPreferencesKey("medical_grant_default")
        val KEEP_PRIVATE = booleanPreferencesKey("keep_private")
        val ON_DEVICE_MODEL_PATH = stringPreferencesKey("on_device_model_path")
    }

    val selectedModelId: Flow<String> = context.aiDataStore.data.map {
        it[Keys.SELECTED_MODEL_ID] ?: DEFAULT_MODEL_ID
    }
    val medicalGrantDefault: Flow<Boolean> = context.aiDataStore.data.map {
        it[Keys.MEDICAL_GRANT_DEFAULT] ?: false
    }
    val keepPrivate: Flow<Boolean> = context.aiDataStore.data.map {
        it[Keys.KEEP_PRIVATE] ?: true
    }
    val onDeviceModelPath: Flow<String?> = context.aiDataStore.data.map {
        it[Keys.ON_DEVICE_MODEL_PATH]
    }

    suspend fun setSelectedModelId(v: String) = context.aiDataStore.edit { it[Keys.SELECTED_MODEL_ID] = v }
    suspend fun setMedicalGrantDefault(v: Boolean) = context.aiDataStore.edit { it[Keys.MEDICAL_GRANT_DEFAULT] = v }
    suspend fun setKeepPrivate(v: Boolean) = context.aiDataStore.edit { it[Keys.KEEP_PRIVATE] = v }

    suspend fun setOnDeviceModelPath(v: String?) = context.aiDataStore.edit {
        if (v == null) it.remove(Keys.ON_DEVICE_MODEL_PATH) else it[Keys.ON_DEVICE_MODEL_PATH] = v
    }

    companion object {
        /** First BYOK cloud model in the registry — a stable, always-present default. */
        val DEFAULT_MODEL_ID: String =
            ModelRegistry.cloudModels().firstOrNull()?.id ?: ModelRegistry.models.first().id
    }
}
