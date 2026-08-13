package xyz.mdhv.formanalyser.app.ai.providers

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import xyz.mdhv.formanalyser.coach.*

class DeepSeekClient(private val apiKey: () -> String?) : LlmClient {
    override fun supports(model: CoachModel): Boolean = model.provider == Provider.DEEPSEEK

    override fun complete(request: CompletionRequest): CompletionResult {
        if (!supports(request.model))
            return fail(LlmErrorKind.UNSUPPORTED, "DeepSeekClient cannot serve ${request.model.id}")
        val key =
            apiKey()?.takeIf { it.isNotBlank() }
                ?: return fail(LlmErrorKind.MISSING_API_KEY, "No DeepSeek API key configured")
        val payload =
            ChatBody(
                request.model.id,
                request.messages.map { WireMessage(it.role.toWire(), it.content) },
                request.maxTokens,
                request.temperature,
            )
        return when (
            val o =
                HttpJson.postJson(
                    ENDPOINT,
                    mapOf("Authorization" to "Bearer $key"),
                    HttpJson.json.encodeToString(ChatBody.serializer(), payload),
                )
        ) {
            is HttpJson.HttpOutcome.Transport ->
                fail(LlmErrorKind.NETWORK, o.cause.message ?: "Network error")
            is HttpJson.HttpOutcome.HttpError ->
                fail(HttpJson.errorKindFor(o.code), errorMessage(o.code, o.body))
            is HttpJson.HttpOutcome.Ok -> parseSuccess(request, o.body)
        }
    }

    private fun parseSuccess(request: CompletionRequest, body: String): CompletionResult {
        val d =
            runCatching { HttpJson.json.decodeFromString(ChatResponse.serializer(), body) }
                .getOrElse {
                    return fail(
                        LlmErrorKind.PROVIDER_ERROR,
                        "Malformed DeepSeek response: ${it.message}",
                    )
                }
        val c = d.choices?.firstOrNull()
        val text =
            c?.message?.content
                ?: return fail(
                    LlmErrorKind.PROVIDER_ERROR,
                    "DeepSeek response had no message content",
                )
        return CompletionResult.Success(
            CompletionResponse(
                text,
                d.model ?: request.model.id,
                c.finishReason,
                d.usage?.promptTokens,
                d.usage?.completionTokens,
            )
        )
    }

    private fun errorMessage(code: Int, body: String) =
        runCatching {
                HttpJson.json.decodeFromString(ErrorEnvelope.serializer(), body).error?.message
            }
            .getOrNull() ?: "DeepSeek HTTP $code"

    private fun MessageRole.toWire() =
        when (this) {
            MessageRole.SYSTEM -> "system"
            MessageRole.USER -> "user"
            MessageRole.ASSISTANT -> "assistant"
        }

    private fun fail(k: LlmErrorKind, m: String) = CompletionResult.Failure(LlmError(k, m))

    @Serializable
    private data class ChatBody(
        val model: String,
        val messages: List<WireMessage>,
        @SerialName("max_tokens") val maxTokens: Int,
        val temperature: Double,
        val stream: Boolean = false,
    )

    @Serializable private data class WireMessage(val role: String, val content: String)

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

    @Serializable private data class ErrorEnvelope(val error: ErrorDetail? = null)

    @Serializable private data class ErrorDetail(val message: String? = null)

    private companion object {
        const val ENDPOINT = "https://api.deepseek.com/v1/chat/completions"
    }
}
