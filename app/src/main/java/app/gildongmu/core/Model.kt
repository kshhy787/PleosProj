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
 * What sort of question a [QuizQuestion] is. Facts come from a place's sources; riddles and
 * nonsense questions are word play written by the team, so they need none.
 */
enum class QuizKind(val key: String) {
    FACT("fact"),
    RIDDLE("riddle"),
    NONSENSE("nonsense");

    companion object {
        /** The kinds a package's `funQuiz` may use. */
        val FUN = listOf(RIDDLE, NONSENSE)
    }
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
    val choices: List<String> = emptyList(),
    val kind: QuizKind = QuizKind.FACT
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

/** What a [Spot] offers. [key] is how a route package writes it. */
enum class SpotCategory(val key: String) {
    FOOD("food"),
    SIGHT("sight"),
    CAFE("cafe");

    companion object {
        fun of(key: String): SpotCategory? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Somewhere to eat or to visit that the host can recommend, and add as a waypoint.
 *
 * [reason] is one short spoken sentence on why to go. [near] says which part of the trip the spot
 * belongs to: a stop's id, [NEAR_DESTINATION], or null when it is simply somewhere along the way.
 */
data class Spot(
    val id: String,
    val category: SpotCategory,
    val name: String,
    val area: String,
    val location: GeoPoint,
    val reason: String,
    val near: String?,
    val sources: List<SourceRef>
) {
    companion object {
        const val NEAR_DESTINATION = "destination"
    }
}

/**
 * Everything the host needs for one route, prepared before departure.
 *
 * Nothing in here is generated while driving: the content pipeline builds a package when the route
 * is known, and the car only plays it back. [spots] and [funQuiz] are optional in the file, so a
 * package written before they existed still loads.
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
    val geometryNote: String?,
    val spots: List<Spot> = emptyList(),
    val funQuiz: List<QuizQuestion> = emptyList()
)
