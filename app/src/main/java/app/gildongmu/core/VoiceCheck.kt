package app.gildongmu.core

import kotlin.math.max

/**
 * Judges whether a speech engine really said a sentence.
 *
 * An engine can be installed and still produce nothing: no voice data, a stub service, no audio
 * device. Such an engine reports each sentence as done, or as failed, the moment it receives it.
 * Taken at its word, the host would run through a whole story in a blink, with nothing heard and
 * the captions gone before anyone could read them.
 *
 * So a sentence that ends much sooner than it could have been said counts as unheard: it stays up
 * for the time it would have taken to say. After [limit] unheard sentences in a row the engine's
 * word is no longer trusted ([distrusted]), and the caller should pace every sentence by its
 * estimated length instead.
 */
class VoiceCheck(
    /** A sentence over in less than this share of its estimated length was not really said. */
    private val tooFast: Double = 0.35,
    private val limit: Int = 2
) {
    /** Sentences the engine took a believable time over. */
    var heard = 0
        private set

    /** Sentences the engine failed, or finished too quickly to have said. */
    var unheard = 0
        private set

    private var unheardInARow = 0

    val distrusted: Boolean get() = unheardInARow >= limit

    /**
     * The engine reported a sentence over [elapsedMs] after it was handed over; saying it was
     * estimated to take [estimateMs]. Returns how much longer the sentence should stay up: zero
     * when it was really said.
     */
    fun onEngineReport(elapsedMs: Long, estimateMs: Long, failed: Boolean): Long {
        if (!failed && elapsedMs >= estimateMs * tooFast) {
            heard++
            unheardInARow = 0
            return 0
        }
        unheard++
        unheardInARow++
        return max(0L, estimateMs - elapsedMs)
    }
}
