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

    /** [minimal] with spots and fun questions; [extra] is spliced into the first spot. */
    private fun withSpots(extra: String = "", kind: String = "riddle") = minimal.trimEnd().removeSuffix("}") + """,
          "spots": [ {
            "id": "f", "category": "food", "name": "두부마을", "area": "강릉", "lat": 37.4, "lon": 127.0,
            "reason": "바닷물로 만든 두부예요.", "near": "destination",
            "sources": [ { "title": "출처", "url": "https://example.org/f" } ]$extra
          }, {
            "id": "v", "category": "sight", "name": "누각", "lat": 37.2, "lon": 127.0,
            "reason": "오래된 누각이에요.", "near": "s",
            "sources": [ { "title": "출처", "url": "https://example.org/v" } ]
          }, {
            "id": "c", "category": "cafe", "name": "커피거리", "lat": 37.35, "lon": 127.0,
            "reason": "커피거리예요.", "sources": [ { "title": "출처", "url": "https://example.org/c" } ]
          } ],
          "funQuiz": [ { "id": "r", "kind": "$kind", "prompt": "수수께끼?", "answers": ["나이"], "explanation": "설명." } ]
        }
    """

    @Test
    fun readsAMinimalPackage() {
        val pkg = RoutePackageParser.parse(minimal)
        assertEquals("r", pkg.id)
        assertEquals(2, pkg.polyline.size)
        assertEquals(false, pkg.stops.single().hasCharger)
        assertEquals("이야기.", pkg.pois.single().story.shortest.text)
        assertEquals(listOf("답"), pkg.pois.single().quiz.single().answers)
        assertEquals(QuizKind.FACT, pkg.pois.single().quiz.single().kind)
        assertTrue(pkg.generalQuiz.isEmpty())
        assertTrue("A package written before spots existed still loads", pkg.spots.isEmpty() && pkg.funQuiz.isEmpty())
    }

    @Test
    fun readsSpotsAndFunQuestions() {
        val pkg = RoutePackageParser.parse(withSpots())
        val (food, sight, cafe) = pkg.spots
        assertEquals(listOf(SpotCategory.FOOD, SpotCategory.SIGHT, SpotCategory.CAFE), pkg.spots.map { it.category })
        assertEquals("두부마을" to "강릉", food.name to food.area)
        assertEquals(GeoPoint(37.4, 127.0), food.location)
        assertEquals("바닷물로 만든 두부예요.", food.reason)
        assertEquals(Spot.NEAR_DESTINATION, food.near)
        assertEquals("s", sight.near)
        assertEquals(null, cafe.near)
        assertEquals("", sight.area)
        assertEquals("https://example.org/f", food.sources.single().url)

        val riddle = pkg.funQuiz.single()
        assertEquals(QuizKind.RIDDLE, riddle.kind)
        assertEquals(listOf("나이"), riddle.answers)
        assertEquals(QuizKind.NONSENSE, RoutePackageParser.parse(withSpots(kind = "nonsense")).funQuiz.single().kind)
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
    fun theBundledSpotsAreSourcedAndNearWhatTheySayTheyAreNear() {
        val pkg = RoutePackageParser.parse(bundled())
        assertEquals(12, pkg.spots.size)
        val nearDestination = pkg.spots.filter { it.near == Spot.NEAR_DESTINATION }
        // The demo asks what to eat in Gangneung; the recommendation card has room for three.
        assertEquals(3, nearDestination.count { it.category == SpotCategory.FOOD })
        assertTrue(nearDestination.count { it.category == SpotCategory.SIGHT } >= 3)
        assertTrue(nearDestination.any { it.category == SpotCategory.CAFE })
        assertTrue("Some spots belong to rest stops along the way", pkg.spots.any { it.near in pkg.stops.map { s -> s.id } })

        for (spot in pkg.spots) {
            val anchor = if (spot.near == Spot.NEAR_DESTINATION) pkg.destination.location else pkg.stops.single { it.id == spot.near }.location
            val km = Geo.distanceKm(anchor, spot.location)
            assertTrue("${spot.name} is $km km from what it is near", km < 20.0)
            assertTrue("${spot.name} needs a source", spot.sources.isNotEmpty() && spot.sources.all { it.url.startsWith("https://") })
        }
    }

    @Test
    fun theBundledFunQuizHasRiddlesAndNonsense() {
        val pkg = RoutePackageParser.parse(bundled())
        assertTrue("About twenty, found ${pkg.funQuiz.size}", pkg.funQuiz.size in 18..26)
        assertTrue(pkg.funQuiz.any { it.kind == QuizKind.RIDDLE })
        assertTrue(pkg.funQuiz.any { it.kind == QuizKind.NONSENSE })
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
        for (q in pkg.pois.flatMap { it.quiz } + pkg.generalQuiz + pkg.funQuiz) {
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
            (pkg.generalQuiz + pkg.funQuiz).flatMap { listOf(it.prompt, it.explanation, it.answers.first()) } +
            pkg.spots.flatMap { listOf(it.name, it.reason) }
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
        // A spot's reason is said when it is recommended, usually long before the car is there.
        for (spot in pkg.spots) {
            assertTrue("${spot.name}: ${spot.reason}", onTheSpot.none { spot.reason.trimStart().startsWith(it) })
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

        // Spots and fun questions are written by hand into the route file; the pipeline carries
        // them over unchanged.
        val handWritten = RoutePackageParser.parse(bundled())
        assertEquals(handWritten.spots, pkg.spots)
        assertEquals(handWritten.funQuiz, pkg.funQuiz)
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

    @Test
    fun rejectsBrokenSpotsAndFunQuestions() {
        assertRejected(withSpots().replace("\"category\": \"food\"", "\"category\": \"bar\""), "category \"bar\"")
        assertRejected(withSpots().replace("\"near\": \"s\"", "\"near\": \"nowhere\""), "near \"nowhere\"")
        assertRejected(withSpots().replace("\"reason\": \"오래된 누각이에요.\"", "\"reason\": \" \""), "spot \"v\" has no reason")
        assertRejected(withSpots().replace("\"name\": \"누각\"", "\"name\": \"\""), "spot \"v\" has no name")
        assertRejected(withSpots().replace("\"lat\": 37.35", "\"lat\": 97.35"), "spot \"c\" is not at a valid")
        assertRejected(
            withSpots().replace("\"sources\": [ { \"title\": \"출처\", \"url\": \"https://example.org/c\" } ]", "\"sources\": []"),
            "spot \"c\" needs at least one source"
        )
        assertRejected(withSpots().replace("\"id\": \"v\"", "\"id\": \"p\""), "spot id \"p\" is blank or used twice")
        assertRejected(withSpots().replace("\"category\": \"cafe\", ", ""), "spots[2].category")
        assertRejected(withSpots(kind = "joke"), "kind \"joke\"")
        assertRejected(withSpots().replace("\"kind\": \"riddle\", ", ""), "funQuiz[0].kind")
        assertRejected(withSpots().replace("\"id\": \"r\", \"kind\"", "\"id\": \"q\", \"kind\""), "question id \"q\" is blank or used twice")
        assertRejected(withSpots().replace("\"answers\": [\"나이\"]", "\"answers\": [\"\"]"), "question \"r\" needs at least one answer")
    }
}
