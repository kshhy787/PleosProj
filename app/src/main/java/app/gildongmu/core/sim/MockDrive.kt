package app.gildongmu.core.sim

import app.gildongmu.core.ListenMode
import app.gildongmu.core.RouteGeometry
import app.gildongmu.core.RoutePackage
import app.gildongmu.core.Signal
import app.gildongmu.core.Stop
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A scripted car. It moves along the route at the scenario's speeds and reports what a real car
 * would: position, trip time, battery, parking, navigation prompts. The host cannot tell it from
 * the Pleos adapters, because both speak in [Signal]s.
 *
 * It is driven by [advance] and has no clock or thread of its own, so the same class runs on the
 * Android main thread and in unit tests.
 */
class MockDrive(private val pkg: RoutePackage, private val scenario: Scenario) {

    private class Timer(val atMs: Long, val signal: Signal, val isReply: Boolean)

    private val geometry = RouteGeometry(pkg.polyline)
    private val stopKm: Map<String, Double> = pkg.stops.associate { it.id to geometry.project(it.location).routeKm }

    private var realMs = 0L
    private var started = false
    private var battery = scenario.startBatteryPercent
    private var lastReportedBattery = Int.MIN_VALUE
    private var lastReportedCharging = false
    private var nextEvent = 0
    private val timers = ArrayList<Timer>()
    private var quizReplies = 0

    private var acceptedStop: Stop? = null
    private var restingAt: ScenarioStop? = null
    private var restRemainingMs = 0.0
    private var restChargePerMs = 0.0

    /** Kilometres along the route. */
    var progressKm = 0.0
        private set

    /** Driving time so far, in simulated minutes. Time spent parked does not count. */
    var tripMinutes = 0.0
        private set

    var arrived = false
        private set

    val parked: Boolean get() = restingAt != null || arrived

    val routeLengthKm: Double get() = geometry.lengthKm

    /** The stop the host asked navigation to add, until the car has left it. */
    val waypoint: Stop? get() = acceptedStop

    /** When false, scripted passenger replies are off and someone has to answer the host. */
    var autoRepliesEnabled = true

    /** Begins the trip: navigation starts guiding and the car reports its first readings. */
    fun start(): List<Signal> {
        if (started) return emptyList()
        started = true
        val out = ArrayList<Signal>()
        out.add(Signal.Parked(false))
        out.add(Signal.Location(geometry.pointAt(0.0), currentKph()))
        out.add(Signal.Trip(0.0, 0.0))
        reportBattery(charging = false, out = out)
        out.add(Signal.RouteStarted(distanceKm = geometry.lengthKm, etaMinutes = estimateMinutes()))
        return out
    }

    /** Moves real time forward by [deltaMs] and returns what the car reports as a result. */
    fun advance(deltaMs: Long): List<Signal> {
        if (!started) return emptyList()
        val out = ArrayList<Signal>()
        realMs += deltaMs
        fireTimers(out)
        if (arrived) return out

        if (restingAt != null) rest(deltaMs, out) else drive(deltaMs, out)
        return out
    }

    /** The host asked navigation to add [stop] to the route. */
    fun onWaypointAdded(stop: Stop) {
        if (stopKm.getValue(stop.id) > progressKm) acceptedStop = stop
    }

    /** The host started listening; schedule the scripted reply, if the scenario has one. */
    fun onListen(mode: ListenMode, expected: List<String>) {
        val replies = scenario.autoReplies ?: return
        if (!autoRepliesEnabled) return
        val text = when (mode) {
            ListenMode.YES_NO -> replies.yesNo
            ListenMode.QUIZ_ANSWER -> {
                val script = replies.quiz.getOrNull(quizReplies++) ?: "correct"
                when (script) {
                    "correct" -> expected.firstOrNull()
                    "wrong" -> "잘 모르겠어요"
                    else -> script
                }
            }
        } ?: return
        timers.add(Timer(realMs + replies.delayMs, Signal.Utterance(text), isReply = true))
    }

    /** The host stopped listening; a reply that has not been said yet is dropped. */
    fun onListenCancelled() {
        timers.removeAll { it.isReply }
    }

    private fun fireTimers(out: MutableList<Signal>) {
        val due = timers.filter { it.atMs <= realMs }.sortedBy { it.atMs }
        if (due.isEmpty()) return
        timers.removeAll(due)
        due.forEach { out.add(it.signal) }
    }

