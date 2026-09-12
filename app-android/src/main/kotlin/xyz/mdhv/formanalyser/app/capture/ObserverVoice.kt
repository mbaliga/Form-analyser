package xyz.mdhv.formanalyser.app.capture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import java.util.Locale
import xyz.mdhv.formanalyser.scoring.ObserverSpeech

/**
 * Turns one microphone listen into an [ObserverSpeech.Result], for Live Observer voice input.
 *
 * Sits beside [PoseRecorder] as the other sensor wrapper: it owns the platform API and nothing
 * else, and it owns no scoring rules — [ObserverSpeech] in pure `core-scoring` decides what was
 * said, this class only decides how to ask a microphone and hands the answer over verbatim.
 *
 * **On-device recognition only, by default.** [Availability.READY] is reported, and [start] only
 * ever creates a recogniser, via [SpeechRecognizer.createOnDeviceSpeechRecognizer] — never
 * [SpeechRecognizer.createSpeechRecognizer]. `EXTRA_PREFER_OFFLINE` is a *preference* a cloud
 * recogniser is free to ignore; it is not a guarantee, and silently sending an athlete's voice to a
 * cloud recogniser would break local-first. The on-device factory is the only API that actually
 * guarantees that, which is why it is the only path this class will take without a separate,
 * explicit opt-in — and no such opt-in exists yet (see `AppPrefs` in the design notes: deferred,
 * not built, rather than shipped half-tested).
 *
 * No audio buffer is ever requested or kept. The recogniser hands back text and confidence scores
 * only; this class never touches raw audio, and neither does anything downstream of it.
 *
 * **Main thread only.** [SpeechRecognizer] requires every call — construction, `startListening`,
 * `cancel`, `destroy` — to happen on the thread that created it, and that thread must be the main
 * thread. Callers must not wrap [start], [cancel] or [close] in `Dispatchers.IO`.
 *
 * This class cannot be exercised here: there is no Android SDK in this environment, no device, and
 * no on-device language pack to test against. Everything below is written to the platform API as
 * documented and matched carefully against the working `PoseRecorder`/`CaptureScreen` patterns in
 * this codebase, but it is unverified until CI — or a real device — runs it.
 */
class ObserverVoice(private val context: Context) {

    /**
     * Why voice can or cannot be offered right now, or the specific reason a listen just failed
     * before it produced any speech result.
     *
     * Deliberately a separate type from [ObserverSpeech.RejectReason]: that type lives in pure
     * `core-scoring` and is about what the athlete *said* being unparseable. This type is about the
     * *microphone path itself* not being usable — permission, hardware, language pack, locale — and
     * those are Android platform concerns that have no business leaking into a module that has to
     * stay portable and mic-free to be unit-tested at all.
     */
    enum class Availability {
        /** A listen can be started. */
        READY,
        /** [Manifest.permission.RECORD_AUDIO] is not currently granted. */
        NEEDS_PERMISSION,
        /** Below API 33, or the device has no on-device recogniser at all. */
        NO_ON_DEVICE_RECOGNIZER,
        /** An on-device recogniser exists but this language's model is not installed. */
        LANGUAGE_UNAVAILABLE,
        /** The device locale is not English — the only language [ObserverSpeech] understands. */
        UNSUPPORTED_LANGUAGE,
    }

    /** The live recogniser, or null between listens. Never held open longer than one listen. */
    private var recognizer: SpeechRecognizer? = null

