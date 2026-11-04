package xyz.mdhv.formanalyser.app.ai.providers

import kotlinx.serialization.json.Json
import xyz.mdhv.formanalyser.coach.LlmErrorKind
import xyz.mdhv.formanalyser.coach.SseDecoder
import xyz.mdhv.formanalyser.coach.StreamDirective
import xyz.mdhv.formanalyser.coach.SseEvent
import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * Tiny blocking JSON-over-HTTP helper shared by the BYOK cloud provider clients. Uses the platform
 * [HttpURLConnection] (no Retrofit/OkHttp in the project). Deliberately blocking — the enclosing
 * [xyz.mdhv.formanalyser.coach.LlmClient.complete] is synchronous by contract; callers invoke it on
 * [kotlinx.coroutines.Dispatchers.IO]. Never logs headers or bodies (API keys travel through here).
 */
internal object HttpJson {

    /** Lenient JSON: providers add fields over time; unknown keys must never break parsing. */
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private const val DEFAULT_TIMEOUT_MS = 60_000

    /**
     * [HttpURLConnection.setReadTimeout] applies PER `read()` call, not to the whole response — so
     * for a streamed response it behaves as an IDLE timeout, not a total time budget. A long
     * generation with steady tokens never trips it; only a connection that genuinely goes silent
     * does. That is why this is shorter than [DEFAULT_TIMEOUT_MS]: a stalled stream should surface
     * as a network error well before the athlete gives up waiting, where a one-shot call is willing
     * to wait longer for a single big response.
     */
    private const val SSE_IDLE_TIMEOUT_MS = 30_000

    /** Outcome of a raw POST: a decoded HTTP exchange, or a transport-level failure. */
    sealed interface HttpOutcome {
        /** 2xx response with its body text. */
        data class Ok(val code: Int, val body: String) : HttpOutcome

        /** Non-2xx response; [body] is the error payload (may be empty). */
        data class HttpError(val code: Int, val body: String) : HttpOutcome

        /** No usable HTTP response — DNS, timeout, connection reset, TLS, etc. */
        data class Transport(val cause: Throwable) : HttpOutcome
    }

    /**
     * POST [body] as `application/json` to [url] with [headers], returning the outcome without
     * throwing. Blocking; call on IO.
     */
    fun postJson(
        url: String,
        headers: Map<String, String>,
        body: String,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    ): HttpOutcome {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code in 200..299) {
                HttpOutcome.Ok(code, conn.inputStream.readAllText())
            } else {
                HttpOutcome.HttpError(code, conn.errorStreamText())
            }
        } catch (e: SocketTimeoutException) {
            HttpOutcome.Transport(e)
        } catch (e: UnknownHostException) {
            HttpOutcome.Transport(e)
        } catch (e: IOException) {
            HttpOutcome.Transport(e)
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * POST [body] and consume the response as a Server-Sent Event stream, handing each decoded
     * [SseEvent] to [onEvent] as it completes. Blocking; call on IO — same contract as [postJson].
     * Returns [HttpOutcome.HttpError]/[HttpOutcome.Transport] exactly like [postJson] so providers
     * reuse one error mapping ([errorKindFor]); on success the returned [HttpOutcome.Ok.body] is
     * always empty — the payload already went to [onEvent] as it arrived, not buffered for return.
     *
     * [onEvent] returning [StreamDirective.CANCEL] stops reading immediately: the input stream is
     * closed and the connection disconnected without draining the rest of the response. Never logs
     * headers or bodies, same as [postJson].
     */
    fun postSse(
        url: String,
        headers: Map<String, String>,
        body: String,
        connectTimeoutMs: Int = DEFAULT_TIMEOUT_MS,
        idleTimeoutMs: Int = SSE_IDLE_TIMEOUT_MS,
        onEvent: (SseEvent) -> StreamDirective,
    ): HttpOutcome {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMs
                readTimeout = idleTimeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "text/event-stream")
                // Transparent gzip on a streamed response risks the platform's inflater buffering
                // whole chunks before releasing them, turning a token-by-token stream into a stutter
                // of large bursts. Unverified on any real device from this environment; "identity"
                // is the documented mitigation for HttpURLConnection's transparent-gzip behaviour.
                setRequestProperty("Accept-Encoding", "identity")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code !in 200..299) {
                return HttpOutcome.HttpError(code, conn.errorStreamText())
            }

            val decoder = SseDecoder()
            var directive = StreamDirective.CONTINUE
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                var line = reader.readLine()
                while (line != null && directive == StreamDirective.CONTINUE) {
                    decoder.pushLine(line)?.let { directive = onEvent(it) }
                    if (directive == StreamDirective.CONTINUE) line = reader.readLine()
                }
                if (directive == StreamDirective.CONTINUE) {
                    decoder.flush()?.let { onEvent(it) }
                }
            }
            HttpOutcome.Ok(code, "")
        } catch (e: SocketTimeoutException) {
            HttpOutcome.Transport(e)
        } catch (e: UnknownHostException) {
            HttpOutcome.Transport(e)
        } catch (e: IOException) {
            HttpOutcome.Transport(e)
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * GET [url] with [headers], returning the outcome without throwing. Blocking; call on IO. Used
     * only by the (manual, athlete-triggered) per-provider model-list discovery check — never on a
     * schedule, never without the athlete asking, per the local-first invariant.
     */
    fun getJson(
        url: String,
        headers: Map<String, String>,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    ): HttpOutcome {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("Accept", "application/json")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            val code = conn.responseCode
            if (code in 200..299) {
                HttpOutcome.Ok(code, conn.inputStream.readAllText())
            } else {
                HttpOutcome.HttpError(code, conn.errorStreamText())
            }
        } catch (e: SocketTimeoutException) {
            HttpOutcome.Transport(e)
        } catch (e: UnknownHostException) {
            HttpOutcome.Transport(e)
        } catch (e: IOException) {
            HttpOutcome.Transport(e)
        } finally {
            conn?.disconnect()
        }
    }

    /** HTTP status → provider-agnostic error kind. */
    fun errorKindFor(code: Int): LlmErrorKind = when (code) {
        401, 403 -> LlmErrorKind.MISSING_API_KEY
        429 -> LlmErrorKind.RATE_LIMITED
        400, 404, 422 -> LlmErrorKind.INVALID_REQUEST
        in 500..599 -> LlmErrorKind.PROVIDER_ERROR
        else -> LlmErrorKind.PROVIDER_ERROR
    }

    private fun HttpURLConnection.errorStreamText(): String =
        try {
            errorStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
        } catch (_: IOException) {
            ""
        }

    private fun java.io.InputStream.readAllText(): String =
        bufferedReader(Charsets.UTF_8).use(BufferedReader::readText)
}
