package app.gildongmu.host.platform

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.util.Log

/**
 * Listening through the Pleos SpeechToText SDK (ai.pleos.playground:SpeechToText).
 *
 * From the guide pages (result listeners, start and stop):
 *   SpeechToText(applicationContext, Mode.ON_DEVICE); initialize(); addListener(ResultListener);
 *   request() to start; stop(); removeListener(); release().
 *   ResultListener: onReady, onStartedRecognition, onUpdated(stt: String, completed: Boolean),
 *   onUpdatedEpdData(on: Long, off: Long), onUpdatedRms(rms: Double), onEndedRecognition,
 *   onError(errorType). The recognised text is the `stt` argument; completed marks the final result.
 *
 * VERIFY on first build:
 *   - the package of ErrorType (assumed to sit beside Mode in ...stt.constant)
 *   - that initialize takes an OnInitializeListener with onSuccess/onFailure, as TextToSpeech does
 */
class PleosListener(context: Context, private val onHeard: (text: String) -> Unit) {

    private val stt = SpeechToText(context.applicationContext, Mode.ON_DEVICE)
    private var ready = false
    private var listening = false

    private val results = object : ResultListener {
        override fun onReady() {}
        override fun onStartedRecognition() {}
        override fun onUpdatedEpdData(on: Long, off: Long) {}
        override fun onUpdatedRms(rms: Double) {}
        override fun onEndedRecognition() {}

        override fun onUpdated(stt: String, completed: Boolean) {
            if (!completed || !listening) return
            listening = false
            onHeard(stt)
        }

        override fun onError(errorType: ErrorType) {
            Log.w(TAG, "Recognition failed: $errorType")
            listening = false
        }
    }

    init {
        stt.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                stt.addListener(results)
                ready = true
            }

            override fun onFailure(errorType: ErrorType) {
                Log.e(TAG, "SpeechToText did not initialise: $errorType")
            }
        })
    }

    fun listen() {
        if (!ready) return
        listening = true
        stt.request()
    }

    fun cancel() {
        if (!listening) return
        listening = false
        stt.stop()
    }

    fun shutdown() {
        listening = false
        if (ready) stt.removeListener(results)
        stt.release()
    }

    private companion object {
        const val TAG = "Gildongmu"
    }
}
