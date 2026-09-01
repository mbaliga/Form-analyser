package xyz.mdhv.formanalyser.app.ai.providers

import kotlinx.serialization.json.Json
import xyz.mdhv.formanalyser.coach.LlmErrorKind
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
