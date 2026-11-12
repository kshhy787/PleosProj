package app.gildongmu.host.platform

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.util.Log
import java.util.Locale

/**
 * Speech through the Pleos TextToSpeech SDK (ai.pleos.playground:TextToSpeech).
 *
 * From the guide pages (initialization, event listeners, start and stop, set locale):
 *   TextToSpeech(applicationContext, Mode.ON_DEVICE); initialize(OnInitializeListener);
 *   addEventListener(EventListener); setLocale(Locale.KOREA); speak(text); stop(); release().
 *   EventListener: onReady, onStart, onUpdatedRms(rms: Double), onDone, onStop, onError(errorType).
 *   ON_DEVICE mode needs no app registration; SERVER and HYBRID need registerApp(clientID, clientSecret).
 *
 * VERIFY on first build:
 *   - the package of ErrorType (assumed to sit beside Mode in ...tts.constant)
 *   - whether EventListener has further abstract methods
 */
class PleosSpeaker(context: Context, private val onFinished: (utteranceId: String) -> Unit) {

    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
    private var ready = false
    private var current: String? = null
    private var pending: Pair<String, String>? = null

    // The SDK reports "done" without saying which sentence, so the one in flight is remembered.
    private val events = object : EventListener {
        override fun onReady() {}
        override fun onStart() {}
        override fun onUpdatedRms(rms: Double) {}
        override fun onStop() {}

        override fun onDone() {
            finishCurrent()
        }

        override fun onError(errorType: ErrorType) {
            Log.w(TAG, "Speech failed: $errorType")
            finishCurrent() // treated as spoken, so the host never stalls on a failed sentence
        }
    }

    init {
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                tts.addEventListener(events)
                tts.setLocale(Locale.KOREA)
                ready = true
                pending?.let { (id, text) -> speak(id, text) }
                pending = null
            }

            override fun onFailure(errorType: ErrorType) {
                Log.e(TAG, "TextToSpeech did not initialise: $errorType")
            }
        })
    }

    fun speak(utteranceId: String, text: String) {
        if (!ready) {
            pending = utteranceId to text
            return
        }
        current = utteranceId
        tts.speak(text)
    }

    fun stop() {
        pending = null
        current = null // a "done" or "stop" callback for the interrupted sentence is ignored
        if (ready) tts.stop()
    }

    fun shutdown() {
        current = null
        if (ready) tts.removeEventListener(events)
        tts.release()
    }

    private fun finishCurrent() {
        val id = current ?: return
        current = null
        onFinished(id)
    }

    private companion object {
        const val TAG = "Gildongmu"
    }
}
