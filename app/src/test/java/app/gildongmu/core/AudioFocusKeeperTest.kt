package app.gildongmu.core

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFocusKeeperTest {

    /** Stands in for the system: counts requests and withdrawals, and records what the host is told. */
    private class Channel(var grants: Boolean = true) {
        var requests = 0
        var abandons = 0
        val told = ArrayList<Boolean>()
        val keeper = AudioFocusKeeper(
            request = { requests++; grants },
            abandon = { abandons++ },
            onAvailable = { told.add(it) },
            lingerMs = 1_500,
            briefLossLimitMs = 15_000,
            lostPauseMs = 2_000
        )

        /** Ticks four times a second from [fromMs] to [toMs], as the platform does. */
        fun run(fromMs: Long, toMs: Long) {
            var t = fromMs
            while (t <= toMs) {
                keeper.tick(t)
                t += 250
            }
        }
    }

    @Test
    fun holdsTheChannelAcrossSentencesAndGivesItBackAfterAPause() {
        val c = Channel()
        c.keeper.beforeSpeaking()
        c.keeper.afterSpeaking(1_000)
        c.run(1_000, 2_000)
        c.keeper.beforeSpeaking() // the next sentence, a second later
        c.keeper.afterSpeaking(3_000)
        assertEquals("Asked once for both sentences", 1, c.requests)
        assertEquals(0, c.abandons)

        c.run(3_000, 4_400)
        assertTrue("Still held just before the pause is up", c.keeper.holding)
        c.run(4_500, 5_000)
        assertFalse(c.keeper.holding)
        assertEquals(1, c.abandons)
        assertFalse("Nothing left to do", c.keeper.busy)
        assertTrue("The host was never told anything", c.told.isEmpty())
    }

    @Test
    fun aBriefLossEndsWhenTheSystemHandsTheChannelBack() {
        val c = Channel()
        c.keeper.beforeSpeaking()
        c.keeper.onLostBriefly(500)
        assertEquals(listOf(false), c.told)

        c.keeper.afterSpeaking(600) // the host stopped talking because of the loss
        c.run(600, 5_000)
        assertEquals("The request stays in while waiting, or the system would never say it is back", 0, c.abandons)
        assertTrue(c.keeper.waiting)

        c.keeper.onGained(5_000)
        assertEquals(listOf(false, true), c.told)
        assertTrue(c.keeper.holding)

        c.run(5_000, 7_000)
        assertFalse("With nothing said after getting it back, the channel is given up again", c.keeper.holding)
        assertEquals(1, c.abandons)
    }

    @Test
    fun stopsWaitingIfTheSystemNeverHandsTheChannelBack() {
        val c = Channel()
        c.keeper.beforeSpeaking()
        c.keeper.onLostBriefly(0)
        c.run(0, 14_750)
        assertEquals("Still waiting inside the limit", listOf(false), c.told)

        c.run(15_000, 15_000)
        assertEquals(listOf(false, true), c.told)
        assertEquals("The stale request is withdrawn", 1, c.abandons)
        assertFalse(c.keeper.holding)

        c.keeper.beforeSpeaking()
        assertEquals("The next sentence asks afresh", 2, c.requests)
    }

    @Test
    fun aPermanentLossIsAPauseNotTheEnd() {
        val c = Channel()
        c.keeper.beforeSpeaking()
        c.keeper.onLost(1_000)
        assertEquals(listOf(false), c.told)
        assertFalse("The system has already dropped the request", c.keeper.holding)

        c.run(1_000, 2_750)
        assertEquals(listOf(false), c.told)
        c.run(3_000, 3_000)
        assertEquals(listOf(false, true), c.told)
        assertEquals("There was nothing left to withdraw", 0, c.abandons)

        c.keeper.beforeSpeaking()
        assertEquals(2, c.requests)
        assertTrue(c.keeper.holding)
    }

    @Test
    fun aBriefLossThatTurnsPermanentStillEnds() {
        val c = Channel()
        c.keeper.beforeSpeaking()
        c.keeper.onLostBriefly(0)
        c.keeper.onLost(4_000)
        assertEquals("The host is told once, not twice", listOf(false), c.told)
        c.run(4_000, 6_000)
        assertEquals(listOf(false, true), c.told)
    }

    @Test
    fun givesTheChannelUpBeforeListening() {
        val c = Channel()
        c.keeper.beforeSpeaking()
        c.keeper.afterSpeaking(1_000)
        c.keeper.beforeListening()
        assertFalse(c.keeper.holding)
        assertEquals(1, c.abandons)
        c.run(1_000, 5_000)
        assertEquals("And not a second time when the pause runs out", 1, c.abandons)

        // While waiting for the channel to come back, the request has to stay in.
        c.keeper.beforeSpeaking()
        c.keeper.onLostBriefly(6_000)
        c.keeper.beforeListening()
        assertTrue(c.keeper.holding)
        assertEquals(1, c.abandons)
    }

    @Test
    fun speaksEvenWhenTheSystemRefusesTheChannel() {
        val c = Channel(grants = false)
        c.keeper.beforeSpeaking()
        assertFalse(c.keeper.holding)
        c.keeper.afterSpeaking(1_000)
        c.run(1_000, 4_000)
        assertEquals("Nothing was held, so nothing is withdrawn", 0, c.abandons)
        assertTrue("A refusal is not reported as a loss", c.told.isEmpty())

        c.keeper.beforeSpeaking()
        assertEquals("It asks again for the next sentence", 2, c.requests)
    }

    @Test
    fun shutdownWithdrawsTheRequest() {
        val c = Channel()
        c.keeper.beforeSpeaking()
        c.keeper.onLostBriefly(0)
        c.keeper.shutdown()
        assertEquals(1, c.abandons)
        assertFalse(c.keeper.busy)
    }

    @Test
    fun ignoresReportsAboutARequestItNoLongerHas() {
        val c = Channel()
        c.keeper.beforeSpeaking()
        c.keeper.afterSpeaking(0)
        c.keeper.beforeListening()

        // The system's reports travel through a message queue and can arrive after the fact.
        c.keeper.onLostBriefly(100)
        c.keeper.onLost(200)
        c.keeper.onGained(300)
        assertTrue("None of them starts a wait or is passed on to the host", c.told.isEmpty())
        assertFalse(c.keeper.holding)
        assertFalse(c.keeper.busy)
    }

    /**
     * Whatever the system reports, and whenever: every "gone" the host is told is followed by a
     * "back" within the longest wait, the channel is not left held with nothing to say, and it is
     * never withdrawn more often than it was asked for.
     */
    @Test
    fun theHostIsNeverLeftWaiting() {
        val random = Random(20261111)
        repeat(2_000) { round ->
            val c = Channel(grants = random.nextInt(4) != 0)
            var now = 0L
            var speaking = false
            repeat(40) {
                now += random.nextInt(4_000)
                c.keeper.tick(now)
                when (random.nextInt(8)) {
                    0, 1 -> {
                        c.keeper.beforeSpeaking()
                        speaking = true
                    }
                    2 -> if (speaking) {
                        c.keeper.afterSpeaking(now)
                        speaking = false
                    }
                    3 -> if (!speaking) c.keeper.beforeListening()
                    4 -> c.keeper.onLostBriefly(now)
                    5 -> c.keeper.onLost(now)
                    6 -> c.keeper.onGained(now)
                    else -> {}
                }
                assertTrue("Round $round: withdrew more often than it asked", c.abandons <= c.requests)
            }
            // Every sentence ends, by finishing or by being cut off. Then nothing more happens.
            if (speaking) c.keeper.afterSpeaking(now)
            c.run(now, now + 16_000)

            assertFalse("Round $round: still waiting at the end", c.keeper.waiting)
            assertFalse("Round $round: still holding the channel with nothing to say", c.keeper.holding)
            assertFalse(c.keeper.busy)
            for (i in c.told.indices) {
                assertEquals("Round $round: the host is told \"gone\" and \"back\" in turn: ${c.told}", i % 2 != 0, c.told[i])
            }
            assertEquals("Round $round: the last word is \"back\": ${c.told}", 0, c.told.size % 2)
        }
    }
}
