package app.gildongmu.core

/**
 * A made-up 100 km route running due north, so that "kilometres along the route" in a test is
 * exactly what it says: places at 20, 50 and 80 km, rest areas at 30, 60 and 90 km.
 */
object Fixtures {

    private const val START_LAT = 37.0
    private const val LON = 127.0

    // Degrees of latitude per kilometre, from the same earth radius Geo uses.
    private val degreesPerKm = 360.0 / (2 * Math.PI * 6371.0088)

    fun at(km: Double) = GeoPoint(START_LAT + km * degreesPerKm, LON)

    private fun question(id: String, prompt: String, vararg answers: String) =
        QuizQuestion(id, prompt, answers.toList(), "설명 $id.")

    private fun poi(id: String, name: String, km: Double, vararg quiz: QuizQuestion) = Poi(
        id = id,
        name = name,
        area = "",
        location = at(km),
        story = Story(
            title = "$name 이야기",
            variants = listOf(
                StoryVariant(20, "$name 짧은 이야기 하나. $name 짧은 이야기 둘."),
                StoryVariant(60, "$name 긴 이야기 하나. $name 긴 이야기 둘. $name 긴 이야기 셋.")
            )
        ),
        quiz = quiz.toList(),
        sources = emptyList()
    )

    fun route(generalQuiz: List<QuizQuestion> = emptyList()) = RoutePackage(
        id = "test",
        title = "시험 경로",
        origin = Place("출발지", at(0.0)),
        destination = Place("도착지", at(100.0)),
        polyline = listOf(at(0.0), at(100.0)),
        pois = listOf(
            poi("a", "가나", 20.0, question("a1", "가나 문제 하나?", "사과"), question("a2", "가나 문제 둘?", "832")),
            poi("b", "다라", 50.0, question("b1", "다라 문제 하나?", "바다")),
            poi("c", "마바", 80.0)
        ),
        stops = listOf(
            Stop("s1", "하나휴게소", at(30.0), hasCharger = true),
            Stop("s2", "두리휴게소", at(60.0), hasCharger = false),
            Stop("s3", "세모휴게소", at(90.0), hasCharger = true)
        ),
        generalQuiz = generalQuiz,
        geometryNote = null
    )
}

/**
 * Drives a [HostEngine] by hand: moves the car, advances the clock, and plays the part of the
 * speech engine by reporting each sentence as finished when the test asks for it.
 */
class Harness(pkg: RoutePackage = Fixtures.route(), config: HostConfig = HostConfig()) {

    val engine = HostEngine(pkg, config)
    var now = 0L
    private var km = 0.0

    /** Everything the host has asked for, in order. */
    val actions = ArrayList<Action>()
    private var finished = 0

    fun send(signal: Signal): List<Action> {
        val out = engine.handle(signal, now)
        for (action in out) {
            // Speech the host cut off is over: there is nothing left to report as finished.
            if (action == Action.StopSpeaking) finished = actions.count { it is Action.Speak }
            actions.add(action)
        }
        return out
    }

    fun idle(ms: Long) {
        now += ms
        send(Signal.Tick)
    }

    /**
     * Drives to [toKm] at [kph], reporting a position once a second. [timeScale] compresses time
     * the way the demo does: 40 means the car covers 40 seconds of road per real second.
     */
    fun drive(toKm: Double, kph: Double = 90.0, timeScale: Double = 1.0) {
        val kmPerSecond = kph / 3600.0 * timeScale
        while (km < toKm) {
            now += 1000
            km = minOf(toKm, km + kmPerSecond)
            send(Signal.Location(Fixtures.at(km), kph))
        }
    }

    /**
     * Puts the car at [atKm] without any time passing. Handy for setting a scene, but the host
     * sees an impossibly fast car, so tests about pacing should use [drive] instead.
     */
    fun jump(atKm: Double) {
        km = atKm
        send(Signal.Location(Fixtures.at(km), 90.0))
    }

    fun say(text: String) = send(Signal.Utterance(text))

    val spoken: List<String> get() = actions.filterIsInstance<Action.Speak>().map { it.text }

    /** The sentence being spoken right now, if any. */
    val speaking: Action.Speak?
        get() = actions.filterIsInstance<Action.Speak>().getOrNull(finished)

    /** Reports the current sentence as finished. Returns false if nothing was being said. */
    fun finishSentence(): Boolean {
        val current = speaking ?: return false
        finished++
        now += 1000
        send(Signal.SpeechFinished(current.utteranceId))
        return true
    }

    /** Lets the host talk until it has nothing more to say (or starts listening). */
    fun finishSpeaking() {
        var guard = 0
        while (finishSentence()) check(++guard < 200) { "The host never stops talking" }
    }

    /** Starts the trip and gets the departure briefing out of the way. */
    fun startTrip() {
        send(Signal.Parked(false))
        send(Signal.Location(Fixtures.at(0.0), 90.0))
        send(Signal.RouteStarted())
        finishSpeaking()
    }

    inline fun <reified T : Action> count(): Int = actions.count { it is T }

    inline fun <reified T : Action> last(): T? = actions.filterIsInstance<T>().lastOrNull()
}
