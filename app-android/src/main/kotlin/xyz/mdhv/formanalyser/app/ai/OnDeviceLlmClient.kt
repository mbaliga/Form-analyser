package xyz.mdhv.formanalyser.app.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import xyz.mdhv.formanalyser.coach.ChatMessage
import xyz.mdhv.formanalyser.coach.CoachModel
import xyz.mdhv.formanalyser.coach.CompletionRequest
import xyz.mdhv.formanalyser.coach.CompletionResponse
import xyz.mdhv.formanalyser.coach.CompletionResult
import xyz.mdhv.formanalyser.coach.LlmClient
import xyz.mdhv.formanalyser.coach.LlmError
import xyz.mdhv.formanalyser.coach.LlmErrorKind
import xyz.mdhv.formanalyser.coach.MessageRole
import xyz.mdhv.formanalyser.coach.ModelKind
import java.io.File

/**
 * On-device [LlmClient] backed by MediaPipe LLM Inference (`tasks-genai`). Serves the ON_DEVICE
 * registry models (Gemma 3n) by loading a local `.task`/`.bin` weights file and running
 * [LlmInference.generateResponse] entirely on the phone — no network, no key, so richer (redacted)
 * facts may be routed here than to any BYOK cloud.
 *
 * Deliberately isolated and defensive: the whole MediaPipe surface lives in this one file so it can
 * be swapped or stubbed if the dependency misbehaves. Nothing throws across the [LlmClient] seam —
 * a missing model file, an unloadable engine, or a failed generation all become a typed
 * [CompletionResult.Failure]. The engine is created lazily on first use and cached per model path.
 *
 * [modelPath] is read fresh on each call (a lambda, mirroring the BYOK clients' `apiKey()` pattern),
 * so installing or clearing a model via [ModelInstall] takes effect without rebuilding the client.
 */
class OnDeviceLlmClient(
    private val context: Context,
    private val modelPath: () -> String?,
    private val maxTokens: Int = DEFAULT_MAX_TOKENS,
) : LlmClient {

    @Volatile private var engine: LlmInference? = null
    @Volatile private var engineForPath: String? = null

    /** Only local ON_DEVICE models — never a cloud model. */
    override fun supports(model: CoachModel): Boolean = model.kind == ModelKind.ON_DEVICE

    override fun complete(request: CompletionRequest): CompletionResult {
        if (!supports(request.model)) {
            return fail(LlmErrorKind.UNSUPPORTED, "OnDeviceLlmClient cannot serve ${request.model.id}")
        }

        val path = modelPath()?.takeIf { it.isNotBlank() }
        if (path == null || !ModelInstall.isInstalled(path)) {
            return fail(LlmErrorKind.PROVIDER_ERROR, "No on-device model installed")
        }

        val prompt = flattenPrompt(request.messages)

        return runCatching {
            val text = engineFor(path).generateResponse(prompt).orEmpty()
            CompletionResult.Success(
                CompletionResponse(
                    text = text,
                    modelId = request.model.id,
                    stopReason = "stop",
                )
            )
        }.getOrElse {
            // The engine may be in a bad state after a failure — drop it so the next call rebuilds.
            close()
            fail(LlmErrorKind.PROVIDER_ERROR, it.message ?: "On-device inference failed")
        }
    }

    /**
     * Flatten a provider-agnostic chat transcript into the single prompt string MediaPipe expects.
     * System turns become a preamble; user/assistant turns are labelled so the model can follow the
     * exchange. A trailing "Assistant:" cue nudges the model to answer as the coach.
     */
    private fun flattenPrompt(messages: List<ChatMessage>): String {
        val sb = StringBuilder()
        val system = messages.filter { it.role == MessageRole.SYSTEM }
            .joinToString("\n\n") { it.content }
            .trim()
        if (system.isNotEmpty()) {
            sb.append(system).append("\n\n")
        }
        messages.filter { it.role != MessageRole.SYSTEM }.forEach { m ->
            val label = if (m.role == MessageRole.ASSISTANT) "Assistant" else "User"
            sb.append(label).append(": ").append(m.content.trim()).append('\n')
        }
        sb.append("Assistant:")
        return sb.toString()
    }

    /** Create (or reuse) the inference engine for [path]. Guarded so concurrent calls share one. */
    @Synchronized
    private fun engineFor(path: String): LlmInference {
        val cached = engine
        if (cached != null && engineForPath == path) return cached

        cached?.let { runCatching { it.close() } }
        engine = null
        engineForPath = null

        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(path)
            .setMaxTokens(maxTokens)
            .build()
        val created = LlmInference.createFromOptions(context, options)
        engine = created
        engineForPath = path
        return created
    }

    /** Release native resources. Safe to call repeatedly; the next [complete] rebuilds lazily. */
    @Synchronized
    fun close() {
        engine?.let { runCatching { it.close() } }
        engine = null
        engineForPath = null
    }

    private fun fail(kind: LlmErrorKind, message: String): CompletionResult =
        CompletionResult.Failure(LlmError(kind, message))

    companion object {
        /** Engine token budget (input + output). Gemma 3n on-device contexts are small (4k–8k). */
        const val DEFAULT_MAX_TOKENS = 1024
    }
}

/**
 * Filesystem helper for on-device model weights. Copies a user-picked `.task`/`.bin` file into the
 * app's private [Context.filesDir] (MediaPipe needs a real, readable file path — it cannot load from
 * a content Uri) and reports where it landed. The returned absolute path is what callers persist in
 * [AiSettings.setOnDeviceModelPath] and hand back to [OnDeviceLlmClient] via its `modelPath` lambda.
 *
 * Kept alongside the client so the on-device runtime is one swappable unit.
 */
object ModelInstall {

    private const val DIR = "on-device-models"
    private val ALLOWED_EXT = setOf("task", "bin")

    /**
     * Copy the model at [source] into private storage and return its absolute path. [displayName]
     * overrides the derived file name (otherwise the Uri's display name, else a default) so the
     * installed file keeps a sensible, sanitized name with an allowed extension.
     */
    fun install(context: Context, source: Uri, displayName: String? = null): String {
        val name = sanitize(displayName ?: queryDisplayName(context, source) ?: "model.task")
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val out = File(dir, name)
        context.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "cannot open model source" }
            out.outputStream().use { input.copyTo(it, bufferSize = 256 * 1024) }
        }
        return out.absolutePath
    }

    /** True iff [path] points at a non-empty, readable file. Null/blank/absent all return false. */
    fun isInstalled(path: String?): Boolean {
        val p = path?.takeIf { it.isNotBlank() } ?: return false
        val f = File(p)
        return f.exists() && f.isFile && f.canRead() && f.length() > 0
    }

    /** Absolute paths of every installed model file, newest not guaranteed — for a picker/cleanup. */
    fun installedModels(context: Context): List<String> {
        val dir = File(context.filesDir, DIR)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.length() > 0 }
            ?.map { it.absolutePath }
            ?: emptyList()
    }

    /** Delete an installed model file. Returns true if a file was removed. No-op on null/blank. */
    fun remove(path: String?): Boolean {
        val p = path?.takeIf { it.isNotBlank() } ?: return false
        val f = File(p)
        return runCatching { f.exists() && f.delete() }.getOrDefault(false)
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx) else null
                } else null
            }
    }.getOrNull()

    /** Strip path separators, keep a basename, and force an allowed model extension. */
    private fun sanitize(raw: String): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "model.task" }
        val ext = base.substringAfterLast('.', "")
        return if (ext.lowercase() in ALLOWED_EXT) base else "$base.task"
    }
}
