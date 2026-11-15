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
        scoresBeside: Boolean = false,
        showRoute: Boolean = false
    ) = ScreenFit(stacked, compact, narrow, captionLines, showArt, tiles, tileReasonLines, tileIconDp, scoresBeside, showRoute)

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
        for (height in listOf(1210, 1172)) {
            for (sidePanel in listOf(true, false)) {
                val label = "1682 x $height, sidePanel=$sidePanel"
                assertEquals(label, expected(compact = false, captionLines = 3, showArt = true), fit(1682, height, sidePanel))
                assertEquals(
                    "Spots on offer: the compact card, full tiles with their icons at full height, and no picture. $label",
                    expected(compact = true, captionLines = 2, showArt = false, tiles = TileStyle.FULL, tileReasonLines = 3, tileIconDp = 200f),
                    fit(1682, height, sidePanel, offers = true)
                )
                assertEquals(
                    "Parked: the picture, the scoreboard beside the card and the route under it. $label",
                    expected(compact = true, captionLines = 2, showArt = true, scoresBeside = true, showRoute = true),
                    fit(1682, height, sidePanel, parked = true)
                )
                assertEquals(
                    "Parked with spots on offer: the tiles keep everything and the route stays. $label",
                    expected(
                        compact = true, captionLines = 2, showArt = false, tiles = TileStyle.FULL, tileReasonLines = 3, tileIconDp = 200f,
                        scoresBeside = true, showRoute = true
                    ),
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

        val offers = fit(640, 950, offers = true)
        assertEquals("A third of this card is too narrow for a reason", TileStyle.NAMES, offers.tiles)
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

        assertEquals("No height for icons and reasons", TileStyle.NAMES, fit(1400, 450, offers = true).tiles)
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
        // 32 and the bottom row with the "다음" line (91): 540, under the 54 of the status row and
        // inside the window's 48 of padding.
        assertTrue(fit(1408, 641).compact)
        assertFalse(fit(1408, 642).compact)
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
        // 160 inside the compact card's padding: a card of 208, in a window 102 higher.
        assertFalse(fit(1400, 309, sidePanel = false).showArt)
        assertTrue(fit(1400, 310, sidePanel = false).showArt)
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
    fun theRecommendationCardIsTheCompactCardWithoutAPicture() {
        // The tiles need the height that the full-size card spends on its type, and the width
        // that the picture would take.
        val driving = fit(1408, 700)
        assertFalse(driving.compact)
        assertTrue(driving.showArt)

        val offers = fit(1408, 700, offers = true)
        assertEquals(
            expected(compact = true, captionLines = 2, showArt = false, tiles = TileStyle.FULL, tileReasonLines = 3, tileIconDp = 108f),
            offers
        )
    }

    @Test
    fun tilesShowIconsAndReasonsOnlyWithRoomForTwoLinesOfReason() {
        // The compact card around the tiles is 307; a tile with the smallest icon, a name of two
        // lines and two lines of reason is 212: a card of 519, in a window 102 higher.
        assertEquals(expected(compact = true, captionLines = 2, showArt = false), fit(1400, 620, sidePanel = false, offers = true))
        assertEquals(
            expected(compact = true, captionLines = 2, showArt = false, tiles = TileStyle.FULL, tileReasonLines = 2, tileIconDp = 56f),
            fit(1400, 621, sidePanel = false, offers = true)
        )
    }

    @Test
    fun spareHeightGoesFirstToAThirdLineOfReasonAndThenToTheIcons() {
        fun tiles(height: Int) = fit(1400, height, sidePanel = false, offers = true)

        // 26 short of the third line (27): the icon has them.
        assertEquals(2, tiles(647).tileReasonLines)
        assertEquals(82f, tiles(647).tileIconDp, 0f)
        // The third line, and the icon is back to its smallest.
        assertEquals(3, tiles(648).tileReasonLines)
        assertEquals(56f, tiles(648).tileIconDp, 0f)
        // From there on every dp goes to the icon, up to 200.
        assertEquals(108f, tiles(700).tileIconDp, 0f)
        assertEquals(199f, tiles(791).tileIconDp, 0f)
        assertEquals(200f, tiles(792).tileIconDp, 0f)
        assertEquals(200f, tiles(1200).tileIconDp, 0f)
        assertEquals("A reason never needs a fourth line", 3, tiles(1200).tileReasonLines)
    }

    @Test
    fun aNarrowCardShowsNamesOnlyHoweverHighItIs() {
        // A card under 620 wide: each tile's text is under 160.
        assertEquals(TileStyle.NAMES, fit(667, 1400, sidePanel = false, offers = true).tiles)
        assertEquals(TileStyle.FULL, fit(668, 1400, sidePanel = false, offers = true).tiles)
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
        // under the 54 of the status row and inside the window's 48 of padding: 619.
        assertFalse(fit(1000, 618, sidePanel = false, parked = true).showRoute)
        assertTrue(fit(1000, 619, sidePanel = false, parked = true).showRoute)

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
        assertFalse(fit(900, 672, sidePanel = false, parked = true).showRoute)
        assertTrue("54 dp higher than with the scores beside the card", fit(900, 673, sidePanel = false, parked = true).showRoute)
    }

    @Test
    fun aNarrowCardNeedsMoreHeightBeforeTheRouteShows() {
        // Two more caption lines (64) and a second title line (50) than the card of 329.
        assertFalse(fit(640, 786, sidePanel = false, parked = true).showRoute)
        assertTrue(fit(640, 787, sidePanel = false, parked = true).showRoute)
    }

    @Test
    fun theRouteStripGivesWayToTheTiles() {
        // The stock Automotive window, parked at a rest stop: room for the route under the card...
        assertTrue(fit(1408, 700, parked = true).showRoute)
        // ...but not under the card with the tiles, which keeps its icons and reasons instead.
        assertEquals(
            expected(
                compact = true, captionLines = 2, showArt = false, tiles = TileStyle.FULL, tileReasonLines = 2, tileIconDp = 59f,
                scoresBeside = true, showRoute = false
            ),
            fit(1408, 700, parked = true, offers = true)
        )

        // The route comes back when the card with full tiles (519) fits above it: 190 more than
        // the plain card needs.
        val without = fit(1400, 808, sidePanel = false, parked = true, offers = true)
        assertFalse(without.showRoute)
        assertEquals("The height the route would have had goes to the tiles", 140f, without.tileIconDp, 0f)
        val with = fit(1400, 809, sidePanel = false, parked = true, offers = true)
        assertTrue(with.showRoute)
        assertEquals(TileStyle.FULL, with.tiles)
        assertEquals(2, with.tileReasonLines)
        assertEquals(56f, with.tileIconDp, 0f)

        // A narrow card's tiles show names only, and still need 16 more than its plain card.
        assertTrue(fit(640, 787, sidePanel = false, parked = true).showRoute)
        assertFalse(fit(640, 802, sidePanel = false, parked = true, offers = true).showRoute)
        assertTrue(fit(640, 803, sidePanel = false, parked = true, offers = true).showRoute)
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
        // spare, and a caption always gets at least two lines.
        for (width in 300..2600 step 20) {
            for (height in 250..1600 step 20) {
                for (flags in 0 until 8) {
                    val sidePanel = flags and 1 != 0
                    val parked = flags and 2 != 0
                    val offers = flags and 4 != 0
                    val f = ScreenFit.of(width.toFloat(), height.toFloat(), sidePanel, parked, offers)
                    val label = "$width x $height, sidePanel=$sidePanel, parked=$parked, offers=$offers: $f"

                    assertTrue(label, f.captionLines in 2..4)
                    assertEquals("More caption lines exactly when the compact card is narrow: $label", f.compact && f.narrow, f.captionLines == 4)
                    if (!f.compact) {
                        assertFalse(label, parked || offers || f.stacked)
                        assertTrue(label, height >= 540 + 24 + 30 + 48)
                        assertTrue(label, width >= 640 + 48 + (if (sidePanel) 404 else 0))
                    }
                    if (f.stacked) assertTrue(label, sidePanel)

                    if (f.showArt) assertFalse("A picture only on a card with width to spare, never beside tiles: $label", offers || f.narrow || f.stacked)
                    assertEquals(label, if (f.showArt) ScreenFit.ART_SHARE else 0f, f.artShare, 0f)

                    if (f.scoresBeside) assertTrue("The scoreboard never makes the card narrow: $label", parked && !f.narrow)
                    if (f.showRoute) assertTrue(label, parked)

                    if (f.tiles == TileStyle.FULL) {
                        assertTrue(label, offers && !f.narrow)
                        assertTrue(label, f.tileReasonLines in 2..3)
                        assertTrue(label, f.tileIconDp in ScreenFit.TILE_ICON_DP..ScreenFit.TILE_ICON_MAX_DP)
                    } else {
                        assertEquals(label, 0, f.tileReasonLines)
                        assertEquals(label, 0f, f.tileIconDp, 0f)
                    }

                    // The heights, added up again from the list in ScreenFit's comment. A stacked
                    // card's height is a share of the window's, so this is checked beside the
                    // panel and without one.
                    if (f.stacked) continue
                    var card = height - 48f - 54f
                    if (parked) card -= 16f + 60f
                    if (parked && !f.scoresBeside) card -= 16f + 38f
                    if (f.showRoute) card -= 16f + 96f
                    if (f.tiles == TileStyle.FULL) {
                        val tile = 24f + f.tileIconDp + 8f + 64f + 6f + 27f * f.tileReasonLines
                        assertTrue("The card with these tiles is higher than its room: $label", 307f + tile <= card)
                    }
                    if (f.showRoute) {
                        val needs = when {
                            !offers -> if (f.narrow) 443f else 329f
                            f.narrow -> 371f + 88f
                            else -> 307f + 212f
                        }
                        assertTrue("The route strip took the card's room: $label", needs <= card)
                    }
                    if (f.showArt) assertTrue("A picture in a slot under 160 high: $label", card - (if (f.compact) 48f else 80f) >= 160f)
                }
            }
        }
    }
}
