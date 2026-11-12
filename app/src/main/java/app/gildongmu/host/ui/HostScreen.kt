package app.gildongmu.host.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import app.gildongmu.core.HostSnapshot
import app.gildongmu.core.LightCue
import app.gildongmu.core.Phase

/**
 * The whole screen.
 *
 * While the car is moving it is one card: what the host is saying, how far through it is, and a
 * skip button. Everything else is voice. The lower panel with the scoreboard, the route diagram
 * and two shortcut buttons exists only while parked, and is removed the moment the car moves.
 */
class HostScreen(private val context: Context) {

    /** Skip was pressed on the card. */
    var onSkip: () -> Unit = {}

    /** A parked-screen shortcut was pressed; the text is handled exactly as if it had been spoken. */
    var onShortcut: (String) -> Unit = {}

    /** The destination at the top left was pressed and held. A hook for test builds, not part of the driving UI. */
    var onDestinationHeld: () -> Unit = {}

    /** Shows the sentence being spoken under the title. Off in the car, on for demo recordings. */
    var captions: Boolean = false
        set(value) {
            field = value
            last?.let(::render)
        }

    val root: LinearLayout = Ui.row(context)

    /** Free space on the right for build-specific content: the demo controls in the mock build. */
    val sidePanel: FrameLayout = FrameLayout(context)

    private val main = Handler(Looper.getMainLooper())
    private var last: HostSnapshot? = null

    private val destination = Ui.text(context, 22f, Palette.TEXT, bold = true)
    private val trip = Ui.text(context, 20f, Palette.TEXT_SECONDARY)
    private val battery = Ui.text(context, 20f, Palette.TEXT_SECONDARY)
    private val waypoint = Ui.text(context, 20f, Palette.WARM, bold = true)

    private val card: LinearLayout = Ui.column(context)
    private val cardShape: GradientDrawable = Ui.rounded(Palette.SURFACE, Ui.dp(context, 28), Palette.OUTLINE, Ui.dp(context, 2))
    private val kind = Ui.text(context, 22f, Palette.ACCENT, bold = true)
    private val title = Ui.text(context, 46f, Palette.TEXT, bold = true)
    private val caption = Ui.text(context, 28f, Palette.TEXT_SECONDARY)
    private val state = Ui.text(context, 22f, Palette.WARM, bold = true)
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
    private val skip: TextView = Ui.button(context, "건너뛰기") { onSkip() }
    private var bottom: View? = null

    private val parkedPanel: LinearLayout = Ui.column(context)
    private val scores = Ui.text(context, 26f, Palette.TEXT, bold = true)
    private val strip = RouteStripView(context)

    init {
        val pad = Ui.dp(context, 24)
        root.setBackgroundColor(Palette.BACKGROUND)
        root.setPadding(pad, pad, pad, pad)

        val left = Ui.column(context)
        left.addView(buildStatusRow(), Ui.params(Ui.MATCH, Ui.WRAP))
        left.addView(buildCard(), Ui.margins(Ui.params(Ui.MATCH, 0, 1f), top = pad))
        left.addView(buildParkedPanel(), Ui.margins(Ui.params(Ui.MATCH, Ui.WRAP), top = pad))
        root.addView(left, Ui.params(0, Ui.MATCH, 1f))

        root.addView(sidePanel, Ui.margins(Ui.params(Ui.dp(context, 380), Ui.MATCH), left = pad))
        Ui.show(sidePanel, false)
        Ui.show(parkedPanel, false)
        destination.setOnLongClickListener {
            if (sidePanel.childCount > 0) setSidePanelVisible(true)
            onDestinationHeld()
            true
        }
    }

    private fun buildStatusRow(): View {
        val row = Ui.row(context)
        val gap = Ui.dp(context, 28)
        row.addView(destination, Ui.params(Ui.WRAP, Ui.WRAP))
        row.addView(trip, Ui.margins(Ui.params(Ui.WRAP, Ui.WRAP), left = gap))
        row.addView(battery, Ui.margins(Ui.params(Ui.WRAP, Ui.WRAP), left = gap))
        row.addView(waypoint, Ui.margins(Ui.params(Ui.WRAP, Ui.WRAP), left = gap))
        return row
    }

    private fun buildCard(): View {
        val pad = Ui.dp(context, 40)
        card.background = cardShape
        card.setPadding(pad, pad, pad, pad)
        card.setGravity(Gravity.CENTER_VERTICAL)

        title.maxLines = 2
        title.ellipsize = TextUtils.TruncateAt.END
        caption.maxLines = 3
        caption.ellipsize = TextUtils.TruncateAt.END
        progress.max = 1000
        progress.progressTintList = ColorStateList.valueOf(Palette.ACCENT)
        progress.progressBackgroundTintList = ColorStateList.valueOf(Palette.OUTLINE)

        card.addView(kind, Ui.params(Ui.MATCH, Ui.WRAP))
        card.addView(title, Ui.params(Ui.MATCH, Ui.WRAP))
        card.addView(caption, Ui.params(Ui.MATCH, Ui.WRAP))

        val bottom = Ui.row(context)
        val meter = Ui.column(context)
        meter.addView(state, Ui.params(Ui.MATCH, Ui.WRAP))
        meter.addView(progress, Ui.margins(Ui.params(Ui.MATCH, Ui.dp(context, 10)), top = Ui.dp(context, 12)))
        bottom.addView(meter, Ui.params(0, Ui.WRAP, 1f))
        bottom.addView(skip, Ui.margins(Ui.params(Ui.WRAP, Ui.WRAP), left = Ui.dp(context, 32)))
        card.addView(bottom, Ui.params(Ui.MATCH, Ui.WRAP))
        this.bottom = bottom
        sizeCard(compact = false)
        return card
    }

