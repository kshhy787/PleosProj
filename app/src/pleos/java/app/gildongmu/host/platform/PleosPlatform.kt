package app.gildongmu.host.platform

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import app.gildongmu.core.HostEngine
import app.gildongmu.core.HostPlatform
import app.gildongmu.core.HostRuntime
import app.gildongmu.core.HostSnapshot
import app.gildongmu.core.LightCue
import app.gildongmu.core.ListenMode
import app.gildongmu.core.ParkedDetector
import app.gildongmu.core.RoutePackageParser
import app.gildongmu.core.Signal
import app.gildongmu.core.Waypoint
import app.gildongmu.core.WordList
import app.gildongmu.core.sim.DemoSession
import app.gildongmu.core.sim.ScenarioParser
import app.gildongmu.host.AppPlatform
import app.gildongmu.host.speech.CaptionSpeaker
import app.gildongmu.host.speech.Speaker
import app.gildongmu.host.ui.HostScreen

/**
 * Runs the host in the car, on the Pleos Connect SDK.
 *
 * STATUS: written from the public Pleos guides, then checked against the SDK's class files and
 * compiled against the SDK (assemblePleosDebug). It has NOT RUN on a car or an emulator yet. What
 * only a device can settle (permissions being granted, the services behind the SDKs, units) is
 * marked "VERIFY" in the adapter it concerns; docs/pleos-integration.md has the full list.
 *
 * The host logic itself (app.gildongmu.core) is the same code the mock build runs and tests.
 */
class PleosPlatform(private val activity: Activity, private val screen: HostScreen) : AppPlatform {

    private val main = Handler(Looper.getMainLooper())
    private val parked = ParkedDetector()

    private var runtime: HostRuntime? = null
    private var speaker: Speaker? = null
    private var listener: PleosListener? = null
    private var navigation: PleosNavigation? = null
    private var vehicle: PleosVehicle? = null
    private var location: PleosLocation? = null

    private val heartbeat = object : Runnable {
        override fun run() {
            runtime?.dispatch(Signal.Tick)
            main.postDelayed(this, 1000)
        }
    }

    /**
     * A scripted drive in the car's own voice, for showing the app on an emulator that has no
     * route to follow. Once started, position, trip time, battery, stops and navigation prompts
     * come from the scenario file, exactly as in the mock build, and the real car's signals are
     * no longer listened to; speech still goes through the Pleos SDK. The scripted passengers
     * answer the host, so the microphone stays closed. See [startDemoDrive].
     */
    private var demo: DemoSession? = null
    private var demoTickedAtMs = 0L

    private val demoTicker = object : Runnable {
        override fun run() {
            val session = demo ?: return
            val now = SystemClock.uptimeMillis()
            // A long gap (a stalled emulator, the app in the background) must not fast-forward the drive.
            val delta = (now - demoTickedAtMs).coerceIn(0L, 500L)
            demoTickedAtMs = now
            session.tick(delta)
            main.postDelayed(this, DEMO_TICK_MS)
        }
    }

    private val device = object : HostPlatform {
        override fun speak(utteranceId: String, text: String) {
            speaker?.speak(utteranceId, text)
        }

        override fun stopSpeaking() {
            speaker?.stop()
        }

        override fun listen(mode: ListenMode, timeoutMs: Long, expected: List<String>) {
            // In a demo drive the scripted passengers answer; an open microphone would let
            // whatever is said in the room answer first.
            if (demo == null) listener?.listen()
        }

        override fun cancelListen() {
            if (demo == null) listener?.cancel()
        }

        override fun addWaypoint(waypoint: Waypoint) {
            // The scripted car has no real route to add a stop to.
            if (demo == null) navigation?.addWaypoint(waypoint)
        }

        override fun light(cue: LightCue) {
            vehicle?.light(cue)
            // Until the cabin light is wired up, the card stands in for it, washed in the cue's colour.
            screen.flash(cue)
        }

        override fun setExpandedUi(enabled: Boolean) {
            screen.setExpanded(enabled)
        }

        override fun render(snapshot: HostSnapshot) {
            screen.render(snapshot)
        }
    }

    /** SDK callbacks can arrive on any thread; the host is only ever touched on the main thread. */
    private fun emit(signal: Signal) {
        main.post { handle(signal) }
    }

    private fun handle(signal: Signal) {
        val session = demo
        if (session != null) {
            // The scripted car is the car now. Only what this device itself reports still counts:
            // a sentence ending, the skip button, the parked screen's shortcuts, a touch on a tile.
            when (signal) {
                is Signal.SpeechFinished, is Signal.Utterance, Signal.SkipPressed, is Signal.OfferTapped -> session.dispatch(signal)
                else -> {}
            }
            return
        }
        val now = SystemClock.uptimeMillis()
        // "Parked" is derived here from position and charging. The Vehicle SDK can read the gear
        // (Gear.registerGearSelection, VehicleGear.GEAR_PARK, under CAR_DRIVE), which would be
        // the better source, but that is not wired up yet.
        when (signal) {
            is Signal.Location -> parked.onLocation(signal.point, now)?.let { runtime?.dispatch(Signal.Parked(it)) }
            is Signal.Battery -> parked.onCharging(signal.charging)?.let { runtime?.dispatch(Signal.Parked(it)) }
            else -> {}
        }
        runtime?.dispatch(signal)
    }

