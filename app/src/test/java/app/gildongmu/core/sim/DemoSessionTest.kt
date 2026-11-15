package app.gildongmu.core.sim

import app.gildongmu.core.Fixtures
import app.gildongmu.core.ListenMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * A scenario and a route package have to belong together; a mismatched pair must not load. Once
 * they do, the scripted car plays its part in what the host starts.
 */
class DemoSessionTest {

    private val pkg = Fixtures.route()

    private fun scenario(route: String = "test", onlyPois: String = "", stop: String = "s1") = ScenarioParser.parse(
        """
        {
          "schema": 1, "route": "$route", "timeScale": 60, "players": ["가", "나"],
          "onlyPois": [$onlyPois],
          "battery": { "startPercent": 50, "kmPerPercent": 5 },
          "legs": [ { "untilKm": 999, "kph": 60 } ],
          "events": [],
          "stops": [ { "stop": "$stop", "seconds": 10 } ]
        }
        """
    )

    private fun session(scenario: Scenario): DemoSession {
        val now = 0L
        return DemoSession(pkg, scenario, HeadlessPlatform { now }) { now }
    }

    private fun refusal(scenario: Scenario): String {
        try {
            session(scenario)
        } catch (e: ScenarioFormatException) {
            return e.message.orEmpty()
        }
        fail("The mismatched scenario was accepted")
        return ""
    }

    @Test
    fun keepsOnlyThePlacesTheScenarioNames() {
        assertEquals(listOf("a", "c"), session(scenario(onlyPois = "\"a\", \"c\"")).route.pois.map { it.id })
        assertEquals("No list means every place", 3, session(scenario()).route.pois.size)
    }

    @Test
    fun refusesAScenarioWrittenForAnotherRoute() {
        val message = refusal(scenario(route = "somewhere-else"))
        assertTrue(message, message.contains("somewhere-else") && message.contains("test"))
    }

    @Test
    fun refusesPlacesThePackageDoesNotHave() {
        // What happens when a regenerated package (ids poi-01, poi-02, ...) meets the old scenario.
        val message = refusal(scenario(onlyPois = "\"a\", \"yeongneung\""))
        assertTrue(message, message.contains("yeongneung"))
        assertTrue("Places that do exist are not reported: $message", !message.contains("\"a\"") && !message.contains(": a"))
    }

    @Test
    fun refusesStopsThePackageDoesNotHave() {
        val message = refusal(scenario(stop = "hoengseong"))
        assertTrue(message, message.contains("hoengseong"))
    }

    @Test
    fun theScriptedPlayersPlayWordChainWithTheSessionsWordsUntilTheGameEnds() {
        // The session hands its words to the host, and the scripted player answers every turn with
        // the first of the host's hints. Each word here can be followed by exactly one other, so
        // the game can only go one way.
        val words = listOf("사과", "과자", "자두", "두부", "부채", "채소", "소풍", "풍선", "선물")
        val scenario = ScenarioParser.parse(
            """
            {
              "schema": 1, "route": "test", "timeScale": 1, "players": ["가", "나"],
              "battery": { "startPercent": 50, "kmPerPercent": 5 },
              "legs": [ { "untilKm": 999, "kph": 60 } ],
              "events": [ { "atKm": 0.2, "say": "끝말잇기 하자" } ],
              "stops": [],
              "autoReplies": { "delayMs": 1000, "quiz": [] }
            }
            """
        )
        var now = 0L
        val platform = HeadlessPlatform { now }
        val session = DemoSession(pkg, scenario, platform, words) { now }
        platform.dispatch = session::dispatch
        val passengers = ArrayList<String>()
        session.onPassengerSpeech = { passengers.add(it) }

        session.start()
        // Two minutes in real time, at 60 km/h: the first place, and its story, are still far ahead.
        repeat(2 * 60 * 10) {
            now += 100
            session.tick(100)
            platform.pump()
        }

        assertEquals(
            "The request, then the host's first hint at every turn",
            listOf("끝말잇기 하자", "과자", "두부", "채소", "풍선"),
            passengers
        )
        val hostWords = platform.spoken.filter { it.endsWith("!") && it.dropLast(1) in words }
        assertEquals("The host's own words come from the session's list", listOf("사과!", "자두!", "부채!", "소풍!"), hostWords)
        // A game is four of the players' words long, by the host's default.
        val turns = platform.log.count { it.kind == "LISTEN" && it.text == ListenMode.WORD.name }
        assertEquals("A turn for each of the players' words, and none after the last", 4, turns)

        val end = platform.snapshot!!
        assertNull("The game is over: nobody is being listened to", end.listening)
        assertNull("and the card is clear", end.card)
        assertTrue("The game's own closing line came last: ${platform.spoken.last()}", platform.spoken.last().contains("끝말잇기"))
    }
}
