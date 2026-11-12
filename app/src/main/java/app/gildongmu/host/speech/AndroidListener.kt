package app.gildongmu.host.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Listens with Android's own speech recogniser. Used by the mock build in place of the Pleos
 * SpeechToText SDK.
 *
 * It needs the microphone permission and a recognition service on the device. An emulator image
 * may have neither; then [available] is false, [listen] does nothing, and the demo relies on the
 * scripted replies or the buttons on the demo panel instead.
 *
 * Must be used from the main thread, where its callbacks also arrive.
 */
class AndroidListener(
    private val context: Context,
    private val onHeard: (text: String) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    val available: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context) &&
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private val intent: Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

    private val restart = Runnable {
        if (listening) recognizer?.startListening(intent)
    }

    private val callbacks = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onResults(results: Bundle?) {
            if (!listening) return
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (text.isNullOrBlank()) {
                restartSoon()
            } else {
                listening = false
                onHeard(text)
            }
        }

        override fun onError(error: Int) {
            if (!listening) return
            when (error) {
                // The recogniser gives up after a few seconds of silence, and can still be busy
                // closing one session when asked to start the next. The host decides how long to
                // wait for an answer, so keep listening until it says stop.
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> restartSoon()
                // No network, no permission, a broken service: listening is over. The host will
                // time out as if nobody had answered, and the buttons still work.
                else -> listening = false
            }
        }
    }

    fun listen() {
        if (!available || listening) return
        val current = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(callbacks)
            recognizer = it
        }
        main.removeCallbacks(restart)
        listening = true
        current.startListening(intent)
    }

    fun cancel() {
        main.removeCallbacks(restart)
        if (!listening) return
        listening = false
        recognizer?.cancel()
    }

    fun shutdown() {
        main.removeCallbacks(restart)
        listening = false
        recognizer?.destroy()
        recognizer = null
    }

    /** Not from inside the recogniser's own callback: it is not ready for the next session yet. */
    private fun restartSoon() {
        main.removeCallbacks(restart)
        main.postDelayed(restart, RESTART_DELAY_MS)
    }

    private companion object {
        const val RESTART_DELAY_MS = 250L
    }
}
