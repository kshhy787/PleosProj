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
import app.gildongmu.core.ScreenFit
import kotlin.math.roundToInt

/**
 * The whole screen.
 *
 * While the car is moving it is one card: what the host is saying, how far through it is, and a
 * skip button. Everything else is voice. The lower panel with the scoreboard, the route diagram
 * and two shortcut buttons exists only while parked, and is removed the moment the car moves.
 *
 * The window it is given can be any size and can change: a whole wide display, half of a split
 * screen, a short strip. [ScreenFit] decides the arrangement for the size at hand, and this class
 * applies it whenever the size changes.
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

    /** The status row, the card and the parked panel: everything that is not the side panel. */
    private val column: LinearLayout = Ui.column(context)

    /** The parked panel is showing. */
    private var parked = false

    /** The arrangement in force; null until the window has a size. */
    private var fit: ScreenFit? = null

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

    /** Says how to get a hidden side panel back. Floats over the card's bottom edge while the panel is hidden. */
    private val panelHint = Ui.text(context, 16f, Palette.TEXT_SECONDARY)

    private val parkedPanel: LinearLayout = Ui.column(context)
    private val scores = Ui.text(context, 26f, Palette.TEXT, bold = true)
    private val strip = RouteStripView(context)

    init {
        val pad = Ui.dp(context, 24)
        root.setBackgroundColor(Palette.BACKGROUND)
        root.setPadding(pad, pad, pad, pad)

        column.addView(buildStatusRow(), Ui.params(Ui.MATCH, Ui.WRAP))
        column.addView(buildCard(), Ui.margins(Ui.params(Ui.MATCH, 0, 1f), top = pad))
        column.addView(buildParkedPanel(), Ui.margins(Ui.params(Ui.MATCH, Ui.WRAP), top = pad))
        root.addView(column, Ui.params(0, Ui.MATCH, 1f))

        root.addView(sidePanel, Ui.margins(Ui.params(Ui.dp(context, ScreenFit.SIDE_PANEL_DP.toInt()), Ui.MATCH), left = pad))
        Ui.show(sidePanel, false)
        Ui.show(parkedPanel, false)
        Ui.show(panelHint, false)
        destination.setOnLongClickListener {
            if (sidePanel.childCount > 0) setSidePanelVisible(true)
            onDestinationHeld()
            true
        }

        // Rearranged after the layout pass that reported the new size, not in the middle of it.
        root.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) root.post { arrange() }
        }
    }

    /** Fits the screen to the window it has now. */
    private fun arrange() {
        if (root.width == 0 || root.height == 0) return
        val density = context.resources.displayMetrics.density
        val next = ScreenFit.of(root.width / density, root.height / density, sidePanel.visibility == View.VISIBLE, parked)
        val previous = fit
        if (next == previous) return
        fit = next

        if (previous?.stacked != next.stacked) {
            val pad = Ui.dp(context, 24)
            if (next.stacked) {
                // Too narrow for the card and the side panel to sit side by side: card on top.
                root.orientation = LinearLayout.VERTICAL
                column.layoutParams = Ui.params(Ui.MATCH, 0, ScreenFit.STACKED_SHARE)
                sidePanel.layoutParams = Ui.margins(Ui.params(Ui.MATCH, 0, 1f - ScreenFit.STACKED_SHARE), top = pad)
            } else {
                root.orientation = LinearLayout.HORIZONTAL
                column.layoutParams = Ui.params(0, Ui.MATCH, 1f)
                sidePanel.layoutParams = Ui.margins(Ui.params(Ui.dp(context, ScreenFit.SIDE_PANEL_DP.toInt()), Ui.MATCH), left = pad)
            }
        }
        sizeCard(next)
        Ui.show(strip, next.showRoute)
        root.requestLayout()
    }

    /** The window and the arrangement chosen for it, in words, for the demo panel's diagnostics. */
    fun describe(): String {
        val density = context.resources.displayMetrics.density
        val size = "${(root.width / density).roundToInt()}×${(root.height / density).roundToInt()}dp · 밀도 $density"
        val current = fit ?: return size
        return size + " · " + (if (current.stacked) "위아래" else "나란히") + " · " +
            (if (current.compact) "작은 카드" else "큰 카드") + (if (current.narrow) " · 좁음" else "") + " · 자막 ${current.captionLines}줄"
    }

    private fun buildStatusRow(): View {
        val row = Ui.row(context)
        val gap = Ui.dp(context, 28)
        // In a narrow window the row is too long: the destination is the part that gives way.
        for (view in listOf(destination, trip, battery, waypoint)) {
            view.maxLines = 1
            view.ellipsize = TextUtils.TruncateAt.END
        }
        row.addView(destination, Ui.params(0, Ui.WRAP, 1f))
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
        // One line each, cut if need be: in a narrow window they would otherwise wrap and push
        // the caption out of the card.
        for (label in listOf(kind, state)) {
            label.maxLines = 1
            label.ellipsize = TextUtils.TruncateAt.END
        }
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
        // Until the window has a size, assume the wide display the card was drawn for.
        sizeCard(ScreenFit(stacked = false, compact = false, narrow = false, captionLines = 3, showRoute = false))

        // The card sits in a frame so that the hint can float over its bottom margin without taking
        // any room from the card, which ScreenFit has already measured out.
        panelHint.text = PANEL_HINT
        panelHint.maxLines = 1
        panelHint.background = Ui.rounded(Palette.SURFACE_RAISED, Ui.dp(context, 16), Palette.OUTLINE, Ui.dp(context, 1))
        panelHint.setPadding(Ui.dp(context, 16), Ui.dp(context, 6), Ui.dp(context, 16), Ui.dp(context, 6))
        val frame = FrameLayout(context)
        frame.addView(card, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        val hintParams = FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        hintParams.bottomMargin = Ui.dp(context, 10)
        frame.addView(panelHint, hintParams)
        return frame
    }

    /**
     * The card at full size when it has the room, and at a smaller size when it does not: while
     * parked, when it shares the screen with the parked panel, and in a narrow or short window.
     */
    private fun sizeCard(fit: ScreenFit) {
        val compact = fit.compact
        val pad = Ui.dp(context, if (compact) 24 else 40)
        card.setPadding(pad, pad, pad, pad)
        title.textSize = if (compact) 34f else 46f
        // One line is enough for a title on a wide compact card; a narrow one needs the second.
        title.maxLines = if (compact && !fit.narrow) 1 else 2
        caption.textSize = if (compact) 22f else 28f
        caption.maxLines = fit.captionLines

        // The status row has four items and a narrow window has room for about two. The stop on
        // the route is the one to drop: the card says it when it matters.
        Ui.show(waypoint, !fit.narrow)
        destination.textSize = if (fit.narrow) 18f else 22f
        trip.textSize = if (fit.narrow) 16f else 20f
        battery.textSize = if (fit.narrow) 16f else 20f
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

    /**
     * Shows or hides [sidePanel]. While it is hidden, a tap anywhere on the card or the status row
     * brings it back, as a tap brings back a video player's controls; buttons on the card keep their
     * own taps, and pressing and holding the destination still works too.
     */
    fun setSidePanelVisible(visible: Boolean) {
        Ui.show(sidePanel, visible)
        // Only a build that puts something in the panel (mock) ever hides it, so the screen of the
        // others never becomes tappable.
        val bringBack = if (visible || sidePanel.childCount == 0) null else View.OnClickListener { setSidePanelVisible(true) }
        for (view in listOf(column, destination)) {
            // The destination needs its own listener: its long press makes it keep taps for itself.
            view.setOnClickListener(bringBack)
            view.isClickable = bringBack != null
        }
        // While the screen can bring the panel back, say so: nothing else on screen does.
        Ui.show(panelHint, bringBack != null)
        arrange()
    }

    /** The parked-only panel. The host turns it on when the car is parked and off before it moves. */
    fun setExpanded(enabled: Boolean) {
        parked = enabled
        Ui.show(parkedPanel, enabled)
        arrange()
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

        const val PANEL_HINT = "화면을 눌러 패널 띄우기"
    }
}
