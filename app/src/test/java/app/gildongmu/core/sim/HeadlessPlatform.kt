package app.gildongmu.core.sim

import app.gildongmu.core.HostPlatform
import app.gildongmu.core.HostSnapshot
import app.gildongmu.core.LightCue
import app.gildongmu.core.ListenMode
import app.gildongmu.core.Signal
import app.gildongmu.core.Speech
import app.gildongmu.core.Waypoint

/**
 * A [HostPlatform] with no car and no audio: it "speaks" by waiting as long as the sentence would
 * take to say, and writes down everything the host does. Tests read the log; the demo test prints
 * it as a transcript.
 *
 * [slowdown] stretches every sentence, to stand in for a speech engine that talks more slowly
 * than the app's estimate.
 */
class HeadlessPlatform(private val slowdown: Double = 1.0, private val clock: () -> Long) : HostPlatform {

    data class Line(val atMs: Long, val kind: String, val text: String)

    val log = ArrayList<Line>()
    val waypoints = ArrayList<Waypoint>()
    val lights = ArrayList<LightCue>()
    var expandedUi = false
        private set
    var snapshot: HostSnapshot? = null
        private set

    /** Where to report "finished speaking"; set once the runtime exists. */
    var dispatch: (Signal) -> Unit = {}

    private var speakingId: String? = null
    private var speakingUntilMs = 0L

    val spoken: List<String> get() = log.filter { it.kind == "HOST" }.map { it.text }

    override fun speak(utteranceId: String, text: String) {
        speakingId = utteranceId
        speakingUntilMs = clock() + (Speech.estimateSeconds(text) * slowdown * 1000).toLong()
        log.add(Line(clock(), "HOST", text))
    }

    override fun stopSpeaking() {
        if (speakingId != null) log.add(Line(clock(), "STOP", "(말을 멈춤)"))
        speakingId = null
    }

    override fun listen(mode: ListenMode, timeoutMs: Long, expected: List<String>) {
        log.add(Line(clock(), "LISTEN", mode.name))
    }

    override fun cancelListen() {}

    override fun addWaypoint(waypoint: Waypoint) {
        waypoints.add(waypoint)
        log.add(Line(clock(), "NAV", "경유지 추가: ${waypoint.name}"))
    }

    override fun light(cue: LightCue) {
        lights.add(cue)
        log.add(Line(clock(), "LIGHT", cue.name))
    }

    override fun setExpandedUi(enabled: Boolean) {
        expandedUi = enabled
        log.add(Line(clock(), "UI", if (enabled) "정차 화면 열림" else "정차 화면 닫힘"))
    }

    override fun render(snapshot: HostSnapshot) {
        this.snapshot = snapshot
    }

    /** Call regularly: reports speech as finished once its time is up. */
    fun pump() {
        val id = speakingId ?: return
        if (clock() < speakingUntilMs) return
        speakingId = null
        dispatch(Signal.SpeechFinished(id))
    }
}
