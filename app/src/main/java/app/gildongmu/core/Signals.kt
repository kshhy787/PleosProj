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

    /**
     * A touch on the tile of a recommended spot: [index] counts from zero through
     * [HostSnapshot.offers]. The host acts on it only while the car is parked, where touching
     * the screen is allowed; a screen may send it at any time and leave that rule to the host.
     */
    data class OfferTapped(val index: Int) : Signal

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

/**
 * The colour family of what is on the card. The screen maps each to a Palette colour.
 *
 * It is decided here rather than on the screen so that a glance tells the driver what sort of thing
 * the host is saying, the same way on every screen: a new kind of card cannot be added without
 * saying which family it belongs to.
 */
enum class Tone { STORY, FOOD, GAME, STOP, NOTICE }

/**
 * A picture for the card. [key] is how a route package names it.
 *
 * The pictures are drawn by the app, so a package can only pick among these: it carries no images,
 * which keeps it small and keeps anything unchecked off a screen the driver can see.
 */
enum class Illustration(val key: String) {
    // One for each kind of content.
    MOUNTAINS("mountains"),
    BOWL("bowl"),
    LANDMARK("landmark"),
    STAR("star"),
    CUP("cup"),

    /** For what is about the trip itself: notices, the briefing, the wrap-up. */
    ROAD("road"),

    // Picked by a place.
    SEA("sea"),
    FLOWER("flower"),
    POTTERY("pottery"),
    WHEEL("wheel"),
    COFFEE("coffee");

    companion object {
        /**
         * The illustration a package calls [key], or null when it names none or one this app does
         * not have. The caller then falls back to the picture of the content's kind, so a package
         * written for a newer app, with pictures this one lacks, still loads and still shows one.
         */
        fun of(key: String?): Illustration? = entries.firstOrNull { it.key == key }
    }
}

/**
 * What sort of thing the host is saying. [tone] is its colour family and [illustration] the picture
 * a card of this kind shows when what it is about has none of its own.
 */
enum class SegmentKind(val label: String, val tone: Tone, val illustration: Illustration) {
    BRIEFING("출발 브리핑", Tone.NOTICE, Illustration.ROAD),
    STORY("장소 이야기", Tone.STORY, Illustration.MOUNTAINS),
    QUIZ("퀴즈", Tone.GAME, Illustration.STAR),
    STOP_SUGGESTION("정차 제안", Tone.STOP, Illustration.CUP),
    REST("쉬어 가기", Tone.STOP, Illustration.CUP),
    WRAP_UP("오늘의 기록", Tone.NOTICE, Illustration.ROAD),
    NOTICE("길동무", Tone.NOTICE, Illustration.ROAD),

    /**
     * Places to eat or to see, which can become waypoints. The bowl is for a card that names no
     * spots; one that does shows the picture of their category (see [SpotCategory.illustration]).
     */
    RECOMMENDATION("먹거리·볼거리", Tone.FOOD, Illustration.BOWL),

    /** Riddles and 끝말잇기: just for fun, where the quiz is about the places passed. */
    GAME("놀이", Tone.GAME, Illustration.STAR)
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
    val progress: Float,
    /**
     * The picture beside the text. Never null, so a screen has nothing to decide: the host gives
     * the picture of the place the card is about when its package names one, and otherwise the
     * picture of the card's kind.
     */
    val illustration: Illustration = kind.illustration
) {
    /** The colour family of the card. It follows from [kind], so the two can never disagree. */
    val tone: Tone get() = kind.tone
}

/**
 * A player's points. [leader] marks everyone who has the highest score, once somebody has scored:
 * before that all are level at zero, and a crown on every name would mean nothing.
 */
data class PlayerScore(val player: String, val score: Int, val leader: Boolean)

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
    /**
     * The "다음 · 치악산 · 23km" line under a card, so that whoever is looking knows what comes
     * after what is being said or asked. Null when nothing is on the card (the idle card says it
     * with [upNext]) and when no place is left to talk about.
     */
    val nextLine: String?,
    val scores: List<PlayerScore>,
    val places: List<PlaceStatus>,
    /**
     * The spots being recommended, while the host reads them out and waits for a choice, so that a
     * screen can show them as well. Empty the rest of the time.
     */
    val offers: List<Spot> = emptyList(),
    /**
     * One of [offers] can be chosen, by naming it or, while the car is parked, by touching it: true
     * while a list that ends in "which one?" is read out and while the host waits for the answer.
     * False for spots that are only described (the destination tip, anything after arrival), so
     * that a screen does not invite a choice the host would not act on.
     */
    val choosable: Boolean = false
)
