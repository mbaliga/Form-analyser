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
import xyz.mdhv.formanalyser.coach.StreamAssembler
import xyz.mdhv.formanalyser.coach.StreamDirective
import xyz.mdhv.formanalyser.coach.StreamSink
import java.net.URLEncoder

/**
 * BYOK Google Gemini (generateContent) client. [apiKey] is read fresh per call; never logged or
 * persisted. The key travels as the `key` query parameter per the Gemini REST contract.
 *
 * Endpoint: POST https://generativelanguage.googleapis.com/v1beta/models/{id}:generateContent?key=<key>
 * Body:     { systemInstruction?, contents: [{role, parts: [{text}]}], generationConfig }
 * Parse:    candidates[0].content.parts[0].text
 */
class GoogleClient(
    private val apiKey: () -> String?,
) : LlmClient {

    override fun supports(model: CoachModel): Boolean = model.provider == Provider.GOOGLE

    override fun complete(request: CompletionRequest): CompletionResult {
        if (!supports(request.model)) {
            return fail(LlmErrorKind.UNSUPPORTED, "GoogleClient cannot serve ${request.model.id}")
        }
        val key = apiKey()?.takeIf { it.isNotBlank() }
            ?: return fail(LlmErrorKind.MISSING_API_KEY, "No Google API key configured")

        // Gemini carries the system prompt as systemInstruction; roles are "user"/"model".
        val system = request.messages
            .filter { it.role == MessageRole.SYSTEM }
            .joinToString("\n\n") { it.content }
            .ifBlank { null }
        val contents = request.messages
            .filter { it.role != MessageRole.SYSTEM }
            .map { Content(role = it.role.toWire(), parts = listOf(Part(it.content))) }

        val payload = GenerateBody(
            systemInstruction = system?.let { Content(role = null, parts = listOf(Part(it))) },
            contents = contents,
            generationConfig = GenerationConfig(
                maxOutputTokens = request.maxTokens,
                temperature = request.temperature,
            ),
        )

        val url = "$BASE${request.model.id}:generateContent?key=" +
            URLEncoder.encode(key, Charsets.UTF_8.name())

        val outcome = HttpJson.postJson(
            url = url,
            headers = emptyMap(),
            body = HttpJson.json.encodeToString(GenerateBody.serializer(), payload),
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
            HttpJson.json.decodeFromString(GenerateResponse.serializer(), body)
        }.getOrElse {
            return fail(LlmErrorKind.PROVIDER_ERROR, "Malformed Gemini response: ${it.message}")
        }
        val candidate = decoded.candidates?.firstOrNull()
        val text = candidate?.content?.parts
            ?.mapNotNull { it.text }
            ?.joinToString("")
            ?.takeIf { it.isNotEmpty() }
        if (text == null) {
            val reason = candidate?.finishReason ?: decoded.promptFeedback?.blockReason
            if (reason == "SAFETY" || reason == "PROHIBITED_CONTENT" ||
                decoded.promptFeedback?.blockReason != null
            ) {
                return fail(LlmErrorKind.CONTENT_FILTERED, "Gemini blocked the response (${reason ?: "safety"})")
            }
            return fail(LlmErrorKind.PROVIDER_ERROR, "Gemini response had no text content")
        }
        return CompletionResult.Success(
            CompletionResponse(
                text = text,
                modelId = decoded.modelVersion ?: request.model.id,
                stopReason = candidate.finishReason,
                inputTokens = decoded.usageMetadata?.promptTokenCount,
                outputTokens = decoded.usageMetadata?.totalOutputTokens(),
            )
        )
    }

    override fun supportsStreaming(model: CoachModel): Boolean = supports(model)

    /**
     * Streams `POST …/{id}:streamGenerateContent?alt=sse&key=…`. `alt=sse` is **mandatory** — without
     * it Gemini instead streams one top-level JSON *array* (`[{...}, {...}, ...]`), which is not a
     * sequence of Server-Sent Events and would not decode as one here.
     *
     * Each unnamed `data:` frame is a COMPLETE [GenerateResponse] — the same DTO [complete] already
     * parses — and Gemini's own chunking already yields the incremental text for that turn, not the
     * whole-so-far transcript, so each chunk's text is fed to [StreamAssembler.delta] directly with
     * no diffing needed. There is no `[DONE]` sentinel: end of stream is the connection closing,
     * which [HttpJson.postSse] surfaces by simply returning, handled below via [StreamAssembler.finish].
     *
     * `usageMetadata.thoughtsTokenCount` is folded into the output count the same way [complete] now
     * does (see [UsageMetadata.totalOutputTokens]) so a thinking model's streamed cost estimate isn't
     * quietly wrong in the same way the non-streaming path used to be.
     *
     * Unverified on a device: authored from Gemini's documented `alt=sse` streaming shape, not
     * confirmed against a live response (no Android SDK / socket in this environment).
     */
    override fun stream(request: CompletionRequest, sink: StreamSink): CompletionResult {
        if (!supports(request.model)) {
            return fail(LlmErrorKind.UNSUPPORTED, "GoogleClient cannot serve ${request.model.id}")
        }
        val key = apiKey()?.takeIf { it.isNotBlank() }
            ?: return fail(LlmErrorKind.MISSING_API_KEY, "No Google API key configured")

        val system = request.messages
            .filter { it.role == MessageRole.SYSTEM }
            .joinToString("\n\n") { it.content }
            .ifBlank { null }
        val contents = request.messages
            .filter { it.role != MessageRole.SYSTEM }
            .map { Content(role = it.role.toWire(), parts = listOf(Part(it.content))) }

        val payload = GenerateBody(
            systemInstruction = system?.let { Content(role = null, parts = listOf(Part(it))) },
            contents = contents,
            generationConfig = GenerationConfig(
                maxOutputTokens = request.maxTokens,
                temperature = request.temperature,
            ),
        )

        val url = "$BASE${request.model.id}:streamGenerateContent?alt=sse&key=" +
            URLEncoder.encode(key, Charsets.UTF_8.name())

        val assembler = StreamAssembler(request.model.id, sink)
        var startedDispatched = false
        var lastFinishReason: String? = null
        var blockReason: String? = null

        val outcome = HttpJson.postSse(
            url = url,
            headers = emptyMap(),
            body = HttpJson.json.encodeToString(GenerateBody.serializer(), payload),
        ) { event ->
            val decoded = runCatching {
                HttpJson.json.decodeFromString(GenerateResponse.serializer(), event.data)
            }.getOrNull() ?: return@postSse StreamDirective.CONTINUE // an unparseable frame must not abort the stream

            if (!startedDispatched) {
                startedDispatched = true
                val startDirective = assembler.started(
                    decoded.modelVersion,
                    decoded.usageMetadata?.promptTokenCount,
                )
                if (startDirective == StreamDirective.CANCEL) return@postSse StreamDirective.CANCEL
            }

            val candidate = decoded.candidates?.firstOrNull()
            candidate?.finishReason?.let { lastFinishReason = it }
            decoded.promptFeedback?.blockReason?.let { blockReason = it }

            val text = candidate?.content?.parts?.mapNotNull { it.text }?.joinToString("").orEmpty()
            var directive = StreamDirective.CONTINUE
            if (text.isNotEmpty()) directive = assembler.delta(text)
            if (directive == StreamDirective.CONTINUE && decoded.usageMetadata != null) {
                directive = assembler.usage(
                    decoded.usageMetadata.promptTokenCount,
                    decoded.usageMetadata.totalOutputTokens(),
                )
            }
            directive
        }

        return when (outcome) {
            is HttpJson.HttpOutcome.Transport ->
                fail(LlmErrorKind.NETWORK, outcome.cause.message ?: "Network error")
            is HttpJson.HttpOutcome.HttpError ->
                fail(HttpJson.errorKindFor(outcome.code), errorMessage(outcome.code, outcome.body))
            is HttpJson.HttpOutcome.Ok -> {
                // Same predicate complete()'s parseSuccess uses: a block only counts as CONTENT_FILTERED
                // when no text ever arrived — a filter reason after real text already streamed must not
                // erase what the athlete already saw.
                val filtered = lastFinishReason == "SAFETY" || lastFinishReason == "PROHIBITED_CONTENT" ||
                    blockReason != null
                if (filtered && assembler.partialText.isEmpty()) {
                    fail(LlmErrorKind.CONTENT_FILTERED, "Gemini blocked the response (${lastFinishReason ?: blockReason ?: "safety"})")
                } else {
                    assembler.finish(lastFinishReason)
                }
            }
        }
    }

    private fun errorMessage(code: Int, body: String): String {
        val detail = runCatching {
            HttpJson.json.decodeFromString(ErrorEnvelope.serializer(), body).error?.message
        }.getOrNull()
        return detail ?: "Gemini HTTP $code"
    }

    private fun MessageRole.toWire(): String = when (this) {
        MessageRole.ASSISTANT -> "model"
        else -> "user"
    }

    private fun fail(kind: LlmErrorKind, message: String) =
        CompletionResult.Failure(LlmError(kind, message))

    // ── wire DTOs ────────────────────────────────────────────────────────────
    @Serializable
    private data class GenerateBody(
        val systemInstruction: Content? = null,
        val contents: List<Content>,
        val generationConfig: GenerationConfig? = null,
    )

    @Serializable
    private data class Content(val role: String? = null, val parts: List<Part>)

    @Serializable
    private data class Part(val text: String? = null)

    @Serializable
    private data class GenerationConfig(
        val maxOutputTokens: Int? = null,
        val temperature: Double? = null,
    )

    @Serializable
    private data class GenerateResponse(
        val candidates: List<Candidate>? = null,
        val usageMetadata: UsageMetadata? = null,
        val promptFeedback: PromptFeedback? = null,
        val modelVersion: String? = null,
    )

    @Serializable
    private data class Candidate(
        val content: Content? = null,
        @SerialName("finishReason") val finishReason: String? = null,
    )

    @Serializable
    private data class UsageMetadata(
        val promptTokenCount: Int? = null,
        val candidatesTokenCount: Int? = null,
        /**
         * On "thinking" models, Gemini bills reasoning tokens separately from [candidatesTokenCount]
         * — the visible-answer count alone silently undercounts what was actually billed as output.
         * [totalOutputTokens] folds this in; a non-thinking model simply omits the field (null), so
         * the fold is a no-op for it.
         */
        val thoughtsTokenCount: Int? = null,
    ) {
        /** [candidatesTokenCount] + [thoughtsTokenCount], or null if neither was reported at all. */
        fun totalOutputTokens(): Int? =
            if (candidatesTokenCount == null && thoughtsTokenCount == null) null
            else (candidatesTokenCount ?: 0) + (thoughtsTokenCount ?: 0)
    }

    @Serializable
    private data class PromptFeedback(val blockReason: String? = null)

    @Serializable
    private data class ErrorEnvelope(val error: ErrorDetail? = null)

    @Serializable
    private data class ErrorDetail(
        val code: Int? = null,
        val message: String? = null,
        val status: String? = null,
    )

    private companion object {
        const val BASE = "https://generativelanguage.googleapis.com/v1beta/models/"
    }
}
