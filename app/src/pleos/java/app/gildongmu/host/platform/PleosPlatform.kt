package app.gildongmu.host.platform

import android.app.Activity
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
import app.gildongmu.core.Stop
import app.gildongmu.host.AppPlatform
import app.gildongmu.host.ui.HostScreen

/**
 * Runs the host in the car, on the Pleos Connect SDK.
 *
 * STATUS: written from the public Pleos guides and NOT YET COMPILED against the SDK, because the
 * SDK repository was not reachable where this was written. The guides show how each SDK is set
 * up and called, but the reference pages with exact signatures do not render outside the Pleos
 * Playground. Every assumption is marked "VERIFY" in the adapter that makes it; expect to adjust
 * those lines on the first build with gildongmu.pleos=true. docs/pleos-integration.md has the
 * full list.
 *
 * The host logic itself (app.gildongmu.core) is the same code the mock build runs and tests.
 */
class PleosPlatform(private val activity: Activity, private val screen: HostScreen) : AppPlatform {

    private val main = Handler(Looper.getMainLooper())
    private val parked = ParkedDetector()

    private var runtime: HostRuntime? = null
    private var speaker: PleosSpeaker? = null
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

    private val device = object : HostPlatform {
        override fun speak(utteranceId: String, text: String) {
            speaker?.speak(utteranceId, text)
        }

        override fun stopSpeaking() {
            speaker?.stop()
        }

        override fun listen(mode: ListenMode, timeoutMs: Long, expected: List<String>) {
            listener?.listen()
        }

        override fun cancelListen() {
            listener?.cancel()
        }

        override fun addWaypoint(stop: Stop) {
            navigation?.addWaypoint(stop)
        }

        override fun light(cue: LightCue) {
            vehicle?.light(cue)
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
        val now = SystemClock.uptimeMillis()
        // The SDK does not report "parked" directly (VERIFY: gear or parking brake may be readable
        // through a domain not covered by the public guides), so it is derived here.
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
        // For testing on an emulator whose navigation cannot be made to start route guidance:
        // pressing and holding the destination at the top left starts the trip by hand. It changes
        // nothing once a trip is under way. Take it out before the app goes into a real car.
        screen.onDestinationHeld = { handle(Signal.RouteStarted()) }

        // Until the content server exists, the car plays the route package bundled with the app.
        val route = try {
            RoutePackageParser.parse(activity.assets.open(ROUTE).bufferedReader().use { it.readText() })
        } catch (e: Exception) {
            Log.e(TAG, "Could not load the route package", e)
            return
        }
        runtime = HostRuntime(HostEngine(route), device) { SystemClock.uptimeMillis() }

        speaker = PleosSpeaker(activity) { id -> emit(Signal.SpeechFinished(id)) }
        listener = PleosListener(activity) { text -> emit(Signal.Utterance(text)) }
        navigation = PleosNavigation(activity, ::emit)
        vehicle = PleosVehicle(activity, ::emit).also { it.start() }
        location = PleosLocation(activity, ::emit).also { it.start() }

        handle(Signal.Tick) // draws the first frame
        main.postDelayed(heartbeat, 1000)
    }

    override fun onPermissionsChanged() {}

    override fun shutdown() {
        main.removeCallbacksAndMessages(null)
        location?.shutdown()
        vehicle?.shutdown()
        navigation?.shutdown()
        listener?.shutdown()
        speaker?.shutdown()
        runtime = null
    }

    private companion object {
        const val TAG = "Gildongmu"
        const val ROUTE = "routes/seoul-gangneung.json"
    }
}
