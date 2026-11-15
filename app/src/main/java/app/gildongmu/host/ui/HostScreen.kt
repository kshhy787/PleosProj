package app.gildongmu.host.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Space
import android.widget.TextView
import app.gildongmu.core.CardText
import app.gildongmu.core.HostSnapshot
import app.gildongmu.core.Illustration
import app.gildongmu.core.LightCue
import app.gildongmu.core.Phase
import app.gildongmu.core.ScreenFit
import app.gildongmu.core.Tone
import kotlin.math.roundToInt

/**
 * The whole screen.
 *
 * While the car is moving it is one card: a picture, what the host is saying, how far through it
 * is, what comes next, and a skip button. Everything else is voice. The card has the colour of
 * what is on it (its [Tone]), so a glance tells a story from a quiz from a place to stop. When the
 * host recommends places, the card shows them as numbered tiles instead of the picture and the
 * title. The scoreboard, the route diagram and the shortcut buttons exist only while parked, and
 * are removed the moment the car moves.
 *
 * The window it is given can be any size and can change: a whole wide display, half of a split
 * screen, a short strip. [ScreenFit] decides the arrangement for the size at hand and for what is
 * showing, and this class applies it whenever either changes. The pieces are laid out exactly as
 * the list in [ScreenFit]'s comment has them, because its rules add up their heights: a size
 * changed here has to change there too.
 */
class HostScreen(private val context: Context) {

    /** Skip was pressed on the card. */
    var onSkip: () -> Unit = {}

    /** A parked-screen shortcut was pressed; the text is handled exactly as if it had been spoken. */
    var onShortcut: (String) -> Unit = {}

    /**
     * The tile of a recommended spot was touched; the number counts from zero through
     * [HostSnapshot.offers]. Tiles take touches only while the car is parked and a spot can be
     * chosen, and the host checks both again.
     */
    var onOfferTapped: (Int) -> Unit = {}

    /** The destination at the top left was pressed and held. A hook for test builds, not part of the driving UI. */
    var onDestinationHeld: () -> Unit = {}

    /**
     * Shows the sentence being spoken under the title. Off in the car, on for demo recordings.
     * A question asked while parked shows either way: `CardText.caption` has the rule.
     */
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

    /** The status row, the card and the parked screen's pieces: everything that is not the side panel. */
    private val column: LinearLayout = Ui.column(context)

    /** The parked screen is showing. */
    private var parked = false

    /** Spots are on offer, so the card is the recommendation card. [ScreenFit] is asked with it. */
    private var offering = false

    /** How many spots are on offer: a single one's tile is laid out differently. */
    private var offered = 0

    /** The arrangement in force. Until the window has a size, the one for a wide display. */
    private var fit: ScreenFit = ScreenFit.of(1408f, 700f, sidePanel = false, parked = false, offers = false)

    private val destination = Ui.text(context, 22f, Palette.TEXT, bold = true)
    private val trip = Ui.text(context, 20f, Palette.TEXT_SECONDARY)
    private val battery = Ui.text(context, 20f, Palette.TEXT_SECONDARY)
    private val waypoint = Ui.text(context, 20f, Palette.WARM, bold = true)

    private val card: LinearLayout = Ui.row(context)
    private val cardShape: GradientDrawable = Ui.rounded(Palette.SURFACE, Ui.dp(context, 28), Palette.OUTLINE, Ui.dp(context, 2))

    /** The illustration panel on the left of the card. */
    private val art = ArtPanel(context)

    /**
     * Everything on the card that is not the illustration, from the chip down to the bottom row:
     * the title and the caption between them or, on the recommendation card, the tiles and the hint.
     */
    private val text = TextColumn(context, art)
    private val chip = Ui.text(context, 22f, Palette.NOTICE, bold = true)
    private val chipShape: GradientDrawable = Ui.rounded(Palette.faint(Tone.NOTICE), Ui.dp(context, 22))
    private val title = Ui.text(context, 46f, Palette.TEXT, bold = true)
    private val tileRow: LinearLayout = Ui.row(context)
    private val tiles = List(MAX_TILES) { index -> OfferTile(context, index + 1) { onOfferTapped(index) } }
    private val hint = Ui.text(context, 18f, Palette.TEXT_SECONDARY)
    private val caption = Ui.text(context, 28f, Palette.TEXT_SECONDARY)

