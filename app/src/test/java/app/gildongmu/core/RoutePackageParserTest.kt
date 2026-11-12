package app.gildongmu.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RoutePackageParserTest {

    private fun bundled(): String {
        val file = listOf(File("src/main/assets/routes/seoul-gangneung.json"), File("app/src/main/assets/routes/seoul-gangneung.json"))
            .firstOrNull { it.exists() } ?: error("Cannot find the bundled route from ${File(".").absolutePath}")
        return file.readText()
    }

    /** A package written by the pipeline; pipeline/tests/test_package.py keeps it up to date. */
    private fun fromPipeline(): String {
        val file = listOf(File("src/test/resources/pipeline-package.json"), File("app/src/test/resources/pipeline-package.json"))
            .firstOrNull { it.exists() } ?: error("Cannot find the pipeline's package from ${File(".").absolutePath}")
        return file.readText()
    }

    private val minimal = """
        {
          "schema": 1, "id": "r", "title": "t",
          "origin": { "name": "a", "lat": 37.0, "lon": 127.0 },
          "destination": { "name": "b", "lat": 37.5, "lon": 127.0 },
          "polyline": [[37.0, 127.0], [37.5, 127.0]],
          "stops": [ { "id": "s", "name": "휴게소", "lat": 37.2, "lon": 127.0 } ],
          "pois": [ {
            "id": "p", "name": "장소", "lat": 37.3, "lon": 127.0,
            "story": { "title": "제목", "variants": [ { "seconds": 20, "text": "이야기." } ] },
            "quiz": [ { "id": "q", "prompt": "문제?", "answers": ["답"] } ],
            "sources": [ { "title": "출처", "url": "https://example.org" } ]
          } ]
        }
    """

    private fun assertRejected(json: String, fragment: String) {
        try {
            RoutePackageParser.parse(json)
            fail("Expected the package to be rejected")
        } catch (e: PackageFormatException) {
            assertTrue("Expected \"$fragment\" in: ${e.message}", e.message!!.contains(fragment))
        }
    }

    @Test
    fun readsAMinimalPackage() {
        val pkg = RoutePackageParser.parse(minimal)
        assertEquals("r", pkg.id)
        assertEquals(2, pkg.polyline.size)
        assertEquals(false, pkg.stops.single().hasCharger)
        assertEquals("이야기.", pkg.pois.single().story.shortest.text)
        assertEquals(listOf("답"), pkg.pois.single().quiz.single().answers)
        assertTrue(pkg.generalQuiz.isEmpty())
    }

    @Test
    fun theBundledPackageIsValidAndInRouteOrder() {
        val pkg = RoutePackageParser.parse(bundled())
        assertEquals("seoul-gangneung", pkg.id)
        assertEquals(7, pkg.pois.size)
        assertEquals(6, pkg.stops.size)

        val geometry = RouteGeometry(pkg.polyline)
        assertEquals("The demo line is about 205 km long", 205.5, geometry.lengthKm, 1.0)

        val kms = pkg.pois.map { geometry.project(it.location).routeKm }
        assertEquals("Places are listed in the order the car reaches them", kms.sorted(), kms)
        for (poi in pkg.pois) {
            val offset = geometry.project(poi.location).offsetKm
            assertTrue("${poi.name} is ${offset} km from the route", offset < 8.0)
        }
    }

    @Test
    fun everyBundledStoryFitsItsStatedLength() {
        val pkg = RoutePackageParser.parse(bundled())
        for (poi in pkg.pois) {
            assertEquals("${poi.name} should have a short and a long version", 2, poi.story.variants.size)
            for (variant in poi.story.variants) {
                val estimate = Speech.estimateSeconds(variant.text)
                assertTrue(
                    "${poi.name} (${variant.seconds}s) is estimated at ${"%.0f".format(estimate)}s",
                    estimate <= variant.seconds * 1.15 && estimate >= variant.seconds * 0.7
                )
            }
            assertTrue("${poi.name}: the short version should fit a compressed demo", poi.story.shortest.seconds <= 25)
        }
    }

    @Test
    fun everyBundledQuestionAcceptsItsOwnAnswerAndRejectsNonsense() {
        val pkg = RoutePackageParser.parse(bundled())
        for (q in pkg.pois.flatMap { it.quiz } + pkg.generalQuiz) {
            for (answer in q.answers) {
                assertTrue("\"$answer\" should be accepted for ${q.id}", Understanding.isCorrect(answer, q))
                assertTrue("\"${answer}이요\" should be accepted for ${q.id}", Understanding.isCorrect(answer + "이요", q))
            }
            assertTrue("\"잘 모르겠어요\" should be wrong for ${q.id}", !Understanding.isCorrect("잘 모르겠어요", q))
            for (wrong in q.choices.filter { it !in q.answers }) {
                assertTrue("\"$wrong\" should be wrong for ${q.id}", !Understanding.isCorrect(wrong, q))
            }
        }
    }

    @Test
    fun bundledTextIsWrittenForSpeech() {
        // Brackets, hanja and symbols are read out badly or not at all by speech synthesis.
        val pkg = RoutePackageParser.parse(bundled())
        val spoken = pkg.pois.flatMap { poi -> poi.story.variants.map { it.text } + poi.quiz.flatMap { listOf(it.prompt, it.explanation) } } +
            pkg.generalQuiz.flatMap { listOf(it.prompt, it.explanation) }
        for (text in spoken) {
            assertTrue("Unspeakable character in: $text", text.none { it in "()[]{}<>《》「」·%&/*#" || it in '一'..'鿿' })
        }
    }

    @Test
    fun bundledStoriesCanBeToldAwayFromTheirPlace() {
        // The host offers the next story at a rest stop and tells one early when asked, so a story
        // must not open as if the car were on the spot. The pipeline checks the same thing.
        val pkg = RoutePackageParser.parse(bundled())
        val onTheSpot = listOf("지금 지나", "지금 보이", "지금 달리", "여기는", "여기가", "여기에", "이곳", "이 근처", "이제 ")
        for (poi in pkg.pois) {
            for (variant in poi.story.variants) {
                assertTrue("${poi.name}: ${variant.text.take(20)}", onTheSpot.none { variant.text.trimStart().startsWith(it) })
            }
        }
    }

    @Test
    fun readsAPackageWrittenByThePipeline() {
        val pkg = RoutePackageParser.parse(fromPipeline())
        assertEquals("seoul-gangneung", pkg.id)
        assertEquals("The route file's stops and questions are carried over", 6 to 2, pkg.stops.size to pkg.generalQuiz.size)
        assertTrue(pkg.stops.any { it.hasCharger })

        val poi = pkg.pois.single()
        assertEquals("poi-01", poi.id)
        assertEquals("대관령" to "평창", poi.name to poi.area)
        assertEquals(37.688, poi.location.lat, 1e-6)
        assertEquals(128.757, poi.location.lon, 1e-6)
        assertEquals("위키백과: 대관령", poi.sources.single().title)

        // The pipeline states each version's length from its own copy of the estimate; the host
        // picks a version by that number, so the two copies have to agree.
        assertEquals(2, poi.story.variants.size)
        for (variant in poi.story.variants) {
            assertEquals(Math.ceil(Speech.estimateSeconds(variant.text)).toInt(), variant.seconds)
        }

        val (height, gate) = poi.quiz
        assertEquals(listOf("poi-01-q1", "poi-01-q2"), poi.quiz.map { it.id })
        assertTrue(height.choices.isEmpty())
        assertTrue(Understanding.isCorrect("팔백삼십이 미터", height))
        assertEquals(listOf("영동과 영서", "서울과 부산"), gate.choices)
        assertTrue(Understanding.isCorrect("1번", gate))
        assertTrue(!Understanding.isCorrect("서울과 부산", gate))
    }

    @Test
    fun rejectsBrokenPackages() {
        assertRejected("not json", "not valid JSON")
        assertRejected(minimal.replace("\"schema\": 1", "\"schema\": 2"), "schema 2")
        assertRejected(minimal.replace("[[37.0, 127.0], [37.5, 127.0]]", "[[37.0, 127.0]]"), "at least two points")
        assertRejected(minimal.replace("[37.5, 127.0]]", "[137.5, 127.0]]"), "polyline[1]")
        assertRejected(minimal.replace("\"answers\": [\"답\"]", "\"answers\": []"), "needs at least one answer")
        assertRejected(minimal.replace("\"id\": \"s\"", "\"id\": \"p\""), "used twice")
        assertRejected(minimal.replace("\"seconds\": 20", "\"seconds\": 0"), "empty story variant")
        assertRejected(minimal.replace("\"title\": \"제목\",", ""), "pois[0].story.title")
        assertRejected(
            minimal.replace("\"answers\": [\"답\"]", "\"answers\": [\"답\"], \"choices\": [\"가\", \"나\"]"),
            "exactly one choice"
        )
    }
}
