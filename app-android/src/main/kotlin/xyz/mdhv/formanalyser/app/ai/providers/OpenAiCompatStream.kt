package xyz.mdhv.formanalyser.app.ai.providers

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import xyz.mdhv.formanalyser.coach.CompletionResult
import xyz.mdhv.formanalyser.coach.LlmError
import xyz.mdhv.formanalyser.coach.LlmErrorKind
import xyz.mdhv.formanalyser.coach.StreamAssembler
import xyz.mdhv.formanalyser.coach.StreamDirective
import xyz.mdhv.formanalyser.coach.StreamSink

/**
 * Streaming helper shared by [OpenAiClient] and [DeepSeekClient]: both speak the OpenAI Chat
 * Completions wire shape (their non-streaming `ChatBody`/`WireMessage`/`Usage` DTOs already match —
 * see [DeepSeekClient]) and their streamed `chat.completion.chunk` frames are the same shape too. One
 * decoder here instead of two near-identical copies that would drift apart.
 *
 * The caller's request body MUST set `"stream": true` and `"stream_options": {"include_usage": true}`
 * — without the latter, a streamed OpenAI-compatible response reports NO usage at all (the final
 * usage-only chunk this decoder relies on simply never arrives), and cost display for that ask
 * degrades to "tokens only". Both [OpenAiClient] and [DeepSeekClient] set this on their streamed
 * payload only (never on the non-streaming one, which already has no way to carry it).
 *
 * DeepSeek reasoner models additionally stream `delta.reasoning_content` alongside `delta.content`.
 * It is parsed here (so the frame isn't silently mis-decoded) but deliberately never read into the
 * assembled answer: it is still billed inside `completion_tokens` (so the cost estimate stays
 * correct), but surfacing raw chain-of-thought as if it were the coach's finished answer would
 * misrepresent it. A dedicated "show reasoning" surface is out of scope for this pass — this comment
 * exists so the field's absence from the assembled text reads as a decision, not an oversight.
 *
 * Unverified on a device: authored from OpenAI's/DeepSeek's documented streaming chunk shape, not
 * confirmed against a live response (no Android SDK, no socket, in this environment).
 */
internal object OpenAiCompatStream {

    /**
     * Drive one streamed request. [mapHttpError] lets each provider keep its own HTTP-error-body
     * parsing (their envelope shapes differ) while sharing everything about the SSE frame format.
     */
    fun run(
        endpoint: String,
        headers: Map<String, String>,
        body: String,
        fallbackModelId: String,
        sink: StreamSink,
        mapHttpError: (code: Int, body: String) -> LlmError,
    ): CompletionResult {
        val assembler = StreamAssembler(fallbackModelId, sink)
        var finishReason: String? = null
        var startedDispatched = false

        val outcome = HttpJson.postSse(url = endpoint, headers = headers, body = body) { event ->
            val data = event.data
            if (data == "[DONE]") return@postSse StreamDirective.CONTINUE

            val chunk = runCatching {
                HttpJson.json.decodeFromString(ChunkResponse.serializer(), data)
            }.getOrNull() ?: return@postSse StreamDirective.CONTINUE // an unparseable frame must not abort the stream

            if (!startedDispatched) {
                startedDispatched = true
                val startDirective = assembler.started(chunk.model, null)
                if (startDirective == StreamDirective.CANCEL) return@postSse StreamDirective.CANCEL
            }

            // choices is `[]` (present but empty) on the final usage-only chunk some providers send
            // after stream_options.include_usage — firstOrNull() on an empty list is null, same as a
            // genuinely absent choices field, so both shapes fall through to the usage check below.
            val choice = chunk.choices?.firstOrNull()
            choice?.finishReason?.let { finishReason = it }

            var directive = StreamDirective.CONTINUE
            val content = choice?.delta?.content
            if (!content.isNullOrEmpty()) {
                directive = assembler.delta(content)
            }
            if (directive == StreamDirective.CONTINUE && chunk.usage != null) {
                directive = assembler.usage(chunk.usage.promptTokens, chunk.usage.completionTokens)
            }
            directive
        }

        return when (outcome) {
            is HttpJson.HttpOutcome.Transport ->
                CompletionResult.Failure(LlmError(LlmErrorKind.NETWORK, outcome.cause.message ?: "Network error"))
            is HttpJson.HttpOutcome.HttpError ->
                CompletionResult.Failure(mapHttpError(outcome.code, outcome.body))
            is HttpJson.HttpOutcome.Ok ->
                when (val result = assembler.finish(finishReason)) {
                    is CompletionResult.Success ->
                        if (result.response.stopReason == "content_filter") {
                            CompletionResult.Failure(
                                LlmError(LlmErrorKind.CONTENT_FILTERED, "The provider filtered the response")
                            )
                        } else {
                            result
                        }
                    is CompletionResult.Failure -> result
                }
        }
    }

    @Serializable
    private data class ChunkResponse(
        val model: String? = null,
        val choices: List<Choice>? = null,
        val usage: Usage? = null,
    )

    @Serializable
    private data class Choice(
        val delta: Delta? = null,
        @SerialName("finish_reason") val finishReason: String? = null,
    )

    @Serializable
    private data class Delta(
        val content: String? = null,
        // Parsed so a reasoner model's frame isn't misread, deliberately never used — see class KDoc.
        @SerialName("reasoning_content") val reasoningContent: String? = null,
    )

    @Serializable
    private data class Usage(
        @SerialName("prompt_tokens") val promptTokens: Int? = null,
        @SerialName("completion_tokens") val completionTokens: Int? = null,
    )
}