    private val bottom: LinearLayout = Ui.row(context)
    private val state = Ui.text(context, 22f, Palette.WARM, bold = true)
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
    private val next = Ui.text(context, 20f, Palette.TEXT_SECONDARY)
    private val skip: TextView = Ui.button(context, "건너뛰기") { onSkip() }

    /** Says how to get a hidden side panel back. Floats over the card's bottom edge while the panel is hidden. */
    private val panelHint = Ui.text(context, 16f, Palette.TEXT_SECONDARY)

    private val scoreboard = Scoreboard(context)
    private val strip = RouteStripView(context)
    private val shortcuts: LinearLayout = Ui.row(context)

    /** The colour family the card has now; null until the first card is drawn. */
    private var tone: Tone? = null

    /** What [art] shows now, so that it is redrawn only when the picture or its colour changes. */
    private var pictured: Pair<Illustration, Tone>? = null

    /** The card is showing a light cue, and its border is not to be given the card's colour until it is over. */
    private var flashing = false

    init {
        val pad = Ui.dp(context, 24)
        root.setBackgroundColor(Palette.BACKGROUND)
        root.setPadding(pad, pad, pad, pad)

        // The card and, when ScreenFit puts it there, the scoreboard beside it.
        val cardRow = Ui.row(context)
        cardRow.isBaselineAligned = false
        cardRow.addView(buildCard(), Ui.params(0, Ui.MATCH, 1f))
        cardRow.addView(
            scoreboard.panel,
            Ui.margins(Ui.params(Ui.dp(context, ScreenFit.SCOREBOARD_DP), Ui.MATCH), left = Ui.dp(context, ScreenFit.PARKED_GAP_DP))
        )

        val gap = Ui.dp(context, ScreenFit.PARKED_GAP_DP)
        column.addView(buildStatusRow(), Ui.params(Ui.MATCH, Ui.WRAP))
        column.addView(cardRow, Ui.margins(Ui.params(Ui.MATCH, 0, 1f), top = pad))
        column.addView(strip, Ui.margins(Ui.params(Ui.MATCH, Ui.dp(context, ScreenFit.ROUTE_STRIP_DP)), top = gap))
        column.addView(scoreboard.line, Ui.margins(Ui.params(Ui.MATCH, Ui.dp(context, ScreenFit.SCORES_LINE_DP)), top = gap))
        column.addView(buildShortcuts(), Ui.margins(Ui.params(Ui.MATCH, Ui.dp(context, ScreenFit.SHORTCUT_ROW_DP)), top = gap))
        root.addView(column, Ui.params(0, Ui.MATCH, 1f))

        root.addView(sidePanel, Ui.margins(Ui.params(Ui.dp(context, ScreenFit.SIDE_PANEL_DP), Ui.MATCH), left = pad))
        Ui.show(sidePanel, false)
        Ui.show(panelHint, false)
        destination.setOnLongClickListener {
            if (sidePanel.childCount > 0) setSidePanelVisible(true)
            onDestinationHeld()
            true
        }
        tint(Tone.NOTICE, Illustration.ROAD)
        apply(fit)

        // Rearranged after the layout pass that reported the new size, not in the middle of it.
        root.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) root.post { arrange() }
        }
    }

    /**
     * Fits the screen to the window it has now and to what is showing. Called whenever anything
     * [ScreenFit] is asked about changes: the size, the side panel, the parked screen, the tiles.
     */
    private fun arrange() {
        if (root.width == 0 || root.height == 0) {
            // No size yet: the wide display's arrangement, but for what is showing now.
            fit = ScreenFit.of(1408f, 700f, sidePanel = false, parked = parked, offers = offering)
            apply(fit)
            return
        }
        val density = context.resources.displayMetrics.density
        val next = ScreenFit.of(root.width / density, root.height / density, sidePanel.visibility == View.VISIBLE, parked, offering)
        val wasStacked = fit.stacked
        fit = next

        if (wasStacked != next.stacked) {
            val pad = Ui.dp(context, 24)
            if (next.stacked) {
                // Too narrow for the card and the side panel to sit side by side: card on top.
                root.orientation = LinearLayout.VERTICAL
                column.layoutParams = Ui.params(Ui.MATCH, 0, ScreenFit.STACKED_SHARE)
                sidePanel.layoutParams = Ui.margins(Ui.params(Ui.MATCH, 0, 1f - ScreenFit.STACKED_SHARE), top = pad)
            } else {
                root.orientation = LinearLayout.HORIZONTAL
                column.layoutParams = Ui.params(0, Ui.MATCH, 1f)
                sidePanel.layoutParams = Ui.margins(Ui.params(Ui.dp(context, ScreenFit.SIDE_PANEL_DP), Ui.MATCH), left = pad)
            }
        }
        apply(next)
        root.requestLayout()
    }

    /** The window and the arrangement chosen for it, in words, for the demo panel's diagnostics. */
    fun describe(): String {
        val density = context.resources.displayMetrics.density
        // The app's own text is held at a scale of 1 (see MainActivity); this is what the system is set to.
        val systemFontScale = context.applicationContext.resources.configuration.fontScale
        val size = "${(root.width / density).roundToInt()}×${(root.height / density).roundToInt()}dp · 밀도 $density · 시스템 글자 배율 $systemFontScale"
        if (root.width == 0 || root.height == 0) return size
        val current = fit
        val least = ScreenFit.leastHeightDp(root.width / density, sidePanel.visibility == View.VISIBLE, parked, offering)
        val low = if (root.height / density < least) " · 창이 낮음(최소 ${least.roundToInt()}dp)" else ""
        val tiles = when {
            !offering -> ""
            current.tiles == ScreenFit.TileStyle.FULL -> " · 타일 전체(이유 ${current.tileReasonLines}줄, 그림 ${current.tileIconDp.roundToInt()}dp)"
            else -> " · 타일 이름만"
        }
        val scores = when {
            !parked -> ""
            current.scoresBeside -> " · 점수판 옆"
            else -> " · 점수 한 줄"
        }
        return size + " · " + (if (current.stacked) "위아래" else "나란히") + " · " +
            (if (current.compact) "작은 카드" else "큰 카드") + (if (current.narrow) " · 좁음" else "") + " · 자막 ${current.captionLines}줄" +
            (if (current.showArt) " · 그림" else "") + tiles + (if (offering && !current.showHint) " · 안내 줄 없음" else "") +
            scores + (if (current.showRoute) " · 경로" else "") + low
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
        card.background = cardShape
        // A row lines its children up on their text's baseline unless told not to, and makes
        // itself higher to do it. These rows hold boxes, and ScreenFit has counted their heights.
        for (row in listOf(card, tileRow, bottom)) row.isBaselineAligned = false

        chip.background = chipShape
        chip.setPadding(Ui.dp(context, 16), Ui.dp(context, 6), Ui.dp(context, 16), Ui.dp(context, 6))
        title.ellipsize = TextUtils.TruncateAt.END
        caption.ellipsize = TextUtils.TruncateAt.END
        // One line each, cut if need be: in a narrow window they would otherwise wrap and push
        // the caption out of the card.
        for (label in listOf(chip, hint, state, next)) {
            label.maxLines = 1
            label.ellipsize = TextUtils.TruncateAt.END
        }
        hint.text = CardText.CHOICE_HINT
        progress.max = 1000
        progress.progressBackgroundTintList = ColorStateList.valueOf(Palette.OUTLINE)

        for ((index, tile) in tiles.withIndex()) {
            tileRow.addView(tile.view, Ui.margins(Ui.params(0, Ui.MATCH, 1f), left = if (index == 0) 0 else Ui.dp(context, ScreenFit.TILE_GAP_DP)))
        }
        // The skip button stands on the same line as the foot of what is beside it.
        bottom.setGravity(Gravity.BOTTOM)
        val meter = Ui.column(context)
        meter.addView(state, Ui.params(Ui.MATCH, Ui.WRAP))
        meter.addView(progress, Ui.margins(Ui.params(Ui.MATCH, Ui.dp(context, 10)), top = Ui.dp(context, 12)))
        meter.addView(next, Ui.margins(Ui.params(Ui.MATCH, Ui.WRAP), top = Ui.dp(context, 8)))
        bottom.addView(meter, Ui.params(0, Ui.WRAP, 1f))
        bottom.addView(skip, Ui.margins(Ui.params(Ui.WRAP, Ui.WRAP), left = Ui.dp(context, 32)))

        // What the card says is kept together in the middle of the card's height, with the
        // picture beside it: in a card much higher than its text, a chip at the top edge and a
        // skip button at the bottom one would be a long way from the title they belong to.
        // Beside a picture higher than the text, the column is as high as the picture (see
        // TextColumn) and the Space above the bottom row takes what is left over, so that the
        // chip is level with the picture's top and the bottom row stands on its foot. The title
        // or the tiles show, never both; `apply` sets the margins that depend on the card's size.
        text.addView(chip, Ui.params(Ui.WRAP, Ui.WRAP))
        text.addView(title)
        text.addView(tileRow)
        text.addView(hint, Ui.margins(Ui.params(Ui.MATCH, Ui.WRAP), top = Ui.dp(context, 12)))
        text.addView(caption)
        // A Space and not a plain View, which makes itself as high as it is allowed to be.
        text.addView(Space(context), Ui.params(Ui.MATCH, 0, 1f))
        text.addView(bottom)
        card.addView(art)
        card.addView(text)

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

    /** The parked screen's buttons: one row, a third of the width each, as [ScreenFit.PARKED_SHORTCUTS] says. */
    private fun buildShortcuts(): View {
        val side = Ui.dp(context, 8)
        shortcuts.isBaselineAligned = false
        for ((index, shortcut) in ScreenFit.PARKED_SHORTCUTS.withIndex()) {
            val button = Ui.button(context, shortcut.label, sizeSp = 20f, minHeightDp = 60) { onShortcut(shortcut.request) }
            // Cut rather than wrapped in a window narrower than ScreenFit plans for: a second line
            // would make the row taller than the room it has been given.
            button.maxLines = 1
            button.ellipsize = TextUtils.TruncateAt.END
            // A third of the row with the label centred in it: wide side padding would only take the label's room.
            button.setPadding(side, button.paddingTop, side, button.paddingBottom)
            shortcuts.addView(button, Ui.margins(Ui.params(0, Ui.MATCH, 1f), left = if (index == 0) 0 else Ui.dp(context, ScreenFit.SHORTCUT_GAP_DP)))
        }
        return shortcuts
    }

    /**
     * Lays the pieces out as [fit] says. The card is at full size when it has the room, and at a
     * smaller size when it does not: in a narrow or short window, and where the parked screen's
     * pieces or the tiles leave it too little.
     */
    private fun apply(fit: ScreenFit) {
        val compact = fit.compact
        val pad = Ui.dp(context, if (compact) 24 else 40)
        card.setPadding(pad, pad, pad, pad)

        // The picture takes its share of the card, or goes and leaves the text the whole of it.
        // The panel chooses its own height (see ArtPanel), so it is not told to fill the card's.
        Ui.show(art, fit.showArt)
        art.layoutParams = Ui.params(0, Ui.WRAP, fit.artShare)
        text.layoutParams = Ui.margins(Ui.params(0, Ui.WRAP, 1f - fit.artShare), left = if (fit.showArt) Ui.dp(context, ScreenFit.ART_GAP_DP) else 0)

        title.textSize = if (compact) 34f else 46f
        // One line is enough for a title on a wide compact card; a narrow one needs the second.
        title.maxLines = if (compact && !fit.narrow) 1 else 2
        title.layoutParams = Ui.margins(Ui.params(Ui.MATCH, Ui.WRAP), top = Ui.dp(context, if (compact) 8 else 16))
        caption.textSize = if (compact) 22f else 28f
        // The room of all its lines however few it has: a caption changes every few seconds,
        // and the title above it and the bottom row under it must not move each time it does.
        // With no line it is not shown at all (see showWhatFits).
        caption.setLines(fit.captionLines.coerceAtLeast(1))
        caption.layoutParams = Ui.margins(Ui.params(Ui.MATCH, Ui.WRAP), top = Ui.dp(context, if (compact) 10 else 20))
        bottom.layoutParams = Ui.margins(Ui.params(Ui.MATCH, Ui.WRAP), top = Ui.dp(context, if (compact) 14 else 32))
        tileRow.layoutParams = Ui.margins(Ui.params(Ui.MATCH, Ui.WRAP), top = Ui.dp(context, if (compact) 12 else 16))
        hint.textSize = if (compact) 18f else 22f
        for (tile in tiles) tile.arrange(fit, fit.tileOnItsSide(offered))

        // The status row has four items and a narrow window has room for about two. The stop on
        // the route is the one to drop: the card says it when it matters.
        Ui.show(waypoint, !fit.narrow)
        destination.textSize = if (fit.narrow) 18f else 22f
        trip.textSize = if (fit.narrow) 16f else 20f
        battery.textSize = if (fit.narrow) 16f else 20f

        // The parked screen: the scores beside the card or on a line of their own, the route
        // when there is room for it, and the buttons.
        Ui.show(scoreboard.panel, parked && fit.scoresBeside)
        Ui.show(scoreboard.line, parked && !fit.scoresBeside)
        Ui.show(strip, fit.showRoute)
        Ui.show(shortcuts, parked)
        showWhatFits()
    }

    /**
     * Shows the two pieces that give way in a low window, the caption and the hint under the
     * tiles, where the card has something to put in them and [fit] has the room. Called when
     * either changes: what the host reports, and the arrangement.
     */
    private fun showWhatFits() {
        val snapshot = last
        Ui.show(caption, fit.captionLines > 0 && snapshot != null && CardText.caption(snapshot, captions) != null)
        // How to choose is said only where choosing does something.
        Ui.show(hint, fit.showHint && offering && snapshot?.choosable == true)
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

    /** The parked-only screen. The host turns it on when the car is parked and off before it moves. */
    fun setExpanded(enabled: Boolean) {
        parked = enabled
        arrange()
    }

    /**
     * Stands in for the cabin's ambient light on a build that has none: the whole card takes a
     * wash of the cue's colour inside a thick border of it, and then goes back to the surface
     * and to the colour of whatever the card shows by then. The border alone would not do: a
     * right answer is green, and so is the border of the quiz card it is given on.
     */
    fun flash(cue: LightCue) {
        flashing = true
        cardShape.setStroke(Ui.dp(context, 6), Palette.cue(cue))
        cardShape.setColor(Palette.flash(cue))
        main.removeCallbacksAndMessages(FLASH)
        main.postAtTime({
            flashing = false
            cardShape.setStroke(Ui.dp(context, 2), Palette.tone(tone ?: Tone.NOTICE))
            cardShape.setColor(Palette.SURFACE)
        }, FLASH, android.os.SystemClock.uptimeMillis() + FLASH_MS)
    }

    /**
     * Gives the card the colour of [tone] (its border, the chip, the progress bar) and puts
     * [illustration] in that colour on the illustration panel.
     */
    private fun tint(tone: Tone, illustration: Illustration) {
        val color = Palette.tone(tone)
        if (tone != this.tone) {
            this.tone = tone
            if (!flashing) cardShape.setStroke(Ui.dp(context, 2), color)
            chip.setTextColor(color)
            chipShape.setColor(Palette.faint(tone))
            progress.progressTintList = ColorStateList.valueOf(color)
        }
        val wanted = illustration to tone
        if (wanted != pictured) {
            pictured = wanted
            val layers = LayerDrawable(arrayOf(Ui.rounded(Palette.faint(tone), Ui.dp(context, 20)), IllustrationDrawable(illustration, color)))
            val inset = Ui.dp(context, 20)
            layers.setLayerInset(1, inset, inset, inset, inset)
            art.background = layers
        }
    }

    fun render(snapshot: HostSnapshot) {
        last = snapshot

        Ui.write(
            destination,
            when (snapshot.phase) {
                Phase.ARRIVED -> "${snapshot.destinationName} 도착"
                else -> "${snapshot.destinationName} · ${Math.round(snapshot.remainingKm)}km"
            }
        )
        Ui.write(trip, "주행 ${duration(snapshot.tripMinutes)}")
        val percent = snapshot.batteryPercent
        Ui.write(
            battery,
            when {
                percent == null -> ""
                snapshot.charging -> "배터리 $percent% · 충전 중"
                else -> "배터리 $percent%"
            }
        )
        Ui.write(waypoint, snapshot.waypointName?.let { "경유지 $it" } ?: "")

        // The tiles need another arrangement than the card they replace, so ScreenFit is asked again.
        val spots = snapshot.offers.take(tiles.size)
        if (spots.size != offered) {
            offered = spots.size
            offering = spots.isNotEmpty()
            arrange()
        }

        // The idle card, with nothing being said, is the app's own: neutral, with the road on it.
        val now = snapshot.card
        val shownTone = now?.tone ?: Tone.NOTICE
        tint(shownTone, now?.illustration ?: Illustration.ROAD)
        Ui.write(chip, CardText.chip(snapshot))
        Ui.write(title, now?.title ?: idleTitle(snapshot))
        Ui.write(caption, CardText.caption(snapshot, captions).orEmpty())
        progress.progress = ((now?.progress ?: 0f) * 1000).toInt()

        Ui.show(title, !offering)
        Ui.show(tileRow, offering)
        showWhatFits()
        // Blanked, not removed, so that the bottom row keeps its height and the title above it its place.
        Ui.keep(progress, now != null)
        Ui.keep(skip, now != null)
        Ui.write(next, snapshot.nextLine.orEmpty())
        // The recommendation card has no "다음" line: the tiles are what is next.
        if (offering) Ui.show(next, false) else Ui.keep(next, snapshot.nextLine != null)

        for ((index, tile) in tiles.withIndex()) {
            val spot = spots.getOrNull(index)
            Ui.show(tile.view, spot != null)
            // A tile is a button only while parked: while the car moves the skip button is the one touch control.
            if (spot != null) tile.show(spot, shownTone, tappable = snapshot.tilesTappable)
        }

        Ui.write(state, CardText.state(snapshot))

        scoreboard.update(snapshot.scores)
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

        /** How long a light cue stays on the card. */
        const val FLASH_MS = 1400L

        const val PANEL_HINT = "화면을 눌러 패널 띄우기"

        /** The host recommends at most three spots at a time, and three tiles are what a card holds side by side. */
        const val MAX_TILES = 3
    }
}

/**
 * The card's text column. On its own it is as high as its pieces. Beside the picture it is at
 * least as high as the picture, and the piece with a layout weight (the gap above the bottom row)
 * takes the difference: the chip is then level with the picture's top and the skip button stands
 * on its foot, where a column of its own height would float beside the middle of the picture.
 *
 * It reads the height [beside] was just measured to, so [beside] has to come before it in the row.
 */
private class TextColumn(context: Context, private val beside: View) : LinearLayout(context) {
    init {
        orientation = VERTICAL
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val floor = if (beside.visibility == VISIBLE) beside.measuredHeight else 0
        if (measuredHeight < floor) super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(floor, MeasureSpec.EXACTLY))
    }
}

/**
 * The panel the card's picture is drawn on. It is as high as the card lets it be, but never much
 * higher than it is wide (`ScreenFit.artHeight`): in a card far higher than its text needs, a
 * panel of the card's full height would be a stripe with the picture lost in the middle of it.
 */
private class ArtPanel(context: Context) : View(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val room = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, ScreenFit.artHeight(width.toFloat(), room.toFloat()).toInt())
    }
}
