package app.gildongmu.core

/** A named position, used for the trip's origin and destination. */
data class Place(val name: String, val location: GeoPoint)

/** One spoken version of a story. [seconds] is roughly how long it takes to say. */
data class StoryVariant(val seconds: Int, val text: String)

/**
 * A place story in several lengths. The host picks the longest one that fits the time available
 * before the next thing it has to say (see [HostEngine]).
 */
data class Story(val title: String, val variants: List<StoryVariant>) {
    val shortest: StoryVariant get() = variants.minByOrNull { it.seconds }!!
    val longest: StoryVariant get() = variants.maxByOrNull { it.seconds }!!
}

/**
 * A spoken quiz question.
 *
 * [answers] lists every spoken form that counts as correct; the first one is what the host reads
 * out as "the answer". [choices] is optional: when present the question is multiple choice and
 * [answers] must contain the correct choice's text.
 */
data class QuizQuestion(
    val id: String,
    val prompt: String,
    val answers: List<String>,
    val explanation: String,
    val choices: List<String> = emptyList()
)

/** Where a fact in a story or quiz came from. */
data class SourceRef(val title: String, val url: String)

/** A place along the route that the host can talk about. */
data class Poi(
    val id: String,
    val name: String,
    val area: String,
    val location: GeoPoint,
    val story: Story,
    val quiz: List<QuizQuestion>,
    val sources: List<SourceRef>
)

/** Somewhere the host may suggest stopping: a rest area, optionally with chargers. */
data class Stop(
    val id: String,
    val name: String,
    val location: GeoPoint,
    val hasCharger: Boolean
)

/**
 * Everything the host needs for one route, prepared before departure.
 *
 * Nothing in here is generated while driving: the content pipeline builds a package when the route
 * is known, and the car only plays it back.
 */
data class RoutePackage(
    val id: String,
    val title: String,
    val origin: Place,
    val destination: Place,
    val polyline: List<GeoPoint>,
    val pois: List<Poi>,
    val stops: List<Stop>,
    val generalQuiz: List<QuizQuestion>,
    val geometryNote: String?
)
