package app.gildongmu.core

/**
 * Decides when the host holds the car's audio channel ("audio focus" on Android), and what the
 * host is told when something else takes the channel away.
 *
 * The rule it exists to keep: once the host has been told the channel is unavailable, it is
 * always told when it is available again. The host stops talking on the first and waits for the
 * second, so a "gone" that is never followed by a "back" would leave it silent for the rest of
 * the trip. Two things could cause that, and both are handled here:
 *
 *  - The system only says "back" to an app that still has its request in. So the request is
 *    never withdrawn while the host is waiting, and if the system stays silent for
 *    [briefLossLimitMs] the keeper stops waiting on its own.
 *  - After a permanent loss the system says nothing more. The keeper pauses for [lostPauseMs]
 *    and then lets the host carry on; the next sentence asks for the channel again.
 *
 * This is plain logic with no Android in it, so that it can be tested. The platform supplies
 * [request] (true if the channel was granted) and [abandon], passes on what the system reports,
 * and calls [tick] a few times a second for as long as [busy] is true. Times are any monotonic
 * clock in milliseconds.
 */
class AudioFocusKeeper(
    private val request: () -> Boolean,
    private val abandon: () -> Unit,
    private val onAvailable: (Boolean) -> Unit,
    /** How long to keep the channel after a sentence, so it is not given up between sentences. */
    private val lingerMs: Long = 1_500,
    /** How long to wait for the system to hand the channel back after a brief loss. */
    private val briefLossLimitMs: Long = 15_000,
    /** How long to stay quiet after losing the channel for good, before carrying on. */
    private val lostPauseMs: Long = 2_000
) {
    /** A request for the channel is in with the system. */
    var holding = false
        private set

    /** The host has been told the channel is unavailable and is waiting to hear otherwise. */
    var waiting = false
        private set

    private var releaseAtMs: Long? = null
    private var stopWaitingAtMs: Long? = null

    /** Whether [tick] still has something to do. */
    val busy: Boolean get() = holding || waiting || releaseAtMs != null

    /**
     * A sentence is about to be spoken. Asks for the channel unless it is already held. The
     * sentence should be spoken whatever the answer: a host that talks over something is a
     * smaller problem than a host that never talks.
     */
    fun beforeSpeaking() {
        releaseAtMs = null
        if (!holding) holding = request()
    }

    /** A sentence has finished or was cut off. */
    fun afterSpeaking(nowMs: Long) {
        releaseAtMs = nowMs + lingerMs
    }

    /**
     * The host is about to listen. A speech recogniser may take the channel for itself while it
     * listens; giving it up first keeps that from looking like an interruption of the host.
     */
    fun beforeListening() {
        if (waiting) return
        releaseAtMs = null
        release()
    }

    // The system reports on a request that is in. A report that arrives after the request was
    // withdrawn is late news about nothing, and acting on it would start a wait nobody ends.

    /** The system handed the channel back. */
    fun onGained(nowMs: Long) {
        if (!holding) return
        // If the host has nothing to say now, give the channel back rather than sit on it.
        releaseAtMs = nowMs + lingerMs
        stopWaiting()
    }

    /** Something else needs the channel for a moment; the system will say when it is done. */
    fun onLostBriefly(nowMs: Long) {
        if (!holding) return
        startWaiting(untilMs = nowMs + briefLossLimitMs)
    }

    /** Something else has taken the channel for good; the system has dropped this app's request. */
    fun onLost(nowMs: Long) {
        if (!holding) return
        holding = false
        startWaiting(untilMs = nowMs + lostPauseMs)
    }

    fun tick(nowMs: Long) {
        val stopAt = stopWaitingAtMs
        if (waiting && stopAt != null && nowMs >= stopAt) {
            // No word from the system. Withdraw the old request; the next sentence asks afresh.
            release()
            stopWaiting()
        }
        val releaseAt = releaseAtMs
        if (!waiting && releaseAt != null && nowMs >= releaseAt) {
            releaseAtMs = null
            release()
        }
    }

    /** The app is closing. */
    fun shutdown() {
        releaseAtMs = null
        stopWaitingAtMs = null
        waiting = false
        release()
    }

    private fun startWaiting(untilMs: Long) {
        stopWaitingAtMs = untilMs
        if (!waiting) {
            waiting = true
            onAvailable(false)
        }
    }

    private fun stopWaiting() {
        stopWaitingAtMs = null
        if (waiting) {
            waiting = false
            onAvailable(true)
        }
    }

    private fun release() {
        if (holding) {
            holding = false
            abandon()
        }
    }
}