    private fun drive(deltaMs: Long, out: MutableList<Signal>) {
        val leg = currentLeg()
        val simulatedHours = deltaMs / 1000.0 * (leg.timeScale ?: scenario.timeScale) / 3600.0
        val kph = leg.kph
        var targetKm = progressKm + kph * simulatedHours

        val stop = acceptedStop
        val stopAt = stop?.let { stopKm.getValue(it.id) }
        val reachesStop = stopAt != null && targetKm >= stopAt
        if (stopAt != null && reachesStop) targetKm = stopAt
        val reachesEnd = !reachesStop && targetKm >= geometry.lengthKm
        if (reachesEnd) targetKm = geometry.lengthKm

        val drivenKm = max(0.0, targetKm - progressKm)
        progressKm = targetKm
        tripMinutes += if (kph > 0.0) drivenKm / kph * 60.0 else 0.0
        battery = max(0.0, battery - drivenKm / scenario.kmPerBatteryPercent)

        out.add(Signal.Location(geometry.pointAt(progressKm), if (reachesStop || reachesEnd) 0.0 else kph))
        out.add(Signal.Trip(tripMinutes, progressKm))
        reportBattery(charging = false, out = out)
        fireEvents(out)

        if (stop != null && reachesStop) {
            park(stop, out)
        } else if (reachesEnd) {
            arrived = true
            out.add(Signal.DestinationArrived)
            out.add(Signal.Parked(true))
        }
    }

    private fun fireEvents(out: MutableList<Signal>) {
        while (nextEvent < scenario.events.size && scenario.events[nextEvent].atKm <= progressKm) {
            when (val event = scenario.events[nextEvent++]) {
                is ScenarioEvent.Say -> out.add(Signal.Utterance(event.text))
                is ScenarioEvent.Turn -> {
                    out.add(Signal.TurnGuidance(event.distanceM))
                    timers.add(Timer(realMs + (event.holdSeconds * 1000).toLong(), Signal.TurnGuidance(null), isReply = false))
                }
                is ScenarioEvent.Warning -> {
                    out.add(Signal.VehicleWarning(true))
                    timers.add(Timer(realMs + (event.holdSeconds * 1000).toLong(), Signal.VehicleWarning(false), isReply = false))
                }
            }
        }
    }

    private fun park(stop: Stop, out: MutableList<Signal>) {
        val plan = scenario.stops.firstOrNull { it.stopId == stop.id } ?: ScenarioStop(stop.id, 30.0, null)
        restingAt = plan
        restRemainingMs = plan.seconds * 1000.0
        val target = plan.chargeToPercent
        val gain = if (stop.hasCharger && target != null) target - battery else 0.0
        val charges = gain > 0.0 && restRemainingMs > 0.0
        restChargePerMs = if (charges) gain / restRemainingMs else 0.0
        reportBattery(charging = charges, out = out)
        out.add(Signal.Parked(true))
    }

    private fun rest(deltaMs: Long, out: MutableList<Signal>) {
        val step = min(deltaMs.toDouble(), restRemainingMs)
        restRemainingMs -= step
        if (restChargePerMs > 0.0) {
            battery = min(100.0, battery + restChargePerMs * step)
            reportBattery(charging = restRemainingMs > 0.0, out = out)
        }
        if (restRemainingMs <= 0.0) {
            restingAt = null
            acceptedStop = null
            reportBattery(charging = false, out = out)
            out.add(Signal.Parked(false))
        }
    }

    private fun reportBattery(charging: Boolean, out: MutableList<Signal>) {
        val percent = battery.roundToInt()
        if (percent == lastReportedBattery && charging == lastReportedCharging) return
        lastReportedBattery = percent
        lastReportedCharging = charging
        out.add(Signal.Battery(percent, charging))
    }

    private fun currentLeg(): Leg = scenario.legs.firstOrNull { progressKm < it.untilKm } ?: scenario.legs.last()

    private fun currentKph(): Double = currentLeg().kph

    /** Driving time for the whole route at the scenario's speeds, in minutes. */
    private fun estimateMinutes(): Int {
        var minutes = 0.0
        var fromKm = 0.0
        for (leg in scenario.legs) {
            val toKm = min(leg.untilKm, geometry.lengthKm)
            if (toKm > fromKm) minutes += (toKm - fromKm) / leg.kph * 60.0
            fromKm = max(fromKm, toKm)
        }
        if (fromKm < geometry.lengthKm) minutes += (geometry.lengthKm - fromKm) / scenario.legs.last().kph * 60.0
        return minutes.roundToInt()
    }
}
