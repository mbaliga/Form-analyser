package xyz.mdhv.formanalyser.app.ai.providers

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import xyz.mdhv.formanalyser.coach.LlmError
import xyz.mdhv.formanalyser.coach.LlmErrorKind
import xyz.mdhv.formanalyser.coach.Provider
import java.net.URLEncoder

/** One model id a provider's own list endpoint reports, with whatever extra it happens to include. */
data class DiscoveredModel(
    val id: String,
    val displayName: String? = null,
    /** Google-only: the provider's own token limit for this model, when it reports one. */
    val inputTokenLimit: Int? = null,
)

/** Where a curated [xyz.mdhv.formanalyser.coach.CoachModel] id stands against a [DiscoveryOutcome]. */
enum class Availability {
    /** The key can currently reach this id. */
    AVAILABLE,
    /** The key's provider account works, but this id isn't in what it listed — could be renamed,
     *  retired, or region/tier-gated. */
    NOT_LISTED_BY_PROVIDER,
    /** The check itself failed (network, bad key, malformed response) — no verdict either way. */
    UNKNOWN,
}

/** Result of one [ModelDiscovery.check] call. */
sealed interface DiscoveryOutcome {
    data class Listed(
        val ids: Set<String>,
        val details: Map<String, DiscoveredModel>,
        val checkedAtEpochMs: Long,
    ) : DiscoveryOutcome

    data class Unavailable(val error: LlmError) : DiscoveryOutcome
}

/**
 * "Refresh the model list" — the honest version of that request.
 *
 * Every BYOK provider here does expose a models-list endpoint, but none of them expose what a
 * picker actually needs — a display name AND a context window AND a price, all three, together, for
 * every id. Anthropic gives id + display name (no context, no price); OpenAI and DeepSeek give bare
 * ids (hundreds of them, for OpenAI — embeddings/TTS/dated snapshots mixed in with chat models); only
 * Google's list is genuinely rich (display name + token limits). None of the four expose a price via
 * API, ever.
 *
 * So what this object does is answer one narrow, honest question per CURATED model:
 * **"can this key reach this id right now?"** It is an entitlement check, not a catalog import:
 *  - It never adds an id to [xyz.mdhv.formanalyser.coach.ModelRegistry]'s picker on its own — that
 *    registry is what makes `ModelRegistry.hasFreeHostedTier()` and the context-window assumptions
 *    testable in `core-coach`; auto-importing an id here would put an unvetted, unpriced,
 *    unknown-context model behind the athlete's key without anyone having decided that's safe.
 *  - It never fetches or infers a price — no provider exposes one via this endpoint or any other.
 *  - It is meant to be triggered manually, from a button the athlete presses — never on a schedule or
 *    on app launch. Local-first means no background call spending the athlete's own API quota without
 *    them asking for it.
 *  - It doubles as the honest API-key test this app otherwise lacks: a 200 means the key works right
 *    now; 401/403 means it doesn't (mapped the same way [HttpJson.errorKindFor] already maps them for
 *    a real coaching call).
 *
 * Pagination: Anthropic's `/v1/models` paginates (`has_more`/`last_id`); this reads only the first
 * page. For the small, curated set of ids this app actually cares about, the default page size
 * comfortably covers them — full pagination support is left as follow-up rather than added blind.
 *
 * Unverified on a device: authored from each provider's documented models-list endpoint shape, not
 * confirmed against a live response (no Android SDK / socket in this environment).
 */
object ModelDiscovery {

