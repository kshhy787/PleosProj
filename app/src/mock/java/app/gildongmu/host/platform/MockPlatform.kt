package app.gildongmu.host.platform

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import app.gildongmu.core.HostPlatform
import app.gildongmu.core.HostSnapshot
import app.gildongmu.core.LightCue
import app.gildongmu.core.ListenMode
import app.gildongmu.core.RoutePackageParser
import app.gildongmu.core.Signal
import app.gildongmu.core.Stop
import app.gildongmu.core.sim.DemoSession
import app.gildongmu.core.sim.ScenarioParser
import app.gildongmu.host.AppPlatform
import app.gildongmu.host.speech.AndroidListener
import app.gildongmu.host.speech.AndroidSpeaker
import app.gildongmu.host.speech.CaptionSpeaker
import app.gildongmu.host.speech.Speaker
import app.gildongmu.host.ui.HostScreen

/**
 * Runs the host against a scripted drive instead of a car.
 *
 * Position, trip time, battery, parking and navigation prompts come from the scenario in
 * `assets/scenarios`; speech and listening use Android's own services; "navigation" and the
 * "ambient light" are shown on screen. The host itself is exactly the code the `pleos` build runs.
 */
class MockPlatform(private val activity: Activity, private val screen: HostScreen) : AppPlatform, DemoPanel.Actions {

    private val main = Handler(Looper.getMainLooper())
    private val panel = DemoPanel(activity, this)
    private var speaker: Speaker? = null

    /** [speaker], when it is Android's own engine: the one that shares the audio channel. */
    private var voice: AndroidSpeaker? = null
    private var listener: AndroidListener? = null
    private var session: DemoSession? = null

    private var started = false
    private var running = false
    private var lastTickMs = 0L

