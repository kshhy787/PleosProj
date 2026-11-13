package app.gildongmu.host.platform

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import app.gildongmu.core.SpeechGuard
import app.gildongmu.host.speech.Speaker
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
 *
 * This class is only the wiring to the SDK. When a sentence is over is decided by [SpeechGuard],
 * the same tested logic the mock build uses: if the engine does not start, never reports, or
 * reports without having spoken, the host carries on at reading pace and [onStatus] says why.
 *
 * Must be used from the main thread, where its callbacks also arrive.
 */
class PleosSpeaker(
    context: Context,
    private val onFinished: (utteranceId: String) -> Unit,
    private val onStatus: (message: String?) -> Unit
) : Speaker {
    private val main = Handler(Looper.getMainLooper())
    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
    private var closed = false
    private var listening = false

    private val guard: SpeechGuard = SpeechGuard(
        say = { _, text ->
            try {
                tts.speak(text)
                true
            } catch (e: RuntimeException) {
                // A sentence the SDK throws on is shown for its time instead of taking the app down.
                Log.w(TAG, "Speech was refused", e)
                false
            }
        },
        silence = { tts.stop() },
        onFinished = { utteranceId -> onFinished(utteranceId) },
        onStatus = { message ->
            if (message != null) Log.w(TAG, message)
            onStatus(message)
        },
        createdAtMs = SystemClock.uptimeMillis()
    )

    private val tick = object : Runnable {
        override fun run() {
            guard.tick(SystemClock.uptimeMillis())
            if (guard.busy) main.postDelayed(this, TICK_MS)
        }
    }

    // The SDK reports "done" without saying which sentence; the guard allows for that.
    private val events = object : EventListener {
        override fun onReady() {}
        override fun onStart() {}
        override fun onUpdatedRms(rms: Double) {}
        override fun onStop() {}

        override fun onDone() {
            report(failed = false)
        }

        override fun onError(errorType: ErrorType) {
            Log.w(TAG, "Speech failed: $errorType")
            report(failed = true)
        }
    }

    init {
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                main.post {
                    if (closed) return@post
                    tts.addEventListener(events)
                    listening = true
                    tts.setLocale(Locale.KOREA)
                    guard.onEngineStarted(ok = true, problem = null, nowMs = SystemClock.uptimeMillis())
                    keepTicking()
                }
            }

            override fun onFailure(errorType: ErrorType) {
                Log.e(TAG, "TextToSpeech did not initialise: $errorType")
                main.post {
                    if (closed) return@post
                    guard.onEngineStarted(ok = false, problem = "차량 음성 엔진을 시작하지 못했어요. 자막으로 진행합니다.", nowMs = SystemClock.uptimeMillis())
                    keepTicking()
                }
            }
        })
        // The guard is waiting for the engine to start, and gives up on it after a while.
        keepTicking()
    }

    /** SDK callbacks can arrive on any thread; the guard is only touched on the main one. */
    private fun report(failed: Boolean) {
        main.post {
            if (closed) return@post
            guard.onEngineReport(null, failed, SystemClock.uptimeMillis())
            keepTicking()
        }
    }

    override fun speak(utteranceId: String, text: String) {
        if (closed) return
        guard.speak(utteranceId, text, SystemClock.uptimeMillis())
        keepTicking()
    }

    override fun stop() {
        if (closed) return
        guard.stop()
        keepTicking()
    }

    /** How the car's voice has behaved, in words, for the log. */
    override fun describe(): String = "Pleos TextToSpeech · ${guard.describe()}"

    override fun shutdown() {
        closed = true
        main.removeCallbacksAndMessages(null)
        if (listening) tts.removeEventListener(events)
        tts.release()
    }

    private fun keepTicking() {
        main.removeCallbacks(tick)
        if (guard.busy) main.postDelayed(tick, TICK_MS)
    }

    private companion object {
        const val TAG = "Gildongmu"
        const val TICK_MS = 100L
    }
}