    /** Query which model ids [apiKey] can currently reach for [provider]. Blocking; call on IO. */
    fun check(provider: Provider, apiKey: String): DiscoveryOutcome {
        val outcome = when (provider) {
            Provider.ANTHROPIC ->
                HttpJson.getJson(
                    url = "https://api.anthropic.com/v1/models",
                    headers = mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01"),
                )
            Provider.OPENAI ->
                HttpJson.getJson(
                    url = "https://api.openai.com/v1/models",
                    headers = mapOf("Authorization" to "Bearer $apiKey"),
                )
            Provider.GOOGLE ->
                HttpJson.getJson(
                    url = "https://generativelanguage.googleapis.com/v1beta/models?key=" +
                        URLEncoder.encode(apiKey, Charsets.UTF_8.name()),
                    headers = emptyMap(),
                )
            Provider.DEEPSEEK ->
                HttpJson.getJson(
                    url = "https://api.deepseek.com/v1/models",
                    headers = mapOf("Authorization" to "Bearer $apiKey"),
                )
            Provider.ON_DEVICE, Provider.OTHER ->
                return unsupported(provider)
        }

        return when (outcome) {
            is HttpJson.HttpOutcome.Transport ->
                DiscoveryOutcome.Unavailable(LlmError(LlmErrorKind.NETWORK, outcome.cause.message ?: "Network error"))
            is HttpJson.HttpOutcome.HttpError ->
                DiscoveryOutcome.Unavailable(
                    LlmError(HttpJson.errorKindFor(outcome.code), "HTTP ${outcome.code} checking $provider's models")
                )
            is HttpJson.HttpOutcome.Ok -> parse(provider, outcome.body)
        }
    }

    /** Badge logic shared by every settings row: where does [modelId] stand against [outcome]? */
    fun availability(outcome: DiscoveryOutcome, modelId: String): Availability =
        when (outcome) {
            is DiscoveryOutcome.Unavailable -> Availability.UNKNOWN
            is DiscoveryOutcome.Listed ->
                if (modelId in outcome.ids) Availability.AVAILABLE else Availability.NOT_LISTED_BY_PROVIDER
        }

    private fun unsupported(provider: Provider) =
        DiscoveryOutcome.Unavailable(
            LlmError(LlmErrorKind.UNSUPPORTED, "$provider has no model-list endpoint to check")
        )

    private fun parse(provider: Provider, body: String): DiscoveryOutcome {
        val now = System.currentTimeMillis()
        return runCatching {
            when (provider) {
                Provider.ANTHROPIC -> {
                    val decoded = HttpJson.json.decodeFromString(AnthropicModelList.serializer(), body)
                    val details = decoded.data.orEmpty()
                        .associate { it.id to DiscoveredModel(id = it.id, displayName = it.displayName) }
                    DiscoveryOutcome.Listed(details.keys, details, now)
                }
                Provider.OPENAI, Provider.DEEPSEEK -> {
                    val decoded = HttpJson.json.decodeFromString(OpenAiModelList.serializer(), body)
                    val details = decoded.data.orEmpty().associate { it.id to DiscoveredModel(id = it.id) }
                    DiscoveryOutcome.Listed(details.keys, details, now)
                }
                Provider.GOOGLE -> {
                    val decoded = HttpJson.json.decodeFromString(GoogleModelList.serializer(), body)
                    // Gemini's "name" is "models/<id>" -- strip the prefix to compare against our ids.
                    val details = decoded.models.orEmpty()
                        .mapNotNull { m -> m.name?.removePrefix("models/")?.let { id -> id to DiscoveredModel(id, m.displayName, m.inputTokenLimit) } }
                        .toMap()
                    DiscoveryOutcome.Listed(details.keys, details, now)
                }
                Provider.ON_DEVICE, Provider.OTHER -> return unsupported(provider)
            }
        }.getOrElse {
            DiscoveryOutcome.Unavailable(LlmError(LlmErrorKind.PROVIDER_ERROR, "Malformed model list: ${it.message}"))
        }
    }

    // ── wire DTOs (list endpoints only — unrelated to each client's completion DTOs) ──────────────
    @Serializable
    private data class AnthropicModelList(val data: List<AnthropicModel>? = null)

    @Serializable
    private data class AnthropicModel(val id: String, @SerialName("display_name") val displayName: String? = null)

    @Serializable
    private data class OpenAiModelList(val data: List<OpenAiModel>? = null)

    @Serializable
    private data class OpenAiModel(val id: String)

    @Serializable
    private data class GoogleModelList(val models: List<GoogleModel>? = null)

    @Serializable
    private data class GoogleModel(
        val name: String? = null,
        val displayName: String? = null,
        val inputTokenLimit: Int? = null,
    )
}
