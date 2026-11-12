package app.gildongmu.host.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import app.gildongmu.core.AudioFocusKeeper
import app.gildongmu.core.Speech
import java.util.Locale

/**
 * Speaks with Android's own text-to-speech engine. Used by the mock build in place of the Pleos
 * TextToSpeech SDK, which only exists in the car.
 *
 * The host says one sentence, waits to hear that it is finished, and says the next. So whatever
 * the engine does, every sentence handed to [speak] is reported through [onFinished] exactly once
 * unless [stop] cuts it off first:
 *
 *  - No engine, no Korean voice, or an engine that refuses a sentence: the speaker stays silent
 *    for as long as the sentence would have taken, so the captions still tell the story.
 *    [onStatus] reports why, so the screen can say so.
 *  - An engine that never answers, at start-up or after a sentence: the speaker stops waiting
 *    for it after a generous time and carries on.
 *
 * Must be used from the main thread, where its callbacks also arrive.
 */
class AndroidSpeaker(
    context: Context,
    private val onFinished: (utteranceId: String) -> Unit,
    private val onAudioFocus: (available: Boolean) -> Unit,
    private val onStatus: (message: String?) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private val audio = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var closed = false
    private var initialised = false
    private var voiceReady = false
    private var pending: Pair<String, String>? = null

    /** The sentence being spoken, until it is reported as finished or is cut off. */
    private var speakingId: String? = null

    // The listener and the keeper refer to each other, so both are given their types in full.
    private val focusListener: AudioManager.OnAudioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        main.post {
            if (closed) return@post
            val now = SystemClock.uptimeMillis()
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> focus.onGained(now)
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> focus.onLostBriefly(now)
                AudioManager.AUDIOFOCUS_LOSS -> focus.onLost(now)
                else -> {} // Being ducked under something else is fine: keep talking.
            }
            keepFocusTicking()
        }
    }

    private val focus: AudioFocusKeeper = AudioFocusKeeper(
        // Asks other audio to lower its volume while the host talks, rather than to stop.
        request = {
            audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) ==
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        },
        abandon = { audio.abandonAudioFocus(focusListener) },
        onAvailable = { available -> onAudioFocus(available) }
    )

    private val focusTick = object : Runnable {
        override fun run() {
            focus.tick(SystemClock.uptimeMillis())
            if (focus.busy) main.postDelayed(this, FOCUS_TICK_MS)
        }
    }

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}

        override fun onDone(utteranceId: String?) {
            if (utteranceId != null) main.post { finished(utteranceId) }
        }

        @Deprecated("Deprecated in the platform, but still abstract")
        override fun onError(utteranceId: String?) {
            // A sentence that could not be spoken is treated as spoken, so the host never stalls.
            if (utteranceId != null) main.post { finished(utteranceId) }
        }
    }

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        main.post { onInit(status) }
    }

    init {
        // An engine that never answers must not leave the host waiting to say its first sentence.
        main.postDelayed({ if (!initialised) onInit(TextToSpeech.ERROR) }, INIT_LIMIT_MS)
    }

    private fun onInit(status: Int) {
        if (closed) return
        initialised = true
        if (status == TextToSpeech.SUCCESS) {
            val language = tts.setLanguage(Locale.KOREAN)
            voiceReady = language != TextToSpeech.LANG_MISSING_DATA && language != TextToSpeech.LANG_NOT_SUPPORTED
            if (voiceReady) {
                tts.setOnUtteranceProgressListener(progress)
                tts.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
            }
        }
        onStatus(
            when {
                voiceReady -> null
                status != TextToSpeech.SUCCESS -> "음성 엔진을 찾지 못했어요. 자막으로 진행합니다."
                else -> "한국어 음성이 설치되어 있지 않아요. 자막으로 진행합니다."
            }
        )
        val waiting = pending
        pending = null
        if (waiting != null) speak(waiting.first, waiting.second)
    }

    fun speak(utteranceId: String, text: String) {
        if (closed) return
        if (!initialised) {
            pending = utteranceId to text
            return
        }
        main.removeCallbacksAndMessages(TIMER)
        speakingId = utteranceId
        val estimateMs = (Speech.estimateSeconds(text) * 1000).toLong()
        if (voiceReady) {
            focus.beforeSpeaking()
            keepFocusTicking()
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.SUCCESS) {
                // The engine says when it is done. Should it never do so, carry on regardless.
                finishLater(utteranceId, estimateMs * 3 + 5_000)
                return
            }
            // The engine would not take the sentence: give it the time it would have taken, as
            // when there is no voice at all.
        }
        finishLater(utteranceId, estimateMs)
    }

    fun stop() {
        if (closed) return
        pending = null
        speakingId = null
        main.removeCallbacksAndMessages(TIMER)
        if (voiceReady) tts.stop()
        focus.afterSpeaking(SystemClock.uptimeMillis())
        keepFocusTicking()
    }

    /** The host is about to listen for an answer; see [AudioFocusKeeper.beforeListening]. */
    fun yieldForListening() {
        focus.beforeListening()
    }

    fun shutdown() {
        closed = true
        pending = null
        speakingId = null
        main.removeCallbacksAndMessages(null)
        tts.stop()
        tts.shutdown()
        focus.shutdown()
    }

    private fun finishLater(utteranceId: String, delayMs: Long) {
        main.postAtTime({ finished(utteranceId) }, TIMER, SystemClock.uptimeMillis() + delayMs)
    }

    private fun finished(utteranceId: String) {
        // The engine's word and the timer can both arrive, and either can arrive for a sentence
        // that was cut off in the meantime. Only the sentence in progress counts, and only once.
        if (utteranceId != speakingId) return
        speakingId = null
        main.removeCallbacksAndMessages(TIMER)
        focus.afterSpeaking(SystemClock.uptimeMillis())
        keepFocusTicking()
        onFinished(utteranceId)
    }

    private fun keepFocusTicking() {
        main.removeCallbacks(focusTick)
        if (focus.busy) main.postDelayed(focusTick, FOCUS_TICK_MS)
    }

    private companion object {
        /** Token for the pending "this sentence is over" callback. */
        val TIMER = Any()
        const val FOCUS_TICK_MS = 250L
        const val INIT_LIMIT_MS = 5_000L
    }
}
