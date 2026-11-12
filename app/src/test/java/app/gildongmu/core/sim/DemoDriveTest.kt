package app.gildongmu.core.sim

import app.gildongmu.core.HostSnapshot
import app.gildongmu.core.LightCue
import app.gildongmu.core.Phase
import app.gildongmu.core.RoutePackageParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plays the bundled Seoul to Gangneung demo from start to finish without a car, a screen or
 * audio, and checks that the story the demo is meant to tell actually happens, in order.
 *
 * The first test prints the transcript to standard output.
 */
class DemoDriveTest {

    private class Run(val platform: HeadlessPlatform, val lines: List<HeadlessPlatform.Line>, val minutes: Double, val end: HostSnapshot)

    private fun asset(path: String): String {
        // Unit tests run with the module directory (app/) as the working directory.
        val file = listOf(File("src", path), File("app/src", path)).firstOrNull { it.exists() }
            ?: error("Cannot find $path from ${File(".").absolutePath}")
        return file.readText()
    }

    /** Runs the whole demo. [slowdown] makes every sentence take that many times longer to say. */
    private fun play(slowdown: Double = 1.0): Run {
        val pkg = RoutePackageParser.parse(asset("main/assets/routes/seoul-gangneung.json"))
        val scenario = ScenarioParser.parse(asset("mock/assets/scenarios/seoul-gangneung-demo.json"))

        var now = 0L
        val platform = HeadlessPlatform(slowdown) { now }
        val session = DemoSession(pkg, scenario, platform) { now }
        platform.dispatch = session::dispatch

        val passenger = ArrayList<HeadlessPlatform.Line>()
        session.onPassengerSpeech = { passenger.add(HeadlessPlatform.Line(now, "SAY", it)) }

        session.start()
        val stepMs = 100L
        var finishedAtMs = -1L
        while (now < 20 * 60 * 1000L) {
            now += stepMs
            session.tick(stepMs)
            platform.pump()
            val snapshot = platform.snapshot
            if (session.drive.arrived && snapshot != null && snapshot.card == null && snapshot.listening == null) {
                finishedAtMs = now
                break
            }
        }
        assertTrue("The demo should finish within 20 minutes", finishedAtMs > 0)
        // A passenger line and the host's reaction share a timestamp; show the passenger first.
        val lines = (passenger + platform.log).sortedBy { it.atMs }
        return Run(platform, lines, finishedAtMs / 60000.0, platform.snapshot!!)
    }

    /** Checks the beats of the demo, in order. */
    private fun assertStoryline(run: Run) {
        val spoken = run.platform.spoken
        fun indexOf(fragment: String): Int {
            val index = spoken.indexOfFirst { it.contains(fragment) }
            assertTrue("The host never said anything containing \"$fragment\"", index >= 0)
            return index
        }

        val beats = listOf(
            "길동무예요",              // departure briefing
            "세종대왕의 무덤",          // a story as the car passes Yeoju
            "퀴즈 시간",               // the quiz a passenger asked for
            "치악산이라는 큰 산",       // a second story, the one the navigation prompt interrupts
            "들를까요?",               // the stop suggestion at two hours
            "경유지로 추가했어요",       // accepted, and added to the route
            "충전을 시작했어요",        // at the rest area
            "이효석이 태어난 마을",      // the long story, told while parked
            "퀴즈 결승",
            "대관령을 넘어야 해요",
            "오죽헌이라는 오래된 집",
            "도착했어요"
        ).map { it to indexOf(it) }
        assertEquals("The beats should come in this order", beats.sortedBy { it.second }, beats)

        // Every story that was started was also finished: nothing was cut off by the next event.
        for (ending in listOf("만 원짜리 지폐에", "치악산이 됐대요", "메밀국수 좋아하는", "바다가 가까워져요", "지폐에 그려져 있답니다", "또 만나요")) {
            indexOf(ending)
        }

        // The stop was added to navigation exactly once, and it was the one the host proposed.
        assertEquals(listOf("횡성휴게소"), run.platform.waypoints.map { it.name })

        // The navigation prompt interrupted the Chiaksan story, and the sentence was said again.
        val log = run.platform.log
        val stop = log.indexOfFirst { it.kind == "STOP" }
        assertTrue("Speech should be held for the navigation prompt", stop > 0)
        val interrupted = log.subList(0, stop).last { it.kind == "HOST" }.text
        val resumed = log.subList(stop, log.size).first { it.kind == "HOST" }.text
        assertEquals("The interrupted sentence should be repeated after the prompt", interrupted, resumed)
        assertEquals("Only the navigation prompt interrupts the host", 1, log.count { it.kind == "STOP" })
        val chiaksanStarted = spoken.indexOfFirst { it.contains("치악산이라는 큰 산") }
        val chiaksanEnded = spoken.indexOfFirst { it.contains("치악산이 됐대요") }
        assertTrue("It was the Chiaksan story that was interrupted", spoken.indexOf(interrupted) in chiaksanStarted..chiaksanEnded)

        // A correct answer flashed the ambient light; arrival did too.
        assertTrue(run.platform.lights.contains(LightCue.CORRECT_ANSWER))
        assertEquals(LightCue.ARRIVAL, run.platform.lights.last())

        // The parked-only screen opened at the rest area, closed for the drive, and opened on arrival.
        assertEquals(listOf("정차 화면 열림", "정차 화면 닫힘", "정차 화면 열림"), log.filter { it.kind == "UI" }.map { it.text })

        assertEquals(Phase.ARRIVED, run.end.phase)
        assertEquals("All five places in the demo were told", 5, run.end.places.count { it.state == app.gildongmu.core.PlaceState.TOLD })
        assertTrue("Someone should have scored", run.end.scores.any { it.score > 0 })
    }

    @Test
    fun bothBuildsCarryTheSameScenario() {
        // The car build can play the scripted drive too, in the car's own voice, so it has a copy
        // of the scenario. A copy that drifted would make the two demos differ without anyone noticing.
        assertEquals(
            asset("mock/assets/scenarios/seoul-gangneung-demo.json"),
            asset("pleos/assets/scenarios/seoul-gangneung-demo.json")
        )
    }

    @Test
    fun theDemoTellsItsWholeStory() {
        val run = play()
        println("=== 서울 → 강릉 데모 대본 (" + "%.1f".format(run.minutes) + "분) ===")
        for (line in run.lines) {
            val t = line.atMs / 1000
            println("%02d:%02d  %-6s %s".format(t / 60, t % 60, line.kind, line.text))
        }
        assertStoryline(run)
        assertTrue("The demo should take about six minutes, took ${run.minutes}", run.minutes in 5.0..7.0)
    }

    @Test
    fun theDemoSurvivesAVoiceThatSpeaksMoreSlowlyThanEstimated() {
        // Real speech engines differ. With every sentence taking a quarter longer, the car still
        // must not outrun the host: no story may be skipped or cut off.
        assertStoryline(play(slowdown = 1.25))
    }

    @Test
    fun theDemoSurvivesAVoiceThatSpeaksFasterThanEstimated() {
        assertStoryline(play(slowdown = 0.8))
    }
}
