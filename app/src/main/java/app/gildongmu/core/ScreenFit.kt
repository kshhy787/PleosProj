package app.gildongmu.core

/**
 * How the screen arranges itself in the space the car gives it.
 *
 * The app does not always get a wide display to itself. An infotainment system can show it in
 * half of a split screen, or in a short strip, and a layout drawn for one size leaves the card,
 * which is the one thing that must be readable, squeezed to nothing. This decides the arrangement
 * from the size of the window; the screen only applies it. Sizes are in dp.
 */
data class ScreenFit(
    /** Card above the side panel instead of beside it. */
    val stacked: Boolean,
    /** The smaller card: smaller type and tighter spacing. */
    val compact: Boolean,
    /** The card is narrow: a sentence needs more lines, and the status row has to shed something. */
    val narrow: Boolean,
    /** Lines the caption may take before it is cut. */
    val captionLines: Int,
    /** Whether the parked panel has room for the route diagram as well as the scores. */
    val showRoute: Boolean
) {
    companion object {
        /** Width of the side panel when it sits beside the card. */
        const val SIDE_PANEL_DP = 380f

        /** Share of the height the card's column gets when it is stacked above the side panel. */
        const val STACKED_SHARE = 0.6f

        /** Beside the panel, the card has to keep at least this width to be worth having there. */
        private const val MIN_CARD_WIDTH_DP = 480f

        /** The full-size card wants this much room; with less, the compact one reads better. */
        private const val ROOMY_CARD_WIDTH_DP = 640f
        private const val ROOMY_CARD_HEIGHT_DP = 510f

        /** Below this width a sentence needs more than two caption lines. */
        private const val NARROW_CARD_WIDTH_DP = 620f

        // Heights of the pieces as HostScreen lays them out.
        private const val PADDING_DP = 24f
        private const val STATUS_ROW_DP = 30f
        private const val COMPACT_CARD_DP = 285f
        private const val CAPTION_LINE_DP = 28f
        private const val TITLE_LINE_DP = 42f
        private const val PARKED_PANEL_DP = 92f
        private const val ROUTE_DP = 92f

        /**
         * [sidePanel]: the side panel (the demo controls) is showing. [parked]: the parked panel
         * with the scores is showing under the card.
         */
        fun of(widthDp: Float, heightDp: Float, sidePanel: Boolean, parked: Boolean): ScreenFit {
            val innerWidth = widthDp - 2 * PADDING_DP
            val innerHeight = heightDp - 2 * PADDING_DP

            val besideWidth = innerWidth - SIDE_PANEL_DP - PADDING_DP
            val stacked = sidePanel && besideWidth < MIN_CARD_WIDTH_DP

            val cardWidth = if (stacked || !sidePanel) innerWidth else besideWidth
            val columnHeight = if (stacked) (innerHeight - PADDING_DP) * STACKED_SHARE else innerHeight
            val cardHeight = columnHeight - STATUS_ROW_DP - PADDING_DP

            val compact = parked || stacked || cardHeight < ROOMY_CARD_HEIGHT_DP || cardWidth < ROOMY_CARD_WIDTH_DP
            val narrow = cardWidth < NARROW_CARD_WIDTH_DP
            val captionLines = when {
                !compact -> 3
                narrow -> 4
                else -> 2
            }

            // While parked the card shares its column with the scores, and with the route diagram
            // if the compact card still fits above both.
            val compactCard = COMPACT_CARD_DP + (captionLines - 2) * CAPTION_LINE_DP + (if (narrow) TITLE_LINE_DP else 0f)
            val leftForCard = cardHeight - PARKED_PANEL_DP - PADDING_DP - ROUTE_DP
            val showRoute = parked && leftForCard >= compactCard

            return ScreenFit(stacked, compact, narrow, captionLines, showRoute)
        }
    }
}
