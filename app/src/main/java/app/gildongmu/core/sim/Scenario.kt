package app.gildongmu.core.sim

import app.gildongmu.core.Json
import app.gildongmu.core.JsonException
import app.gildongmu.core.JsonObject

/** Thrown when a scenario file is missing something the mock drive needs. */
class ScenarioFormatException(message: String) : Exception(message)

/**
 * Drive at [kph] until [untilKm] along the route. [timeScale] overrides the scenario's time
 * compression for this stretch, so the drive can rush through empty road and slow down where the
 * host has something to say.
 */
data class Leg(val untilKm: Double, val kph: Double, val timeScale: Double?)

/** Something that happens when the car passes [atKm]. */
sealed interface ScenarioEvent {
    val atKm: Double

    /** A passenger says [text]. */
    data class Say(override val atKm: Double, val text: String) : ScenarioEvent

    /** A navigation prompt is [distanceM] away, and has passed after [holdSeconds] real seconds. */
    data class Turn(override val atKm: Double, val distanceM: Int, val holdSeconds: Double) : ScenarioEvent

    /** A vehicle warning shows for [holdSeconds] real seconds. */
    data class Warning(override val atKm: Double, val holdSeconds: Double) : ScenarioEvent
}

/** How long to stay at a stop the host got added to the route, in real seconds. */
data class ScenarioStop(val stopId: String, val seconds: Double, val chargeToPercent: Int?)

/**
 * Scripted passenger replies, so the demo can run hands-free. [yesNo] answers the host's yes/no
 * questions; [quiz] entries are "correct", "wrong", or the literal words to say. [choice] is what
 * to say when the host asks which recommended spot to go to; without it nobody answers, so no spot
 * is added and the drive keeps to its script. In 끝말잇기 the scripted player always says the first
 * of the host's hints.
 */
data class AutoReplies(val delayMs: Long, val yesNo: String?, val quiz: List<String>, val choice: String?)

/**
 * A scripted drive along a route package: how fast time runs, how fast the car goes, and what
 * happens on the way. It stands in for the car so the host can be shown and tested without one.
 */
data class Scenario(
    val routeId: String,
    /** Simulated seconds per real second, unless a leg says otherwise. 1 is real time. */
    val timeScale: Double,
    val players: List<String>,
    /** Questions per quiz round; null keeps the host's default. */
    val quizQuestionsPerRound: Int?,
    /** If not empty, only these places are used, to keep a demo short. */
    val onlyPois: List<String>,
    val startBatteryPercent: Double,
    val kmPerBatteryPercent: Double,
    val legs: List<Leg>,
    val events: List<ScenarioEvent>,
    val stops: List<ScenarioStop>,
    val autoReplies: AutoReplies?
)

object ScenarioParser {

    const val SCHEMA = 1

    fun parse(text: String): Scenario {
        try {
            val root = Json.parseObject(text)
            val schema = root.int("schema")
            if (schema != SCHEMA) throw ScenarioFormatException("Unsupported scenario schema $schema")

            val battery = root.obj("battery")
            val scenario = Scenario(
                routeId = root.string("route"),
                timeScale = root.double("timeScale"),
                players = root.strings("players"),
                quizQuestionsPerRound = root.optInt("quizQuestionsPerRound"),
                onlyPois = root.strings("onlyPois"),
                startBatteryPercent = battery.double("startPercent"),
                kmPerBatteryPercent = battery.double("kmPerPercent"),
                legs = root.objects("legs").map { Leg(it.double("untilKm"), it.double("kph"), it.optDouble("timeScale")) },
                events = root.objects("events").map(::event).sortedBy { it.atKm },
                stops = root.objects("stops").map {
                    ScenarioStop(it.string("stop"), it.double("seconds"), it.optInt("chargeTo"))
                },
                autoReplies = root.optObj("autoReplies")?.let {
                    AutoReplies(
                        delayMs = (it.optDouble("delayMs") ?: 1500.0).toLong(),
                        yesNo = it.optString("yesNo"),
                        quiz = it.strings("quiz"),
                        choice = it.optString("choice")
                    )
                }
            )
            if (scenario.timeScale <= 0.0 || scenario.legs.any { it.timeScale != null && it.timeScale <= 0.0 }) {
                throw ScenarioFormatException("timeScale must be positive")
            }
            if (scenario.legs.isEmpty() || scenario.legs.any { it.kph <= 0.0 }) {
                throw ScenarioFormatException("legs must list at least one positive speed")
            }
            if (scenario.kmPerBatteryPercent <= 0.0) throw ScenarioFormatException("battery.kmPerPercent must be positive")
            return scenario
        } catch (e: JsonException) {
            throw ScenarioFormatException("Scenario: ${e.message}")
        }
    }

    private fun event(o: JsonObject): ScenarioEvent {
        val atKm = o.double("atKm")
        return when {
            o.has("say") -> ScenarioEvent.Say(atKm, o.string("say"))
            o.has("turnDistanceM") -> ScenarioEvent.Turn(atKm, o.int("turnDistanceM"), o.optDouble("holdSeconds") ?: 6.0)
            o.has("warning") -> ScenarioEvent.Warning(atKm, o.optDouble("holdSeconds") ?: 6.0)
            else -> throw ScenarioFormatException("Scenario event at ${atKm}km needs \"say\", \"turnDistanceM\" or \"warning\"")
        }
    }
}