    override fun start() {
        screen.onSkip = { handle(Signal.SkipPressed) }
        screen.onShortcut = { text -> handle(Signal.Utterance(text)) }
        // Sent whenever a tile is touched; whether a touch may choose a spot is the host's rule.
        screen.onOfferTapped = { index -> handle(Signal.OfferTapped(index)) }
        // For showing the app on an emulator that cannot be made to start route guidance or to
        // move along a route: pressing and holding the destination at the top left starts the
        // scripted demo drive. Take it out before the app goes into a real car.
        screen.onDestinationHeld = { startDemoDrive() }

        // Until the content server exists, the car plays the route package bundled with the app.
        val route = try {
            RoutePackageParser.parse(asset(ROUTE))
        } catch (e: Exception) {
            Log.e(TAG, "Could not load the route package", e)
            return
        }
        runtime = HostRuntime(HostEngine(route, words = loadWords()), device) { SystemClock.uptimeMillis() }

        // If the car's voice is not working, what it would have said goes on the screen instead.
        val onVoiceStatus = { problem: String? -> if (problem != null) screen.captions = true }
        // The host waits to hear that each sentence is over, so it must always have a speaker:
        // without the SDK's, one that only keeps time.
        speaker = attempt("TextToSpeech") {
            PleosSpeaker(activity, onFinished = { id -> emit(Signal.SpeechFinished(id)) }, onStatus = onVoiceStatus)
        } ?: CaptionSpeaker(onFinished = { id -> emit(Signal.SpeechFinished(id)) }, onStatus = onVoiceStatus)
        listener = attempt("SpeechToText") { PleosListener(activity) { text -> emit(Signal.Utterance(text)) } }
        navigation = attempt("NaviHelper") { PleosNavigation(activity, ::emit) }
        vehicle = attempt("Vehicle") { PleosVehicle(activity, ::emit).also { it.start() } }
        location = attempt("FusedLocation") { PleosLocation(activity, ::emit).also { it.start() } }
        // FUSED_LOCATION is a dangerous permission: until it is granted, PleosLocation waits, and
        // onPermissionsChanged starts it once the dialog is answered.
        if (location != null && activity.checkSelfPermission(PleosLocation.PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(PleosLocation.PERMISSION), LOCATION_REQUEST)
        }
        Log.i(TAG, "Voice: ${speaker?.describe()}")

        handle(Signal.Tick) // draws the first frame
        main.postDelayed(heartbeat, 1000)
    }

    override fun onPermissionsChanged() {
        location?.let { l -> attempt("FusedLocation") { l.start() } }
    }

    /** Hands the trip over to the scripted car. See [demo]. */
    private fun startDemoDrive() {
        if (demo != null) return
        val session = try {
            DemoSession(
                RoutePackageParser.parse(asset(ROUTE)),
                ScenarioParser.parse(asset(SCENARIO)),
                device,
                loadWords()
            ) { SystemClock.uptimeMillis() }
        } catch (e: Exception) {
            Log.e(TAG, "Could not start the demo drive", e)
            return
        }
        Log.i(TAG, "Demo drive: the scripted car takes over from the real one")
        main.removeCallbacks(heartbeat)
        runtime = null
        speaker?.stop()
        listener?.cancel()
        // A demo is for showing: the sentences go on the screen as well.
        screen.captions = true
        demo = session
        session.start()
        demoTickedAtMs = SystemClock.uptimeMillis()
        main.post(demoTicker)
    }

    /**
     * Sets up one SDK. An SDK that is missing or fails on this device (an emulator image without
     * the service behind it, say) is logged and left out; it must not take the others, or the
     * app, down with it.
     */
    private fun <T> attempt(what: String, create: () -> T): T? = try {
        create()
    } catch (e: Exception) {
        Log.e(TAG, "$what is not available", e)
        null
    } catch (e: LinkageError) {
        Log.e(TAG, "$what is not available", e)
        null
    }

    /**
     * The host's 끝말잇기 words. A missing or broken list must not stop the app: it is logged, and
     * the host gets no words, so it says it cannot play.
     */
    private fun loadWords(): List<String> = try {
        WordList.parse(asset(WORDS))
    } catch (e: Exception) {
        Log.e(TAG, "Could not load the word list", e)
        emptyList()
    }

    private fun asset(path: String): String =
        activity.assets.open(path).bufferedReader().use { it.readText() }

    override fun shutdown() {
        main.removeCallbacksAndMessages(null)
        location?.shutdown()
        vehicle?.shutdown()
        navigation?.shutdown()
        listener?.shutdown()
        speaker?.shutdown()
        runtime = null
        demo = null
    }

    private companion object {
        const val TAG = "Gildongmu"
        const val ROUTE = "routes/seoul-gangneung.json"
        const val SCENARIO = "scenarios/seoul-gangneung-demo.json"
        const val WORDS = "games/word-chain.txt"
        const val DEMO_TICK_MS = 100L
        const val LOCATION_REQUEST = 1
    }
}
