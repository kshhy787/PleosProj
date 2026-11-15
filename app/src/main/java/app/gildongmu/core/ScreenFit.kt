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
 * out as they were added up here. Around the column: 24 of padding, the status row, 24, then the
 * card. Inside the card the pieces are kept together, in the middle of the card's height when it
 * is higher than they need; from the top (full-size card / compact card):
 *
 * **Driving card** (also the card of the parked screen). Padding 40 / 24. When [showArt], the
 * illustration panel on the left ([artHeight] high) and [ART_GAP_DP] between it and the text
 * column. In the text column:
 * - the chip: 22sp bold with 6 above and below its text;
 * - the title, 16 / 8 under the chip: 46sp / 34sp bold, two lines / one line (two when [narrow]);
 * - the caption when captions are on, 20 / 10 under the title: 28sp / 22sp, [captionLines] lines;
 * - the bottom row, 32 / 14 under that: on the left the state line (22sp), 12, the progress bar
 *   (10 high), 8, the "다음 · …" line (20sp, one line); on the right the skip button (76 high).
 *
 * **Recommendation card** (always the compact card, without the illustration panel). Padding 24.
 * - the chip, as above;
 * - the row of tiles, 12 under the chip: equal widths, [TILE_GAP_DP] between them, each with
 *   [TILE_PADDING_DP] of padding. A [TileStyle.FULL] tile is the icon area ([tileIconDp] high, the
 *   number badge of [TILE_BADGE_DP] on its top left corner), 8, the name (22sp bold, at most two
 *   lines), 6, the reason (18sp, [tileReasonLines] lines). A [TileStyle.NAMES] tile is the badge
 *   and, beside it, the name (22sp bold, at most two lines);
 * - the hint, 12 under the tiles: 18sp, one line, cut if need be;
 * - the caption when captions are on, 10 under the hint: 22sp, [captionLines] lines;
 * - the bottom row, 14 under that: the state line (22sp), 12 and the progress bar (10 high) on the
 *   left, the skip button (76 high) on the right. It has no "다음 · …" line: the tiles are what is
 *   next.
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
 */
