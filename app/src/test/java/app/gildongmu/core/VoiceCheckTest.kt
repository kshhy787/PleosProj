package app.gildongmu.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCheckTest {

    @Test
    fun aSentenceThatTookABelievableTimeWasSaid() {
        val check = VoiceCheck()
        assertEquals("Nothing more to wait for", 0L, check.onEngineReport(elapsedMs = 3_200, estimateMs = 4_000, failed = false))
        // A voice that talks twice as fast as the estimate is still a voice.
        assertEquals(0L, check.onEngineReport(elapsedMs = 2_000, estimateMs = 4_000, failed = false))
        assertEquals(2, check.heard)
        assertEquals(0, check.unheard)
        assertFalse(check.distrusted)
    }

    @Test
    fun aSentenceFinishedAtOnceStaysUpForTheTimeItWouldHaveTaken() {
        val check = VoiceCheck()
        // "Done" 40 ms after a four-second sentence was handed over: nobody heard that.
        assertEquals(3_960L, check.onEngineReport(elapsedMs = 40, estimateMs = 4_000, failed = false))
        assertEquals(1, check.unheard)
        assertFalse("Once could be a hiccup", check.distrusted)
    }

    @Test
    fun aFailedSentenceIsUnheardHoweverLongItTook() {
        val check = VoiceCheck()
        assertEquals(1_000L, check.onEngineReport(elapsedMs = 3_000, estimateMs = 4_000, failed = true))
        assertEquals("Never a negative wait", 0L, check.onEngineReport(elapsedMs = 9_000, estimateMs = 4_000, failed = true))
        assertEquals(2, check.unheard)
    }

    @Test
    fun twoUnheardSentencesInARowEndTheTrust() {
        val check = VoiceCheck()
        check.onEngineReport(10, 4_000, failed = false)
        assertFalse(check.distrusted)
        check.onEngineReport(3_000, 4_000, failed = false) // a real one in between starts the count again
        check.onEngineReport(10, 4_000, failed = false)
        assertFalse(check.distrusted)
        check.onEngineReport(10, 4_000, failed = false)
        assertTrue(check.distrusted)
        assertEquals(1, check.heard)
        assertEquals(3, check.unheard)
    }

    @Test
    fun shortSentencesAreNotMistakenForSilence() {
        val check = VoiceCheck()
        // "네!" is estimated at about 0.7 s and really takes about 0.4 s.
        assertEquals(0L, check.onEngineReport(elapsedMs = 400, estimateMs = 700, failed = false))
        assertEquals(1, check.heard)
    }
}
