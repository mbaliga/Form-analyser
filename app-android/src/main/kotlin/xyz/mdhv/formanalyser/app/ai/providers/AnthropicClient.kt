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
import xyz.mdhv.formanalyser.coach.StreamAssembler
import xyz.mdhv.formanalyser.coach.StreamDirective
import xyz.mdhv.formanalyser.coach.StreamSink

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

    override fun supportsStreaming(model: CoachModel): Boolean = supports(model)

    /**
     * Streams `POST /v1/messages` with `"stream": true`. Anthropic's named SSE events map onto
     * [StreamAssembler] as follows (see the class KDoc for the general contract):
     *  - `message_start` → [StreamAssembler.started] (model id + input tokens, known up front here).
     *  - `content_block_start` records whether an index is a `text` block; a `thinking` block's
     *    deltas are deliberately ignored below (its text is not the athlete-facing answer).
     *  - `content_block_delta` with `delta.type == "text_delta"` on a known text-block index →
     *    [StreamAssembler.delta]. `thinking_delta`/`signature_delta`/`input_json_delta` are ignored —
     *    this client sends no tools, so `input_json_delta` should never actually occur.
     *  - `message_delta` carries the *cumulative final* output-token count and the stop reason.
     *  - `message_stop` is the normal terminal event; this implementation instead finishes on
     *    end-of-stream (see [HttpJson.postSse]) since by the time `message_stop` arrives there is
     *    nothing left to read anyway.
     *  - `error` can arrive **after** a 200 and after tokens — a mid-stream provider failure. It
     *    short-circuits to a [CompletionResult.Failure] without touching [StreamAssembler.finish],
     *    mirroring [complete]'s existing error mapping. The VM keeps whatever partial text it already
     *    received via [sink] and shows it under the error banner — see [xyz.mdhv.formanalyser.coach.LlmClient.stream]'s KDoc.
     *  - `stop_reason == "refusal"` → [LlmErrorKind.CONTENT_FILTERED], parity with [complete]'s
     *    existing `content_filter` handling.
     *
     * Unverified on a device: this environment has no Android SDK and cannot open a socket. The
     * mapping above is authored from Anthropic's documented streaming event shapes and this client's
     * own non-streaming parse, not confirmed against a live response.
     */
    override fun stream(request: CompletionRequest, sink: StreamSink): CompletionResult {
        if (!supports(request.model)) {
            return fail(LlmErrorKind.UNSUPPORTED, "AnthropicClient cannot serve ${request.model.id}")
        }
        val key = apiKey()?.takeIf { it.isNotBlank() }
            ?: return fail(LlmErrorKind.MISSING_API_KEY, "No Anthropic API key configured")

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
            stream = true,
        )

        val assembler = StreamAssembler(request.model.id, sink)
        val textBlockIndices = HashSet<Int>()
        var finalStopReason: String? = null
        var streamFailure: CompletionResult.Failure? = null

        val outcome = HttpJson.postSse(
            url = ENDPOINT,
            headers = mapOf(
                "x-api-key" to key,
                "anthropic-version" to ANTHROPIC_VERSION,
            ),
            body = HttpJson.json.encodeToString(ChatBody.serializer(), payload),
        ) { event ->
            when (event.event) {
                "message_start" -> {
                    val decoded = runCatching {
                        HttpJson.json.decodeFromString(SseMessageStart.serializer(), event.data)
                    }.getOrNull()
                    assembler.started(decoded?.message?.model, decoded?.message?.usage?.inputTokens)
                }
                "content_block_start" -> {
                    val decoded = runCatching {
                        HttpJson.json.decodeFromString(SseContentBlockStart.serializer(), event.data)
                    }.getOrNull()
                    if (decoded?.contentBlock?.type == "text") textBlockIndices.add(decoded.index)
                    StreamDirective.CONTINUE
                }
                "content_block_delta" -> {
                    val decoded = runCatching {
                        HttpJson.json.decodeFromString(SseContentBlockDelta.serializer(), event.data)
                    }.getOrNull()
                    val delta = decoded?.delta
                    if (decoded != null &&
                        delta != null &&
                        decoded.index in textBlockIndices &&
                        delta.type == "text_delta"
                    ) {
                        assembler.delta(delta.text.orEmpty())
                    } else {
                        StreamDirective.CONTINUE
                    }
                }
                "message_delta" -> {
                    val decoded = runCatching {
                        HttpJson.json.decodeFromString(SseMessageDelta.serializer(), event.data)
                    }.getOrNull()
                    decoded?.delta?.stopReason?.let { finalStopReason = it }
                    assembler.usage(null, decoded?.usage?.outputTokens)
                }
                "error" -> {
                    // Anthropic's mid-stream error event's data shape ({"error":{"type","message"}})
                    // matches the same envelope complete()'s HTTP-error path parses; reused as-is.
                    val decoded = runCatching {
                        HttpJson.json.decodeFromString(ErrorEnvelope.serializer(), event.data)
                    }.getOrNull()
                    streamFailure = anthropicStreamError(decoded?.error?.type, decoded?.error?.message)
                    StreamDirective.CANCEL // stop reading; the error is terminal, not a cancel-by-user.
                }
                else -> StreamDirective.CONTINUE // content_block_stop / ping / message_stop / unknown
            }
        }

        return when (outcome) {
            is HttpJson.HttpOutcome.Transport ->
                fail(LlmErrorKind.NETWORK, outcome.cause.message ?: "Network error")
            is HttpJson.HttpOutcome.HttpError ->
                fail(HttpJson.errorKindFor(outcome.code), errorMessage(outcome.code, outcome.body))
            is HttpJson.HttpOutcome.Ok -> {
                streamFailure?.let { return it }
                when (val result = assembler.finish(finalStopReason)) {
                    is CompletionResult.Success ->
                        if (result.response.stopReason == "refusal") {
                            fail(LlmErrorKind.CONTENT_FILTERED, "Anthropic filtered the response")
                        } else {
                            result
                        }
                    is CompletionResult.Failure -> result
                }
            }
        }
    }

    private fun anthropicStreamError(type: String?, message: String?): CompletionResult.Failure {
        val kind = when (type) {
            "overloaded_error" -> LlmErrorKind.PROVIDER_ERROR
            "rate_limit_error" -> LlmErrorKind.RATE_LIMITED
            "invalid_request_error" -> LlmErrorKind.INVALID_REQUEST
            "authentication_error", "permission_error" -> LlmErrorKind.MISSING_API_KEY
            else -> LlmErrorKind.PROVIDER_ERROR
        }
        return CompletionResult.Failure(LlmError(kind, message ?: "Anthropic stream error ($type)"))
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
        // Defaulted + appended last so complete()'s existing construction sites compile unchanged;
        // encodeDefaults=true means complete() now sends an explicit "stream": false rather than
        // omitting the field, which Anthropic treats identically (DeepSeekClient already does this).
        val stream: Boolean = false,
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

    // ── streaming SSE frame DTOs ─────────────────────────────────────────────
    // One small data class per named event Anthropic sends; unknown fields are ignored
    // (HttpJson.json has ignoreUnknownKeys=true) so this only needs the fields [stream] reads.
    @Serializable
    private data class SseMessageStart(val message: SseMessage? = null)

    @Serializable
    private data class SseMessage(val model: String? = null, val usage: Usage? = null)

    @Serializable
    private data class SseContentBlockStart(
        val index: Int = 0,
        @SerialName("content_block") val contentBlock: SseContentBlockType? = null,
    )

    @Serializable
    private data class SseContentBlockType(val type: String? = null)

    @Serializable
    private data class SseContentBlockDelta(val index: Int = 0, val delta: SseDelta? = null)

    @Serializable
    private data class SseDelta(
        val type: String? = null,
        val text: String? = null,
        @SerialName("stop_reason") val stopReason: String? = null,
    )

    @Serializable
    private data class SseMessageDelta(val delta: SseDelta? = null, val usage: Usage? = null)

    private companion object {
        const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        const val ANTHROPIC_VERSION = "2023-06-01"
    }
}
