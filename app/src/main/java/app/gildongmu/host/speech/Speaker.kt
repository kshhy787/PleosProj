package app.gildongmu.host.speech

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import app.gildongmu.core.SpeechGuard

/**
 * Something that says the host's sentences, one at a time. Each sentence given to [speak] is
 * reported as finished exactly once, however the voice behind it behaves, unless [stop] or the
 * next sentence cuts it off.
 */
interface Speaker {
    fun speak(utteranceId: String, text: String)

    fun stop()

    fun shutdown()

    /** What the voice is and how it has behaved, in words, for diagnostics. */
    fun describe(): String
}

/**
 * The speaker of last resort, for a device with no voice at all: every sentence lasts as long as
 * saying it would have taken, so the host keeps its pace and the captions carry the story.
 *
 * Must be used from the main thread, where its callbacks also arrive.
 */
class CaptionSpeaker(
    private val onFinished: (utteranceId: String) -> Unit,
    onStatus: (message: String?) -> Unit
) : Speaker {
    private val main = Handler(Looper.getMainLooper())

    private val guard: SpeechGuard = SpeechGuard(
        say = { _, _ -> false },
        silence = {},
        onFinished = { utteranceId -> onFinished(utteranceId) },
        onStatus = onStatus,
        createdAtMs = SystemClock.uptimeMillis()
    )

    private val tick = object : Runnable {
        override fun run() {
            guard.tick(SystemClock.uptimeMillis())
            if (guard.busy) main.postDelayed(this, TICK_MS)
        }
    }

    init {
        guard.onEngineStarted(ok = false, problem = null, nowMs = SystemClock.uptimeMillis())
    }

    override fun speak(utteranceId: String, text: String) {
        guard.speak(utteranceId, text, SystemClock.uptimeMillis())
        main.removeCallbacks(tick)
        main.postDelayed(tick, TICK_MS)
    }

    override fun stop() {
        guard.stop()
    }

    override fun shutdown() {
        guard.stop()
        main.removeCallbacksAndMessages(null)
    }

    override fun describe(): String = "음성 없음 · ${guard.describe()}"

    private companion object {
        const val TICK_MS = 100L
    }
}
