package xyz.mdhv.formanalyser.app.ai.providers

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
 * BYOK OpenAI Chat Completions client. [apiKey] is read fresh per call; never logged or persisted.
 *
 * Endpoint: POST https://api.openai.com/v1/chat/completions
 * Headers:  Authorization: Bearer <key>
 * Body:     { model, messages: [{role, content}], max_tokens, temperature }
 * Parse:    choices[0].message.content
 */
class OpenAiClient(
    private val apiKey: () -> String?,
) : LlmClient {

    override fun supports(model: CoachModel): Boolean = model.provider == Provider.OPENAI

    override fun complete(request: CompletionRequest): CompletionResult {
        if (!supports(request.model)) {
            return fail(LlmErrorKind.UNSUPPORTED, "OpenAiClient cannot serve ${request.model.id}")
        }
        val key = apiKey()?.takeIf { it.isNotBlank() }
            ?: return fail(LlmErrorKind.MISSING_API_KEY, "No OpenAI API key configured")

        val payload = ChatBody(
            model = request.model.id,
            messages = request.messages.map { WireMessage(role = it.role.toWire(), content = it.content) },
            maxTokens = request.maxTokens,
            temperature = request.temperature,
        )

        val outcome = HttpJson.postJson(
            url = ENDPOINT,
            headers = mapOf("Authorization" to "Bearer $key"),
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
            return fail(LlmErrorKind.PROVIDER_ERROR, "Malformed OpenAI response: ${it.message}")
        }
        val choice = decoded.choices?.firstOrNull()
        val text = choice?.message?.content
        if (text == null) {
            if (choice?.finishReason == "content_filter") {
                return fail(LlmErrorKind.CONTENT_FILTERED, "OpenAI filtered the response")
            }
            return fail(LlmErrorKind.PROVIDER_ERROR, "OpenAI response had no message content")
        }
        return CompletionResult.Success(
            CompletionResponse(
                text = text,
                modelId = decoded.model ?: request.model.id,
                stopReason = choice.finishReason,
                inputTokens = decoded.usage?.promptTokens,
                outputTokens = decoded.usage?.completionTokens,
            )
        )
    }

    private fun errorMessage(code: Int, body: String): String {
        val detail = runCatching {
            HttpJson.json.decodeFromString(ErrorEnvelope.serializer(), body).error?.message
        }.getOrNull()
        return detail ?: "OpenAI HTTP $code"
    }

    private fun MessageRole.toWire(): String = when (this) {
        MessageRole.SYSTEM -> "system"
        MessageRole.USER -> "user"
        MessageRole.ASSISTANT -> "assistant"
    }

    private fun fail(kind: LlmErrorKind, message: String) =
        CompletionResult.Failure(LlmError(kind, message))

    // ── wire DTOs ────────────────────────────────────────────────────────────
    @Serializable
    private data class ChatBody(
        val model: String,
        val messages: List<WireMessage>,
        @SerialName("max_tokens") val maxTokens: Int,
        val temperature: Double,
    )

    @Serializable
    private data class WireMessage(val role: String, val content: String)

    @Serializable
    private data class ChatResponse(
        val model: String? = null,
        val choices: List<Choice>? = null,
        val usage: Usage? = null,
    )

    @Serializable
    private data class Choice(
        val message: WireMessage? = null,
        @SerialName("finish_reason") val finishReason: String? = null,
    )

    @Serializable
    private data class Usage(
        @SerialName("prompt_tokens") val promptTokens: Int? = null,
        @SerialName("completion_tokens") val completionTokens: Int? = null,
    )

    @Serializable
    private data class ErrorEnvelope(val error: ErrorDetail? = null)

    @Serializable
    private data class ErrorDetail(val type: String? = null, val message: String? = null)

    private companion object {
        const val ENDPOINT = "https://api.openai.com/v1/chat/completions"
    }
}
