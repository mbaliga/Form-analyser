package xyz.mdhv.formanalyser.app.ui.components

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** A strict on-device speech wrapper. It never falls back to a network recognizer. */
class OfflineScoreRecognizer(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null

    fun listen(onStatus: (String) -> Unit, onResult: (String) -> Unit) {
        if (!isAvailable(context)) {
            onStatus("Offline speech recognition is unavailable on this device.")
            return
        }
        recognizer?.destroy()
        val speech = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        recognizer = speech
        speech.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = onStatus("Listening…")
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = onStatus("Understanding…")
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit

                override fun onResults(results: Bundle?) {
                    val heard =
                        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (heard == null) onStatus("Nothing understood. Tap and try again.")
                    else {
                        onStatus("Heard: $heard")
                        onResult(heard)
                    }
                }

                override fun onError(error: Int) {
                    onStatus(
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "No score recognised. Try again."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard. Try again."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer is busy. Wait a moment."
                            else -> "Offline recognition failed ($error). Tap scoring still works."
                        }
                    )
                }
            }
        )
        speech.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        )
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    companion object {
        fun isAvailable(context: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    }
}
