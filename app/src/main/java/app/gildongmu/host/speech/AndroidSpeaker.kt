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
import app.gildongmu.core.SpeechGuard
import java.util.Locale

/**
 * Speaks with Android's own text-to-speech engine. Used by the mock build in place of the Pleos
 * TextToSpeech SDK, which only exists in the car.
 *
 * This class is only the wiring. When a sentence is over is decided by [SpeechGuard], which
 * copes with a missing engine, a missing Korean voice, and engines that never report or report
 * without having spoken; when the app holds the audio channel is decided by [AudioFocusKeeper].
 * Both are plain logic with tests of their own.
 *
 * Must be used from the main thread, where its callbacks also arrive.
 */
class AndroidSpeaker(
    context: Context,
    private val onFinished: (utteranceId: String) -> Unit,
    private val onAudioFocus: (available: Boolean) -> Unit,
    private val onStatus: (message: String?) -> Unit
) : Speaker {
    private val main = Handler(Looper.getMainLooper())
    private val audio = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var closed = false

    // Kept for describe(): what the engine answered when it was set up.
    private var initStatus: Int? = null
    private var languageResult: Int? = null

    // These refer to one another, so each is given its type in full.
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
            keepTicking()
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

    private val guard: SpeechGuard = SpeechGuard(
        say = { utteranceId, text ->
            focus.beforeSpeaking()
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.SUCCESS
        },
        silence = { tts.stop() },
        onFinished = { utteranceId ->
            focus.afterSpeaking(SystemClock.uptimeMillis())
            onFinished(utteranceId)
        },
        onStatus = { message -> onStatus(message) },
        createdAtMs = SystemClock.uptimeMillis()
    )

    /** Drives the two timers above while either has something pending. */
    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            guard.tick(now)
            focus.tick(now)
            if (guard.busy || focus.busy) main.postDelayed(this, TICK_MS)
        }
    }

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}

        override fun onDone(utteranceId: String?) {
            report(utteranceId, failed = false)
        }

        @Deprecated("Deprecated in the platform, but still abstract")
        override fun onError(utteranceId: String?) {
            report(utteranceId, failed = true)
        }
    }

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        main.post { onInit(status) }
    }

    init {
        // The guard is waiting for the engine to start, and gives up on it after a while.
        keepTicking()
    }

    private fun onInit(status: Int) {
        if (closed) return
        initStatus = status
        var voice = false
        if (status == TextToSpeech.SUCCESS) {
            val language = tts.setLanguage(Locale.KOREAN)
            languageResult = language
            voice = language != TextToSpeech.LANG_MISSING_DATA && language != TextToSpeech.LANG_NOT_SUPPORTED
            if (voice) {
                tts.setOnUtteranceProgressListener(progress)
                tts.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
            }
        }
        val problem = when {
            voice -> null
            status != TextToSpeech.SUCCESS -> SpeechGuard.NO_ENGINE
            else -> "한국어 음성이 설치되어 있지 않아요. 자막으로 진행합니다."
        }
        guard.onEngineStarted(voice, problem, SystemClock.uptimeMillis())
        keepTicking()
    }

    /** The engine calls back on a thread of its own; the guard is only touched on the main one. */
    private fun report(utteranceId: String?, failed: Boolean) {
        main.post {
            if (closed) return@post
            guard.onEngineReport(utteranceId, failed, SystemClock.uptimeMillis())
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
        focus.afterSpeaking(SystemClock.uptimeMillis())
        keepTicking()
    }

    /** The host is about to listen for an answer; see [AudioFocusKeeper.beforeListening]. */
    fun yieldForListening() {
        focus.beforeListening()
    }

    /** What the engine is and how it has behaved, in words, for the demo panel's diagnostics. */
    override fun describe(): String {
        // Asking a broken engine about itself must not take the screen down with it.
        val engines = try { tts.engines.size } catch (e: RuntimeException) { -1 }
        val engine = try { tts.defaultEngine } catch (e: RuntimeException) { null }
        val setUp = when (initStatus) {
            null -> "응답 없음"
            TextToSpeech.SUCCESS -> "성공"
            else -> "실패"
        }
        val korean = when (languageResult) {
            null -> "확인 못 함"
            TextToSpeech.LANG_MISSING_DATA -> "음성 데이터 없음"
            TextToSpeech.LANG_NOT_SUPPORTED -> "지원 안 함"
            else -> "있음"
        }
        return "Android 엔진 ${engines}개(${engine ?: "없음"}) · 초기화 $setUp · 한국어 $korean · ${guard.describe()}"
    }

    override fun shutdown() {
        closed = true
        main.removeCallbacksAndMessages(null)
        tts.stop()
        tts.shutdown()
        focus.shutdown()
    }

    private fun keepTicking() {
        main.removeCallbacks(tick)
        if (guard.busy || focus.busy) main.postDelayed(tick, TICK_MS)
    }

    private companion object {
        const val TICK_MS = 100L
    }
}
