package app.gildongmu.core

import app.gildongmu.core.ScreenFit.TileStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenFitTest {

    private fun fit(width: Int, height: Int, sidePanel: Boolean = true, parked: Boolean = false, offers: Boolean = false) =
        ScreenFit.of(width.toFloat(), height.toFloat(), sidePanel, parked, offers)

    /** A fit spelled out, with the fields most windows share left at what they usually are. */
    private fun expected(
        stacked: Boolean = false,
        compact: Boolean,
        narrow: Boolean = false,
        captionLines: Int,
        showArt: Boolean,
        tiles: TileStyle = TileStyle.NAMES,
        tileReasonLines: Int = 0,
        tileIconDp: Float = 0f,
        showHint: Boolean = false,
        scoresBeside: Boolean = false,
        showRoute: Boolean = false
    ) = ScreenFit(stacked, compact, narrow, captionLines, showArt, tiles, tileReasonLines, tileIconDp, showHint, scoresBeside, showRoute)

    @Test
    fun aWideCarDisplayKeepsTheCardBesideThePanelAtFullSize() {
        // The stock Automotive emulator profile, less the system bars.
        assertEquals(expected(compact = false, captionLines = 3, showArt = true), fit(1408, 700))

        // Parked, the card is 680 wide beside the scoreboard: room for the route under it, but
        // not for a picture beside its text.
        assertEquals(
            expected(compact = true, captionLines = 2, showArt = false, scoresBeside = true, showRoute = true),
            fit(1408, 700, parked = true)
        )
    }

    @Test
    fun thePleosEmulatorWindowShowsEverythingWithAndWithoutTheDemoControls() {
        // 1682×1210 is the window the Pleos Connect emulator gives the app; 1682×1172 is the same
        // window under the display cutout it sometimes reports. The mock build has the demo
        // controls beside the card, the pleos build has the whole width.
        //
        // The 1172 case pins the arithmetic for a window of that size, and no more. On the PC
        // whose emulator reports the cutout, the home screen draws the app 128 below its window,
        // so the bottom 128 of these 1172 (the shortcut buttons and the lower part of the route
        // strip) are under the system's bar: that the fit shows them is no evidence that they
        // can be seen there. The app cannot tell from the size of its window.
        for (height in listOf(1210, 1172)) {
            for (sidePanel in listOf(true, false)) {
                val label = "1682 x $height, sidePanel=$sidePanel"
                assertEquals(label, expected(compact = false, captionLines = 3, showArt = true), fit(1682, height, sidePanel))
                assertEquals(
                    "Spots on offer: the full-size card, full tiles with their icons at full height, and no picture. $label",
                    expected(compact = false, captionLines = 3, showArt = false, tiles = TileStyle.FULL, tileReasonLines = 3, tileIconDp = 320f, showHint = true),
                    fit(1682, height, sidePanel, offers = true)
                )
                assertEquals(
                    "Parked: still the full-size card with its picture, the scoreboard beside it and the route under it. $label",
                    expected(compact = false, captionLines = 3, showArt = true, scoresBeside = true, showRoute = true),
                    fit(1682, height, sidePanel, parked = true)
                )
                // Beside the demo controls and the scoreboard the card is 954 wide, and a tile's
                // text 270: the compact card. With the whole width the card is 1358 and still the
                // full-size one, its pictures as high as the route strip and the buttons leave.
                val parkedTiles = when {
                    sidePanel -> expected(
                        compact = true, captionLines = 2, showArt = false, tiles = TileStyle.FULL, tileReasonLines = 3, tileIconDp = 200f,
                        showHint = true, scoresBeside = true, showRoute = true
                    )
                    else -> expected(
                        compact = false, captionLines = 3, showArt = false, tiles = TileStyle.FULL, tileReasonLines = 3,
                        tileIconDp = if (height == 1210) 267f else 229f, showHint = true, scoresBeside = true, showRoute = true
                    )
                }
                assertEquals(
                    "Parked with spots on offer: the tiles keep everything and the route stays. $label",
                    parkedTiles,
                    fit(1682, height, sidePanel, parked = true, offers = true)
                )
            }
        }
    }

    @Test
    fun halfOfASplitScreenPutsTheCardAboveThePanel() {
        // Beside a 380 dp panel the card would be under 200 dp wide: too narrow to read anything.
        val driving = fit(640, 950)
        assertTrue(driving.stacked)
        assertTrue(driving.compact)
        assertTrue(driving.narrow)
        assertEquals("A narrow card needs more lines for the same sentence", 4, driving.captionLines)
        assertFalse("A picture would leave the text 350 dp", driving.showArt)

        val parked = fit(640, 950, parked = true)
        assertFalse("No room for the route diagram above a stacked panel", parked.showRoute)
        assertFalse("Nor for the scoreboard beside a card this narrow", parked.scoresBeside)
        assertEquals("Nor, above the buttons and the line of scores, for a caption: 341 are left for a card of 305", 0, parked.captionLines)

        val offers = fit(640, 950, offers = true)
        assertEquals("A third of this card is too narrow for a reason", TileStyle.NAMES, offers.tiles)
        assertEquals("The tiles and the hint leave the caption three of its four lines", 3, offers.captionLines)
        assertTrue(offers.showHint)
    }

    @Test
    fun withoutASidePanelNothingIsStacked() {
        // The car build has no demo controls; the card has the whole width.
        val narrow = fit(640, 950, sidePanel = false)
        assertFalse(narrow.stacked)
        assertTrue("Still the compact card: full-size type would wrap badly at this width", narrow.compact)
        assertFalse(narrow.showArt)

        assertEquals(expected(compact = false, captionLines = 3, showArt = true), fit(1408, 700, sidePanel = false))
    }

    @Test
    fun aShortWindowGetsTheCompactCard() {
        val short = fit(1400, 450)
        assertFalse(short.stacked)
        assertTrue(short.compact)
        assertEquals(2, short.captionLines)
        assertTrue("The picture costs no height, and the card is wide enough for it", short.showArt)

        val parked = fit(1400, 450, parked = true)
        assertFalse(parked.showRoute)
        assertTrue("The scoreboard costs no height either", parked.scoresBeside)
        assertEquals("Above the buttons 270 are left: the card without its caption is 255", 0, parked.captionLines)

        val offers = fit(1400, 450, offers = true)
        assertEquals("No height for icons and reasons", TileStyle.NAMES, offers.tiles)
        assertEquals("Nor, in 346, for a caption under the tiles", 0, offers.captionLines)
        assertFalse("Nor for the hint: with it the card is 361", offers.showHint)
    }

    @Test
    fun theCardIsStackedExactlyWhenItWouldBeTooNarrowBesideThePanel() {
        // 24 padding each side, 380 panel, 24 gap, and at least 480 for the card: 932 in all.
        assertTrue(fit(931, 800).stacked)
        assertFalse(fit(932, 800).stacked)
        assertTrue("Beside the panel but narrow: compact", fit(932, 800).compact)
        assertFalse("Roomy from 1092 up", fit(1092, 800).compact)
    }

    @Test
    fun theFullSizeCardNeedsTheHeightOfItsChipTitleCaptionAndBottomRow() {
        // 80 of padding, the chip (44), 16, two title lines (134), 20, three caption lines (123),
        // 32 and the bottom row with the "다음" line (91): 540, under the status row (32) and the
        // 24 below it, inside the window's 48 of padding.
        assertTrue(fit(1408, 643).compact)
        assertFalse(fit(1408, 644).compact)
    }

    @Test
    fun theParkedCardKeepsItsFullSizeWhereWhatIsLeftForItIsEnough() {
        // Parked, the card gives up 76 to the buttons and 112 to the route strip: the 540 it
        // needs at full size are left in a window 188 higher than while driving.
        assertTrue(fit(1408, 831, sidePanel = false, parked = true).compact)
        val high = fit(1408, 832, sidePanel = false, parked = true)
        assertEquals(expected(compact = false, captionLines = 3, showArt = true, scoresBeside = true, showRoute = true), high)

        // And 276 to the scoreboard and its gap: the 640 it needs are left in a window of 964.
        assertTrue(fit(963, 900, sidePanel = false, parked = true).compact)
        val wide = fit(964, 900, sidePanel = false, parked = true)
        assertFalse(wide.compact)
        assertTrue(wide.scoresBeside)
        assertFalse("At 640 the full-size card has no width for a picture", wide.showArt)

        // The route strip is not given up for larger type: where only one of the two fits, it
        // is the strip.
        val low = fit(1408, 720, sidePanel = false, parked = true)
        assertTrue(low.showRoute)
        assertTrue(low.compact)

    }

    @Test
    fun theTilesHaveTheFullSizeCardWhereTheirReasonsAndPicturesFitInItsType() {
        fun tiles(width: Int, height: Int, parked: Boolean = false) = fit(width, height, sidePanel = false, parked = parked, offers = true)

        // The full-size card around the tiles is 435; a tile with three lines of reason at 22sp,
        // a name of two lines at 28sp and a picture of 160 is 376: a card of 811, in a window 104
        // higher. One short of that, the compact card with its picture at 200.
        assertEquals(
            expected(compact = true, captionLines = 2, showArt = false, tiles = TileStyle.FULL, tileReasonLines = 3, tileIconDp = 200f, showHint = true),
            tiles(1400, 914)
        )
        assertEquals(
            expected(compact = false, captionLines = 3, showArt = false, tiles = TileStyle.FULL, tileReasonLines = 3, tileIconDp = 160f, showHint = true),
            tiles(1400, 915)
        )
        // From there on every dp goes to the picture, up to 320.
        assertEquals(161f, tiles(1400, 916).tileIconDp, 0f)
        assertEquals(319f, tiles(1400, 1074).tileIconDp, 0f)
        assertEquals(320f, tiles(1400, 1075).tileIconDp, 0f)
        assertEquals(320f, tiles(1400, 1500).tileIconDp, 0f)
        assertEquals("A reason never needs a fourth line", 3, tiles(1400, 1500).tileReasonLines)

        // Each tile's text keeps 340 for the reason: with the tiles' padding, the gaps between
        // them and the card's padding, a card of 1196, in a window 48 wider.
        assertTrue(tiles(1243, 1200).compact)
        assertFalse(tiles(1244, 1200).compact)

        // Parked, the route strip is not given up for larger type here either: the buttons and
        // the strip take 188 first.
        val low = tiles(1700, 1102, parked = true)
        assertTrue(low.showRoute)
        assertTrue(low.compact)
        val high = tiles(1700, 1103, parked = true)
        assertTrue(high.showRoute)
        assertFalse(high.compact)
        assertEquals(160f, high.tileIconDp, 0f)
    }

    @Test
    fun thePictureShowsOnlyWhereItCostsTheTextNothing() {
        // The compact card (here because the window is low) keeps 572 for its text, as the
        // narrowest card with two caption lines has: 858 inside the gap, 930 with the padding.
        assertFalse(fit(977, 500, sidePanel = false).showArt)
        assertTrue(fit(978, 500, sidePanel = false).showArt)

        // The full-size card keeps 560, as the narrowest full-size card has: a card of 944.
        assertFalse(fit(991, 800, sidePanel = false).showArt)
        assertTrue(fit(992, 800, sidePanel = false).showArt)
        assertFalse("It stays the full-size card without the picture: the type is not made smaller to fit one", fit(991, 800, sidePanel = false).compact)
        assertFalse(fit(1395, 800).showArt)
        assertTrue(fit(1396, 800).showArt)

        // Parked, the scoreboard and its gap come off the card's width first.
        assertFalse(fit(1253, 800, sidePanel = false, parked = true).showArt)
        assertTrue(fit(1254, 800, sidePanel = false, parked = true).showArt)
    }

    @Test
    fun aCardTooLowForAPictureHasNone() {
        // 160 inside the compact card's padding: a card of 208, in a window 104 higher.
        assertFalse(fit(1400, 311, sidePanel = false).showArt)
        assertTrue(fit(1400, 312, sidePanel = false).showArt)
    }

    @Test
    fun thePictureTakesAThirdOfTheCardWhenItShows() {
        assertEquals(1f / 3f, fit(1408, 700).artShare, 0f)
        assertEquals(ScreenFit.ART_SHARE, fit(1408, 700).artShare, 0f)
        assertEquals("No picture, no share: the text column has the whole card", 0f, fit(640, 950).artShare, 0f)
    }

    @Test
    fun thePicturePanelIsNeverMuchHigherThanItIsWide() {
        // The Pleos emulator's card is nearly square: the panel is 375 wide with 1028 of height to take.
        assertEquals(468.75f, ScreenFit.artHeight(375f, 1028f), 0f)
        // On a landscape card it is as high as the inside of the card.
        assertEquals(518f, ScreenFit.artHeight(416f, 518f), 0f)
        assertEquals(160f, ScreenFit.artHeight(300f, 160f), 0f)
    }

    @Test
    fun onAWideAndLowDisplayTheRecommendationCardIsTheCompactCardWithoutAPicture() {
        // The tiles need the height that the full-size card spends on its type, and the width
        // that the picture would take.
        val driving = fit(1408, 700)
        assertFalse(driving.compact)
        assertTrue(driving.showArt)

        val offers = fit(1408, 700, offers = true)
        assertEquals(
            expected(compact = true, captionLines = 2, showArt = false, tiles = TileStyle.FULL, tileReasonLines = 3, tileIconDp = 106f, showHint = true),
            offers
        )
    }

    @Test
    fun tilesShowIconsAndReasonsOnlyWithRoomForTwoLinesOfReason() {
        // The compact card around the tiles is 307; a tile with the smallest icon, a name of two
        // lines and two lines of reason is 212: a card of 519, in a window 104 higher.
        assertEquals(expected(compact = true, captionLines = 2, showArt = false, showHint = true), fit(1400, 622, sidePanel = false, offers = true))
        assertEquals(
            expected(compact = true, captionLines = 2, showArt = false, tiles = TileStyle.FULL, tileReasonLines = 2, tileIconDp = 56f, showHint = true),
            fit(1400, 623, sidePanel = false, offers = true)
        )
    }

    @Test
    fun spareHeightGoesFirstToAThirdLineOfReasonAndThenToTheIcons() {
        fun tiles(height: Int) = fit(1400, height, sidePanel = false, offers = true)

        // 26 short of the third line (27): the icon has them.
        assertEquals(2, tiles(649).tileReasonLines)
        assertEquals(82f, tiles(649).tileIconDp, 0f)
        // The third line, and the icon is back to its smallest.
        assertEquals(3, tiles(650).tileReasonLines)
        assertEquals(56f, tiles(650).tileIconDp, 0f)
        // From there on every dp goes to the icon, up to 200.
        assertEquals(108f, tiles(702).tileIconDp, 0f)
        assertEquals(199f, tiles(793).tileIconDp, 0f)
        assertEquals(200f, tiles(794).tileIconDp, 0f)
        assertEquals(200f, tiles(900).tileIconDp, 0f)
        assertEquals("A reason never needs a fourth line", 3, tiles(900).tileReasonLines)
    }

    @Test
    fun aSingleSpotsTileLiesOnItsSide() {
        // The tip about one place at the destination: one tile with the whole row to itself.
        val full = fit(1682, 1210, offers = true)
        assertEquals(TileStyle.FULL, full.tiles)
        assertTrue(full.tileOnItsSide(1))
        assertFalse("Two or three share the row upright", full.tileOnItsSide(2))
        assertFalse(full.tileOnItsSide(3))

        // A tile that shows its name only has no picture to put beside it.
        val names = fit(640, 950, offers = true)
        assertEquals(TileStyle.NAMES, names.tiles)
        assertFalse(names.tileOnItsSide(1))
    }

    @Test
    fun tilesShowReasonsOnlyWhereTheirWidthHoldsOne() {
        fun tiles(width: Int, height: Int = 1400) = fit(width, height, sidePanel = false, offers = true)
        fun ScreenFit.shows() = tiles to tileReasonLines

        // A reason of thirty-five syllables at 18sp is 630 long. Three lines hold it in a tile
        // whose text is 210 wide: with the tiles' padding, the gaps between them and the card's
        // padding, a card of 774, in a window 48 wider. However high the card is.
        assertEquals(35, ScreenFit.REASON_SYLLABLES)
        assertEquals(TileStyle.NAMES to 0, tiles(821).shows())
        assertEquals(TileStyle.FULL to 3, tiles(822).shows())
        assertEquals("A narrow card's tiles hold nine syllables to the line", TileStyle.NAMES, tiles(640).tiles)

        // Two lines hold it only in a tile whose text is 315 wide, on a card of 1089. So where
        // the card has the height for two lines of reason and not for three (519 to 545), a
        // narrower card than that shows names only.
        assertEquals(TileStyle.NAMES to 0, tiles(1136, 649).shows())
        assertEquals(TileStyle.FULL to 2, tiles(1137, 649).shows())
        assertEquals("With the height for the third line it has them", TileStyle.FULL to 3, tiles(1136, 650).shows())
    }

    @Test
    fun onTheStockAutomotiveWindowTilesShowReasonsOnlyWhereTheyFit() {
        // About 1408 by 650 is what the stock Automotive emulator's system bars leave an app.
        // Parked beside the demo controls and the scoreboard, the card is 680 wide and a tile's
        // text 178: ten syllables to the line. Names only, with the caption and the hint.
        val names = expected(compact = true, captionLines = 2, showArt = false, showHint = true, scoresBeside = true)
        assertEquals(names, fit(1408, 650, parked = true, offers = true))
        assertEquals("And not for want of height: at 700 two lines of reason would fit", names, fit(1408, 700, parked = true, offers = true))
        assertEquals(TileStyle.NAMES, fit(1408, 1200, parked = true, offers = true).tiles)

        // Without the demo controls (the car build) the card beside the scoreboard is 1084 and a
        // tile's text 313: a reason takes three lines, and the card with them 546, which the
        // window has above the buttons from 726 up.
        assertEquals(names, fit(1408, 650, sidePanel = false, parked = true, offers = true))
        assertEquals(names, fit(1408, 725, sidePanel = false, parked = true, offers = true))
        assertEquals(
            expected(
                compact = true, captionLines = 2, showArt = false, tiles = TileStyle.FULL, tileReasonLines = 3, tileIconDp = 56f,
                showHint = true, scoresBeside = true
            ),
            fit(1408, 726, sidePanel = false, parked = true, offers = true)
        )

        // While the car moves the card has the whole 1360: tiles with everything, at 650 too.
        assertEquals(
            expected(compact = true, captionLines = 2, showArt = false, tiles = TileStyle.FULL, tileReasonLines = 3, tileIconDp = 56f, showHint = true),
            fit(1408, 650, sidePanel = false, offers = true)
        )
    }

    @Test
    fun aNamesOnlyTileIsItsBadgeAboveANameOfTwoLines() {
        // The badge (32) stands above the name, which so has the tile's whole width: 24 of
        // padding, the badge, 8 and two lines of 22sp (64) are 128. The compact card around the
        // tiles is 307 with its hint and two caption lines: a card of 435, in a window 104 higher.
        fun tiles(height: Int) = fit(800, height, sidePanel = false, offers = true)
        assertEquals(expected(compact = true, captionLines = 2, showArt = false, showHint = true), tiles(539))
        assertEquals("One short of the tile's 128, a caption line goes", 1, tiles(538).captionLines)
        assertEquals(8f, ScreenFit.NAMES_BADGE_GAP_DP, 0f)
        assertEquals("A tile is a touch target while parked, and this one is well above the least", 76f, ScreenFit.TILE_MIN_DP, 0f)
    }

    @Test
    fun inALowCardTheCaptionGivesWayLineByLine() {
        // The compact card without a caption is 255: 48 of padding, the chip (44), 8, the title
        // (50), 14 and the bottom row (91). The caption's gap and first line are 42 more, each
        // further line 32. All of it under the status row, inside the padding: 104.
        fun lines(width: Int, vararg heights: Int) = heights.map { fit(width, it, sidePanel = false).captionLines }
        assertEquals(listOf(2, 2, 1, 1, 0, 0), lines(1400, 500, 433, 432, 401, 400, 359))
        // A narrow card has a title of two lines (305) and up to four caption lines.
        assertEquals(listOf(4, 3, 3, 2, 2, 1, 1, 0), lines(640, 547, 546, 515, 514, 483, 482, 451, 450))

        // Parked, the buttons come off first (76), and the route strip is not there to give way.
        assertEquals(2, fit(1400, 509, sidePanel = false, parked = true).captionLines)
        assertEquals(1, fit(1400, 508, sidePanel = false, parked = true).captionLines)
        assertEquals(0, fit(1400, 476, sidePanel = false, parked = true).captionLines)

        // The full-size card is only chosen with room for its three lines.
        assertEquals(3, fit(1408, 644).captionLines)
    }

    @Test
    fun afterTheCaptionTheHintGivesWay() {
        // The card with names-only tiles is 322 with neither: 48 of padding, the chip (44), 12,
        // the tiles (128), 14 and the bottom row (76). The hint is 39 more, then the caption 42.
        fun tiles(height: Int) = fit(800, height, sidePanel = false, offers = true).let { it.captionLines to it.showHint }
        assertEquals(1 to true, tiles(507))
        assertEquals(0 to true, tiles(506))
        assertEquals(0 to true, tiles(465))
        assertEquals(0 to false, tiles(464))
        assertEquals(0 to false, tiles(426))

        assertFalse("Without tiles there is no hint to show", fit(1682, 1210).showHint)
    }

    @Test
    fun theLeastWindowIsTheOneThatHoldsTheChipTheTitleOrTheTilesAndTheBottomRow() {
        fun least(width: Int, sidePanel: Boolean = false, parked: Boolean = false, offers: Boolean = false) =
            ScreenFit.leastHeightDp(width.toFloat(), sidePanel, parked, offers)

        // The card: 255, 305 when narrow, 322 with tiles (see the two tests above). The window
        // is 104 higher.
        assertEquals(359f, least(1400), 0f)
        assertEquals(409f, least(640), 0f)
        assertEquals(426f, least(1400, offers = true), 0f)
        assertEquals("The side panel beside the card changes nothing", 359f, least(1400, sidePanel = true), 0f)
        // Parked, the buttons take 76, and the line of scores 54 where they are not beside the card.
        assertEquals(435f, least(1400, parked = true), 0f)
        assertEquals(502f, least(1400, parked = true, offers = true), 0f)
        assertEquals(489f, least(900, parked = true), 0f)
        assertEquals(539f, least(640, parked = true), 0f)
        assertEquals(556f, least(640, parked = true, offers = true), 0f)
        // Stacked above the side panel the column has six tenths of the height, less 72.
        assertEquals(673.67f, least(640, sidePanel = true), 0.01f)
        assertEquals(890.33f, least(640, sidePanel = true, parked = true), 0.01f)
        assertEquals(918.67f, least(640, sidePanel = true, parked = true, offers = true), 0.01f)

        // The two heights ScreenFit's comment gives as safe whatever is showing cover all of these.
        assertEquals(560f, ScreenFit.SAFE_HEIGHT_DP, 0f)
        assertEquals(920f, ScreenFit.SAFE_STACKED_HEIGHT_DP, 0f)
    }

    @Test
    fun aStackedCardHasSixTenthsOfTheHeightAboveThePanel() {
        // 900 wide with the side panel: beside it the card would be 448, so it is stacked above
        // it and has the whole 852, which is not narrow.
        assertEquals(expected(stacked = true, compact = true, captionLines = 2, showArt = false), fit(900, 950))

        // Of the height, the window's padding and the 24 between the column and the panel come
        // off first (72), the column has 0.6 of the rest, and the status row and the 24 under it
        // come off that (56). The compact card with two caption lines is 329: (714 - 72) × 0.6 - 56.
        assertEquals(1, fit(900, 713).captionLines)
        assertEquals(2, fit(900, 714).captionLines)

        // A tile's text is 236 there: three lines of reason, and a card of 546, at 1076.
        assertEquals(expected(stacked = true, compact = true, captionLines = 2, showArt = false, showHint = true), fit(900, 1075, offers = true))
        val full = fit(900, 1076, offers = true)
        assertEquals(TileStyle.FULL to 3, full.tiles to full.tileReasonLines)
        assertEquals(56.4f, full.tileIconDp, 0.01f)
        assertTrue(full.stacked)

        // Parked there, the scores are a line under the card: 130 more come off.
        val parked = fit(900, 950, parked = true)
        assertFalse(parked.scoresBeside)
        assertEquals("(950 - 72) × 0.6 - 56 - 130 is 341: the card with both caption lines is 329", 2, parked.captionLines)
        assertEquals("And at 931 it is 329.4", 2, fit(900, 931, parked = true).captionLines)
        assertEquals(1, fit(900, 930, parked = true).captionLines)
    }

    @Test
    fun aFitAskedForWithoutOffersHasNoTilesToLayOut() {
        val driving = fit(1682, 1210)
        assertEquals(TileStyle.NAMES, driving.tiles)
        assertEquals(0, driving.tileReasonLines)
        assertEquals(0f, driving.tileIconDp, 0f)
    }

    @Test
    fun theScoreboardGoesBesideTheCardOnlyIfTheCardStaysWideEnough() {
        // The card keeps 620, the scoreboard is 260 and the gap 16: a column of 896, in a window
        // 48 wider without the side panel and 452 wider beside it.
        assertFalse(fit(943, 800, sidePanel = false, parked = true).scoresBeside)
        assertTrue(fit(944, 800, sidePanel = false, parked = true).scoresBeside)
        assertFalse(fit(1347, 800, parked = true).scoresBeside)
        assertTrue(fit(1348, 800, parked = true).scoresBeside)

        assertFalse("Only the parked screen has a scoreboard", fit(1682, 1210).scoresBeside)
    }

    @Test
    fun theRouteStripShowsWhenTheCardStillFitsAboveIt() {
        // The compact card (329), the route strip (96) and the buttons (60) with 16 above each,
        // under the status row and its 24 (56) and inside the window's 48 of padding: 621.
        assertFalse(fit(1000, 620, sidePanel = false, parked = true).showRoute)
        assertTrue(fit(1000, 621, sidePanel = false, parked = true).showRoute)

        assertFalse("Only the parked screen has a route strip", fit(1682, 1210).showRoute)
    }

    @Test
    fun aLineOfScoresUnderTheCardLeavesLessRoomForTheRoute() {
        // At this height the card and the route strip fit above the buttons when the scores are
        // beside the card...
        assertTrue(fit(1000, 640, sidePanel = false, parked = true).showRoute)
        // ...but under a card too narrow for that, the scores take a line of 38 and its gap of 16.
        val line = fit(900, 640, sidePanel = false, parked = true)
        assertFalse(line.scoresBeside)
        assertFalse(line.showRoute)
        assertFalse(fit(900, 674, sidePanel = false, parked = true).showRoute)
        assertTrue("54 dp higher than with the scores beside the card", fit(900, 675, sidePanel = false, parked = true).showRoute)
    }

    @Test
    fun aNarrowCardNeedsMoreHeightBeforeTheRouteShows() {
        // Two more caption lines (64) and a second title line (50) than the card of 329.
        assertFalse(fit(640, 788, sidePanel = false, parked = true).showRoute)
        assertTrue(fit(640, 789, sidePanel = false, parked = true).showRoute)
    }

    @Test
    fun theRouteStripGivesWayToTilesThatShowTheirReasons() {
        fun parked(width: Int, height: Int, offers: Boolean = true) = fit(width, height, sidePanel = false, parked = true, offers = offers)

        // Beside the scoreboard the card is 1076 wide and a tile's text 310: a reason takes three
        // lines, and the card with such tiles is 546 (307 and a tile of 239). The route shows
        // when that fits above it, 217 higher than the plain card needs: until then the height
        // the route would have had goes to the tiles.
        assertTrue(parked(1400, 621, offers = false).showRoute)
        val without = parked(1400, 837)
        assertFalse(without.showRoute)
        assertEquals(TileStyle.FULL to 3, without.tiles to without.tileReasonLines)
        assertEquals(167f, without.tileIconDp, 0f)
        assertEquals(
            expected(
                compact = true, captionLines = 2, showArt = false, tiles = TileStyle.FULL, tileReasonLines = 3, tileIconDp = 56f,
                showHint = true, scoresBeside = true, showRoute = true
            ),
            parked(1400, 838)
        )

        // A card too narrow for reasons (752 here, with the scores on a line under it) has
        // names-only tiles whatever its height, so the route waits only for those: a card of
        // 435, not of 546.
        assertFalse(parked(800, 780).showRoute)
        assertEquals(
            expected(compact = true, captionLines = 2, showArt = false, showHint = true, showRoute = true),
            parked(800, 781)
        )

        // A narrow card's tiles, with its four caption lines, need 56 more than its plain card.
        assertTrue(parked(640, 789, offers = false).showRoute)
        assertFalse(parked(640, 844).showRoute)
        assertTrue(parked(640, 845).showRoute)
    }

    @Test
    fun theParkedScreenAsksForAStoryAQuizOrSomewhereToEat() {
        // A shortcut's request is handled exactly as if it had been spoken, so it has to be heard
        // as that request. The car build has no other way to ask for food: its microphone only
        // opens for the answer to a question.
        val shortcuts = ScreenFit.PARKED_SHORTCUTS
        assertEquals(listOf("이야기 듣기", "퀴즈 하기", "먹거리 보기"), shortcuts.map { it.label })
        assertEquals(listOf(Intent.STORY, Intent.QUIZ, Intent.FOOD), shortcuts.map { Understanding.commandOf(it.request) })
    }

    @Test
    fun everyShortcutLabelFitsAThirdOfTheNarrowestCard() {
        // The buttons share one row in equal parts whatever the window. The narrowest card beside
        // the side panel is 480: the gaps between the buttons off, a third each, and 8 of padding
        // each side of a label at 20sp.
        val third = (480f - (ScreenFit.PARKED_SHORTCUTS.size - 1) * ScreenFit.SHORTCUT_GAP_DP) / ScreenFit.PARKED_SHORTCUTS.size
        for (shortcut in ScreenFit.PARKED_SHORTCUTS) {
            assertTrue(shortcut.label, RouteStripPlan.textWidth(shortcut.label, 20f) <= third - 2 * 8f)
        }
    }

    @Test
    fun neverPromisesWhatTheWindowCannotHold() {
        // Whatever the window and whatever is showing: the full-size card, the picture, the full
        // tiles, the scoreboard beside the card and the route strip are only chosen with room to
        // spare, and the card the fit describes, added up again here from the list in ScreenFit's
        // comment, is no higher than the room the window leaves it, down to the least window.
        for (width in 300..2600 step 20) {
            for (height in 250..1600 step 20) {
                for (flags in 0 until 8) {
                    val sidePanel = flags and 1 != 0
                    val parked = flags and 2 != 0
                    val offers = flags and 4 != 0
                    val f = ScreenFit.of(width.toFloat(), height.toFloat(), sidePanel, parked, offers)
                    val label = "$width x $height, sidePanel=$sidePanel, parked=$parked, offers=$offers: $f"

                    val planned = if (!f.compact) 3 else if (f.narrow) 4 else 2
                    assertTrue(label, f.captionLines in 0..planned)
                    if (!f.compact) {
                        assertFalse(label, f.stacked)
                        // Parked, the full-size card still has its 540 by 640 after the buttons,
                        // the route strip and the scoreboard have taken theirs; with the tiles
                        // on it, the 811 by 1196 that they need in its type.
                        val below = (if (parked) 76 else 0) + (if (parked && !f.scoresBeside) 54 else 0) + (if (f.showRoute) 112 else 0)
                        val beside = (if (sidePanel) 404 else 0) + (if (f.scoresBeside) 276 else 0)
                        assertTrue(label, height >= (if (offers) 811 else 540) + 24 + 32 + 48 + below)
                        assertTrue(label, width >= (if (offers) 1196 else 640) + 48 + beside)
                        if (offers) assertEquals("Full tiles with the whole reason on the full-size card: $label", TileStyle.FULL to 3, f.tiles to f.tileReasonLines)
                        assertEquals("The full-size card keeps all its caption: $label", 3, f.captionLines)
                    }
                    if (f.stacked) assertTrue(label, sidePanel)

                    if (f.showArt) assertFalse("A picture only on a card with width to spare, never beside tiles: $label", offers || f.narrow || f.stacked)
                    assertEquals(label, if (f.showArt) ScreenFit.ART_SHARE else 0f, f.artShare, 0f)

                    if (f.scoresBeside) assertTrue("The scoreboard never makes the card narrow: $label", parked && !f.narrow)
                    if (f.showRoute) assertTrue(label, parked)
                    if (f.showHint) assertTrue("A hint only under tiles: $label", offers)

                    if (f.tiles == TileStyle.FULL) {
                        assertTrue(label, offers && !f.narrow)
                        assertTrue(label, f.tileReasonLines in 2..3)
                        val icons = if (f.compact) ScreenFit.TILE_ICON_DP..ScreenFit.TILE_ICON_MAX_DP else ScreenFit.ROOMY_TILE_ICON_DP..ScreenFit.ROOMY_TILE_ICON_MAX_DP
                        assertTrue(label, f.tileIconDp in icons)
                        // The lines of the reason, at the width of a tile's text, hold thirty-five syllables.
                        val cardWidth = width - 48 - (if (sidePanel && !f.stacked) 404 else 0) - (if (f.scoresBeside) 276 else 0)
                        val text = (cardWidth - (if (f.compact) 48 else 80) - 24) / 3f - 24f
                        assertTrue("These tiles cut their reasons: $label", f.tileReasonLines * text >= 35 * (if (f.compact) 18f else 22f))
                        assertTrue("Full tiles keep the caption and the hint: $label", f.captionLines == planned && f.showHint)
                    } else {
                        assertEquals(label, 0, f.tileReasonLines)
                        assertEquals(label, 0f, f.tileIconDp, 0f)
                    }

                    // The room the window leaves the card. A stacked column has its share of the
                    // height the padding and the gap above the panel leave.
                    var room = (if (f.stacked) (height - 48f - 24f) * ScreenFit.STACKED_SHARE else height - 48f) - 56f
                    if (parked) room -= 16f + 60f
                    if (parked && !f.scoresBeside) room -= 16f + 38f
                    if (f.showRoute) room -= 16f + 96f

                    // The card with a caption of so many lines, with or without the hint.
                    fun card(captionLines: Int, hint: Boolean): Float {
                        val caption = when {
                            captionLines == 0 -> 0f
                            f.compact -> 10f + 32f * captionLines
                            else -> 20f + 41f * captionLines
                        }
                        if (!offers) {
                            return if (f.compact) 48f + 44f + 8f + (if (f.narrow) 100f else 50f) + caption + 14f + 91f else 80f + 44f + 16f + 134f + caption + 32f + 91f
                        }
                        val tile = when {
                            f.tiles == TileStyle.NAMES -> 24f + 32f + 8f + 64f
                            f.compact -> 24f + f.tileIconDp + 8f + 64f + 6f + 27f * f.tileReasonLines
                            else -> 24f + f.tileIconDp + 8f + 82f + 6f + 32f * f.tileReasonLines
                        }
                        return if (f.compact) {
                            48f + 44f + 12f + tile + (if (hint) 12f + 27f else 0f) + caption + 14f + 76f
                        } else {
                            80f + 44f + 16f + tile + (if (hint) 12f + 32f else 0f) + caption + 32f + 76f
                        }
                    }

                    // A stacked room is not a whole number: allow for the last digit.
                    val slack = 0.01f
                    val least = ScreenFit.leastHeightDp(width.toFloat(), sidePanel, parked, offers)
                    if (height >= least) {
                        assertTrue("The card is higher than its room ($room): $label", card(f.captionLines, f.showHint) <= room + slack)
                    } else {
                        assertTrue("Under the least window ($least) even the bare card should not fit in $room: $label", card(0, false) > room - slack)
                        assertEquals("Under the least window nothing is left to give way: $label", 0 to false, f.captionLines to f.showHint)
                    }
                    assertTrue(label, least <= if (f.stacked) ScreenFit.SAFE_STACKED_HEIGHT_DP else ScreenFit.SAFE_HEIGHT_DP)
                    // And nothing gives way that the card had room for.
                    if (f.captionLines < planned) {
                        assertTrue("A caption line was given up for nothing: $label", card(f.captionLines + 1, true) > room - slack)
                    }
                    if (offers && !f.showHint) {
                        assertEquals("The hint goes after the caption: $label", 0, f.captionLines)
                        assertTrue("The hint was given up for nothing: $label", card(0, true) > room - slack)
                    }

                    if (f.showRoute) {
                        val needs = when {
                            !f.compact -> if (offers) 811f else 540f
                            !offers -> if (f.narrow) 443f else 329f
                            f.tiles == TileStyle.NAMES -> if (f.narrow) 371f + 128f else 307f + 128f
                            else -> 307f + 212f
                        }
                        assertTrue("The route strip took the card's room: $label", needs <= room + slack)
                    }
                    if (f.showArt) assertTrue("A picture in a slot under 160 high: $label", room - (if (f.compact) 48f else 80f) >= 160f)
                }
            }
        }
    }
}
