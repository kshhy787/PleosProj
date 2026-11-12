package app.gildongmu.core

/** Thrown when a route package is readable JSON but breaks a rule the host relies on. */
class PackageFormatException(message: String) : Exception(message)

/**
 * Reads a route package (schema 1) and checks the rules the host depends on, so a bad package is
 * rejected when it is loaded rather than half-way through a drive.
 *
 * The same schema is written by the Python content pipeline (`pipeline/`).
 */
object RoutePackageParser {

    const val SCHEMA = 1

    fun parse(text: String): RoutePackage {
        val root = try {
            Json.parseObject(text)
        } catch (e: JsonException) {
            throw PackageFormatException("Route package is not valid JSON: ${e.message}")
        }
        try {
            val schema = root.int("schema")
            if (schema != SCHEMA) throw PackageFormatException("Unsupported route package schema $schema (this app reads schema $SCHEMA)")

            val pkg = RoutePackage(
                id = root.string("id"),
                title = root.string("title"),
                origin = place(root.obj("origin")),
                destination = place(root.obj("destination")),
                polyline = polyline(root),
                pois = root.objects("pois").map(::poi),
                stops = root.objects("stops").map(::stop),
                generalQuiz = root.objects("generalQuiz").map(::question),
                geometryNote = root.optString("geometryNote")
            )
            validate(pkg)
            return pkg
        } catch (e: JsonException) {
            throw PackageFormatException("Route package: ${e.message}")
        }
    }

    private fun point(o: JsonObject) = GeoPoint(o.double("lat"), o.double("lon"))

    private fun place(o: JsonObject) = Place(o.string("name"), point(o))

    private fun polyline(root: JsonObject): List<GeoPoint> = root.array("polyline").mapIndexed { index, v ->
        val pair = v as? List<*>
        val lat = pair?.getOrNull(0) as? Double
        val lon = pair?.getOrNull(1) as? Double
        if (pair == null || pair.size != 2 || lat == null || lon == null) {
            throw PackageFormatException("Route package: polyline[$index] should be [latitude, longitude]")
        }
        GeoPoint(lat, lon)
    }

    private fun stop(o: JsonObject) = Stop(
        id = o.string("id"),
        name = o.string("name"),
        location = point(o),
        hasCharger = o.optBoolean("charger") ?: false
    )

    private fun poi(o: JsonObject): Poi {
        val story = o.obj("story")
        return Poi(
            id = o.string("id"),
            name = o.string("name"),
            area = o.optString("area") ?: "",
            location = point(o),
            story = Story(
                title = story.string("title"),
                variants = story.objects("variants").map { StoryVariant(it.int("seconds"), it.string("text")) }
            ),
            quiz = o.objects("quiz").map(::question),
            sources = o.objects("sources").map { SourceRef(it.string("title"), it.string("url")) }
        )
    }

    private fun question(o: JsonObject) = QuizQuestion(
        id = o.string("id"),
        prompt = o.string("prompt"),
        answers = o.strings("answers"),
        explanation = o.optString("explanation") ?: "",
        choices = o.strings("choices")
    )

    private fun validate(pkg: RoutePackage) {
        fun check(condition: Boolean, message: () -> String) {
            if (!condition) throw PackageFormatException("Route package: ${message()}")
        }

        check(pkg.polyline.size >= 2) { "polyline needs at least two points" }
        for ((index, p) in pkg.polyline.withIndex()) {
            check(p.lat in -90.0..90.0 && p.lon in -180.0..180.0) { "polyline[$index] is not a valid latitude/longitude" }
        }

        val ids = HashSet<String>()
        fun unique(id: String, what: String) = check(id.isNotBlank() && ids.add(id)) { "$what id \"$id\" is blank or used twice" }

        for (stop in pkg.stops) unique(stop.id, "stop")
        for (poi in pkg.pois) {
            unique(poi.id, "poi")
            check(poi.name.isNotBlank()) { "poi \"${poi.id}\" has no name" }
            check(poi.story.variants.isNotEmpty()) { "poi \"${poi.id}\" has no story variants" }
            for (variant in poi.story.variants) {
                check(variant.seconds > 0 && variant.text.isNotBlank()) { "poi \"${poi.id}\" has an empty story variant" }
            }
        }
        for (q in pkg.pois.flatMap { it.quiz } + pkg.generalQuiz) {
            unique(q.id, "question")
            check(q.prompt.isNotBlank()) { "question \"${q.id}\" has no prompt" }
            check(q.answers.isNotEmpty() && q.answers.all { it.isNotBlank() }) { "question \"${q.id}\" needs at least one answer" }
            if (q.choices.isNotEmpty()) {
                check(q.choices.size >= 2) { "question \"${q.id}\" needs at least two choices" }
                check(q.choices.count { it in q.answers } == 1) { "question \"${q.id}\" must have exactly one choice listed in answers" }
            }
        }
    }
}
