package xyz.mdhv.formanalyser.coach

/**
 * One decoded Server-Sent Event: an optional [event] name (unnamed frames — OpenAI/DeepSeek/Google
 * all stream unnamed `data:` frames — default to `"message"` per the SSE spec) and the joined [data]
 * payload, still raw text — decoding it as provider-specific JSON is the caller's job.
 */
data class SseEvent(val event: String, val data: String)

/**
 * Decodes the Server-Sent Event **line format** — it is a text framing convention, not a transport:
 * this class opens no sockets and knows nothing about HTTP. It lives in `core-coach` (rather than
 * `app-android/ai/providers`, where the HTTP client that feeds it lives) specifically so the framing
 * logic — the one part of streaming most likely to have an off-by-one — is unit-tested in this
 * pure-JVM module instead of only ever being exercised for the first time by CI's Android compile.
 *
 * Usage: call [pushLine] once per line as the transport reads them (CRLF and bare LF both accepted —
 * strip the trailing newline before calling), collecting whatever [pushLine] returns; call [flush] at
 * end-of-stream in case the transport closed mid-event without a trailing blank line.
 *
 * Rules (per the WHATWG SSE spec, the subset every provider here actually uses):
 * - A line starting with `:` is a comment (Anthropic's keep-alives) and produces nothing.
 * - `field: value` — exactly one leading space after the colon is stripped, if present; `field:value`
 *   (no space) keeps the value as-is. A field with no colon is `field` with an empty value.
 * - `data: <chunk>` lines accumulate for the current event, joined by `\n` between chunks.
 * - `event: <name>` sets the pending event's name; unset stays `"message"`.
 * - A blank line dispatches the pending event (if it has any `data`) and resets the buffer. An event
 *   with no `data` at all before the blank line is silently dropped, matching browser SSE behaviour.
 * - `id:`/`retry:` fields are recognised (consumed, not surfaced) so they don't get misread as data.
 * - The literal payload `[DONE]` is passed through as ordinary `data` — providers that use it as a
 *   sentinel interpret it themselves; the decoder does not treat it specially.
 */
class SseDecoder {
    private var pendingEvent: String = DEFAULT_EVENT
    private val pendingData = StringBuilder()
    private var sawData = false

    /** Feed one line (no trailing newline). Returns the dispatched [SseEvent], or null if none yet. */
    fun pushLine(rawLine: String): SseEvent? {
        val line = rawLine.removeSuffix("\r")
        if (line.isEmpty()) return dispatch()
        if (line.startsWith(":")) return null // comment / keep-alive

        val colonIndex = line.indexOf(':')
        val field: String
        val value: String
        if (colonIndex < 0) {
            field = line
            value = ""
        } else {
            field = line.substring(0, colonIndex)
            val rawValue = line.substring(colonIndex + 1)
            value = if (rawValue.startsWith(" ")) rawValue.substring(1) else rawValue
        }

        when (field) {
            "event" -> pendingEvent = value.ifEmpty { DEFAULT_EVENT }
            "data" -> {
                if (sawData) pendingData.append('\n')
                pendingData.append(value)
                sawData = true
            }
            // "id" and "retry" are valid SSE fields this decoder deliberately ignores: none of the
            // four providers streamed here rely on last-event-id resumption or reconnect timing.
            "id", "retry" -> Unit
            else -> Unit // unknown field — ignore, per spec
        }
        return null
    }

    /**
     * End-of-stream: dispatch whatever event is pending, in case the transport closed without a
     * final blank line. Safe to call even when nothing is pending (returns null).
     */
    fun flush(): SseEvent? = dispatch()

    private fun dispatch(): SseEvent? {
        if (!sawData) {
            // A blank line with no preceding "data:" field carries no event, per spec — but the
            // pending event NAME must still reset, so a stray blank line can't leak an "event:" set
            // moments ago onto an unrelated later frame.
            pendingEvent = DEFAULT_EVENT
            return null
        }
        val event = SseEvent(pendingEvent, pendingData.toString())
        pendingEvent = DEFAULT_EVENT
        pendingData.setLength(0)
        sawData = false
        return event
    }

    private companion object {
        const val DEFAULT_EVENT = "message"
    }
}
