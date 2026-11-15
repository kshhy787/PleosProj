package app.gildongmu.core.sim

import app.gildongmu.core.HostConfig
import app.gildongmu.core.HostEngine
import app.gildongmu.core.HostPlatform
import app.gildongmu.core.HostRuntime
import app.gildongmu.core.ListenMode
import app.gildongmu.core.RoutePackage
import app.gildongmu.core.Signal
import app.gildongmu.core.Waypoint

/**
 * A complete demo: the host, a scripted car, and whatever does the talking and listening.
 *
 * [device] supplies speech, listening and the screen. The session sits between the host and the
 * device so that the scripted car hears about the things a real car would act on: a waypoint being
 * added, and the host starting or stopping to listen. [words] are the host's 끝말잇기 words, as
 * for [HostEngine].
 */
class DemoSession(
    pkg: RoutePackage,
    scenario: Scenario,
    device: HostPlatform,
    words: List<String> = emptyList(),
    clock: () -> Long
) {
    init {
        // A scenario is written against one particular package and refers to its places and stops
        // by id. Paired with another package (a regenerated one, whose ids differ) it would quietly
        // drop every place and play an empty drive, so refuse the pair and say what does not fit.
        val problems = ArrayList<String>()
        if (scenario.routeId != pkg.id) {
            problems.add("it is written for route \"${scenario.routeId}\" but the package is \"${pkg.id}\"")
        }
        val unknownPois = scenario.onlyPois - pkg.pois.map { it.id }.toSet()
        if (unknownPois.isNotEmpty()) problems.add("onlyPois names places the package does not have: ${unknownPois.joinToString()}")
        val unknownStops = scenario.stops.map { it.stopId } - pkg.stops.map { it.id }.toSet()
        if (unknownStops.isNotEmpty()) problems.add("stops names stops the package does not have: ${unknownStops.joinToString()}")
        if (problems.isNotEmpty()) {
            throw ScenarioFormatException("The scenario does not match the route package: ${problems.joinToString("; ")}")
        }
    }

    /** The route as the demo uses it: the scenario may keep only some of the places. */
    val route: RoutePackage =
        if (scenario.onlyPois.isEmpty()) pkg else pkg.copy(pois = pkg.pois.filter { it.id in scenario.onlyPois })

    val drive = MockDrive(route, scenario)

    private val engine = HostEngine(
        route,
        HostConfig().let { defaults ->
            defaults.copy(
                players = scenario.players.ifEmpty { defaults.players },
                quizQuestionsPerRound = scenario.quizQuestionsPerRound ?: defaults.quizQuestionsPerRound
            )
        },
        words
    )

    private val platform = object : HostPlatform by device {
        override fun addWaypoint(waypoint: Waypoint) {
            drive.onWaypointAdded(waypoint)
            device.addWaypoint(waypoint)
        }

        override fun listen(mode: ListenMode, timeoutMs: Long, expected: List<String>) {
            drive.onListen(mode, expected)
            device.listen(mode, timeoutMs, expected)
        }

        override fun cancelListen() {
            drive.onListenCancelled()
            device.cancelListen()
        }
    }

    private val runtime = HostRuntime(engine, platform, clock)

    /** Called with each line a scripted passenger says, so the demo can show it on screen. */
    var onPassengerSpeech: ((String) -> Unit)? = null

    /** Starts navigation and the trip. */
    fun start() {
        drive.start().forEach(runtime::dispatch)
    }

    /** Advances the scripted car by [deltaMs] of real time and lets the host react. */
    fun tick(deltaMs: Long) {
        for (signal in drive.advance(deltaMs)) {
            if (signal is Signal.Utterance) onPassengerSpeech?.invoke(signal.text)
            runtime.dispatch(signal)
        }
        runtime.dispatch(Signal.Tick)
    }

    /** For the device's own callbacks: speech finished, something was said, skip was pressed. */
    fun dispatch(signal: Signal) = runtime.dispatch(signal)
}
