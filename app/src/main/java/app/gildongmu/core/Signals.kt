package app.gildongmu.core

/**
 * Everything the host can be told about the car and its passengers.
 *
 * Each platform (the Pleos Connect SDK, or the mock drive used for demos and tests) translates its
 * own callbacks into these, so the host logic never touches an SDK type. The comment on each signal
 * names the Pleos SDK call it is expected to come from.
 */
sealed interface Signal {

    /** Regular heartbeat; lets the host notice timeouts and idle time. */
    data object Tick : Signal

    /** Navigation started guiding to a destination. NaviHelper `onRouteStarted`. */
    data class RouteStarted(val distanceKm: Double? = null, val etaMinutes: Int? = null) : Signal

    /** A position fix. FusedLocation `onChanged`. */
    data class Location(val point: GeoPoint, val speedKph: Double? = null) : Signal

    /** Driving time and distance of the current trip. Vehicle SDK Odometer. */
    data class Trip(val minutes: Double, val distanceKm: Double? = null) : Signal

    /** Battery level and whether the car is charging. Vehicle SDK EvBattery. */
    data class Battery(val percent: Int, val charging: Boolean) : Signal

    /** The car is stationary and secured, or has started moving again. */
    data class Parked(val parked: Boolean) : Signal

    /**
     * Distance to the next spoken navigation instruction, or null once it has passed.
     * NaviHelper `onTBTInfo`.
     */
    data class TurnGuidance(val distanceM: Int?) : Signal

    /** A warning lamp or message appeared or cleared. Vehicle SDK Safety. */
    data class VehicleWarning(val active: Boolean) : Signal

    /** Another app took or returned the audio channel (a phone call, for example). */
    data class AudioFocus(val available: Boolean) : Signal

    /** Something a passenger said, as text. SpeechToText `onUpdated(stt, completed = true)`. */
    data class Utterance(val text: String) : Signal

    /** The speech with this id finished playing. TextToSpeech `onDone`. */
    data class SpeechFinished(val utteranceId: String) : Signal

    /** The skip button on the now-playing card: the only touch control while driving. */
    data object SkipPressed : Signal

    /** Navigation reports arrival at the destination. NaviHelper `onDestinationArrived`. */
    data object DestinationArrived : Signal
}

/**
 * What kind of reply the host is waiting for. Each kind of question has its own mode, even where
 * the timeout is the same, so that whoever answers (the scripted passengers, the demo panel's
 * buttons) can tell them apart.
 */
enum class ListenMode {
    YES_NO,
    QUIZ_ANSWER,

    /** Which of the spots just recommended to go to, by number or by name. */
    CHOICE,

    /** A player's word in 끝말잇기. */
    WORD
}

/** A moment worth marking with the cabin's ambient light. */
enum class LightCue { CORRECT_ANSWER, ARRIVAL }

/**
 * Everything the host can ask the car to do. A platform carries these out with the Pleos SDK calls
 * named in the comments.
 */
sealed interface Action {

    /** Say [text]. Report back with [Signal.SpeechFinished] carrying [utteranceId]. TextToSpeech `speak`. */
    data class Speak(val utteranceId: String, val text: String) : Action

    /** Stop talking now. TextToSpeech `stop`. */
    data object StopSpeaking : Action

    /**
     * Listen for one reply. [expected] lists likely answers, which a recogniser can use as hints.
     * SpeechToText `request`.
     */
    data class Listen(val mode: ListenMode, val timeoutMs: Long, val expected: List<String>) : Action

    /** Stop listening. SpeechToText `stop`. */
    data object CancelListen : Action

    /** Add [waypoint], a rest stop or a chosen spot, to the navigation route. NaviHelper `addWaypoint`. */
    data class AddWaypoint(val waypoint: Waypoint) : Action

    /** Flash the ambient light. Vehicle SDK Light. */
    data class Light(val cue: LightCue) : Action

    /** Show or hide the larger screen that is only allowed while parked. */
    data class ExpandedUi(val enabled: Boolean) : Action
}

enum class Phase { WAITING, DRIVING, RESTING, ARRIVED }

enum class SegmentKind(val label: String) {
    BRIEFING("출발 브리핑"),
    STORY("장소 이야기"),
    QUIZ("퀴즈"),
    STOP_SUGGESTION("정차 제안"),
    REST("쉬어 가기"),
    WRAP_UP("오늘의 기록"),
    NOTICE("길동무"),

    /** Places to eat or to see, which can become waypoints. */
    RECOMMENDATION("먹거리·볼거리"),

    /** Riddles and 끝말잇기: just for fun, where the quiz is about the places passed. */
    GAME("놀이")
}

/** The now-playing card: all a driver ever sees while the car is moving. */
data class Card(
    val kind: SegmentKind,
    val title: String,
    /** Where or who: "여주 · 세종대왕릉(영릉)", "첫째 차례". */
    val subtitle: String,
    /** The sentence being spoken. Shown only when captions are switched on. */
    val caption: String,
    /** Position within the current segment, 0 to 1. */
    val progress: Float
)

data class PlayerScore(val player: String, val score: Int)

enum class PlaceState { AHEAD, TOLD, PASSED }

data class PlaceStatus(val id: String, val name: String, val routeKm: Double, val state: PlaceState)

/** A read-only picture of the host for the screen. The engine never reads it back. */
data class HostSnapshot(
    val phase: Phase,
    val card: Card?,
    val listening: ListenMode?,
    val quiet: Boolean,
    val holding: Boolean,
    val expandedUi: Boolean,
    val destinationName: String,
    val progressKm: Double,
    val remainingKm: Double,
    val tripMinutes: Int,
    val batteryPercent: Int?,
    val charging: Boolean,
    val parked: Boolean,
    val waypointName: String?,
    /** The next place the host will talk about, for the idle card: "치악산 · 23km". */
    val upNext: String?,
    val scores: List<PlayerScore>,
    val places: List<PlaceStatus>,
    /**
     * The spots being recommended, while the host reads them out and waits for a choice, so that a
     * screen can show them as well. Empty the rest of the time.
     */
    val offers: List<Spot> = emptyList()
)
