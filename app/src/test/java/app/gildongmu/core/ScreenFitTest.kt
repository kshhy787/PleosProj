package app.gildongmu.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenFitTest {

    private fun fit(width: Int, height: Int, sidePanel: Boolean = true, parked: Boolean = false) =
        ScreenFit.of(width.toFloat(), height.toFloat(), sidePanel, parked)

    @Test
    fun aWideCarDisplayKeepsTheCardBesideThePanelAtFullSize() {
        // The stock Automotive emulator profile, less the system bars.
        val driving = fit(1408, 700)
        assertEquals(ScreenFit(stacked = false, compact = false, narrow = false, captionLines = 3, showRoute = false, shortcutsBelow = false), driving)

        val parked = fit(1408, 700, parked = true)
        assertEquals(ScreenFit(stacked = false, compact = true, narrow = false, captionLines = 2, showRoute = true, shortcutsBelow = false), parked)
    }

    @Test
    fun halfOfASplitScreenPutsTheCardAboveThePanel() {
        // Beside a 380 dp panel the card would be under 200 dp wide: too narrow to read anything.
        val driving = fit(640, 950)
        assertTrue(driving.stacked)
        assertTrue(driving.compact)
        assertTrue(driving.narrow)
        assertEquals("A narrow card needs more lines for the same sentence", 4, driving.captionLines)

        val parked = fit(640, 950, parked = true)
        assertFalse("No room for the route diagram above a stacked panel", parked.showRoute)
    }

    @Test
    fun withoutASidePanelNothingIsStacked() {
        // The car build has no demo controls; the card has the whole width.
        val narrow = fit(640, 950, sidePanel = false)
        assertFalse(narrow.stacked)
        assertTrue("Still the compact card: full-size type would wrap badly at this width", narrow.compact)

        val wide = fit(1408, 700, sidePanel = false)
        assertEquals(ScreenFit(stacked = false, compact = false, narrow = false, captionLines = 3, showRoute = false, shortcutsBelow = false), wide)
    }

    @Test
    fun aShortWindowGetsTheCompactCard() {
        val short = fit(1400, 450)
        assertFalse(short.stacked)
        assertTrue(short.compact)
        assertEquals(2, short.captionLines)
        assertFalse(fit(1400, 450, parked = true).showRoute)
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
    fun theParkedPanelAsksForAStoryAQuizOrSomewhereToEat() {
        // A shortcut's request is handled exactly as if it had been spoken, so it has to be heard
        // as that request. The car build has no other way to ask for food: its microphone only
        // opens for the answer to a question. The row's widths were measured for these labels.
        val shortcuts = ScreenFit.PARKED_SHORTCUTS
        assertEquals(listOf("이야기 듣기", "퀴즈 하기", "먹거리 보기"), shortcuts.map { it.label })
        assertEquals(listOf(Intent.STORY, Intent.QUIZ, Intent.FOOD), shortcuts.map { Understanding.commandOf(it.request) })
    }

    @Test
    fun theShortcutsGoUnderTheScoresWhenTheCardIsTooNarrowForOneRow() {
        // The Pleos emulator's window, with the demo controls (mock build) and without (pleos build).
        assertFalse(fit(1682, 1210, parked = true).shortcutsBelow)
        assertFalse(fit(1682, 1210, sidePanel = false, parked = true).shortcutsBelow)
        // Half of a split screen, and the narrowest card that still sits beside the demo controls.
        assertTrue(fit(640, 950, parked = true).shortcutsBelow)
        assertTrue(fit(932, 800, parked = true).shortcutsBelow)

        // 16 padding each side and 12 before the scores, 250 for two players' scores and 490 for
        // the three buttons with their gaps: a card of 784, in a window 48 wider without the side
        // panel and 452 wider beside it.
        assertTrue(fit(831, 800, sidePanel = false, parked = true).shortcutsBelow)
        assertFalse(fit(832, 800, sidePanel = false, parked = true).shortcutsBelow)
        assertTrue(fit(1235, 800, parked = true).shortcutsBelow)
        assertFalse(fit(1236, 800, parked = true).shortcutsBelow)

        assertFalse("Only the parked panel has shortcuts", fit(640, 950).shortcutsBelow)
    }

    @Test
    fun shortcutsUnderTheScoresLeaveLessRoomForTheRoute() {
        // At this height the compact card and the route diagram just fit above a one-row panel...
        assertTrue(fit(900, 595, sidePanel = false, parked = true).showRoute)
        // ...but a card too narrow for one row has a panel a line of scores taller.
        val twoRows = fit(700, 595, sidePanel = false, parked = true)
        assertTrue(twoRows.shortcutsBelow)
        assertFalse(twoRows.showRoute)
        assertTrue("50 dp higher it fits again", fit(700, 645, sidePanel = false, parked = true).showRoute)
    }

    @Test
    fun neverAsksForTheFullCardWhereItCannotFit() {
        // Whatever the window, the full-size card is only chosen with room to spare, and a
        // caption always gets at least two lines.
        for (width in 300..2600 step 20) {
            for (height in 250..1600 step 20) {
                for (sidePanel in listOf(true, false)) {
                    for (parked in listOf(true, false)) {
                        val f = ScreenFit.of(width.toFloat(), height.toFloat(), sidePanel, parked)
                        val label = "$width x $height, sidePanel=$sidePanel, parked=$parked: $f"
                        assertTrue(label, f.captionLines in 2..4)
                        assertEquals("More caption lines exactly when the compact card is narrow: $label", f.compact && f.narrow, f.captionLines == 4)
                        if (!f.compact) {
                            assertFalse(label, parked || f.stacked)
                            assertTrue(label, height >= 510 + 24 + 30 + 48)
                            assertTrue(label, width >= 640 + 48 + (if (sidePanel) 404 else 0))
                        }
                        if (f.stacked) assertTrue(label, sidePanel)
                        if (f.showRoute) assertTrue(label, parked)
                        if (f.shortcutsBelow) assertTrue(label, parked)
                        if (parked && f.narrow) assertTrue("A narrow card has no room for the scores and the shortcuts side by side: $label", f.shortcutsBelow)
                    }
                }
            }
        }
    }
}
