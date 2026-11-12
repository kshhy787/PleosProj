package app.gildongmu.core.sim

import app.gildongmu.core.Fixtures
import app.gildongmu.core.ListenMode
import app.gildongmu.core.Signal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MockDriveTest {

    private val pkg = Fixtures.route()

    private fun scenario(extra: String = "") = ScenarioParser.parse(
        """
        {
          "schema": 1, "route": "test", "timeScale": 60, "players": ["가", "나"],
          "battery": { "startPercent": 50, "kmPerPercent": 5 },
          "legs": [ { "untilKm": 50, "kph": 60 }, { "untilKm": 999, "kph": 120, "timeScale": 30 } ],
          "events": [ { "atKm": 10, "say": "퀴즈 하자" }, { "atKm": 20, "turnDistanceM": 300, "holdSeconds": 3 } ],
          "stops": [ { "stop": "s1", "seconds": 10, "chargeTo": 80 } ]
          $extra
        }
        """
    )

    /** Runs the drive in 100 ms steps until [until] is true, collecting every signal. */
    private fun run(drive: MockDrive, seconds: Int, until: () -> Boolean = { false }): List<Signal> {
        val all = ArrayList<Signal>()
        all.addAll(drive.start())
        repeat(seconds * 10) {
            if (!until()) all.addAll(drive.advance(100))
        }
        return all
    }

    @Test
    fun startsNavigationWithAnEstimateFromTheLegSpeeds() {
        val drive = MockDrive(pkg, scenario())
        val started = drive.start().filterIsInstance<Signal.RouteStarted>().single()
        assertEquals(100.0, started.distanceKm!!, 0.01)
        assertEquals("50 km at 60 plus 50 km at 120", 75, started.etaMinutes)
        assertTrue("Starting twice does nothing", drive.start().isEmpty())
    }

    @Test
    fun movesAtTheLegSpeedTimesTheTimeScale() {
        val drive = MockDrive(pkg, scenario())
        run(drive, seconds = 10)
        // 60 km/h compressed 60x is 1 km per real second.
        assertEquals(10.0, drive.progressKm, 0.2)
        assertEquals(10.0, drive.tripMinutes, 0.2)
    }

    @Test
    fun aLegCanOverrideTheTimeScale() {
        val drive = MockDrive(pkg, scenario())
        run(drive, seconds = 50)
        val at50 = drive.progressKm
        run(drive, seconds = 10)
        // 120 km/h compressed 30x is also 1 km per real second, but trip time runs half as fast.
        assertEquals(10.0, drive.progressKm - at50, 0.3)
        assertEquals(50.0 + 5.0, drive.tripMinutes, 0.3)
    }

    @Test
    fun firesEventsOnceWhenTheCarPassesThem() {
        val drive = MockDrive(pkg, scenario())
        val signals = run(drive, seconds = 30)
        assertEquals(listOf("퀴즈 하자"), signals.filterIsInstance<Signal.Utterance>().map { it.text })
        val turns = signals.filterIsInstance<Signal.TurnGuidance>().map { it.distanceM }
        assertEquals("The prompt appears, then clears three seconds later", listOf(300, null), turns)
    }

    @Test
    fun drivesPastARestAreaNobodyAskedFor() {
        val drive = MockDrive(pkg, scenario())
        val signals = run(drive, seconds = 40)
        assertFalse(signals.any { it is Signal.Parked && it.parked })
        assertTrue(drive.progressKm > 35.0)
    }

    @Test
    fun stopsAndChargesAtAnAcceptedWaypointThenDrivesOn() {
        val drive = MockDrive(pkg, scenario())
        run(drive, seconds = 5)
        drive.onWaypointAdded(pkg.stops.first { it.id == "s1" })
        val signals = run(drive, seconds = 60) { drive.progressKm > 31.0 }

        val parked = signals.filterIsInstance<Signal.Parked>().map { it.parked }
        assertEquals("Parks at the rest area, then leaves", listOf(true, false), parked)
        val charging = signals.filterIsInstance<Signal.Battery>()
        assertTrue("Charging is reported while parked", charging.any { it.charging })
        assertEquals("Charged to the scenario's target", 80, charging.filter { it.charging }.maxOf { it.percent })
        assertFalse("And reported as stopped on leaving", charging.last().charging)
    }

    @Test
    fun timeParkedIsNotDrivingTime() {
        val drive = MockDrive(pkg, scenario())
        drive.onWaypointAdded(pkg.stops.first { it.id == "s1" })
        run(drive, seconds = 45) { drive.progressKm > 30.5 }
        // 30 km at 60 km/h is 30 minutes of driving, however long the stop took.
        assertEquals(30.5, drive.tripMinutes, 0.6)
    }

    @Test
    fun arrivesParksAndStops() {
        val drive = MockDrive(pkg, scenario())
        val signals = run(drive, seconds = 200)
        assertTrue(drive.arrived)
        assertEquals(1, signals.count { it is Signal.DestinationArrived })
        assertEquals(100.0, drive.progressKm, 0.001)
        assertTrue(drive.advance(1000).isEmpty())
    }

    @Test
    fun scriptedRepliesAnswerAfterADelayAndCanBeCancelled() {
        val replies = """, "autoReplies": { "delayMs": 1000, "yesNo": "응", "quiz": ["correct", "wrong", "사과"] }"""
        val drive = MockDrive(pkg, scenario(replies))
        drive.start()

        drive.onListen(ListenMode.YES_NO, listOf("응", "아니"))
        assertTrue(drive.advance(900).none { it is Signal.Utterance })
        assertEquals("응", drive.advance(200).filterIsInstance<Signal.Utterance>().single().text)

        drive.onListen(ListenMode.QUIZ_ANSWER, listOf("바다"))
        assertEquals("바다", drive.advance(1100).filterIsInstance<Signal.Utterance>().single().text)
        drive.onListen(ListenMode.QUIZ_ANSWER, listOf("바다"))
        assertEquals("잘 모르겠어요", drive.advance(1100).filterIsInstance<Signal.Utterance>().single().text)
        drive.onListen(ListenMode.QUIZ_ANSWER, listOf("바다"))
        assertEquals("사과", drive.advance(1100).filterIsInstance<Signal.Utterance>().single().text)

        drive.onListen(ListenMode.YES_NO, listOf("응", "아니"))
        drive.onListenCancelled()
        assertTrue(drive.advance(2000).none { it is Signal.Utterance })

        drive.autoRepliesEnabled = false
        drive.onListen(ListenMode.YES_NO, listOf("응", "아니"))
        assertTrue(drive.advance(2000).none { it is Signal.Utterance })
    }

    @Test
    fun rejectsBrokenScenarios() {
        fun rejected(json: String, fragment: String) {
            try {
                ScenarioParser.parse(json)
                fail("Expected the scenario to be rejected")
            } catch (e: ScenarioFormatException) {
                assertTrue("Expected \"$fragment\" in: ${e.message}", e.message!!.contains(fragment))
            }
        }
        val base = """{ "schema": 1, "route": "r", "timeScale": 40, "players": [], "battery": { "startPercent": 50, "kmPerPercent": 5 }, "legs": [ { "untilKm": 10, "kph": 60 } ] }"""
        ScenarioParser.parse(base)
        rejected(base.replace("\"timeScale\": 40", "\"timeScale\": 0"), "timeScale")
        rejected(base.replace("\"kph\": 60", "\"kph\": 0"), "legs")
        rejected(base.replace("\"schema\": 1", "\"schema\": 9"), "schema 9")
        rejected(base.replace("\"legs\"", "\"events\": [ { \"atKm\": 3 } ], \"legs\""), "needs")
        rejected(base.replace("\"route\": \"r\",", ""), "route")
    }
}
