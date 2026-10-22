package xyz.mdhv.formanalyser.coach

/**
 * One observable step of a streamed completion ([LlmClient.stream]). Push-based on purpose: this
 * module has no coroutines dependency and is not going to grow one (see [LlmClient]'s design note),
 * so the seam is a callback ([StreamSink]) the Android adapter drives from its own IO dispatcher —
 * never a `Flow`, which would drag kotlinx-coroutines into a module whose contract is "synchronous
 * and coroutine-free," and never a blocking queue, which would leak threading policy into core.
 */
sealed interface StreamEvent {
    /** The provider accepted the request. [inputTokens] is known up front on some providers only. */
    data class Started(val modelId: String, val inputTokens: Int? = null) : StreamEvent

    /** A new piece of assistant text. Always the DELTA since the last event, never the running total. */
    data class Delta(val text: String) : StreamEvent

    /**
     * Usage as currently known. Providers report token counts at different points in the stream (up
     * front, mid-stream, only at the end) — [inputTokens]/[outputTokens] here are whatever the
     * provider just revealed; a null field means "unchanged," not "zero."
     */
    data class UsageUpdate(val inputTokens: Int? = null, val outputTokens: Int? = null) : StreamEvent
}

/** The consumer's answer to each [StreamEvent]: keep reading, or stop and keep what we have. */
enum class StreamDirective {
    CONTINUE,
    CANCEL,
}

/**
 * Receives [StreamEvent]s from one [LlmClient.stream] call. Called on the caller's thread, in order,
 * never concurrently — a client drives one sink from a single reading thread.
 */
fun interface StreamSink {
    fun onEvent(event: StreamEvent): StreamDirective
}

/**
 * Shared bookkeeping for turning a provider's raw stream frames into one [CompletionResult].
 *
 * Every streaming [LlmClient] override drives one of these per call: feed it [started]/[delta]/
 * [usage] as the provider's frames decode, then call [finish] on a clean end-of-stream or [failure]
 * on a transport/provider error. Centralising this here means the five Android provider clients
 * don't each re-implement (and each subtly mis-implement) buffer concatenation, "last known usage
 * wins," and the cancel latch — and it is unit-tested in this pure-JVM module instead of five times
 * in app-android, which cannot be tested in this environment.
 *
 * Not thread-safe: the contract mirrors [StreamSink] — one assembler is driven by a single reading
 * thread, in order, never concurrently.
 */
class StreamAssembler(
    private val fallbackModelId: String,
    private val sink: StreamSink,
) {
    private val buffer = StringBuilder()
    private var latestModelId: String? = null
    private var latestInputTokens: Int? = null
    private var latestOutputTokens: Int? = null

    /**
     * True once [sink] has asked to stop. Latched: once set, every further [started]/[delta]/[usage]
     * call is a no-op that still reports [StreamDirective.CANCEL], so a client's read loop can simply
     * check the return value each iteration without tracking its own flag.
     */
    var cancelled: Boolean = false
        private set

    /**
     * Text assembled so far, in order. Still readable after [failure] — a mid-stream provider error
     * does not erase what the athlete already saw, so a caller can surface it under an error banner.
     */
    val partialText: String
        get() = buffer.toString()

    /** The provider accepted the request. */
    fun started(modelId: String?, inputTokens: Int?): StreamDirective {
        if (cancelled) return StreamDirective.CANCEL
        modelId?.let { latestModelId = it }
        inputTokens?.let { latestInputTokens = it }
        return dispatch(StreamEvent.Started(modelId ?: fallbackModelId, inputTokens))
    }

    /** A new piece of text. An empty delta is dropped without notifying [sink] — nothing changed. */
    fun delta(text: String): StreamDirective {
        if (cancelled) return StreamDirective.CANCEL
        if (text.isEmpty()) return StreamDirective.CONTINUE
        buffer.append(text)
        return dispatch(StreamEvent.Delta(text))
    }

    /**
     * Usage as currently known. Only non-null fields update the running total ("last non-null value
     * wins" per field, independently) so a provider that reports input tokens up front and output
     * tokens only at the end doesn't lose the input figure when the final usage frame omits it.
     */
    fun usage(inputTokens: Int?, outputTokens: Int?): StreamDirective {
        if (cancelled) return StreamDirective.CANCEL
        inputTokens?.let { latestInputTokens = it }
        outputTokens?.let { latestOutputTokens = it }
        return dispatch(StreamEvent.UsageUpdate(latestInputTokens, latestOutputTokens))
    }

    /**
     * Clean end of stream. A latched [cancelled] wins even over a provider-reported [stopReason] —
     * the athlete stopped it, that is what happened, and the UI must be able to say so. A stream that
     * opened and closed without ever producing text is a failure, not an empty success — it mirrors
     * the existing "response had no text content" behaviour the non-streaming clients already use.
     */
    fun finish(stopReason: String?): CompletionResult {
        if (!cancelled && buffer.isEmpty()) {
            return CompletionResult.Failure(
                LlmError(
                    LlmErrorKind.PROVIDER_ERROR,
                    "The stream for $fallbackModelId closed without producing any text",
                )
            )
        }
        return CompletionResult.Success(
            CompletionResponse(
                text = buffer.toString(),
                modelId = latestModelId ?: fallbackModelId,
                stopReason = if (cancelled) "cancelled" else stopReason,
                inputTokens = latestInputTokens,
                outputTokens = latestOutputTokens,
                truncated = cancelled,
            )
        )
    }

    /**
     * A transport/provider error ended the stream. [partialText] is left untouched so the caller can
     * still show what arrived before the error, alongside the failure message.
     */
    fun failure(kind: LlmErrorKind, message: String): CompletionResult =
        CompletionResult.Failure(LlmError(kind, message))

    private fun dispatch(event: StreamEvent): StreamDirective {
        val directive = sink.onEvent(event)
        if (directive == StreamDirective.CANCEL) cancelled = true
        return directive
    }
}
