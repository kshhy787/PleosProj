package app.gildongmu.host.ui

import app.gildongmu.core.LightCue
import app.gildongmu.core.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class PaletteTest {

    private fun red(color: Int) = (color ushr 16) and 0xFF
    private fun green(color: Int) = (color ushr 8) and 0xFF
    private fun blue(color: Int) = color and 0xFF

    /** Relative luminance, as WCAG 2 defines it. */
    private fun luminance(color: Int): Double {
        fun linear(channel: Int): Double {
            val value = channel / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(red(color)) + 0.7152 * linear(green(color)) + 0.0722 * linear(blue(color))
    }

    /** The WCAG contrast ratio of two opaque colours: 1 for the same colour, 21 for black on white. */
    private fun contrast(one: Int, other: Int): Double {
        val lighter = maxOf(luminance(one), luminance(other))
        val darker = minOf(luminance(one), luminance(other))
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun theContrastFormulaIsTheWcagOne() {
        // Guards the other tests: with a wrong formula they would pass or fail for no reason.
        assertEquals(21.0, contrast(0xFFFFFFFF.toInt(), 0xFF000000.toInt()), 0.001)
        assertEquals(1.0, contrast(Palette.SURFACE, Palette.SURFACE), 0.001)
        // #777777 on white is the well-known "just under 4.5" grey.
        assertEquals(4.48, contrast(0xFF777777.toInt(), 0xFFFFFFFF.toInt()), 0.005)
    }

    @Test
    fun everyToneReadsAsTextOnTheCard() {
        for (tone in Tone.values()) {
            val ratio = contrast(Palette.tone(tone), Palette.SURFACE)
            assertTrue("$tone on the card is $ratio to 1", ratio >= 4.5)
        }
    }

    @Test
    fun everyToneReadsAsTextOnItsOwnFaintFill() {
        // The chip: tone-coloured text on the tone's faint fill, which is lighter than the card.
        for (tone in Tone.values()) {
            val ratio = contrast(Palette.tone(tone), Palette.faint(tone))
            assertTrue("$tone on its chip is $ratio to 1", ratio >= 4.5)
        }
    }

    @Test
    fun eachToneIsTheColourItsContentIsKnownBy() {
        assertEquals("Stories keep the blue the app already had", Palette.ACCENT, Palette.tone(Tone.STORY))

        val story = Palette.tone(Tone.STORY)
        assertTrue("blue", blue(story) > green(story) && green(story) > red(story))

        val food = Palette.tone(Tone.FOOD)
        assertTrue("orange", red(food) > 240 && green(food) in 110..170 && blue(food) < 100)

        val game = Palette.tone(Tone.GAME)
        assertTrue("green", green(game) > red(game) + 60 && green(game) > blue(game) + 60)

        val stop = Palette.tone(Tone.STOP)
        assertTrue("yellow", red(stop) > 240 && green(stop) > 190 && blue(stop) < 100)
        assertTrue("Yellow and orange are neighbours: keep them well apart", green(stop) - green(food) >= 60)

        val notice = Palette.tone(Tone.NOTICE)
        val channels = listOf(red(notice), green(notice), blue(notice))
        assertTrue("grey, so no brighter in one channel than another by much", channels.max() - channels.min() <= 40)
        assertTrue("and leaning blue like the rest of the app's greys", blue(notice) > green(notice) && green(notice) > red(notice))
    }

    @Test
    fun noTwoTonesShareAColour() {
        assertEquals(Tone.values().size, Tone.values().map { Palette.tone(it) }.toSet().size)
    }

    @Test
    fun aLightCueShowsOnTheWholeCard() {
        assertEquals("Green means right", Palette.GOOD, Palette.cue(LightCue.CORRECT_ANSWER))
        assertEquals(Palette.WARM, Palette.cue(LightCue.ARRIVAL))
        // The border cannot carry a right answer: the quiz card's border is nearly the same green.
        assertTrue(contrast(Palette.GOOD, Palette.GAME) < 1.2)

        // So this checks what is painted while a cue shows, the card's fill: it has to be seen
        // to change from the card at rest, and the text on the card has to stay readable on it.
        for (cue in LightCue.values()) {
            val fill = Palette.flash(cue)
            assertEquals("$cue", Palette.over(Palette.cue(cue), Palette.FLASH_ALPHA, Palette.SURFACE), fill)
            assertEquals("Opaque: it replaces the card's own fill", 0xFF, fill ushr 24)

            val seen = contrast(fill, Palette.SURFACE)
            assertTrue("$cue against the card at rest is $seen to 1", seen >= 1.4)
            val shift = listOf(this::red, this::green, this::blue).maxOf { channel -> Math.abs(channel(fill) - channel(Palette.SURFACE)) }
            assertTrue("$cue moves its strongest channel by $shift of 255", shift >= 30)

            assertTrue("$cue: the title on it", contrast(Palette.TEXT, fill) >= 7.0)
            assertTrue("$cue: the caption on it", contrast(Palette.TEXT_SECONDARY, fill) >= 4.5)
        }
    }

    @Test
    fun theFaintFillIsTheToneAtLowAlphaOverTheSurface() {
        for (tone in Tone.values()) {
            val fill = Palette.faint(tone)
            assertEquals(Palette.over(Palette.tone(tone), Palette.FAINT_ALPHA, Palette.SURFACE), fill)
            assertEquals("Opaque: it must not depend on what is under the view", 0xFF, fill ushr 24)
            assertNotEquals("A fill that cannot be told from the card is no fill", Palette.SURFACE, fill)
            // It sits between the card and the tone, much nearer the card.
            for (channel in listOf(this::red, this::green, this::blue)) {
                val under = channel(Palette.SURFACE)
                val top = channel(Palette.tone(tone))
                assertTrue("$tone", channel(fill) in minOf(under, top)..maxOf(under, top))
                assertTrue("$tone", Math.abs(channel(fill) - under) <= Math.abs(channel(fill) - top))
            }
        }
    }

    @Test
    fun layingOneColourOverAnotherMixesEachChannel() {
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        assertEquals(black, Palette.over(white, 0f, black))
        assertEquals(white, Palette.over(white, 1f, black))
        assertEquals(0xFF808080.toInt(), Palette.over(white, 0.5f, black))
        assertEquals(0xFF401020.toInt(), Palette.over(0xFF802040.toInt(), 0.5f, black))
        // Dark over light mixes downwards just the same.
        assertEquals(0xFFBFBFBF.toInt(), Palette.over(black, 0.25f, white))
        // The alpha of what is laid over is the argument, not the colour's own; out of range is clamped.
        assertEquals(white, Palette.over(0x00FFFFFF, 1f, black))
        assertEquals(white, Palette.over(white, 2f, black))
        assertEquals(black, Palette.over(white, -1f, black))
        assertEquals("Over the card unless told otherwise", Palette.SURFACE, Palette.over(white, 0f))
    }
}
