package app.gildongmu.core

/**
 * What the host needs from the car. The `pleos` build implements this with the Pleos Connect SDK,
 * the `mock` build with Android's own speech services and a scripted drive.
 *
 * Every method is called on the thread that calls [HostRuntime.dispatch]; none may block.
 */
interface HostPlatform {
    /** Speak [text]; when it finishes, dispatch [Signal.SpeechFinished] with [utteranceId]. */
    fun speak(utteranceId: String, text: String)

    fun stopSpeaking()

    /** Listen for one reply and dispatch it as [Signal.Utterance]. The host enforces the timeout itself. */
    fun listen(mode: ListenMode, timeoutMs: Long, expected: List<String>)

    fun cancelListen()

    fun addWaypoint(stop: Stop)

    fun light(cue: LightCue)

    fun setExpandedUi(enabled: Boolean)

    /** Called whenever what the screen should show has changed. */
    fun render(snapshot: HostSnapshot)
}

/**
 * Connects the [HostEngine] to a [HostPlatform]: signals in, actions out, screen refreshed when
 * the snapshot changes.
 *
 * A platform may answer an action straight away (a speech engine that fails immediately, a test
 * double), which calls [dispatch] again from inside [dispatch]. Signals are therefore queued and
 * handled strictly one at a time, in arrival order.
 */
class HostRuntime(
    private val engine: HostEngine,
    private val platform: HostPlatform,
    private val clock: () -> Long
) {
    private val queue = ArrayDeque<Signal>()
    private var draining = false
    private var lastSnapshot: HostSnapshot? = null

    fun dispatch(signal: Signal) {
        queue.addLast(signal)
        if (draining) return
        draining = true
        try {
            while (true) {
                val next = queue.removeFirstOrNull() ?: break
                for (action in engine.handle(next, clock())) perform(action)
            }
        } finally {
            draining = false
        }
        val snapshot = engine.snapshot()
        if (snapshot != lastSnapshot) {
            lastSnapshot = snapshot
            platform.render(snapshot)
        }
    }

    private fun perform(action: Action) {
        when (action) {
            is Action.Speak -> platform.speak(action.utteranceId, action.text)
            Action.StopSpeaking -> platform.stopSpeaking()
            is Action.Listen -> platform.listen(action.mode, action.timeoutMs, action.expected)
            Action.CancelListen -> platform.cancelListen()
            is Action.AddWaypoint -> platform.addWaypoint(action.stop)
            is Action.Light -> platform.light(action.cue)
            is Action.ExpandedUi -> platform.setExpandedUi(action.enabled)
        }
    }
}
