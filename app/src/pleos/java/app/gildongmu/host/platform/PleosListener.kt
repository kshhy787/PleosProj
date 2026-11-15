package app.gildongmu.host.platform

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.util.Log

/**
 * Listening through the Pleos SpeechToText SDK (ai.pleos.playground:SpeechToText:3.3.1).
 *
 * Checked against the SDK's class files:
 *   SpeechToText(context, Mode) - the default mode is HYBRID (the server path), so ON_DEVICE is
 *   passed explicitly; initialize(OnInitializeListener) binds the car's speech service
 *   (ai.pleos.playground.caas) and calls onSuccess or onFailure(ErrorType) at most once;
 *   addListener(ResultListener); request() to start; stop(); removeListener(); release().
 *   ResultListener has exactly onReady, onStartedRecognition, onUpdated(stt: String, completed: Boolean),
 *   onUpdatedEpdData(on: Long, off: Long), onUpdatedRms(rms: Double), onEndedRecognition and
 *   onError(ErrorType). The recognised text is the `stt` argument; completed marks the final result.
 *   ErrorType and Mode are in ...stt.constant. ON_DEVICE needs no registerApp.
 *   The SDK delivers these callbacks, and onSuccess, on the main thread.
 *   Every call except the constructor needs pleos.car.permission.STT_SERVICE (declared in the
 *   pleos manifest) and throws SecurityException without it; PleosPlatform then runs without a
 *   microphone.
 *
 * VERIFY on a device:
 *   - whether the STT_SERVICE permission is granted to the app, and the car's speech service binds
 *   - the recogniser's default language (setLocale(Locale) exists if it is not Korean)
 *   - whether the app itself needs RECORD_AUDIO granted at run time, which nothing asks for yet
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
