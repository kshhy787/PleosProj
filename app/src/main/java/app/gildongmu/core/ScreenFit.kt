package app.gildongmu.core

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/**
 * How the screen arranges itself in the space the car gives it.
 *
 * The app does not always get a wide display to itself. An infotainment system can show it in
 * half of a split screen, or in a short strip, and a layout drawn for one size leaves the card,
 * which is the one thing that must be readable, squeezed to nothing. This decides the arrangement
 * from the size of the window and from what is showing; the screen only applies it. Sizes are in
 * dp.
 *
 * The rules add up the heights of the pieces, so they hold only while the screen lays the pieces
 * out as they were added up here. Around the column: 24 of padding, the status row (as high as
 * its tallest piece, the destination: 22sp bold on one line), 24, then the card. Inside the card
 * the pieces are kept together, in the middle of the card's height when it is higher than they
 * need; from the top (full-size card / compact card):
 *
 * **Driving card** (also the card of the parked screen). Padding 40 / 24. When [showArt], the
 * illustration panel on the left ([artHeight] high) and [ART_GAP_DP] between it and the text
 * column. In the text column:
 * - the chip: 22sp bold with 6 above and below its text;
 * - the title, 16 / 8 under the chip: 46sp / 34sp bold, two lines / one line (two when [narrow]);
 * - the caption when captions are on, 20 / 10 under the title: 28sp / 22sp, with the room of
 *   [captionLines] lines however few it has, so that nothing moves when a longer sentence comes.
 *   With no line, the caption and the gap above it are not there;
 * - the bottom row, 32 / 14 under that: on the left the state line (22sp), 12, the progress bar
 *   (10 high), 8, the "다음 · …" line (20sp, one line); on the right the skip button (76 high).
 *
 * Beside a panel higher than these pieces, the text column is as high as the panel: the chip is
 * level with the panel's top and the bottom row stands on its foot, as in the proposal's drawing,
 * and what is left over lies between the caption and the bottom row.
 *
 * **Recommendation card** (never with the illustration panel). Padding 40 / 24.
 * - the chip, as above;
 * - the row of tiles, 16 / 12 under the chip: equal widths, [TILE_GAP_DP] between them, each with
 *   [TILE_PADDING_DP] of padding. A [TileStyle.FULL] tile is the icon area ([tileIconDp] high, the
 *   number badge of [TILE_BADGE_DP] on its top left corner), 8, the name (28sp / 22sp bold, at
 *   most two lines), 6, the reason (22sp / 18sp, [tileReasonLines] lines). A [TileStyle.NAMES]
 *   tile, which only the compact card has, is the badge and, [NAMES_BADGE_GAP_DP] under it, the
 *   name (22sp bold, at most two lines) over the tile's whole width, the two in the middle of
 *   the tile's height. A single spot's tile lies on its side ([tileOnItsSide]);
 * - the hint when [showHint], 12 under the tiles: 22sp / 18sp, one line, cut if need be;
 * - the caption when captions are on, 20 / 10 under that: 28sp / 22sp, the room of
 *   [captionLines] lines, and not there with none;
 * - the bottom row, 32 / 14 under that: the state line (22sp), 12 and the progress bar (10 high)
 *   on the left, the skip button (76 high) on the right. It has no "다음 · …" line: the tiles are
 *   what is next.
 *
 * **Parked screen.** The card and, when [scoresBeside], the scoreboard to its right, share the row
 * under the status row. Under them, each with [PARKED_GAP_DP] above it: the route strip
 * ([ROUTE_STRIP_DP] high) when [showRoute]; the line of scores ([SCORES_LINE_DP] high: 26sp bold,
 * one line) when the scoreboard is not beside the card; the row of shortcut buttons
 * ([SHORTCUT_ROW_DP] high, a third of the width each with [SHORTCUT_GAP_DP] between them, the
 * labels 20sp bold on one line with 8 of padding each side).
 *
 * Text is taken to be as tall as Android's own Korean font (Noto Sans CJK) makes it, which is
 * taller than the Pleos emulator's, and captions are taken to be on: a rule that is wrong in the
 * safe direction costs a line of text, the other way it pushes the skip button out of the card.
 *
 * **The least window.** The card's pieces are laid out from the top, so in a card too low for
 * them the bottom row, last in line, is the one that is squeezed, and the skip button with it. So
 * what can be done without gives way first: the caption line by line down to none
 * ([captionLines]), and then the hint under the tiles ([showHint]). The chip, the title or the
 * tiles, and the bottom row are what is left, and a card with room for those keeps its skip
 * button whole. [leastHeightDp] says how high a window that takes. It depends on what is showing:
 * from 359 for the plain card while the car moves (409 when [narrow], 426 with tiles) to 556 for
 * a narrow card's tiles above the parked screen's buttons and line of scores. A window at least
 * [SAFE_HEIGHT_DP] (560) high has it whatever is showing, or one at least
 * [SAFE_STACKED_HEIGHT_DP] (920) high where the card is stacked above the side panel and has six
 * tenths of the height. In a lower window than [leastHeightDp] the bottom row is squeezed after
 * all: there is nothing left to take away.
 */