    /** The host has asked a question and is waiting for the answer. */
    private var hostListening = false

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.uptimeMillis()
            // A long gap (the app was in the background) must not fast-forward the drive.
            val delta = (now - lastTickMs).coerceIn(0L, 500L)
            lastTickMs = now
            session?.tick(delta)
            main.postDelayed(this, TICK_MS)
        }
    }

    /**
     * Refreshes the panel's diagnostics once a second, whether or not the drive is running, and
     * writes them to the log whenever they change, so that they can be read without the screen.
     */
    private val diagnosing = object : Runnable {
        private var logged = ""

        override fun run() {
            val lines = listOf(
                "화면  ${screen.describe()}",
                "말하기  ${speaker?.describe() ?: "없음"}",
                "듣기  ${listener?.describe() ?: "없음"}",
                "기기  ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                "빌드  mock · $BUILD_NOTE"
            )
            panel.setDiagnostics(lines)
            val text = lines.joinToString(" | ")
            if (text != logged) {
                logged = text
                Log.i(TAG, "진단: $text")
            }
            main.postDelayed(this, 1000)
        }
    }

    /** What the host asks of the device: speak, listen, and show things. */
    private val device = object : HostPlatform {
        override fun speak(utteranceId: String, text: String) {
            speaker?.speak(utteranceId, text)
        }

        override fun stopSpeaking() {
            speaker?.stop()
        }

        override fun listen(mode: ListenMode, timeoutMs: Long, expected: List<String>) {
            hostListening = true
            panel.setListening(mode, expected)
            openMicrophone()
        }

        override fun cancelListen() {
            hostListening = false
            panel.setListening(null, emptyList())
            listener?.cancel()
        }

        override fun addWaypoint(stop: Stop) {
            // The scripted car picks this up through the session; the panel shows it as navigation would.
        }

        override fun light(cue: LightCue) {
            screen.flash(cue)
        }

        override fun setExpandedUi(enabled: Boolean) {
            screen.setExpanded(enabled)
        }

        override fun render(snapshot: HostSnapshot) {
            screen.render(snapshot)
            panel.update(snapshot, session?.drive?.arrived == true)
        }
    }

    override fun start() {
        screen.sidePanel.addView(panel.root)
        screen.setSidePanelVisible(true)
        screen.captions = true
        screen.onSkip = { session?.dispatch(Signal.SkipPressed) }
        screen.onShortcut = { text -> onSay(text) }

        // Before the speaker exists, because the speaker may have something to say about itself at once.
        panel.setVoiceStatus("준비 중")
        // The host waits to hear that each sentence is over, so it must always have a speaker.
        // If Android's engine cannot even be created on this device, one that only keeps time.
        voice = try {
            AndroidSpeaker(
                activity,
                onFinished = { id -> session?.dispatch(Signal.SpeechFinished(id)) },
                onAudioFocus = { available -> session?.dispatch(Signal.AudioFocus(available)) },
                onStatus = { problem -> panel.setVoiceStatus(problem) }
            )
        } catch (e: RuntimeException) {
            Log.e(TAG, "Android's speech engine could not be created", e)
            null
        }
        speaker = voice ?: CaptionSpeaker(
            onFinished = { id -> session?.dispatch(Signal.SpeechFinished(id)) },
            onStatus = { problem -> panel.setVoiceStatus(problem) }
        )
        listener = AndroidListener(activity) { text -> onSay(text) }
        panel.setRunning(running = false, started = false)

        try {
            val route = RoutePackageParser.parse(asset(ROUTE))
            val scenario = ScenarioParser.parse(asset(SCENARIO))
            val created = DemoSession(route, scenario, device) { SystemClock.uptimeMillis() }
            created.onPassengerSpeech = { text -> panel.showPassenger(text) }
            session = created
            created.dispatch(Signal.Tick) // draws the first frame
        } catch (e: Exception) {
            // A broken route or scenario file: say which, rather than showing an empty screen.
            Log.e(TAG, "Could not load the demo", e)
            panel.showPassenger("데모를 불러오지 못했어요: ${e.message}")
        }

        showHearing()
        main.post(diagnosing)
    }

    override fun onPermissionsChanged() {
        showHearing()
        if (hostListening) openMicrophone()
    }

    /**
     * Opens the microphone for an answer, unless the scripted passengers are answering. With
     * scripted replies on, anything the microphone picked up in the room would answer before the
     * script did, and no two runs of the demo would go the same way.
     */
    private fun openMicrophone() {
        if (scripted()) return
        voice?.yieldForListening()
        listener?.listen()
    }

    private fun scripted(): Boolean = session?.drive?.autoRepliesEnabled != false

    private fun showHearing() {
        panel.setHearingStatus(available = listener?.available == true, scripted = scripted())
    }

    override fun shutdown() {
        running = false
        main.removeCallbacks(ticker)
        main.removeCallbacks(diagnosing)
        listener?.shutdown()
        speaker?.shutdown()
        listener = null
        speaker = null
        voice = null
        session = null
    }

    // ---- demo panel ----

    override fun onStartOrPause() {
        val current = session ?: return
        if (!started) {
            started = true
            current.start()
        }
        running = !running
        if (running) {
            lastTickMs = SystemClock.uptimeMillis()
            main.removeCallbacks(ticker)
            main.post(ticker)
        }
        panel.setRunning(running, started)
    }

    override fun onRestart() {
        activity.recreate()
    }

    override fun onAutoReplies(enabled: Boolean) {
        session?.drive?.autoRepliesEnabled = enabled
        // The microphone is only needed once somebody wants to answer in person, so that is when
        // to ask for it, not when the app starts.
        if (!enabled && activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MICROPHONE_REQUEST)
        }
        // Switching in the middle of a question: hand the answer over to whoever now gives it.
        if (enabled) listener?.cancel() else if (hostListening) openMicrophone()
        showHearing()
    }

    override fun onCaptions(enabled: Boolean) {
        screen.captions = enabled
    }

    override fun onHidePanel() {
        screen.setSidePanelVisible(false)
    }

    /** Something was said to the host: by the microphone, a panel button, or a parked-screen shortcut. */
    override fun onSay(text: String) {
        panel.showPassenger(text)
        session?.dispatch(Signal.Utterance(text))
    }

    private fun asset(path: String): String =
        activity.assets.open(path).bufferedReader().use { it.readText() }

    private companion object {
        const val TAG = "Gildongmu"
        const val ROUTE = "routes/seoul-gangneung.json"
        const val SCENARIO = "scenarios/seoul-gangneung-demo.json"
        const val TICK_MS = 100L

        /** Shown in the diagnostics, to tell from a screenshot which version of the code is running. */
        const val BUILD_NOTE = "2026-11-14"
        const val MICROPHONE_REQUEST = 1
    }
}