    /**
     * Cheap, synchronous, and touches neither the microphone nor RECORD_AUDIO — safe to call as
     * often as the UI likes (e.g. every time the Observer tab is opened) to decide what to render.
     *
     * Order matters: the language gate comes first because it is the one condition [ObserverSpeech]
     * itself cannot route around, then device/recogniser capability, then permission — the same
     * order the athlete would need to fix them in.
     */
    fun availability(): Availability {
        if (Locale.getDefault().language != ENGLISH) return Availability.UNSUPPORTED_LANGUAGE
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        )
            return Availability.NO_ON_DEVICE_RECOGNIZER
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        )
            return Availability.NEEDS_PERMISSION
        return Availability.READY
    }

    /**
     * Start one listen. One utterance in, one outcome out — never a continuous stream, and never a
     * retry of its own: an engine that auto-retried on silence or on a busy error could end up
     * capturing whatever a neighbouring lane says next, which is exactly the false-accept surface
     * [ObserverSpeech] is built to refuse.
     *
     * Exactly one of [onResult] or [onUnavailable] fires per call, and [onState] reports `false`
     * again before either does — a `delivered` guard below enforces the "exactly one" part, because
     * some engines are known to call [RecognitionListener.onError] after already having delivered
     * partial results.
     *
     * Does nothing (and reports [onUnavailable]) instead of starting a hopeless listen when
     * [availability] is not [Availability.READY] — the caller (the Observer screen) is expected to
     * have already checked this before offering the mic button at all, but a permission revoked
     * while the app was backgrounded, or a device that loses its language pack between checks, has
     * to be caught here too rather than assumed away.
     */
    fun start(
        onState: (Boolean) -> Unit,
        onResult: (ObserverSpeech.Result) -> Unit,
        onUnavailable: (Availability) -> Unit = {},
    ) {
        val avail = availability()
        if (avail != Availability.READY) {
            onUnavailable(avail)
            return
        }

        var delivered = false
        fun deliverResult(result: ObserverSpeech.Result) {
            if (delivered) return
            delivered = true
            onResult(result)
        }
        fun deliverUnavailable(a: Availability) {
            if (delivered) return
            delivered = true
            onUnavailable(a)
        }

        recognizer?.destroy()
        val rec = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        recognizer = rec
        rec.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = onState(true)

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() = Unit

                override fun onEvent(eventType: Int, params: Bundle?) = Unit

                override fun onPartialResults(partialResults: Bundle?) = Unit

                override fun onResults(results: Bundle?) {
                    onState(false)
                    deliverResult(ObserverSpeech.resolve(hypotheses(results)))
                }

                override fun onError(error: Int) {
                    onState(false)
                    when (error) {
                        // The recogniser itself is the source of truth for "nothing usable was
                        // said" here, not ObserverSpeech — it never even ran, so NOTHING_HEARD is
                        // the accurate reason rather than a guess at one.
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            deliverResult(
                                ObserverSpeech.Result.Rejected(
                                    "",
                                    ObserverSpeech.RejectReason.NOTHING_HEARD,
                                )
                            )
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            deliverUnavailable(Availability.NEEDS_PERMISSION)
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
                            deliverUnavailable(Availability.LANGUAGE_UNAVAILABLE)
                        // Everything else, including ERROR_RECOGNIZER_BUSY: busy is retryable in
                        // principle, but this wrapper has no retry loop of its own (see the class
                        // doc) and no dedicated UI state exists for "try again in a moment" that
                        // differs from "say it again" — both are recovered the same way, by the
                        // athlete tapping the mic a second time. (`else` cannot share a branch with
                        // named conditions in Kotlin, hence it standing alone here.)
                        else ->
                            deliverResult(
                                ObserverSpeech.Result.Rejected(
                                    "",
                                    ObserverSpeech.RejectReason.NOTHING_HEARD,
                                )
                            )
                    }
                }
            }
        )
        rec.startListening(recognitionIntent())
    }

    /** Stop the current listen without a result. Does not fire [onResult] or [onUnavailable]. */
    fun cancel() {
        recognizer?.cancel()
    }

    /** Release the recogniser. Call from `onCleared()`, mirroring [PoseRecorder.close]. */
    fun close() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun recognitionIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Belt-and-braces: createOnDeviceSpeechRecognizer already guarantees on-device-only
            // regardless of this extra, but it costs nothing to state the preference explicitly too.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_RESULTS)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                TRAILING_SILENCE_MS,
            )
        }

    /** Zip the n-best transcripts with their confidence scores, ready for [ObserverSpeech.resolve]. */
    private fun hypotheses(results: Bundle?): List<ObserverSpeech.Hypothesis> {
        val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
        return texts.mapIndexed { i, text ->
            ObserverSpeech.Hypothesis(text, confidences?.getOrNull(i))
        }
    }

    private companion object {
        const val ENGLISH = "en"
        const val MAX_RESULTS = 5
        const val TRAILING_SILENCE_MS = 900L
    }
}
