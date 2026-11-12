package app.gildongmu.core

/**
 * Stands between the host and a speech engine, and makes sure every sentence ends.
 *
 * The host says one sentence, waits to be told it is over, and says the next. An engine that
 * never tells it leaves the host silent for the rest of the trip. One that tells it at once,
 * without having spoken, makes the host race through a story nobody hears or has time to read.
 * Real engines do both: a voice that is not installed, a service that never starts, a stub that
 * answers "done" on arrival.
 *
 * So the guard decides when a sentence is over, and the engine's word is only one input:
 *
 *  - No engine, or one that refuses the sentence: the sentence lasts as long as saying it would
 *    have taken, so that the captions still tell the story.
 *  - An engine that has not finished starting after [startLimitMs]: the same, until it does.
 *  - An engine that reports the sentence over too soon to have said it: the sentence stays up for
 *    the rest of its time ([VoiceCheck]).
 *  - An engine that never reports at all: the sentence ends after a generous wait.
 *  - After two sentences of either kind the guard stops waiting on the engine and times every
 *    sentence itself. It still hands them over, in case there is sound after all.
 *
 * Every sentence given to [speak] is reported through [onFinished] exactly once, unless [stop] or
 * the next sentence cuts it off first. [onStatus] carries a line for the screen when the voice is
 * not working (null when it is), so that silence is explained rather than mysterious.
 *
 * Plain logic with no clock of its own: the platform calls [tick] several times a second while
 * [busy], and passes the time (any monotonic clock, in milliseconds) with every call. All calls
 * must come from one thread.
 */
class SpeechGuard(
    /** Hands a sentence to the engine. False if the engine would not take it. */
    private val say: (utteranceId: String, text: String) -> Boolean,
    /** Cuts the engine off. */
    private val silence: () -> Unit,
    private val onFinished: (utteranceId: String) -> Unit,
    private val onStatus: (message: String?) -> Unit,
    createdAtMs: Long,
    private val startLimitMs: Long = 5_000,
    private val estimateMs: (text: String) -> Long = { (Speech.estimateSeconds(it) * 1000).toLong() }
) {
    private enum class Engine { STARTING, READY, ABSENT }

    private var engine = Engine.STARTING
    private val startDeadlineMs = createdAtMs + startLimitMs
    private var pending: Pair<String, String>? = null

    // The sentence in progress.
    private var current: String? = null
    private var sinceMs = 0L
    private var lengthMs = 0L
    private var endsAtMs = 0L

    /** The sentence ends when the engine says so; [endsAtMs] is then only the longest it may take. */
    private var awaitingEngine = false

    private val check = VoiceCheck()
    private var unanswered = 0

    /** The engine's word is no longer waited for: every sentence gets its estimated time. */
    var timed = false
        private set

    val heard: Int get() = check.heard
    val unheard: Int get() = check.unheard

    /** Whether [tick] has anything to watch. */
    val busy: Boolean get() = engine == Engine.STARTING || current != null

    /**
     * The engine has finished starting. [ok] false means there is no usable voice; [problem] is
     * what to tell the user. May arrive late, after the guard has stopped waiting: a late success
     * still switches the voice on.
     */
    fun onEngineStarted(ok: Boolean, problem: String?, nowMs: Long) {
        val wasStarting = engine == Engine.STARTING
        engine = if (ok) Engine.READY else Engine.ABSENT
        onStatus(if (ok) null else problem ?: NO_ENGINE)
        if (wasStarting) sayPending(nowMs)
    }

    fun speak(utteranceId: String, text: String, nowMs: Long) {
        if (engine == Engine.STARTING) {
            // Wait for the engine rather than lose the first sentence to a slow start.
            pending = utteranceId to text
            return
        }
        current = utteranceId
        sinceMs = nowMs
        lengthMs = estimateMs(text)
        val accepted = engine == Engine.READY && say(utteranceId, text)
        if (accepted && !timed) {
            awaitingEngine = true
            endsAtMs = nowMs + lengthMs * 3 + 5_000
        } else {
            awaitingEngine = false
            endsAtMs = nowMs + lengthMs
        }
    }

    /**
     * The engine reports a sentence over: said, or [failed]. [utteranceId] is the sentence it is
     * talking about, if the engine says which; null if it does not.
     */
    fun onEngineReport(utteranceId: String?, failed: Boolean, nowMs: Long) {
        val id = current ?: return
        if (!awaitingEngine || (utteranceId != null && utteranceId != id)) return
        unanswered = 0
        val holdMs = check.onEngineReport(nowMs - sinceMs, lengthMs, failed)
        if (holdMs == 0L) {
            finish()
            return
        }
        // Nothing was heard: the sentence stays for the time it would have taken to say.
        awaitingEngine = false
        endsAtMs = nowMs + holdMs
        if (check.distrusted) distrust(REPORTS_AT_ONCE)
    }

    /** Cuts off whatever is being said. Nothing is reported for it. */
    fun stop() {
        pending = null
        current = null
        if (engine == Engine.READY) silence()
    }

    fun tick(nowMs: Long) {
        if (engine == Engine.STARTING && nowMs >= startDeadlineMs) {
            // Not an answer yet. Carry on without a voice; a late answer still counts.
            engine = Engine.ABSENT
            onStatus(NO_ANSWER)
            sayPending(nowMs)
        }
        if (current != null && nowMs >= endsAtMs) {
            if (awaitingEngine && ++unanswered >= 2) distrust(NEVER_REPORTS)
            finish()
        }
    }

    /** How the voice has behaved so far, in words, for diagnostics. */
    fun describe(): String {
        val state = when (engine) {
            Engine.STARTING -> "시작 중"
            Engine.READY -> if (timed) "소리 확인 안 됨 · 시간 맞춰 진행" else "사용 중"
            Engine.ABSENT -> "없음 · 자막으로 진행"
        }
        return "$state · 말한 문장 $heard · 소리 없이 넘긴 문장 $unheard"
    }

    private fun sayPending(nowMs: Long) {
        val waiting = pending ?: return
        pending = null
        speak(waiting.first, waiting.second, nowMs)
    }

    private fun distrust(message: String) {
        if (timed) return
        timed = true
        onStatus(message)
    }

    private fun finish() {
        val id = current ?: return
        current = null
        onFinished(id)
    }

    companion object {
        const val NO_ENGINE = "음성 엔진을 찾지 못했어요. 자막으로 진행합니다."
        const val NO_ANSWER = "음성 엔진이 응답하지 않아요. 자막으로 진행합니다."
        const val REPORTS_AT_ONCE = "음성 엔진이 문장을 말하지 않고 바로 끝냈다고 알려 와요. 자막으로 진행합니다."
        const val NEVER_REPORTS = "음성 엔진이 문장을 마쳤다고 알려 주지 않아요. 시간을 맞춰 진행합니다."
    }
}
