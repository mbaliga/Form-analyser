package xyz.mdhv.formanalyser.app.ai.providers

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import xyz.mdhv.formanalyser.coach.ChatMessage
import xyz.mdhv.formanalyser.coach.CoachModel
import xyz.mdhv.formanalyser.coach.CompletionRequest
import xyz.mdhv.formanalyser.coach.CompletionResponse
import xyz.mdhv.formanalyser.coach.CompletionResult
import xyz.mdhv.formanalyser.coach.LlmClient
import xyz.mdhv.formanalyser.coach.LlmError
import xyz.mdhv.formanalyser.coach.LlmErrorKind
import xyz.mdhv.formanalyser.coach.MessageRole
import xyz.mdhv.formanalyser.coach.Provider

/**
 * BYOK Anthropic Messages API client. The [apiKey] lambda is read fresh on each call so a rotated
 * key takes effect without rebuilding the client; the key is never logged or persisted here.
 *
 * Endpoint: POST https://api.anthropic.com/v1/messages
 * Headers:  x-api-key, anthropic-version: 2023-06-01
 * Body:     { model, max_tokens, temperature, system, messages: [{role, content}] }
 * Parse:    content[0].text
 */
class AnthropicClient(
    private val apiKey: () -> String?,
) : LlmClient {

    override fun supports(model: CoachModel): Boolean = model.provider == Provider.ANTHROPIC

    override fun complete(request: CompletionRequest): CompletionResult {
        if (!supports(request.model)) {
            return fail(LlmErrorKind.UNSUPPORTED, "AnthropicClient cannot serve ${request.model.id}")
        }
        val key = apiKey()?.takeIf { it.isNotBlank() }
            ?: return fail(LlmErrorKind.MISSING_API_KEY, "No Anthropic API key configured")

        // Anthropic carries the system prompt out-of-band; user/assistant turns go in messages.
        val system = request.messages
            .filter { it.role == MessageRole.SYSTEM }
            .joinToString("\n\n") { it.content }
            .ifBlank { null }
        val turns = request.messages
            .filter { it.role != MessageRole.SYSTEM }
            .map { WireMessage(role = it.role.toWire(), content = it.content) }

        val payload = ChatBody(
            model = request.model.id,
            maxTokens = request.maxTokens,
            temperature = request.temperature,
            system = system,
            messages = turns,
        )

        val outcome = HttpJson.postJson(
            url = ENDPOINT,
            headers = mapOf(
                "x-api-key" to key,
                "anthropic-version" to ANTHROPIC_VERSION,
            ),
            body = HttpJson.json.encodeToString(ChatBody.serializer(), payload),
        )

        return when (outcome) {
            is HttpJson.HttpOutcome.Transport ->
                fail(LlmErrorKind.NETWORK, outcome.cause.message ?: "Network error")
            is HttpJson.HttpOutcome.HttpError ->
                fail(HttpJson.errorKindFor(outcome.code), errorMessage(outcome.code, outcome.body))
            is HttpJson.HttpOutcome.Ok -> parseSuccess(request, outcome.body)
        }
    }

    private fun parseSuccess(request: CompletionRequest, body: String): CompletionResult {
        val decoded = runCatching {
            HttpJson.json.decodeFromString(ChatResponse.serializer(), body)
        }.getOrElse {
            return fail(LlmErrorKind.PROVIDER_ERROR, "Malformed Anthropic response: ${it.message}")
        }
        val text = decoded.content
            ?.firstOrNull { it.type == "text" && it.text != null }
            ?.text
            ?: decoded.content?.firstNotNullOfOrNull { it.text }
        if (text == null) {
            if (decoded.stopReason == "content_filter") {
                return fail(LlmErrorKind.CONTENT_FILTERED, "Anthropic filtered the response")
            }
            return fail(LlmErrorKind.PROVIDER_ERROR, "Anthropic response had no text content")
        }
        return CompletionResult.Success(
            CompletionResponse(
                text = text,
                modelId = decoded.model ?: request.model.id,
                stopReason = decoded.stopReason,
                inputTokens = decoded.usage?.inputTokens,
                outputTokens = decoded.usage?.outputTokens,
            )
        )
    }

    private fun errorMessage(code: Int, body: String): String {
        val detail = runCatching {
            HttpJson.json.decodeFromString(ErrorEnvelope.serializer(), body).error?.message
        }.getOrNull()
        return detail ?: "Anthropic HTTP $code"
    }

    private fun MessageRole.toWire(): String = when (this) {
        MessageRole.ASSISTANT -> "assistant"
        else -> "user" // USER (and any stray SYSTEM already stripped) map to user
    }

    private fun fail(kind: LlmErrorKind, message: String) =
        CompletionResult.Failure(LlmError(kind, message))

    // ── wire DTOs ────────────────────────────────────────────────────────────
    @Serializable
    private data class ChatBody(
        val model: String,
        @SerialName("max_tokens") val maxTokens: Int,
        val temperature: Double,
        val system: String? = null,
        val messages: List<WireMessage>,
    )

    @Serializable
    private data class WireMessage(val role: String, val content: String)

    @Serializable
    private data class ChatResponse(
        val model: String? = null,
        val content: List<ContentBlock>? = null,
        @SerialName("stop_reason") val stopReason: String? = null,
        val usage: Usage? = null,
    )

    @Serializable
    private data class ContentBlock(val type: String? = null, val text: String? = null)

    @Serializable
    private data class Usage(
        @SerialName("input_tokens") val inputTokens: Int? = null,
        @SerialName("output_tokens") val outputTokens: Int? = null,
    )

    @Serializable
    private data class ErrorEnvelope(val error: ErrorDetail? = null)

    @Serializable
    private data class ErrorDetail(val type: String? = null, val message: String? = null)

    private companion object {
        const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        const val ANTHROPIC_VERSION = "2023-06-01"
    }
}
