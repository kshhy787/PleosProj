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
        assertEquals(ScreenFit(stacked = false, compact = false, narrow = false, captionLines = 3, showRoute = false), driving)

        val parked = fit(1408, 700, parked = true)
        assertEquals(ScreenFit(stacked = false, compact = true, narrow = false, captionLines = 2, showRoute = true), parked)
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
        assertEquals(ScreenFit(stacked = false, compact = false, narrow = false, captionLines = 3, showRoute = false), wide)
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
                    }
                }
            }
        }
    }
}
