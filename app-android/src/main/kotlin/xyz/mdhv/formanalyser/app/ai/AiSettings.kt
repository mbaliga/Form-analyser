package xyz.mdhv.formanalyser.app.ai

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xyz.mdhv.formanalyser.coach.ModelRegistry
import xyz.mdhv.formanalyser.coach.ModelUsageTotals
import xyz.mdhv.formanalyser.coach.UsageLedger
import xyz.mdhv.formanalyser.coach.UsageSnapshot

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
@Singleton
class AiSettings @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val SELECTED_MODEL_ID = stringPreferencesKey("selected_model_id")
        val MEDICAL_GRANT_DEFAULT = booleanPreferencesKey("medical_grant_default")
        val KEEP_PRIVATE = booleanPreferencesKey("keep_private")
        val ON_DEVICE_MODEL_PATH = stringPreferencesKey("on_device_model_path")
        val USAGE_LEDGER_JSON = stringPreferencesKey("usage_ledger_json")
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

    /**
     * The running token/ask ledger (see [xyz.mdhv.formanalyser.coach.UsageLedger]'s KDoc for the full
     * privacy reasoning). Never a Room table: this DataStore string is structurally unreachable by
     * `ConsentFilter`/`.crocbak` export, which both enumerate Room tables, and carries no
     * `PrivacyRegistry` entry — because it holds only model ids, integer counters and a start date,
     * never prompt text, factsheet content, or response text. If it ever becomes a table, the call is
     * **PRIVATE** and a reset must use the nullable-timestamp idiom (`MIGRATION_6_7`/`MIGRATION_7_8`),
     * not `DELETE` — written down here for whoever makes that move.
     *
     * An absent value decodes to an empty ledger with `sinceEpochMs = 0`; the UI must treat that (or
     * an empty [UsageLedger.byModelId]) as "no usage recorded yet", never render it as "since 1970".
     */
    val usageLedger: Flow<UsageLedger> = context.aiDataStore.data.map { readLedger(it[Keys.USAGE_LEDGER_JSON]) }

    /**
     * Fold one ask's usage into the persisted ledger. An atomic DataStore `edit {}` read-modify-write,
     * so concurrent asks (there should never really be more than one, but the coach button offers no
     * hard guard against a fast double-tap) accumulate rather than racing each other's write.
     */
    suspend fun recordUsage(modelId: String, usage: UsageSnapshot) {
        context.aiDataStore.edit { prefs ->
            val current = readLedger(prefs[Keys.USAGE_LEDGER_JSON])
                .let { if (it.byModelId.isEmpty() && it.sinceEpochMs == 0L) UsageLedger.startingNow(System.currentTimeMillis()) else it }
            prefs[Keys.USAGE_LEDGER_JSON] = ledgerJson.encodeToString(UsageLedgerDto.serializer(), current.record(modelId, usage).toDto())
        }
    }

    /**
     * Start a fresh ledger from now. The UI's "Since <date>" label always reflects this, so a reset
     * can never make a fresh, low counter look like it covers more history than it actually does.
     */
    suspend fun resetUsageLedger() {
        context.aiDataStore.edit { prefs ->
            val fresh = UsageLedger.startingNow(System.currentTimeMillis())
            prefs[Keys.USAGE_LEDGER_JSON] = ledgerJson.encodeToString(UsageLedgerDto.serializer(), fresh.toDto())
        }
    }

    private fun readLedger(raw: String?): UsageLedger {
        val dto = raw?.let { runCatching { ledgerJson.decodeFromString(UsageLedgerDto.serializer(), it) }.getOrNull() }
        return dto?.toDomain() ?: UsageLedger(sinceEpochMs = 0L)
    }

    companion object {
        /** First BYOK cloud model in the registry — a stable, always-present default. */
        val DEFAULT_MODEL_ID: String =
            ModelRegistry.cloudModels().firstOrNull()?.id ?: ModelRegistry.models.first().id
    }
}

/**
 * `core-coach` is deliberately serialization-free (see its `build.gradle.kts` note — the
 * `kotlinx-serialization-json` dependency is declared but unused there); the JSON boundary for
 * [UsageLedger] lives here on the Android side instead, via this small mirror DTO pair.
 */
private val ledgerJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
private data class UsageLedgerDto(
    val sinceEpochMs: Long,
    val byModelId: Map<String, ModelUsageTotalsDto> = emptyMap(),
)

@Serializable
private data class ModelUsageTotalsDto(
    val asks: Int = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
)

private fun UsageLedger.toDto(): UsageLedgerDto =
    UsageLedgerDto(
        sinceEpochMs = sinceEpochMs,
        byModelId = byModelId.mapValues { (_, t) -> ModelUsageTotalsDto(t.asks, t.inputTokens, t.outputTokens) },
    )

private fun UsageLedgerDto.toDomain(): UsageLedger =
    UsageLedger(
        sinceEpochMs = sinceEpochMs,
        byModelId = byModelId.mapValues { (_, t) -> ModelUsageTotals(t.asks, t.inputTokens, t.outputTokens) },
    )
