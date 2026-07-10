package xyz.mdhv.formanalyser.app.ai.providers

import xyz.mdhv.formanalyser.coach.CoachModel
import xyz.mdhv.formanalyser.coach.LlmClient
import xyz.mdhv.formanalyser.coach.Provider

/**
 * Factory that maps a BYOK cloud [Provider] (or a [CoachModel]) to the matching [LlmClient].
 *
 * [apiKeyLookup] is invoked *inside* each completion call, not at construction, so the concrete
 * clients always read the current key from the (encrypted) key store — a rotated or newly-entered
 * key takes effect immediately, and a missing key surfaces as a typed MISSING_API_KEY failure
 * rather than an exception. Only the three hosted clouds are served here; ON_DEVICE and OTHER
 * providers return null (routed elsewhere by the caller).
 */
object CloudLlmClients {

    /** Providers this factory can build a cloud client for. */
    fun handles(provider: Provider): Boolean = when (provider) {
        Provider.ANTHROPIC, Provider.OPENAI, Provider.GOOGLE -> true
        Provider.ON_DEVICE, Provider.OTHER -> false
    }

    /**
     * Build the client for [provider], or null if this factory does not serve it (on-device / other).
     * [apiKeyLookup] returns the BYOK key for a provider, or null/blank if none is stored.
     */
    fun forProvider(provider: Provider, apiKeyLookup: (Provider) -> String?): LlmClient? =
        when (provider) {
            Provider.ANTHROPIC -> AnthropicClient { apiKeyLookup(Provider.ANTHROPIC) }
            Provider.OPENAI -> OpenAiClient { apiKeyLookup(Provider.OPENAI) }
            Provider.GOOGLE -> GoogleClient { apiKeyLookup(Provider.GOOGLE) }
            Provider.ON_DEVICE, Provider.OTHER -> null
        }

    /** Convenience: the client for a model's provider, or null for non-cloud models. */
    fun forModel(model: CoachModel, apiKeyLookup: (Provider) -> String?): LlmClient? =
        forProvider(model.provider, apiKeyLookup)
}
