package xyz.mdhv.formanalyser.app.ai.providers

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import xyz.mdhv.formanalyser.coach.*

class DeepSeekClient(private val apiKey: () -> String?) : LlmClient {
    override fun supports(model: CoachModel): Boolean = model.provider == Provider.DEEPSEEK

    override fun supportsStreaming(model: CoachModel): Boolean = supports(model)

    /**
     * Streams via [OpenAiCompatStream] — DeepSeek's Chat Completions API is OpenAI-shaped, so this
     * client shares the same decoder [OpenAiClient] does. `stream_options.include_usage` is required
     * for a usage figure to arrive at all (see [OpenAiCompatStream]'s KDoc). Unverified on a device:
     * no Android SDK / socket in this environment.
     */
    override fun stream(request: CompletionRequest, sink: StreamSink): CompletionResult {
        if (!supports(request.model))
            return fail(LlmErrorKind.UNSUPPORTED, "DeepSeekClient cannot serve ${request.model.id}")
        val key =
            apiKey()?.takeIf { it.isNotBlank() }
                ?: return fail(LlmErrorKind.MISSING_API_KEY, "No DeepSeek API key configured")
        val payload =
            ChatBody(
                model = request.model.id,
                messages = request.messages.map { WireMessage(it.role.toWire(), it.content) },
                maxTokens = request.maxTokens,
                temperature = request.temperature,
                stream = true,
                streamOptions = StreamOptions(includeUsage = true),
            )
        return OpenAiCompatStream.run(
            endpoint = ENDPOINT,
            headers = mapOf("Authorization" to "Bearer $key"),
            body = HttpJson.json.encodeToString(ChatBody.serializer(), payload),
            fallbackModelId = request.model.id,
            sink = sink,
            mapHttpError = { code, body -> LlmError(HttpJson.errorKindFor(code), errorMessage(code, body)) },
        )
    }

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
        @SerialName("stream_options") val streamOptions: StreamOptions? = null,
    )

    @Serializable
    private data class StreamOptions(@SerialName("include_usage") val includeUsage: Boolean = true)

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
