package app.gildongmu.core

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechGuardTest {

    /** A guard with a pretend engine, a clock, and a record of everything that came out. */
    private class Rig(var accepts: Boolean = true) {
        var now = 0L
        val said = ArrayList<String>()
        var silenced = 0
        val finished = ArrayList<String>()
        val status = ArrayList<String?>()

        val guard = SpeechGuard(
            say = { _, text -> if (accepts) said.add(text); accepts },
            silence = { silenced++ },
            onFinished = { finished.add(it) },
            onStatus = { status.add(it) },
            createdAtMs = 0,
            estimateMs = { 4_000 } // every sentence takes four seconds to say
        )

        /** Lets [ms] pass, ticking ten times a second as the platform does. */
        fun pass(ms: Long) {
            val end = now + ms
            while (now < end) {
                now += 100
                guard.tick(now)
            }
        }

        fun ready() = guard.onEngineStarted(ok = true, problem = null, nowMs = now)
        fun speak(id: String) = guard.speak(id, "문장 $id.", now)
        fun done(id: String? = null) = guard.onEngineReport(id, failed = false, nowMs = now)
    }

    @Test
    fun aWorkingVoiceEndsEachSentenceWhenTheEngineSaysSo() {
        val r = Rig()
        r.ready()
        r.speak("a")
        r.pass(3_000)
        assertTrue("Still talking", r.finished.isEmpty())
        r.done("a")
        assertEquals(listOf("a"), r.finished)
        assertEquals(listOf<String?>(null), r.status)
        assertFalse(r.guard.busy)
        assertEquals(1, r.guard.heard)
    }

    @Test
    fun withoutAnEngineASentenceLastsAsLongAsSayingItWould() {
        val r = Rig()
        r.guard.onEngineStarted(ok = false, problem = null, nowMs = 0)
        assertEquals(listOf<String?>(SpeechGuard.NO_ENGINE), r.status)

        r.speak("a")
        r.pass(3_900)
        assertTrue("The caption is still up", r.finished.isEmpty())
        r.pass(100)
        assertEquals(listOf("a"), r.finished)
        assertTrue("Nothing was handed to an engine that is not there", r.said.isEmpty())
    }

    @Test
    fun theFirstSentenceWaitsForAnEngineThatIsStillStarting() {
        val r = Rig()
        r.speak("a")
        r.pass(2_000)
        assertTrue(r.said.isEmpty())
        r.ready()
        assertEquals("Said as soon as the engine is up", listOf("문장 a."), r.said)
        r.pass(2_500)
        r.done("a")
        assertEquals(listOf("a"), r.finished)
    }

    @Test
    fun anEngineThatNeverStartsDoesNotHoldTheHostUp() {
        val r = Rig()
        r.speak("a")
        r.pass(4_900)
        assertTrue(r.status.isEmpty())
        r.pass(100)
        assertEquals(listOf<String?>(SpeechGuard.NO_ANSWER), r.status)
        r.pass(4_000)
        assertEquals("The sentence was shown for its time instead", listOf("a"), r.finished)

        // If the engine turns up after all, the voice comes on from the next sentence.
        r.ready()
        assertNull(r.status.last())
        r.speak("b")
        assertEquals(listOf("문장 b."), r.said)
    }

    @Test
    fun anEngineThatFinishesAtOnceIsNotBelieved() {
        val r = Rig()
        r.ready()
        r.speak("a")
        r.pass(100)
        r.done("a") // "done" a tenth of a second into a four-second sentence
        assertTrue("The sentence stays up", r.finished.isEmpty())
        r.pass(3_800)
        assertTrue(r.finished.isEmpty())
        r.pass(100)
        assertEquals("And ends when it would have been said", listOf("a"), r.finished)
        assertFalse("Once could be a hiccup", r.guard.timed)

        r.speak("b")
        r.pass(100)
        r.done("b")
        assertTrue("Twice is how this engine behaves", r.guard.timed)
        assertEquals(SpeechGuard.REPORTS_AT_ONCE, r.status.last())
        r.pass(3_900)
        assertEquals(listOf("a", "b"), r.finished)

        // From now on the engine is still given every sentence, but its word is not waited for.
        r.speak("c")
        assertEquals(3, r.said.size)
        r.pass(100)
        r.done("c")
        assertEquals("An early report changes nothing any more", listOf("a", "b"), r.finished)
        r.pass(3_900)
        assertEquals(listOf("a", "b", "c"), r.finished)
        assertEquals(2, r.guard.unheard)
    }

    @Test
    fun anEngineThatNeverReportsIsWaitedForTwiceAndThenTimed() {
        val r = Rig()
        r.ready()
        r.speak("a")
        r.pass(16_900) // three times the length plus five seconds is 17 s
        assertTrue(r.finished.isEmpty())
        r.pass(100)
        assertEquals(listOf("a"), r.finished)
        assertFalse(r.guard.timed)

        r.speak("b")
        r.pass(17_000)
        assertEquals(listOf("a", "b"), r.finished)
        assertTrue(r.guard.timed)
        assertEquals(SpeechGuard.NEVER_REPORTS, r.status.last())

        r.speak("c")
        r.pass(4_000)
        assertEquals("No more long waits", listOf("a", "b", "c"), r.finished)
    }

    @Test
    fun aSentenceTheEngineRefusesIsGivenItsTime() {
        val r = Rig(accepts = false)
        r.ready()
        r.speak("a")
        r.pass(4_000)
        assertEquals(listOf("a"), r.finished)
    }

    @Test
    fun aSentenceThatIsCutOffIsNeverReported() {
        val r = Rig()
        r.ready()
        r.speak("a")
        r.pass(1_000)
        r.guard.stop()
        assertEquals(1, r.silenced)
        r.done("a") // the engine's late word about the sentence that was cut
        r.pass(30_000)
        assertTrue(r.finished.isEmpty())
        assertFalse(r.guard.busy)
    }

    @Test
    fun aLateReportAboutAnEarlierSentenceIsNotTakenForTheCurrentOne() {
        val r = Rig()
        r.ready()
        r.speak("a")
        r.pass(1_000)
        r.guard.stop()
        r.speak("b")
        r.done("a") // names the old sentence: ignored
        r.pass(3_000)
        assertTrue(r.finished.isEmpty())
        r.done("b")
        assertEquals(listOf("b"), r.finished)
        assertEquals("And it did not count against the engine", 0, r.guard.unheard)
    }

    @Test
    fun anEngineThatDoesNotNameItsSentencesIsStillSafe() {
        // The Pleos engine reports "done" without saying which sentence. A stale report then looks
        // like the new sentence ending at once; the sentence must keep its time all the same.
        val r = Rig()
        r.ready()
        r.speak("a")
        r.pass(1_000)
        r.guard.stop()
        r.speak("b")
        r.done(null)
        r.pass(3_900)
        assertTrue(r.finished.isEmpty())
        r.pass(100)
        assertEquals(listOf("b"), r.finished)
    }

    /**
     * Whatever the engine does (starts late or never, accepts or refuses, reports early, late,
     * twice or not at all) every sentence the host was not told to cut off is reported exactly
     * once, in order, and within a bounded time.
     */
    @Test
    fun everySentenceEndsExactlyOnce() {
        val random = Random(20261112)
        repeat(1_500) { round ->
            val r = Rig(accepts = random.nextInt(5) != 0)
            val expected = ArrayList<String>()
            when (random.nextInt(3)) {
                0 -> r.ready()
                1 -> r.guard.onEngineStarted(ok = false, problem = "없음", nowMs = 0)
                else -> {} // never answers, or answers in the middle of things below
            }
            repeat(12) { n ->
                val id = "s$n"
                r.speak(id)
                var cut = false
                // What the engine and the host get up to while the sentence is in the air.
                repeat(random.nextInt(4)) {
                    r.pass(random.nextInt(3_000).toLong())
                    when (random.nextInt(7)) {
                        0 -> r.done(id)
                        1 -> r.done(null)
                        2 -> r.guard.onEngineReport(id, failed = true, nowMs = r.now)
                        3 -> r.done("s${n - 1}")
                        4 -> r.ready()
                        5 -> if (!cut && id !in r.finished) {
                            r.guard.stop()
                            cut = true
                        }
                        else -> {}
                    }
                }
                if (!cut) {
                    expected.add(id)
                    // The longest a sentence can take: a five-second start, then three lengths and five seconds.
                    r.pass(5_000 + 17_000 + 100)
                    assertEquals("Round $round, sentence $id: ${r.finished}", expected, r.finished)
                } else {
                    r.pass(1_000)
                    assertFalse("Round $round: the cut sentence $id was reported", id in r.finished)
                }
            }
            r.pass(6_000)
            assertFalse("Round $round: still busy with nothing left to say", r.guard.busy)
        }
    }
}
