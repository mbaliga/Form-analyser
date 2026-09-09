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
import java.io.DigestInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest

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
 * The app runs exactly one on-device engine (see [xyz.mdhv.formanalyser.app.domain.CoachViewModel]'s
 * factory), but the model FILE it points at is managed from Settings ([ModelInstall]/AiSettingsScreen)
 * — a different screen, with no shared ViewModel (this app's manual DI has no injector that reaches
 * both). Without this registry, removing or replacing the installed file leaves the already-built
 * [OnDeviceLlmClient] holding its [com.google.mediapipe.tasks.genai.llminference.LlmInference] open on
 * the deleted/replaced file — the next ask either serves stale weights or fails opaquely, since
 * [OnDeviceLlmClient] only rebuilds its engine when the path it's given actually changes.
 *
 * [CoachViewModel.factory] registers the one instance it builds; [ModelInstall.remove] and a
 * replace-then-install flow call [closeActive] before touching the file on disk, so the next `ask()`
 * is forced to rebuild against whatever is (or isn't) installed afterward.
 */
object OnDeviceEngineRegistry {
    @Volatile private var active: OnDeviceLlmClient? = null

    fun register(client: OnDeviceLlmClient) {
        active = client
    }

    /** Release the registered engine's native resources, if one is registered. Safe to call anytime. */
    fun closeActive() {
        active?.close()
    }
}

/**
 * Filesystem helper for on-device model weights. Stages a user-picked `.task`/`.bin` file into the
 * app's private [Context.filesDir] (MediaPipe needs a real, readable file path — it cannot load from
 * a content Uri) via [install] and reports where it landed. The returned [InstalledModel.path] is
 * what callers persist in [AiSettings.setOnDeviceModelPath] and hand back to [OnDeviceLlmClient] via
 * its `modelPath` lambda.
 *
 * Kept alongside the client so the on-device runtime is one swappable unit.
 *
 * **Staged copy, not a direct write to the final path.** The naive version of this (copy straight to
 * the destination file) has a real bug: a copy that throws partway through — the athlete backs out of
 * the picker's permission dance, the process is killed, storage fills up — leaves a TRUNCATED file at
 * the final path with `length() > 0`, and [isInstalled] would then report it as installed: a
 * multi-gigabyte dud the app believes is a working model until the next `ask()` fails strangely. To
 * make that impossible, [install] writes to `<name>.part` and only [File.renameTo] onto the real name
 * once the copy, size check, and (for `.task`) magic-byte sniff have all passed — a renameTo on the
 * same filesystem is atomic, so the final path either doesn't exist or is a fully-copied file, never
 * something in between. [sweepStalePartFiles] cleans up an orphaned `.part` after a process death.
 */
object ModelInstall {

    private const val DIR = "on-device-models"
    private val ALLOWED_EXT = setOf("task", "bin")

    /** A model file must be at least this large to plausibly be a Gemma weights file, not a mistake. */
    private const val MIN_PLAUSIBLE_MODEL_BYTES = 50L * 1024 * 1024

    /** Headroom beyond the file's own declared size (covers the `.part` + final coexisting briefly). */
    private const val FREE_SPACE_HEADROOM_BYTES = 64L * 1024 * 1024
    private const val FREE_SPACE_MULTIPLIER = 1.05

    private const val PROGRESS_THROTTLE_MS = 100L
    private const val COPY_BUFFER_BYTES = 1 * 1024 * 1024

    /** ZIP local-file-header magic (`PK\x03\x04`) — every MediaPipe `.task` bundle is a ZIP. */
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    /**
     * Copy the model at [source] into private storage, staged and verified per the class KDoc.
     * [displayName] overrides the derived file name (otherwise the Uri's own display name, else a
     * default). [expectedSha256] is an optional hash the athlete pasted from the publisher — a
     * mismatch aborts the install; Crocodyl ships no vendored hash list of its own (one would go
     * stale and imply a verification the app can't actually maintain). [isCancelled] is polled
     * between buffer reads so a long copy can be stopped; [onProgress] is throttled to roughly
     * [PROGRESS_THROTTLE_MS] so a UI collecting it doesn't recompose on every 1&nbsp;MiB chunk.
     *
     * No exceptions cross this seam — every failure is a typed [InstallOutcome.Failed], mirroring
     * [OnDeviceLlmClient.complete]'s "nothing throws" contract. Call on IO; this blocks.
     */
    fun install(
        context: Context,
        source: Uri,
        displayName: String? = null,
        expectedSha256: String? = null,
        isCancelled: () -> Boolean = { false },
        onProgress: (bytesCopied: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ): InstallOutcome {
        val name = sanitize(displayName ?: queryDisplayName(context, source) ?: "model.task")
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext !in ALLOWED_EXT) {
            return InstallOutcome.Failed(
                InstallFailure.BAD_EXTENSION,
                "Pick a .task or .bin model file (this one is .$ext).",
            )
        }

        val declaredSize = queryDeclaredSize(context, source)
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val partFile = File(dir, "$name.part")
        val finalFile = File(dir, name)

        // A precheck SAF can defeat by simply not reporting a size (declaredSize == null) — we can't
        // precheck what we weren't told, so that case proceeds and fails on IOException instead.
        if (declaredSize != null) {
            val required = (declaredSize * FREE_SPACE_MULTIPLIER).toLong() + FREE_SPACE_HEADROOM_BYTES
            val usable = dir.usableSpace
            if (usable < required) {
                return InstallOutcome.Failed(
                    InstallFailure.NOT_ENOUGH_SPACE,
                    "Not enough free space: this model needs about ${humanBytes(required)}, " +
                        "only ${humanBytes(usable)} is available.",
                )
            }
        }

        val digest = MessageDigest.getInstance("SHA-256")
        var bytesCopied = 0L
        try {
            val input = context.contentResolver.openInputStream(source)
                ?: return InstallOutcome.Failed(InstallFailure.UNREADABLE_SOURCE, "Could not open the selected file.")
            input.use {
                DigestInputStream(it, digest).use { digested ->
                    partFile.outputStream().use { out ->
                        val buffer = ByteArray(COPY_BUFFER_BYTES)
                        var lastProgressAtMs = 0L
                        while (true) {
                            if (isCancelled()) {
                                partFile.delete()
                                return InstallOutcome.Failed(InstallFailure.CANCELLED, "Install cancelled.")
                            }
                            val read = digested.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            bytesCopied += read
                            val now = System.currentTimeMillis()
                            if (now - lastProgressAtMs >= PROGRESS_THROTTLE_MS) {
                                onProgress(bytesCopied, declaredSize)
                                lastProgressAtMs = now
                            }
                        }
                    }
                }
            }
            onProgress(bytesCopied, declaredSize) // final, unthrottled progress call
        } catch (e: IOException) {
            partFile.delete()
            return InstallOutcome.Failed(InstallFailure.COPY_FAILED, e.message ?: "The copy failed.")
        }

        if (bytesCopied < MIN_PLAUSIBLE_MODEL_BYTES) {
            partFile.delete()
            return InstallOutcome.Failed(
                InstallFailure.TOO_SMALL,
                "That file is ${humanBytes(bytesCopied)}; a Gemma model is 1-4 GB — did you pick the right file?",
            )
        }

        // A raw .bin cannot be sniffed reliably (no standard magic bytes for it), so this check is
        // skipped for it rather than pretending to validate something it can't.
        if (ext == "task" && !hasZipMagic(partFile)) {
            partFile.delete()
            return InstallOutcome.Failed(
                InstallFailure.NOT_A_MODEL,
                "That file doesn't look like a .task model bundle (no ZIP header).",
            )
        }

        val actualSha256 = digest.digest().toHexString()
        if (expectedSha256 != null && !expectedSha256.equals(actualSha256, ignoreCase = true)) {
            partFile.delete()
            return InstallOutcome.Failed(
                InstallFailure.DIGEST_MISMATCH,
                "The file's SHA-256 doesn't match what you pasted — it may be the wrong file or a partial download.",
            )
        }

        // Don't delete a pre-existing install outright: if it's the same file name (the common
        // "Replace model" case — the name comes from the source Uri's display name) and the new copy
        // then fails to finalize or probe below, an outright delete here would leave NOTHING at
        // finalFile — the working model gone *and* the replacement rejected. Instead rename it aside
        // and only drop it once the new copy has proven it loads; on any failure below, put it back.
        val backupFile = File(dir, "$name.bak")
        val hadPreviousInstall = finalFile.exists()
        if (hadPreviousInstall) {
            backupFile.delete() // stale leftover from an earlier failed install, if any
            if (!finalFile.renameTo(backupFile)) {
                partFile.delete()
                return InstallOutcome.Failed(InstallFailure.COPY_FAILED, "Could not finalize the installed file.")
            }
        }

        // Attempt the restore-from-backup and fold it into [base]'s message, so a failure never
        // *reads* like "same old model still fine" when the athlete actually lost their working one.
        fun withRestoreStatus(base: String): String {
            if (!hadPreviousInstall) return base
            return if (backupFile.renameTo(finalFile)) {
                "$base The previously installed model is unaffected and still in place."
            } else {
                "$base The previously installed model could not be restored either — please reinstall a model."
            }
        }

        if (!partFile.renameTo(finalFile)) {
            partFile.delete()
            return InstallOutcome.Failed(
                InstallFailure.COPY_FAILED,
                withRestoreStatus("Could not finalize the installed file."),
            )
        }

        val probeFailureMessage = probe(context, finalFile.absolutePath)
        if (probeFailureMessage != null) {
            finalFile.delete()
            return InstallOutcome.Failed(InstallFailure.PROBE_FAILED, withRestoreStatus(probeFailureMessage))
        }
        backupFile.delete() // new model probed fine — safe to drop the old weights now

        return InstallOutcome.Success(
            InstalledModel(
                path = finalFile.absolutePath,
                displayName = name,
                sizeBytes = bytesCopied,
                sha256 = actualSha256,
                installedAtEpochMs = System.currentTimeMillis(),
            )
        )
    }

    /** True iff [path] points at a non-empty, readable, fully-installed file (never a `.part`). */
    fun isInstalled(path: String?): Boolean {
        val p = path?.takeIf { it.isNotBlank() } ?: return false
        val f = File(p)
        return f.exists() && f.isFile && f.canRead() && f.length() > 0 && !f.name.endsWith(".part")
    }

    /**
     * Absolute paths of every installed model file, newest not guaranteed — for a picker/cleanup.
     * Excludes `.part` (mid-copy) and `.bak` (a pre-empted previous install [install] is holding
     * during a swap — see its KDoc) files: neither is a model an athlete ever chose to install.
     */
    fun installedModels(context: Context): List<String> {
        val dir = File(context.filesDir, DIR)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.length() > 0 && !it.name.endsWith(".part") && !it.name.endsWith(".bak") }
            ?.map { it.absolutePath }
            ?: emptyList()
    }

    /**
     * Delete `.part`/`.bak` files left behind by an install that never finished — the athlete backed
     * out mid permission dance, the process died, the app crashed — and are older than [maxAgeMs]
     * (default one hour: anything fresher might be an install genuinely in progress right now). A
     * `.part` is a copy that never completed; a `.bak` is a previous install [install] set aside
     * during a same-name replace and never got to restore or drop (see its KDoc). Call once at app or
     * Settings-screen init; safe to call repeatedly. Returns the number of files removed.
     */
    fun sweepStalePartFiles(context: Context, maxAgeMs: Long = 60 * 60 * 1000L): Int {
        val dir = File(context.filesDir, DIR)
        if (!dir.isDirectory) return 0
        val cutoff = System.currentTimeMillis() - maxAgeMs
        return dir.listFiles()
            ?.filter {
                it.isFile && (it.name.endsWith(".part") || it.name.endsWith(".bak")) && it.lastModified() < cutoff
            }
            ?.count { it.delete() }
            ?: 0
    }

    /**
     * Delete an installed model file. Returns true if a file was removed. No-op on null/blank.
     *
     * Deliberately a hard delete, NOT the app's nullable-`deletedAt`-filtered-at-the-DAO idiom used
     * for athlete records (see `MIGRATION_6_7`/`MIGRATION_7_8` in `AppDatabase.kt`): a downloaded
     * weights file is a reproducible artifact the athlete can re-download, not athlete history that
     * idiom exists to protect. Applying that idiom here would strand multi-gigabyte files on the
     * device forever with no way to reclaim the space.
     *
     * Does not itself call [OnDeviceEngineRegistry.closeActive] — callers that are actually retiring
     * the currently-selected on-device model (as opposed to, say, cleaning up an old replaced file)
     * must call that themselves first so the running engine doesn't keep the deleted file open.
     */
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

    /** SAF-reported file size, or null if the provider doesn't say (see the free-space precheck note). */
    private fun queryDeclaredSize(context: Context, uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && !c.isNull(idx)) c.getLong(idx) else null
            } else null
        }
    }.getOrNull()

    private fun hasZipMagic(file: File): Boolean = runCatching {
        val header = ByteArray(ZIP_MAGIC.size)
        val read = file.inputStream().use { it.read(header) }
        read == ZIP_MAGIC.size && header.contentEquals(ZIP_MAGIC)
    }.getOrDefault(false)

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

    private fun humanBytes(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 0.1) "%.1f GB".format(gb) else "%.0f MB".format(bytes / (1024.0 * 1024.0))
    }

    /**
     * Build an [LlmInference] from [path] and immediately close it again — the only honest proof the
     * weights actually load, rather than leaving a bad file to fail at the athlete's next `ask()`,
     * minutes later, with a confusing runtime error instead of an install-time one. Returns null on
     * success, a user-facing message on failure.
     *
     * Catches [Throwable], not just [Exception]: a load attempt on a device without enough RAM for
     * this model is a realistic way for this to fail as an [OutOfMemoryError], which is an [Error],
     * and the whole point of probing is to surface exactly that ("this device may not have enough
     * memory for this model") instead of letting it propagate. Deletes nothing itself — the caller
     * decides what happens to a failed probe's file.
     */
    private fun probe(context: Context, path: String): String? = try {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(path)
            .setMaxTokens(OnDeviceLlmClient.DEFAULT_MAX_TOKENS)
            .build()
        val engine = LlmInference.createFromOptions(context, options)
        runCatching { engine.close() }
        null
    } catch (e: Throwable) {
        "This model file could not be loaded (${e.message ?: e::class.simpleName}) — it may be corrupt, " +
            "the wrong format, or this device may not have enough memory for it."
    }

    /** Strip path separators, keep a basename, and force an allowed model extension. */
    private fun sanitize(raw: String): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "model.task" }
        val ext = base.substringAfterLast('.', "")
        return if (ext.lowercase() in ALLOWED_EXT) base else "$base.task"
    }
}

/** Outcome of [ModelInstall.install]. No exceptions cross this seam — see that function's KDoc. */
sealed interface InstallOutcome {
    data class Success(val info: InstalledModel) : InstallOutcome
    data class Failed(val reason: InstallFailure, val message: String) : InstallOutcome
}

/** Why an install attempt failed, so the UI can show a specific, actionable message. */
enum class InstallFailure {
    CANCELLED,
    UNREADABLE_SOURCE,
    BAD_EXTENSION,
    TOO_SMALL,
    NOT_ENOUGH_SPACE,
    COPY_FAILED,
    NOT_A_MODEL,
    DIGEST_MISMATCH,
    PROBE_FAILED,
}

/** A successfully installed on-device model file, as reported by [ModelInstall.install]. */
data class InstalledModel(
    val path: String,
    val displayName: String,
    val sizeBytes: Long,
    val sha256: String,
    val installedAtEpochMs: Long,
)