data class ScreenFit(
    /**
     * Card above the side panel instead of beside it: the root is vertical, the card's column gets
     * [STACKED_SHARE] of the height and the side panel the rest.
     */
    val stacked: Boolean,
    /**
     * The smaller card: the second of each pair of sizes in the list above. Always while parked
     * and while spots are on offer, when the card shares its room with the scoreboard or holds
     * the tiles.
     */
    val compact: Boolean,
    /**
     * The card is narrow: a sentence needs more lines, so the compact card's title may take two,
     * and the status row has to shed something (the waypoint goes, and its type gets smaller).
     */
    val narrow: Boolean,
    /** Lines the caption may take before it is cut. */
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
     * How much the recommendation card's tiles show. [TileStyle.NAMES] also for a fit that was
     * asked for without `offers`, which has no tiles to lay out.
     */
    val tiles: TileStyle,
    /**
     * Lines a [TileStyle.FULL] tile's reason may take before it is cut with an ellipsis: two or
     * three. Zero for [TileStyle.NAMES].
     */
    val tileReasonLines: Int,
    /**
     * Height of a [TileStyle.FULL] tile's icon area: [TILE_ICON_DP] at least, and more, up to
     * [TILE_ICON_MAX_DP], when the card is higher than the tiles need, so that a tall card is not
     * three small pictures in an empty frame. Set the icon area to exactly this height; the
     * picture is centred in it. Zero for [TileStyle.NAMES].
     */
    val tileIconDp: Float,
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

    /** How much of a recommended spot its tile shows. */
    enum class TileStyle {
        /** The number badge, the icon, the name and the one-line reason. */
        FULL,

        /**
         * The number badge and the name only. A third of a narrow card holds nine syllables of a
         * reason to the line, and a low card has no height for the icon: the name is what a
         * passenger says to choose, so it is what stays.
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

        /** The least height of a tile's icon area; [tileIconDp] says how high it is on a given card. */
        const val TILE_ICON_DP = 56f

        /** The most a tile's icon area grows to: beyond this the picture dwarfs the name under it. */
        const val TILE_ICON_MAX_DP = 200f

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
        private const val STATUS_ROW_DP = 30f
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

        private val CHIP_DP = line(22f) + 2 * 6f

        /** The driving card's bottom row: the state line, the progress bar and the "다음" line beside the skip button. */
        private val BOTTOM_ROW_DP = maxOf(SKIP_BUTTON_DP, line(22f) + 12f + PROGRESS_BAR_DP + 8f + line(20f))

        /** The recommendation card's bottom row, which has no "다음" line. */
        private val TILE_BOTTOM_ROW_DP = maxOf(SKIP_BUTTON_DP, line(22f) + 12f + PROGRESS_BAR_DP)

        /** The full-size driving card with a two-line title and its three caption lines: what it needs to be chosen. */
        private val ROOMY_CARD_HEIGHT_DP =
            2 * ROOMY_PADDING_DP + CHIP_DP + 16f + 2 * line(46f) + 20f + 3 * line(28f) + 32f + BOTTOM_ROW_DP

        /** The compact driving card, which is also the parked screen's card. */
        private fun compactCard(captionLines: Int, narrow: Boolean): Float =
            2 * COMPACT_PADDING_DP + CHIP_DP + 8f + (if (narrow) 2 else 1) * line(34f) + 10f + captionLines * line(22f) +
                14f + BOTTOM_ROW_DP

        /** The recommendation card around a row of tiles [tileDp] high. */
        private fun tileCard(captionLines: Int, tileDp: Float): Float =
            2 * COMPACT_PADDING_DP + CHIP_DP + 12f + tileDp + 12f + line(18f) + 10f + captionLines * line(22f) +
                14f + TILE_BOTTOM_ROW_DP

        /** A [TileStyle.FULL] tile: the icon area, a name of two lines and the reason. */
        private fun fullTile(iconDp: Float, reasonLines: Int): Float =
            2 * TILE_PADDING_DP + iconDp + 8f + 2 * line(22f) + 6f + reasonLines * line(18f)

        /** A [TileStyle.NAMES] tile: a name of two lines beside the badge. */
        private val NAMES_TILE_DP = 2 * TILE_PADDING_DP + maxOf(TILE_BADGE_DP, 2 * line(22f))

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
            val innerWidth = widthDp - 2 * PADDING_DP
            val innerHeight = heightDp - 2 * PADDING_DP

            val besideWidth = innerWidth - SIDE_PANEL_DP - PADDING_DP
            val stacked = sidePanel && besideWidth < MIN_CARD_WIDTH_DP

            val columnWidth = if (stacked || !sidePanel) innerWidth else besideWidth
            val columnHeight = if (stacked) (innerHeight - PADDING_DP) * STACKED_SHARE else innerHeight
            val underStatusRow = columnHeight - STATUS_ROW_DP - PADDING_DP

            val compact = parked || offers || stacked || underStatusRow < ROOMY_CARD_HEIGHT_DP || columnWidth < ROOMY_CARD_WIDTH_DP
            val narrow = columnWidth < NARROW_CARD_WIDTH_DP
            val captionLines = when {
                !compact -> 3
                narrow -> 4
                else -> 2
            }

            // While parked the card gives up width to the scoreboard, but only if that does not
            // make it a narrow card: a card that needs twice the lines is a worse trade than
            // scores on one line. So `narrow` is true of the column and of the card alike.
            val scoresBeside = parked && columnWidth - PARKED_GAP_DP - SCOREBOARD_DP >= NARROW_CARD_WIDTH_DP
            val cardWidth = if (scoresBeside) columnWidth - PARKED_GAP_DP - SCOREBOARD_DP else columnWidth

            // And it gives up height to the shortcut buttons, to the line of scores when they are
            // not beside it, and to the route strip if the card being shown still fits above it.
            val buttons = when {
                !parked -> 0f
                scoresBeside -> PARKED_GAP_DP + SHORTCUT_ROW_DP
                else -> PARKED_GAP_DP + SCORES_LINE_DP + PARKED_GAP_DP + SHORTCUT_ROW_DP
            }
            val route = PARKED_GAP_DP + ROUTE_STRIP_DP
            val cardNeeds = when {
                !offers -> compactCard(captionLines, narrow)
                narrow -> tileCard(captionLines, NAMES_TILE_DP)
                else -> tileCard(captionLines, fullTile(TILE_ICON_DP, MIN_REASON_LINES))
            }
            val showRoute = parked && underStatusRow - buttons - route >= cardNeeds
            val cardHeight = underStatusRow - buttons - (if (showRoute) route else 0f)

            // The tiles get what the rest of the recommendation card leaves: first the reason's
            // lines, then a bigger icon. A third of a narrow card is too little width for a reason
            // however much height there is.
            val tileRoom = cardHeight - tileCard(captionLines, 0f)
            val reasonLines = min(MAX_REASON_LINES, floor((tileRoom - fullTile(TILE_ICON_DP, 0)) / line(18f)).toInt())
            val fullTiles = offers && !narrow && reasonLines >= MIN_REASON_LINES
            val tileIconDp = if (fullTiles) min(TILE_ICON_MAX_DP, TILE_ICON_DP + tileRoom - fullTile(TILE_ICON_DP, reasonLines)) else 0f

            // The illustration takes its third only where the text beside it is still as wide as
            // on the narrowest card of the same kind without one: there the title and the caption
            // were given their lines for that width, so the picture costs the driver nothing.
            val padding = if (compact) COMPACT_PADDING_DP else ROOMY_PADDING_DP
            val textBesideArt = (cardWidth - 2 * padding - ART_GAP_DP) * (ART_PARTS - 1) / ART_PARTS
            val textNeeds = if (compact) NARROW_CARD_WIDTH_DP - 2 * COMPACT_PADDING_DP else ROOMY_CARD_WIDTH_DP - 2 * ROOMY_PADDING_DP
            val showArt = !offers && textBesideArt >= textNeeds && cardHeight - 2 * padding >= ART_MIN_DP

            return ScreenFit(
                stacked = stacked,
                compact = compact,
                narrow = narrow,
                captionLines = captionLines,
                showArt = showArt,
                tiles = if (fullTiles) TileStyle.FULL else TileStyle.NAMES,
                tileReasonLines = if (fullTiles) reasonLines else 0,
                tileIconDp = tileIconDp,
                scoresBeside = scoresBeside,
                showRoute = showRoute
            )
        }
    }
}
