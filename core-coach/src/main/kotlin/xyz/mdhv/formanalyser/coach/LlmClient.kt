package xyz.mdhv.formanalyser.coach

/** Chat roles the prompt builder emits. */
enum class MessageRole { SYSTEM, USER, ASSISTANT }

/** One turn in a completion request/response. */
data class ChatMessage(val role: MessageRole, val content: String)

/**
 * A provider-agnostic completion request. Carries the chosen [CoachModel] so the Android adapter
 * knows which provider/endpoint to route to and whether a BYOK key is required.
 */
data class CompletionRequest(
    val model: CoachModel,
    val messages: List<ChatMessage>,
    val maxTokens: Int = 1024,
    val temperature: Double = 0.2,
)

/** A successful completion payload. */
data class CompletionResponse(
    val text: String,
    val modelId: String,
    val stopReason: String? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    /**
     * True when [text] is a PARTIAL answer — the athlete stopped the stream ([StreamDirective.CANCEL]
     * from a [StreamSink]), or the provider cut it short. Defaulted and appended last so existing
     * positional constructors (e.g. `DeepSeekClient`'s) keep compiling unchanged. The UI must label a
     * truncated answer: half a sentence must never read as the coach's finished advice.
     */
    val truncated: Boolean = false,
)

/** Categories of failure the Android adapter maps every provider's errors onto. */
enum class LlmErrorKind {
    MISSING_API_KEY,
    RATE_LIMITED,
    NETWORK,
    INVALID_REQUEST,
    CONTENT_FILTERED,
    PROVIDER_ERROR,
    UNSUPPORTED,
}

data class LlmError(val kind: LlmErrorKind, val message: String)

/** Result of a completion — success or a typed error. No exceptions cross the seam. */
sealed interface CompletionResult {
    data class Success(val response: CompletionResponse) : CompletionResult
    data class Failure(val error: LlmError) : CompletionResult
}

/**
 * The one seam the Android layer implements per provider (Anthropic/OpenAI/Google BYOK adapters,
 * an on-device runtime). Deliberately synchronous and coroutine-free: the core stays pure JVM and
 * unit-testable; the Android adapter runs [complete] off the main thread and owns the API key.
 */
interface LlmClient {
    /** Which model this client can serve. Callers match it against [CompletionRequest.model]. */
    fun supports(model: CoachModel): Boolean

    fun complete(request: CompletionRequest): CompletionResult

    /**
     * True iff [stream] really streams for [model]. False means [stream] still works — it just
     * delivers the whole answer as one [StreamEvent.Delta] through the default bridge below. The UI
     * uses this to decide whether to promise incremental text; it must never fake a typewriter over
     * an answer that already arrived complete (that would misrepresent latency).
     */
    fun supportsStreaming(model: CoachModel): Boolean = false

    /**
     * Streaming variant of [complete]. The default implementation is a ONE-SHOT BRIDGE over
     * [complete], so a provider that cannot stream needs no code at all and a caller never needs two
     * code paths — it always calls [stream] and reads [supportsStreaming] only to decide how to
     * *render* what comes back. Returns the same [CompletionResult] as [complete] would, with the
     * full assembled text delivered as a single [StreamEvent.Delta].
     *
     * Still coroutine-free and still synchronous by contract, same as [complete]: this is a plain
     * push-based callback ([StreamSink]), not a `Flow` — this module has no coroutines dependency and
     * is not going to grow one. The Android adapter drives it from its own IO dispatcher.
     *
     * Cancellation is cooperative: when [sink] returns [StreamDirective.CANCEL] a real streaming
     * override stops reading, releases the connection, and returns `Success` with the partial text
     * and [CompletionResponse.truncated] = true. A cancel is not an error. The default bridge here
     * ignores the directive — the (non-streamed) answer has already arrived in full by the time
     * [sink] sees anything, so there is nothing left to stop.
     */
    fun stream(request: CompletionRequest, sink: StreamSink): CompletionResult {
        val result = complete(request)
        if (result is CompletionResult.Success) {
            val r = result.response
            sink.onEvent(StreamEvent.Started(r.modelId, r.inputTokens))
            sink.onEvent(StreamEvent.Delta(r.text))
            sink.onEvent(StreamEvent.UsageUpdate(r.inputTokens, r.outputTokens))
        }
        return result
    }
}