data class ScreenFit(
    /**
     * Card above the side panel instead of beside it: the root is vertical, the card's column gets
     * [STACKED_SHARE] of the height and the side panel the rest.
     */
    val stacked: Boolean,
    /**
     * The smaller card: the second of each pair of sizes in the list above. While parked the card
     * shares its row with the scoreboard and gives up height to the route strip and the buttons,
     * so it is the smaller one unless what is left is still enough for the full-size card: in a
     * window as high as the Pleos emulator's it is, and the type then does not shrink when the
     * car stops. While spots are on offer the full-size card needs more than that: tiles wide
     * enough for a reason in the larger type and high enough for a picture to match
     * ([ROOMY_TILE_ICON_DP]). In a wide and low window, or beside both the side panel and the
     * scoreboard, the tiles have the compact card.
     */
    val compact: Boolean,
    /**
     * The card is narrow: a sentence needs more lines, so the compact card's title may take two,
     * and the status row has to shed something (the waypoint goes, and its type gets smaller).
     */
    val narrow: Boolean,
    /**
     * Lines the caption may take before it is cut, and the room it keeps: three on the full-size
     * card, two on the compact one and four when that is [narrow], or as many of those as the
     * card has the height for. Zero in a card with no height to spare: the caption is then not
     * shown at all.
     */
    val captionLines: Int,
    /**
     * The illustration panel shows on the left of the driving card: [artShare] of its width, as
     * high as [artHeight] makes it, with [ART_GAP_DP] between it and the text column. When this
     * is false, remove the panel and its gap and give the text column the whole card. It is false
     * on a card where the picture would cost the text a line (the text column beside it must stay
     * as wide as the narrowest card that gets the same type and the same number of lines), on a
     * card too low to hold a picture worth looking at, and always on the recommendation card.
     */
    val showArt: Boolean,
    /**
     * How much the recommendation card's tiles show: everything where a tile is wide enough for
     * its reason's lines to hold a reason ([REASON_SYLLABLES]) and the card is high enough for
     * those lines, else the names only. [TileStyle.NAMES] also for a fit that was asked for
     * without `offers`, which has no tiles to lay out.
     */
    val tiles: TileStyle,
    /**
     * Lines a [TileStyle.FULL] tile's reason may take before it is cut with an ellipsis: two or
     * three, and always three on the full-size card. Zero for [TileStyle.NAMES].
     */
    val tileReasonLines: Int,
    /**
     * Height of a [TileStyle.FULL] tile's icon area: [TILE_ICON_DP] at least, and more, up to
     * [TILE_ICON_MAX_DP], when the card is higher than the tiles need, so that a tall card is not
     * three small pictures in an empty frame. On the full-size card it is from
     * [ROOMY_TILE_ICON_DP] to [ROOMY_TILE_ICON_MAX_DP]. Set the icon area to exactly this height;
     * the picture is centred in it. Zero for [TileStyle.NAMES].
     */
    val tileIconDp: Float,
    /**
     * The recommendation card has room for the hint under its tiles. False where the card is so
     * low that the hint would take the bottom row's height: the hint is the last thing to give
     * way, after the caption. Always false for a fit asked for without `offers`.
     */
    val showHint: Boolean,
    /**
     * The parked screen's scoreboard is a panel to the right of the card, [SCOREBOARD_DP] wide, as
     * high as the card, [PARKED_GAP_DP] from it. When this is false while parked, the card is too
     * narrow to give up that width (it would become a [narrow] card): the card keeps the whole row
     * and the scores are one line of text ([SCORES_LINE_DP] high) just above the shortcut buttons.
     * Always false when not parked.
     */
    val scoresBeside: Boolean,
    /**
     * The parked screen has room for the route strip ([ROUTE_STRIP_DP] high) between the card and
     * the shortcut buttons, with the card above it still whole. While spots are on offer the strip
     * gives way sooner, because the card with the tiles needs more height. Always false when not
     * parked.
     */
    val showRoute: Boolean
) {
    /**
     * The share of the width inside the card that the illustration panel takes: [ART_SHARE] when
     * it shows, else zero. Give the panel this layout weight and the text column the rest of 1,
     * with [ART_GAP_DP] as the text column's left margin.
     */
    val artShare: Float get() = if (showArt) ART_SHARE else 0f

    /**
     * A tile lies on its side: its icon area is a square of [tileIconDp] on the left, and the name
     * and the reason are beside it, [ART_GAP_DP] away, in the middle of its height. That is how a
     * [TileStyle.FULL] tile is laid out when it is the only one of [spots] (the tip about one
     * place at the destination): it has the whole row to itself, and a picture stretched over
     * all of it is a band with a small drawing lost in the middle. It is no higher than the
     * upright tile the card was measured for.
     */
    fun tileOnItsSide(spots: Int): Boolean = tiles == TileStyle.FULL && spots == 1

    /** How much of a recommended spot its tile shows. */
    enum class TileStyle {
        /** The number badge, the icon, the name and the one-line reason. */
        FULL,

        /**
         * The number badge and, under it, the name only. A third of a narrow card holds nine
         * syllables of a reason to the line, and a low card has no height for the icon: the name
         * is what a passenger says to choose, so it is what stays, and it has the tile's whole
         * width to itself.
         */
        NAMES
    }

    /** A button on the parked panel: its label, and the request it makes, handled as if it had been spoken. */
    data class Shortcut(val label: String, val request: String)

    companion object {
        /**
         * The parked screen's buttons, in order. The car build opens the microphone only to hear the
         * answer to a question, so while parked these are how a story, a quiz or somewhere to eat is
         * asked for. They always share one row at the bottom of the parked screen, a third of the
         * width each with [SHORTCUT_GAP_DP] between them, the label centred. They are listed here,
         * not in the screen, because a third of the narrowest card has to hold each label: a test
         * measures these against it, and a longer label or a fourth button has to pass it too.
         */
        val PARKED_SHORTCUTS = listOf(
            Shortcut("이야기 듣기", "이야기해 줘"),
            Shortcut("퀴즈 하기", "퀴즈 하자"),
            Shortcut("먹거리 보기", "뭐 먹지")
        )

        /** Width of the side panel when it sits beside the card. */
        const val SIDE_PANEL_DP = 380f

        /** Share of the height the card's column gets when it is stacked above the side panel. */
        const val STACKED_SHARE = 0.6f

        // The illustration panel takes one part in three. Kept as a whole number so that the width
        // left for the text is worked out exactly, and a card of exactly the least width passes.
        private const val ART_PARTS = 3

        /** The illustration panel's share of the width inside the card, about a third as in the proposal's drawing. */
        const val ART_SHARE = 1f / ART_PARTS

        /** Between the illustration panel and the text column. */
        const val ART_GAP_DP = 24f

        /**
         * The illustration panel is at most this many times as high as it is wide. Where the car
         * gives the app a window nearly as high as it is wide (the Pleos emulator's is), a panel
         * as high as the card would be a stripe down its side, the picture lost in the middle of
         * it. At this height the panel is about as high as the full-size card's text beside it.
         */
        const val ART_TALLEST = 1.25f

        /**
         * How high the illustration panel is when it is [width] wide and the inside of the card
         * has [room] for it: all of the room, up to [ART_TALLEST] times the width. In any unit.
         */
        fun artHeight(width: Float, room: Float): Float = min(room, width * ART_TALLEST)

        /** Between two tiles of the recommendation card. */
        const val TILE_GAP_DP = 12f

        /** Padding inside a tile. */
        const val TILE_PADDING_DP = 12f

        /** Diameter of a tile's number badge. */
        const val TILE_BADGE_DP = 32f

        /** Between the badge of a [TileStyle.NAMES] tile and the name under it. */
        const val NAMES_BADGE_GAP_DP = 8f

        /**
         * A tile is a touch target while the car is parked, so it is never lower than a button
         * is. A [TileStyle.NAMES] tile with a name of one line is higher than this already; the
         * floor is there for whatever a tile is made of next.
         */
        const val TILE_MIN_DP = 76f

        /**
         * A reason is a sentence of about this many syllables. To show one, a tile needs that
         * many times the type size over all the lines of its reason together: a Hangul syllable
         * is about as wide as the type is high, and what the spaces between words save is lost
         * again at the ends of the lines. A tile with less cuts most reasons in mid-sentence,
         * which says less than the name alone.
         */
        const val REASON_SYLLABLES = 35

        /**
         * In a window at least this high the skip button keeps its height whatever is showing,
         * as long as the card is not stacked above the side panel. See [leastHeightDp].
         */
        const val SAFE_HEIGHT_DP = 560f

        /** The same for a window in which the card is stacked above the side panel. */
        const val SAFE_STACKED_HEIGHT_DP = 920f

        /** The least height of a tile's icon area; [tileIconDp] says how high it is on a given card. */
        const val TILE_ICON_DP = 56f

        /** The most a tile's icon area grows to: beyond this the picture dwarfs the name under it. */
        const val TILE_ICON_MAX_DP = 200f

        /**
         * The least height of a tile's icon area on the full-size card. Under a picture smaller
         * than this, a name at 28sp is the larger of the two, and the compact card, whose
         * pictures have grown to [TILE_ICON_MAX_DP] long before, shows the spots better.
         */
        const val ROOMY_TILE_ICON_DP = 160f

        /** The most a tile's icon area grows to on the full-size card, whose names are larger in the same measure. */
        const val ROOMY_TILE_ICON_MAX_DP = 320f

        /** Width of the scoreboard panel beside the parked card: a crown, a name of up to five syllables and a two-digit score at 26sp. */
        const val SCOREBOARD_DP = 260f

        /**
         * The gap between the pieces of the parked screen: between the card and the scoreboard,
         * and above the route strip, the line of scores and the shortcut buttons.
         */
        const val PARKED_GAP_DP = 16f

        /**
         * Height of the route strip: the line in the middle with room for one row of 15sp names
         * above it and one below, which is where `RouteStripPlan` puts them.
         */
        const val ROUTE_STRIP_DP = 96f

        /** Height of the line of scores that stands in for the scoreboard under a narrow card: one line of 26sp. */
        const val SCORES_LINE_DP = 38f

        /** Height of the row of shortcut buttons, and so of each button. */
        const val SHORTCUT_ROW_DP = 60f

        /** Between two shortcut buttons. */
        const val SHORTCUT_GAP_DP = 12f

        /** Beside the panel, the card has to keep at least this width to be worth having there. */
        private const val MIN_CARD_WIDTH_DP = 480f

        /** The full-size card wants this much width; with less, the compact one reads better. */
        private const val ROOMY_CARD_WIDTH_DP = 640f

        /**
         * The width a tile's text needs on the full-size card: a reason of forty-odd syllables,
         * the longest in the bundled package, in three lines of 22sp.
         */
        private const val ROOMY_TILE_TEXT_DP = 340f

        /** The full-size recommendation card wants this much width: three such tiles inside its padding. */
        private const val ROOMY_TILE_CARD_WIDTH_DP =
            2 * 40f + 3 * (ROOMY_TILE_TEXT_DP + 2 * TILE_PADDING_DP) + 2 * TILE_GAP_DP

        /** Below this width a sentence needs more than two caption lines. */
        private const val NARROW_CARD_WIDTH_DP = 620f

        /**
         * The illustration panel needs this much height inside the card. The pictures are drawn to
         * read from 80 dp up and the panel keeps a margin around them; in a slot lower than this
         * and three hundred wide a picture is a stripe, not a bowl or a mountain.
         */
        private const val ART_MIN_DP = 160f

        /**
         * A reason is a sentence of thirty-odd syllables. One line of it in a tile is a fragment,
         * so a card with room for fewer than two lines shows names only; more than three it never
         * needs.
         */
        private const val MIN_REASON_LINES = 2
        private const val MAX_REASON_LINES = 3

        // Heights of the pieces as HostScreen lays them out; the class comment has the whole list.
        private const val PADDING_DP = 24f
        private const val ROOMY_PADDING_DP = 40f
        private const val COMPACT_PADDING_DP = 24f
        private const val SKIP_BUTTON_DP = 76f
        private const val PROGRESS_BAR_DP = 10f

        /**
         * Height of a line of Korean text of size [sp] in Noto Sans CJK, whose lines are 1.448
         * times the type size (a 26sp line measured 38). The Pleos emulator's font makes them 1.18
         * times.
         */
        private fun line(sp: Float): Float = ceil(sp * 1.448f)

        /** The status row: its tallest piece is the destination, one line of 22sp. */
        private val STATUS_ROW_DP = line(22f)

        private val CHIP_DP = line(22f) + 2 * 6f

        /** The driving card's bottom row: the state line, the progress bar and the "다음" line beside the skip button. */
        private val BOTTOM_ROW_DP = maxOf(SKIP_BUTTON_DP, line(22f) + 12f + PROGRESS_BAR_DP + 8f + line(20f))

        /** The recommendation card's bottom row, which has no "다음" line. */
        private val TILE_BOTTOM_ROW_DP = maxOf(SKIP_BUTTON_DP, line(22f) + 12f + PROGRESS_BAR_DP)

        /** A caption of [lines] lines with the gap above it, on the full-size or the [compact] card: nothing at all for no line. */
        private fun caption(compact: Boolean, lines: Int): Float = when {
            lines <= 0 -> 0f
            compact -> 10f + lines * line(22f)
            else -> 20f + lines * line(28f)
        }

        /** The full-size driving card with a two-line title: with its three caption lines, what it needs to be chosen. */
        private fun roomyCard(captionLines: Int): Float =
            2 * ROOMY_PADDING_DP + CHIP_DP + 16f + 2 * line(46f) + caption(false, captionLines) + 32f + BOTTOM_ROW_DP

        /** The compact driving card, which is also the parked screen's card. */
        private fun compactCard(captionLines: Int, narrow: Boolean): Float =
            2 * COMPACT_PADDING_DP + CHIP_DP + 8f + (if (narrow) 2 else 1) * line(34f) + caption(true, captionLines) +
                14f + BOTTOM_ROW_DP

        /** The recommendation card, full-size or [compact], around a row of tiles [tileDp] high, with or without its [hint]. */
        private fun tileCard(compact: Boolean, captionLines: Int, tileDp: Float, hint: Boolean = true): Float =
            if (compact) {
                2 * COMPACT_PADDING_DP + CHIP_DP + 12f + tileDp + (if (hint) 12f + line(18f) else 0f) + caption(true, captionLines) +
                    14f + TILE_BOTTOM_ROW_DP
            } else {
                2 * ROOMY_PADDING_DP + CHIP_DP + 16f + tileDp + (if (hint) 12f + line(22f) else 0f) + caption(false, captionLines) +
                    32f + TILE_BOTTOM_ROW_DP
            }

        /** A [TileStyle.FULL] tile on the full-size or the [compact] card: the icon area, a name of two lines and the reason. */
        private fun fullTile(compact: Boolean, iconDp: Float, reasonLines: Int): Float =
            2 * TILE_PADDING_DP + iconDp + 8f + 2 * line(if (compact) 22f else 28f) + 6f +
                reasonLines * line(if (compact) 18f else 22f)

        /** A [TileStyle.NAMES] tile: the badge and, under it, a name of two lines. */
        private val NAMES_TILE_DP = maxOf(TILE_MIN_DP, 2 * TILE_PADDING_DP + TILE_BADGE_DP + NAMES_BADGE_GAP_DP + 2 * line(22f))

        /** The width a tile's text has when three tiles share a card [cardWidth] wide. */
        private fun tileText(cardWidth: Float, compact: Boolean): Float =
            (cardWidth - 2 * (if (compact) COMPACT_PADDING_DP else ROOMY_PADDING_DP) - 2 * TILE_GAP_DP) / 3 - 2 * TILE_PADDING_DP

        /**
         * The fewest lines in which a tile on a card [cardWidth] wide holds a reason
         * ([REASON_SYLLABLES]): [MIN_REASON_LINES] at least, and more than [MAX_REASON_LINES]
         * where the tile is too narrow to show a reason at all.
         */
        private fun reasonLinesNeeded(cardWidth: Float, compact: Boolean): Int {
            val width = tileText(cardWidth, compact)
            if (width <= 0f) return MAX_REASON_LINES + 1
            val lines = ceil(REASON_SYLLABLES * (if (compact) 18f else 22f) / width).toInt()
            return lines.coerceIn(MIN_REASON_LINES, MAX_REASON_LINES + 1)
        }

        /**
         * The column the card is in: how wide it is and how much of a window's height it leaves
         * the card. Everything here follows from the window's width and from what surrounds the
         * card, before anything is decided about the card itself.
         */
        private class Column(widthDp: Float, sidePanel: Boolean, parked: Boolean) {
            private val innerWidth = widthDp - 2 * PADDING_DP
            private val besideWidth = innerWidth - SIDE_PANEL_DP - PADDING_DP

            val stacked = sidePanel && besideWidth < MIN_CARD_WIDTH_DP
            val width = if (stacked || !sidePanel) innerWidth else besideWidth
            val narrow = width < NARROW_CARD_WIDTH_DP

            // While parked the card gives up width to the scoreboard, but only if that does not
            // make it a narrow card: a card that needs twice the lines is a worse trade than
            // scores on one line. So `narrow` is true of the column and of the card alike.
            val scoresBeside = parked && width - PARKED_GAP_DP - SCOREBOARD_DP >= NARROW_CARD_WIDTH_DP
            val cardWidth = if (scoresBeside) width - PARKED_GAP_DP - SCOREBOARD_DP else width

            /** What the parked screen always has under the card: the shortcut buttons, and the line of scores when they are not beside it. */
            private val under = when {
                !parked -> 0f
                scoresBeside -> PARKED_GAP_DP + SHORTCUT_ROW_DP
                else -> PARKED_GAP_DP + SCORES_LINE_DP + PARKED_GAP_DP + SHORTCUT_ROW_DP
            }

            /** The height a window [heightDp] high leaves for the card and, when it shows, the route strip. */
            fun cardRoom(heightDp: Float): Float {
                val inner = heightDp - 2 * PADDING_DP
                val column = if (stacked) (inner - PADDING_DP) * STACKED_SHARE else inner
                return column - STATUS_ROW_DP - PADDING_DP - under
            }

            /** The other way round: how high a window has to be to leave the card [cardDp]. */
            fun windowFor(cardDp: Float): Float {
                val column = cardDp + under + PADDING_DP + STATUS_ROW_DP
                return (if (stacked) column / STACKED_SHARE + PADDING_DP else column) + 2 * PADDING_DP
            }
        }

        /**
         * The least height of a window [widthDp] wide in which the bottom row of the card, and so
         * the skip button, keeps its height: the height at which the card still holds its chip,
         * its title or its names-only tiles, and its bottom row, with the caption and the hint
         * gone and no route strip. The arguments are those of [of].
         */
        fun leastHeightDp(widthDp: Float, sidePanel: Boolean, parked: Boolean, offers: Boolean): Float {
            val column = Column(widthDp, sidePanel, parked)
            val card = if (offers) tileCard(true, 0, NAMES_TILE_DP, hint = false) else compactCard(0, column.narrow)
            return column.windowFor(card)
        }

        /**
         * The arrangement for a window of [widthDp] by [heightDp].
         *
         * [sidePanel]: the side panel (the demo controls) is showing. [parked]: the parked screen
         * (the scores, the route strip and the shortcut buttons around the card) is showing.
         * [offers]: the recommendation card is showing, that is, `HostSnapshot.offers` is not
         * empty. Ask again whenever any of the five changes: the tiles need a different
         * arrangement from the card they replace, so a fit asked for without [offers] must not be
         * used to lay them out. Before the window has a size, the fit of a wide display
         * (`of(1408f, 700f, sidePanel = false, parked = false, offers = false)`) is a fair guess.
         */
        fun of(widthDp: Float, heightDp: Float, sidePanel: Boolean, parked: Boolean, offers: Boolean): ScreenFit {
            val column = Column(widthDp, sidePanel, parked)
            val narrow = column.narrow
            val cardWidth = column.cardWidth

            // Under the status row, and while parked above the shortcut buttons and the line of
            // scores, the card has the height that is left, less the route strip if the card
            // being shown still fits above it. The route is asked for with the compact card in
            // mind: where the car is on its way is worth more on the parked screen than larger
            // type on the card. With spots on offer the strip gives way to tiles that show their
            // reasons, where the tiles are wide enough to show them at all.
            val room = column.cardRoom(heightDp)
            val route = PARKED_GAP_DP + ROUTE_STRIP_DP
            val compactLines = if (narrow) 4 else 2
            val compactReasonLines = reasonLinesNeeded(cardWidth, compact = true)
            val cardNeeds = when {
                !offers -> compactCard(compactLines, narrow)
                compactReasonLines > MAX_REASON_LINES -> tileCard(true, compactLines, NAMES_TILE_DP)
                else -> tileCard(true, compactLines, fullTile(true, TILE_ICON_DP, compactReasonLines))
            }
            val showRoute = parked && room - route >= cardNeeds
            val cardHeight = room - (if (showRoute) route else 0f)

            // The full-size card where what is left for the card, parked or not, is enough for it.
            // With spots on offer that is a card whose tiles hold the whole reason in the larger
            // type under a picture of a size to match; short of either, smaller type shows more.
            val roomy = !column.stacked && if (offers) {
                cardWidth >= ROOMY_TILE_CARD_WIDTH_DP &&
                    cardHeight >= tileCard(false, 3, fullTile(false, ROOMY_TILE_ICON_DP, MAX_REASON_LINES))
            } else {
                cardHeight >= roomyCard(3) && cardWidth >= ROOMY_CARD_WIDTH_DP
            }
            val compact = !roomy
            val plannedLines = if (compact) compactLines else 3

            // The tiles get what the rest of the recommendation card leaves: first the reason's
            // lines, then a bigger icon. They show the reason only if those lines, at the tiles'
            // width, hold one: a third of a narrow card never does, however much height there is.
            val tileRoom = cardHeight - tileCard(compact, plannedLines, 0f)
            val leastIcon = if (compact) TILE_ICON_DP else ROOMY_TILE_ICON_DP
            val mostIcon = if (compact) TILE_ICON_MAX_DP else ROOMY_TILE_ICON_MAX_DP
            val reasonLine = line(if (compact) 18f else 22f)
            val reasonLines = min(MAX_REASON_LINES, floor((tileRoom - fullTile(compact, leastIcon, 0)) / reasonLine).toInt())
            val fullTiles = offers && reasonLines >= reasonLinesNeeded(cardWidth, compact)
            val tileIconDp = if (fullTiles) min(mostIcon, leastIcon + tileRoom - fullTile(compact, leastIcon, reasonLines)) else 0f

            // In a card lower than what was planned for it, the caption gives way line by line,
            // and after its last line the hint under the tiles, so that the bottom row is not
            // what pays. Full tiles and the full-size card were chosen with the planned caption
            // and the hint counted in, so there the plan stands.
            fun card(captionLines: Int): Float = when {
                !offers -> if (compact) compactCard(captionLines, narrow) else roomyCard(captionLines)
                else -> tileCard(compact, captionLines, NAMES_TILE_DP)
            }
            val settled = fullTiles || !compact
            val captionLines = if (settled) plannedLines else (plannedLines downTo 1).firstOrNull { card(it) <= cardHeight } ?: 0
            val showHint = offers && (settled || card(0) <= cardHeight)

            // The illustration takes its third only where the text beside it is still as wide as
            // on the narrowest card of the same kind without one: there the title and the caption
            // were given their lines for that width, so the picture costs the driver nothing.
            val padding = if (compact) COMPACT_PADDING_DP else ROOMY_PADDING_DP
            val textBesideArt = (cardWidth - 2 * padding - ART_GAP_DP) * (ART_PARTS - 1) / ART_PARTS
            val textNeeds = if (compact) NARROW_CARD_WIDTH_DP - 2 * COMPACT_PADDING_DP else ROOMY_CARD_WIDTH_DP - 2 * ROOMY_PADDING_DP
            val showArt = !offers && textBesideArt >= textNeeds && cardHeight - 2 * padding >= ART_MIN_DP

            return ScreenFit(
                stacked = column.stacked,
                compact = compact,
                narrow = narrow,
                captionLines = captionLines,
                showArt = showArt,
                tiles = if (fullTiles) TileStyle.FULL else TileStyle.NAMES,
                tileReasonLines = if (fullTiles) reasonLines else 0,
                tileIconDp = tileIconDp,
                showHint = showHint,
                scoresBeside = column.scoresBeside,
                showRoute = showRoute
            )
        }
    }
}
