package app.gildongmu.core.sim

import app.gildongmu.core.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** A scenario and a route package have to belong together; a mismatched pair must not load. */
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
}
