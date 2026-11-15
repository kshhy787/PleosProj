package app.gildongmu.core.sim

import app.gildongmu.core.Fixtures
import app.gildongmu.core.ListenMode
import app.gildongmu.core.Signal
import app.gildongmu.core.Spot
import app.gildongmu.core.toWaypoint
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MockDriveTest {

    private val pkg = Fixtures.route()

    /** [stops] are the scenario's plans for where the car stops; [extra] is spliced in after them. */
    private fun scenario(extra: String = "", stops: String = """{ "stop": "s1", "seconds": 10, "chargeTo": 80 }""") = ScenarioParser.parse(
        """
        {
          "schema": 1, "route": "test", "timeScale": 60, "players": ["가", "나"],
          "battery": { "startPercent": 50, "kmPerPercent": 5 },
          "legs": [ { "untilKm": 50, "kph": 60 }, { "untilKm": 999, "kph": 120, "timeScale": 30 } ],
          "events": [ { "atKm": 10, "say": "퀴즈 하자" }, { "atKm": 20, "turnDistanceM": 300, "holdSeconds": 3 } ],
          "stops": [ $stops ]
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

    /**
     * Drives to the end in 100 ms steps and returns each stop on the way, rounded: the kilometre
     * along the route where the car parked, and how many real seconds it stayed there.
     */
    private fun stopsOnTheWay(drive: MockDrive): List<Pair<Int, Int>> {
        val stops = ArrayList<Pair<Int, Int>>()
        var nowMs = 0L
        var parkedAtMs = 0L
        drive.start()
        while (!drive.arrived) {
            check(nowMs < 600_000) { "The car never arrived" }
            nowMs += 100
            for (signal in drive.advance(100)) {
                if (signal !is Signal.Parked) continue
                if (signal.parked) {
                    parkedAtMs = nowMs
                } else {
                    stops.add(drive.progressKm.roundToInt() to ((nowMs - parkedAtMs) / 1000.0).roundToInt())
                }
            }
        }
        return stops
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
        drive.onWaypointAdded(pkg.stops.first { it.id == "s1" }.toWaypoint())
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
        drive.onWaypointAdded(pkg.stops.first { it.id == "s1" }.toWaypoint())
        run(drive, seconds = 45) { drive.progressKm > 30.5 }
        // 30 km at 60 km/h is 30 minutes of driving, however long the stop took.
        assertEquals(30.5, drive.tripMinutes, 0.6)
    }

    @Test
    fun stopsForHalfAMinuteAtAChosenSpotWithoutCharging() {
        // The scenario plans a charge there, as it would at a rest area, but a spot has no charger.
        val drive = MockDrive(pkg, scenario(stops = """{ "stop": "f1", "seconds": 30, "chargeTo": 80 }"""))
        // A restaurant a kilometre off the road. The scripted car stays on the route, so it stops
        // where the road passes closest.
        drive.onWaypointAdded(Fixtures.spot("f1", "국밥집", 40.0, eastKm = 1.0).toWaypoint())
        assertEquals("f1", drive.waypoint?.id)

        val signals = ArrayList(run(drive, seconds = 50))
        assertTrue("Parked by the spot", drive.parked)
        assertEquals(40.0, drive.progressKm, 0.05)
        signals.addAll(run(drive, seconds = 30))
        assertFalse("On the road again half a minute later", drive.parked)
        assertNull(drive.waypoint)

        assertEquals(listOf(false, true, false), signals.filterIsInstance<Signal.Parked>().map { it.parked })
        assertTrue("A spot has no charger", signals.filterIsInstance<Signal.Battery>().none { it.charging })
    }

    @Test
    fun parksOnlyAtAWaypointAheadAndAtLeastTwoKilometresBeforeTheEnd() {
        val drive = MockDrive(pkg, scenario())
        run(drive, seconds = 20)
        drive.onWaypointAdded(Fixtures.spot("behind", "지나온 식당", 10.0).toWaypoint())
        assertNull("Behind the car", drive.waypoint)
        // A spot by the destination meets the route on its last stretch.
        drive.onWaypointAdded(Fixtures.spot("sea", "바닷가 식당", 98.5, near = Spot.NEAR_DESTINATION, eastKm = 3.0).toWaypoint())
        assertNull("Less than two kilometres before the end", drive.waypoint)
        drive.onWaypointAdded(Fixtures.spot("market", "시장", 97.5).toWaypoint())
        assertEquals("Two and a half kilometres before the end", "market", drive.waypoint?.id)
    }

    @Test
    fun stopsAtEveryWaypointNearestFirstWhateverTheOrderTheyWereAddedIn() {
        // A rest stop agreed to and then a restaurant beyond it, or the other way round: navigation
        // goes through both in route order, and so does the scripted car.
        val restStop = pkg.stops.first { it.id == "s1" }.toWaypoint()
        val restaurant = Fixtures.spot("f1", "막국수집", 70.0).toWaypoint()
        for (order in listOf(listOf(restStop, restaurant), listOf(restaurant, restStop))) {
            val drive = MockDrive(pkg, scenario())
            order.forEach(drive::onWaypointAdded)
            assertEquals("The nearer one comes first", "s1", drive.waypoint?.id)
            assertEquals(
                "Added as ${order.map { it.name }}: the rest stop for its planned 10 s, then the restaurant for half a minute",
                listOf(30 to 10, 70 to 30),
                stopsOnTheWay(drive)
            )
        }
    }

    @Test
    fun aWaypointAddedTwiceIsOneStop() {
        val drive = MockDrive(pkg, scenario())
        val spot = Fixtures.spot("f1", "국밥집", 40.0).toWaypoint()
        drive.onWaypointAdded(spot)
        drive.onWaypointAdded(spot)
        assertEquals(listOf(40 to 30), stopsOnTheWay(drive))
    }

    @Test
    fun aWaypointAddedDuringARestIsVisitedAfterIt() {
        val drive = MockDrive(pkg, scenario())
        drive.onWaypointAdded(pkg.stops.first { it.id == "s1" }.toWaypoint())
        run(drive, seconds = 40) { drive.parked }
        assertEquals("Resting at the rest area", "s1", drive.waypoint?.id)

        // While they rest, the family picks a restaurant further on.
        drive.onWaypointAdded(Fixtures.spot("f1", "국밥집", 45.0).toWaypoint())
        val signals = run(drive, seconds = 60) { drive.parked && drive.progressKm > 40.0 }
        val parked = signals.filterIsInstance<Signal.Parked>().map { it.parked }
        assertEquals("Leaves the rest area, then parks by the restaurant", listOf(false, true), parked)
        assertEquals(45.0, drive.progressKm, 0.05)
        assertEquals("f1", drive.waypoint?.id)
    }

    @Test
    fun aSpotByTheDestinationIsDrivenPastSoTheDriveEndsWithTheArrival() {
        val drive = MockDrive(pkg, scenario())
        drive.onWaypointAdded(Fixtures.spot("sea", "바닷가 식당", 99.0, near = Spot.NEAR_DESTINATION, eastKm = 2.0).toWaypoint())
        val signals = run(drive, seconds = 200)
        assertTrue(drive.arrived)
        assertEquals("No rest before the arrival", listOf(false, true), signals.filterIsInstance<Signal.Parked>().map { it.parked })
        assertEquals(listOf(Signal.DestinationArrived, Signal.Parked(true)), signals.takeLast(2))
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
    fun choicesAndWordsHaveTheirOwnScriptedRepliesAndLeaveTheQuizScriptAlone() {
        val replies = """, "autoReplies": { "delayMs": 1000, "yesNo": "응", "quiz": ["wrong"], "choice": "두 번째" }"""
        val drive = MockDrive(pkg, scenario(replies))
        drive.start()

        drive.onListen(ListenMode.CHOICE, listOf("국밥집", "냉면집"))
        assertEquals("두 번째", drive.advance(1100).filterIsInstance<Signal.Utterance>().single().text)
        drive.onListen(ListenMode.WORD, listOf("고래", "고구마"))
        assertEquals("The first of the host's hints", "고래", drive.advance(1100).filterIsInstance<Signal.Utterance>().single().text)

        drive.onListen(ListenMode.QUIZ_ANSWER, listOf("바다"))
        val answer = drive.advance(1100).filterIsInstance<Signal.Utterance>().single().text
        assertEquals("The quiz script still starts at its first entry", "잘 모르겠어요", answer)
    }

    @Test
    fun withoutAScriptedChoiceAnOfferForSpotsGoesUnanswered() {
        // The bundled demo has no "choice", so no spot is ever added and the drive keeps to its script.
        val replies = """, "autoReplies": { "delayMs": 1000, "yesNo": "응", "quiz": [] }"""
        val drive = MockDrive(pkg, scenario(replies))
        drive.start()
        drive.onListen(ListenMode.CHOICE, listOf("국밥집", "냉면집"))
        assertTrue(drive.advance(2000).none { it is Signal.Utterance })

        // A word turn with no hint to play is not answered either.
        drive.onListen(ListenMode.WORD, emptyList())
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