    /**
     * The card at full size while driving, and at a smaller size while parked, when it shares the
     * screen with the parked panel.
     */
    private fun sizeCard(compact: Boolean) {
        val pad = Ui.dp(context, if (compact) 24 else 40)
        card.setPadding(pad, pad, pad, pad)
        title.textSize = if (compact) 34f else 46f
        title.maxLines = if (compact) 1 else 2
        caption.textSize = if (compact) 22f else 28f
        caption.maxLines = if (compact) 2 else 3
        title.layoutParams = Ui.margins(Ui.params(Ui.MATCH, Ui.WRAP), top = Ui.dp(context, if (compact) 8 else 16))
        caption.layoutParams = Ui.margins(Ui.params(Ui.MATCH, Ui.WRAP), top = Ui.dp(context, if (compact) 10 else 20))
        bottom?.layoutParams = Ui.margins(Ui.params(Ui.MATCH, Ui.WRAP), top = Ui.dp(context, if (compact) 14 else 32))
    }

    private fun buildParkedPanel(): View {
        val pad = Ui.dp(context, 16)
        parkedPanel.background = Ui.rounded(Palette.SURFACE, Ui.dp(context, 28), Palette.OUTLINE, Ui.dp(context, 2))
        parkedPanel.setPadding(pad, pad, pad, pad)

        val top = Ui.row(context)
        top.addView(scores, Ui.margins(Ui.params(0, Ui.WRAP, 1f), left = Ui.dp(context, 12)))
        top.addView(
            Ui.button(context, "이야기 듣기", sizeSp = 20f, minHeightDp = 60) { onShortcut("이야기해 줘") },
            Ui.margins(Ui.params(Ui.WRAP, Ui.WRAP), left = Ui.dp(context, 16))
        )
        top.addView(
            Ui.button(context, "퀴즈 하기", sizeSp = 20f, minHeightDp = 60) { onShortcut("퀴즈 하자") },
            Ui.margins(Ui.params(Ui.WRAP, Ui.WRAP), left = Ui.dp(context, 16))
        )
        parkedPanel.addView(top, Ui.params(Ui.MATCH, Ui.WRAP))
        parkedPanel.addView(strip, Ui.margins(Ui.params(Ui.MATCH, Ui.dp(context, 84)), top = Ui.dp(context, 8)))
        return parkedPanel
    }

    /** Shows or hides [sidePanel]. Pressing and holding the destination brings a hidden panel back. */
    fun setSidePanelVisible(visible: Boolean) {
        Ui.show(sidePanel, visible)
    }

    /** The parked-only panel. The host turns it on when the car is parked and off before it moves. */
    fun setExpanded(enabled: Boolean) {
        Ui.show(parkedPanel, enabled)
        sizeCard(compact = enabled)
    }

    /** Stands in for the cabin's ambient light on a build that has none: the card border flashes. */
    fun flash(cue: LightCue) {
        val color = if (cue == LightCue.CORRECT_ANSWER) Palette.GOOD else Palette.WARM
        cardShape.setStroke(Ui.dp(context, 6), color)
        main.removeCallbacksAndMessages(FLASH)
        main.postAtTime({ cardShape.setStroke(Ui.dp(context, 2), Palette.OUTLINE) }, FLASH, android.os.SystemClock.uptimeMillis() + 1400)
    }

    fun render(snapshot: HostSnapshot) {
        last = snapshot

        destination.text = when (snapshot.phase) {
            Phase.ARRIVED -> "${snapshot.destinationName} 도착"
            else -> "${snapshot.destinationName} · ${Math.round(snapshot.remainingKm)}km"
        }
        trip.text = "주행 ${duration(snapshot.tripMinutes)}"
        val percent = snapshot.batteryPercent
        battery.text = when {
            percent == null -> ""
            snapshot.charging -> "배터리 $percent% · 충전 중"
            else -> "배터리 $percent%"
        }
        waypoint.text = snapshot.waypointName?.let { "경유지 $it" } ?: ""

        val now = snapshot.card
        if (now != null) {
            kind.text = if (now.subtitle.isBlank()) now.kind.label else "${now.kind.label} · ${now.subtitle}"
            title.text = now.title
            caption.text = if (captions) now.caption else ""
            progress.progress = (now.progress * 1000).toInt()
        } else {
            kind.text = "길동무"
            title.text = idleTitle(snapshot)
            caption.text = ""
            progress.progress = 0
        }
        Ui.show(caption, captions && now != null)
        Ui.show(progress, now != null)
        Ui.show(skip, now != null)

        state.text = when {
            snapshot.listening != null -> "듣고 있어요"
            snapshot.holding -> "내비 안내 중 · 잠시 멈춤"
            snapshot.quiet -> "조용히 가는 중"
            else -> ""
        }

        scores.text = snapshot.scores.joinToString("   ") { "${it.player} ${it.score}점" }
        strip.update(snapshot.places, snapshot.progressKm + snapshot.remainingKm, snapshot.progressKm)
    }

    private fun idleTitle(snapshot: HostSnapshot): String = when (snapshot.phase) {
        Phase.WAITING -> "출발을 기다리고 있어요"
        Phase.DRIVING -> snapshot.upNext?.let { "다음 이야기  $it" } ?: "조용히 달리는 중"
        Phase.RESTING -> "쉬어 가는 중"
        Phase.ARRIVED -> "도착했어요"
    }

    private fun duration(minutes: Int): String {
        val hours = minutes / 60
        val rest = minutes % 60
        return if (hours == 0) "${rest}분" else "${hours}시간 ${rest}분"
    }

    private companion object {
        /** Token for the pending "end of flash" callback, so a new flash replaces the old one. */
        val FLASH = Any()
    }
}
