package app.gildongmu.host.ui

import app.gildongmu.core.Illustration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IllustrationsTest {

    /** One painted shape as a picture asked for it: [width] is null for a fill. */
    private class Part(val shade: Shade, val width: Float?) {
        /** Every point named, control points included: a curve never leaves the hull of its points. */
        val points = ArrayList<Pair<Float, Float>>()

        /** The calls in order, for comparing two pictures. */
        val calls = StringBuilder()

        /** A line may not start out of nowhere: Android would start it at the square's corner. */
        var penDown = false
        var strayStart = false
    }

    /** Stands in for the screen: it keeps what a picture draws so that the rules can be checked. */
    private class Tracing : Sketch {
        val parts = ArrayList<Part>()

        override fun fill(shade: Shade, contour: Contour.() -> Unit) = trace(Part(shade, null), contour)

        override fun stroke(shade: Shade, width: Float, contour: Contour.() -> Unit) = trace(Part(shade, width), contour)

        private fun trace(part: Part, contour: Contour.() -> Unit) {
            object : Contour {
                override fun moveTo(x: Float, y: Float) {
                    part.penDown = true
                    add("M", x, y)
                }

                override fun lineTo(x: Float, y: Float) {
                    if (!part.penDown) part.strayStart = true
                    add("L", x, y)
                }

                override fun curveTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float) {
                    if (!part.penDown) part.strayStart = true
                    add("C", c1x, c1y)
                    add("c", c2x, c2y)
                    add("c", x, y)
                }

                override fun close() {
                    part.penDown = false
                    part.calls.append("Z ")
                }

                private fun add(call: String, x: Float, y: Float) {
                    part.points.add(x to y)
                    part.calls.append(call).append(x).append(',').append(y).append(' ')
                }
            }.contour()
            parts.add(part)
        }

        /** The box around everything painted, lines counted at their full width. */
        fun left() = parts.minOf { part -> part.points.minOf { it.first } - (part.width ?: 0f) / 2f }
        fun right() = parts.maxOf { part -> part.points.maxOf { it.first } + (part.width ?: 0f) / 2f }
        fun top() = parts.minOf { part -> part.points.minOf { it.second } - (part.width ?: 0f) / 2f }
        fun bottom() = parts.maxOf { part -> part.points.maxOf { it.second } + (part.width ?: 0f) / 2f }

        fun signature() = parts.joinToString("|") { "${it.shade} ${it.width} ${it.calls}" }
    }

    private fun traced(illustration: Illustration) = Tracing().also { Artwork.draw(illustration, it) }

    /** Every picture the app has, by name: the eleven illustrations and the crown. */
    private fun everything(): Map<String, Tracing> =
        Illustration.values().associate { it.name to traced(it) } + ("CROWN" to Tracing().also(Artwork::crown))

    @Test
    fun everyIllustrationHasAPictureOfItsOwn() {
        val signatures = Illustration.values().associateWith { traced(it).signature() }
        for ((illustration, signature) in signatures) assertTrue("$illustration draws nothing", signature.isNotEmpty())
        assertEquals("Two illustrations draw the same picture", Illustration.values().size, signatures.values.toSet().size)
    }

    @Test
    fun nothingIsPaintedOutsideTheSquareOrAgainstItsEdge() {
        val low = Artwork.MARGIN
        val high = Artwork.SIZE - Artwork.MARGIN
        for ((name, picture) in everything()) {
            assertTrue("$name reaches left to ${picture.left()}", picture.left() >= low)
            assertTrue("$name reaches right to ${picture.right()}", picture.right() <= high)
            assertTrue("$name reaches up to ${picture.top()}", picture.top() >= low)
            assertTrue("$name reaches down to ${picture.bottom()}", picture.bottom() <= high)
        }
    }

    @Test
    fun everyPictureFillsItsSquareAndSitsInTheMiddle() {
        // Side by side on three tiles, or one after another on the card, they should look one size.
        for ((name, picture) in everything()) {
            val width = picture.right() - picture.left()
            val height = picture.bottom() - picture.top()
            assertTrue("$name is only $width × $height", maxOf(width, height) >= 70f)
            assertTrue("$name is a sliver: $width × $height", minOf(width, height) >= 50f)
            assertEquals("$name is off centre sideways", 50f, (picture.left() + picture.right()) / 2f, 3f)
            assertEquals("$name is off centre up or down", 50f, (picture.top() + picture.bottom()) / 2f, 3f)
        }
    }

    @Test
    fun linesComeInTwoWidthsOnly() {
        for ((name, picture) in everything()) {
            for (part in picture.parts) {
                val width = part.width ?: continue
                assertTrue("$name has a line $width wide", width == Artwork.LINE || width == Artwork.THIN)
            }
        }
        assertTrue("The thin line must still show on an 80 dp tile", Artwork.THIN * 80f / Artwork.SIZE >= 2f)
        assertTrue(Artwork.LINE > Artwork.THIN)
    }

    @Test
    fun everyIllustrationHasAFullStrengthLineAndALighterShade() {
        // Full strength is what is seen at a glance; the lighter shades are what make it a
        // picture rather than a pictogram.
        for (illustration in Illustration.values()) {
            val parts = traced(illustration).parts
            assertTrue("$illustration has no main line", parts.any { it.shade == Shade.FULL && it.width == Artwork.LINE })
            assertTrue("$illustration is one flat strength", parts.any { it.shade != Shade.FULL })
        }
    }

    @Test
    fun theCrownIsSolid() {
        // It is drawn at the height of a line of text, where a wash would be lost.
        val parts = Tracing().also(Artwork::crown).parts
        assertTrue(parts.all { it.shade == Shade.FULL })
        assertTrue(parts.any { it.width == null })
    }

    @Test
    fun theShadesAreThreeStrengthsOfOneColour() {
        assertEquals(1f, Shade.FULL.alpha, 0f)
        assertTrue(Shade.SOFT.alpha < Shade.FULL.alpha)
        assertTrue(Shade.FAINT.alpha < Shade.SOFT.alpha)
        assertTrue("A wash too thin to see is not a shade", Shade.FAINT.alpha >= 0.15f)
    }

    @Test
    fun everyShapeStartsWithAMove() {
        for ((name, picture) in everything()) {
            for (part in picture.parts) {
                assertFalse("$name draws a line before saying where from: ${part.calls}", part.strayStart)
                assertTrue("$name has an empty shape", part.points.size >= 2)
            }
        }
    }

    @Test
    fun aPictureIsAsLargeAsFitsAndCentredInAWidePanel() {
        assertEquals(ArtFit(scale = 2f, left = 50f, top = 0f), ArtFit.of(0f, 0f, 300f, 200f))
    }

    @Test
    fun aPictureIsCentredInATallPanelWhereverThePanelIs() {
        // The card's art area: a third of the card's width, the card's whole height.
        assertEquals(ArtFit(scale = 1.5f, left = 40f, top = 145f), ArtFit.of(40f, 20f, 150f, 400f))
    }

    @Test
    fun aPictureIsNeverStretched() {
        // One scale for both directions, so the square stays a square whatever the panel's shape.
        val fit = ArtFit.of(0f, 0f, 480f, 120f)
        assertEquals(120f, fit.scale * Artwork.SIZE, 0.001f)
        assertEquals(180f, fit.left, 0f)
        assertEquals(0f, fit.top, 0f)
    }

    @Test
    fun aPanelWithNoRoomGetsNoPicture() {
        assertEquals(0f, ArtFit.of(0f, 0f, 0f, 200f).scale, 0f)
        assertEquals("Never a negative scale, which would draw the picture mirrored", 0f, ArtFit.of(0f, 0f, -10f, 200f).scale, 0f)
    }
}
