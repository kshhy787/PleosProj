package app.gildongmu.host.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import app.gildongmu.core.Illustration
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * How strongly a part of a picture is painted. A picture has one colour, its card's tone, and
 * gets its depth from these three strengths of it alone, so it can never clash with the card.
 */
enum class Shade(val alpha: Float) {
    /** Outlines and the few solid parts: what is still there at a glance. */
    FULL(1f),

    /** What is further away or less important: a far hill, steam, a pattern. */
    SOFT(0.55f),

    /** The wash inside an outline. */
    FAINT(0.22f)
}

/** The outline of one shape in a picture's 100×100 square: x runs right, y runs down. */
interface Contour {
    fun moveTo(x: Float, y: Float)
    fun lineTo(x: Float, y: Float)

    /** A cubic curve to ([x], [y]), pulled towards the two control points on the way. */
    fun curveTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float)
    fun close()
}

/**
 * What a picture is drawn on. [Artwork] talks only to this and never to a Canvas: the pictures
 * cannot be looked at from a desk, but a unit test can follow every line of them and check that
 * none leaves the square or uses a line width of its own.
 */
interface Sketch {
    fun fill(shade: Shade, contour: Contour.() -> Unit)
    fun stroke(shade: Shade, width: Float, contour: Contour.() -> Unit)
}

/**
 * Where a picture's design square lands in the bounds it is given: as large as fits, in the
 * middle, never stretched. The art area of a card is rarely square.
 */
data class ArtFit(val scale: Float, val left: Float, val top: Float) {
    companion object {
        fun of(left: Float, top: Float, width: Float, height: Float): ArtFit {
            val side = min(width, height).coerceAtLeast(0f)
            return ArtFit(side / Artwork.SIZE, left + (width - side) / 2f, top + (height - side) / 2f)
        }
    }
}

/**
 * The pictures themselves: flat shapes with round-ended lines, each in a 100×100 square.
 *
 * They are drawn in code, not kept as image files, so that they stay sharp from an 80 dp tile to
 * a 400 dp card, take the colour of whatever card they are on, and add nothing to the app's size.
 * Each is meant to be named by a child at the smallest size: few parts, thick lines, no detail
 * that needs a second look, since the driver gets only a glance.
 */
object Artwork {

    /** The side of the design square. */
    const val SIZE = 100f

    /** The width of every main line: 4 dp on an 80 dp tile, still a line and not a hair. */
    const val LINE = 5f

    /** The width of the lines that only decorate: spokes, rays, a pattern. There is no third width. */
    const val THIN = 3f

    /** Nothing is painted closer than this to the square's edge, so no picture touches its panel's. */
    const val MARGIN = 4f

    /** Draws [illustration] on [sketch]. */
    fun draw(illustration: Illustration, sketch: Sketch) = with(sketch) {
        when (illustration) {
            Illustration.MOUNTAINS -> mountains()
            Illustration.BOWL -> bowl()
            Illustration.LANDMARK -> landmark()
            Illustration.STAR -> star()
            Illustration.CUP -> cup()
            Illustration.ROAD -> road()
            Illustration.SEA -> sea()
            Illustration.FLOWER -> flower()
            Illustration.POTTERY -> pottery()
            Illustration.WHEEL -> wheel()
            Illustration.COFFEE -> coffee()
        }
    }

    /**
     * The crown beside the leader on the scoreboard. It is drawn at the height of a line of text,
     * so it is one solid shape with no inner detail.
     */
    fun crown(sketch: Sketch) = with(sketch) {
        solid(Shade.FULL) { polygon(17f, 80f, 11f, 40f, 32f, 56f, 50f, 28f, 68f, 56f, 89f, 40f, 83f, 80f) }
        fill(Shade.FULL) {
            circle(11f, 36f, 6f)
            circle(50f, 23f, 6f)
            circle(89f, 36f, 6f)
        }
    }

    /** Two peaks, the near one capped with snow, and the sun: a story about a place on the way. */
    private fun Sketch.mountains() {
        fill(Shade.SOFT) { circle(24f, 24f, 9f) }
        // The far peak ends where the near one's slope crosses it. Drawn whole, its outline would
        // show through the near peak's wash.
        fill(Shade.FAINT) { polygon(8f, 84f, 32f, 40f, 43.7f, 61.4f, 32f, 84f) }
        stroke(Shade.SOFT, LINE) { polyline(8f, 84f, 32f, 40f, 43.7f, 61.4f) }
        fill(Shade.FAINT) { polygon(32f, 84f, 62f, 26f, 92f, 84f) }
        fill(Shade.FULL) { polygon(62f, 26f, 71.8f, 45f, 67f, 39f, 62f, 46f, 57f, 39f, 52.2f, 45f) }
        outline { polyline(32f, 84f, 62f, 26f, 92f, 84f) }
        outline { line(7f, 84f, 93f, 84f) }
    }

    /** A heaped bowl with steam rising: something to eat. */
    private fun Sketch.bowl() {
        stroke(Shade.SOFT, LINE) {
            wisp(33f, 34f, 18f)
            wisp(50f, 30f, 10f)
            wisp(67f, 34f, 18f)
        }
        fill(Shade.SOFT) {
            moveTo(23f, 52f)
            curveTo(27f, 38f, 73f, 38f, 77f, 52f)
            close()
        }
        solid(Shade.FAINT) {
            moveTo(11f, 52f)
            lineTo(89f, 52f)
            curveTo(88f, 70f, 74f, 84f, 58f, 84f)
            lineTo(42f, 84f)
            curveTo(26f, 84f, 12f, 70f, 11f, 52f)
            close()
        }
        solid(Shade.FULL) { polygon(41f, 86f, 59f, 86f, 57f, 90.5f, 43f, 90.5f) }
    }

    /** A hall with a tiled roof whose eaves turn up, on a stone base: a sight worth a visit. */
    private fun Sketch.landmark() {
        solid(Shade.FAINT) { polygon(12f, 77f, 88f, 77f, 88f, 86f, 12f, 86f) }
        outline {
            line(24f, 46f, 24f, 77f)
            line(42f, 46f, 42f, 77f)
            line(58f, 46f, 58f, 77f)
            line(76f, 46f, 76f, 77f)
        }
        solid(Shade.SOFT) {
            moveTo(33f, 16f)
            lineTo(67f, 16f)
            curveTo(70f, 28f, 80f, 36f, 93f, 33f)
            lineTo(86f, 45f)
            lineTo(14f, 45f)
            lineTo(7f, 33f)
            curveTo(20f, 36f, 30f, 28f, 33f, 16f)
            close()
        }
    }

    /** A star among sparkles: a quiz or a game. */
    private fun Sketch.star() {
        solid(Shade.FAINT) {
            for (point in 0 until 10) {
                val angle = (-90.0 + 36.0 * point) * PI / 180.0
                val radius = if (point % 2 == 0) 40f else 18f
                val x = 50f + radius * cos(angle).toFloat()
                val y = 53f + radius * sin(angle).toFloat()
                if (point == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        fill(Shade.SOFT) {
            twinkle(16f, 22f, 10f)
            twinkle(86f, 15f, 7f)
            twinkle(85f, 78f, 8f)
        }
    }

    /** A mug on a saucer, steaming: time for a rest. */
    private fun Sketch.cup() {
        stroke(Shade.SOFT, LINE) {
            wisp(36f, 30f, 10f)
            wisp(56f, 30f, 10f)
        }
        outline {
            moveTo(72f, 46f)
            curveTo(92f, 43f, 92f, 67f, 71.3f, 64f)
        }
        solid(Shade.FAINT) {
            moveTo(20f, 39f)
            lineTo(72f, 39f)
            lineTo(72f, 58f)
            curveTo(72f, 72f, 62f, 80f, 50f, 80f)
            lineTo(42f, 80f)
            curveTo(30f, 80f, 20f, 72f, 20f, 58f)
            close()
        }
        outline {
            moveTo(12f, 87f)
            curveTo(28f, 93f, 64f, 93f, 80f, 87f)
        }
    }

    /** A road running to the sun on the horizon: the trip itself. */
    private fun Sketch.road() {
        fill(Shade.SOFT) {
            moveTo(36f, 36f)
            curveTo(36f, 17.3f, 64f, 17.3f, 64f, 36f)
            close()
        }
        stroke(Shade.SOFT, THIN) {
            for (ray in 0 until 5) {
                val angle = (-162.0 + 36.0 * ray) * PI / 180.0
                val along = cos(angle).toFloat()
                val up = sin(angle).toFloat()
                line(50f + 20f * along, 36f + 20f * up, 50f + 27f * along, 36f + 27f * up)
            }
        }
        fill(Shade.FAINT) { polygon(44f, 36f, 56f, 36f, 85f, 92f, 15f, 92f) }
        outline {
            line(44f, 36f, 15f, 92f)
            line(56f, 36f, 85f, 92f)
            line(7f, 36f, 93f, 36f)
            line(50f, 88f, 50f, 76f)
            line(50f, 66f, 50f, 58f)
        }
        stroke(Shade.FULL, THIN) { line(50f, 50f, 50f, 45f) }
    }

    /** A sailing boat on the waves under the sun: the sea, or a lake. */
    private fun Sketch.sea() {
        fill(Shade.SOFT) { circle(74f, 20f, 10f) }
        solid(Shade.FAINT) { polygon(40f, 14f, 40f, 42f, 60f, 42f) }
        solid(Shade.FULL) { polygon(22f, 48f, 62f, 48f, 55f, 56f, 29f, 56f) }
        outline { wave(8f, 65f, 3) }
        stroke(Shade.SOFT, LINE) { wave(8f, 75.5f, 3) }
        stroke(Shade.FAINT, LINE) { wave(8f, 86f, 3) }
    }

    /** A flower on its stem: a field in bloom. */
    private fun Sketch.flower() {
        outline {
            moveTo(50f, 54f)
            curveTo(51f, 68f, 49f, 80f, 50f, 91f)
        }
        solid(Shade.SOFT) {
            moveTo(50f, 76f)
            curveTo(60f, 76f, 70f, 70f, 73f, 59f)
            curveTo(61f, 59f, 51f, 66f, 50f, 76f)
            close()
        }
        solid(Shade.SOFT) {
            moveTo(50f, 87f)
            curveTo(40f, 87f, 30f, 81f, 27f, 70f)
            curveTo(39f, 70f, 49f, 77f, 50f, 87f)
            close()
        }
        // Five round petals as one outline. Each is a half circle whose ends meet its neighbours',
        // which they do exactly when the radius is the reach times tan 36°.
        val reach = 15f
        val round = reach * tan(PI / 5).toFloat()
        val bulge = round * QUARTER
        solid(Shade.FAINT) {
            for (petal in 0 until 5) {
                val angle = (-90.0 + 72.0 * petal) * PI / 180.0
                val ux = cos(angle).toFloat()
                val uy = sin(angle).toFloat()
                val cx = 50f + reach * ux
                val cy = 34f + reach * uy
                // (ux, uy) points out along the petal, (-uy, ux) across it.
                if (petal == 0) moveTo(cx + round * uy, cy - round * ux)
                curveTo(
                    cx + round * uy + bulge * ux, cy - round * ux + bulge * uy,
                    cx + round * ux + bulge * uy, cy + round * uy - bulge * ux,
                    cx + round * ux, cy + round * uy
                )
                curveTo(
                    cx + round * ux - bulge * uy, cy + round * uy + bulge * ux,
                    cx - round * uy + bulge * ux, cy + round * ux + bulge * uy,
                    cx - round * uy, cy + round * ux
                )
            }
            close()
        }
        fill(Shade.FULL) { circle(50f, 34f, 7f) }
    }

    /** A round-bellied jar with a painted band: a pottery village. */
    private fun Sketch.pottery() {
        solid(Shade.FAINT) {
            moveTo(40f, 13f)
            lineTo(60f, 13f)
            lineTo(58f, 23f)
            curveTo(82f, 31f, 90f, 64f, 66f, 89f)
            lineTo(34f, 89f)
            curveTo(10f, 64f, 18f, 31f, 42f, 23f)
            close()
        }
        outline { line(36f, 13f, 64f, 13f) }
        stroke(Shade.SOFT, THIN) {
            moveTo(24f, 44f)
            curveTo(38f, 49f, 62f, 49f, 76f, 44f)
            moveTo(23f, 66f)
            curveTo(38f, 71f, 62f, 71f, 77f, 66f)
        }
        fill(Shade.SOFT) {
            circle(35f, 57f, 3f)
            circle(50f, 59f, 3f)
            circle(65f, 57f, 3f)
        }
    }

    /** A big wheel with its cars, on its stand: an amusement park. */
    private fun Sketch.wheel() {
        stroke(Shade.SOFT, THIN) {
            for (spoke in 0 until 8) {
                val angle = 45.0 * spoke * PI / 180.0
                line(50f, 44f, 50f + 31f * cos(angle).toFloat(), 44f + 31f * sin(angle).toFloat())
            }
        }
        outline { circle(50f, 44f, 31f) }
        fill(Shade.FULL) {
            for (car in 0 until 8) {
                val angle = 45.0 * car * PI / 180.0
                circle(50f + 31f * cos(angle).toFloat(), 44f + 31f * sin(angle).toFloat(), 5.5f)
            }
            circle(50f, 44f, 5.5f)
        }
        outline {
            line(50f, 44f, 31f, 92f)
            line(50f, 44f, 69f, 92f)
            line(20f, 92f, 80f, 92f)
        }
    }

    /** A paper cup with a lid and a sleeve with a coffee bean on it: a cafe. */
    private fun Sketch.coffee() {
        solid(Shade.FAINT) { polygon(29f, 31f, 71f, 31f, 65f, 91f, 35f, 91f) }
        fill(Shade.SOFT) { polygon(30.5f, 46f, 69.5f, 46f, 67f, 72f, 33f, 72f) }
        solid(Shade.SOFT) { polygon(35f, 12f, 65f, 12f, 68f, 22f, 75f, 22f, 75f, 31f, 25f, 31f, 25f, 22f, 32f, 22f) }
        // The bean is two halves with the sleeve showing between them: nothing here can erase.
        fill(Shade.FULL) {
            moveTo(48.6f, 50f)
            curveTo(39f, 51f, 39f, 67f, 48.6f, 68f)
            curveTo(46.6f, 62f, 50.6f, 56f, 48.6f, 50f)
            close()
            moveTo(51.4f, 50f)
            curveTo(61f, 51f, 61f, 67f, 51.4f, 68f)
            curveTo(49.4f, 62f, 53.4f, 56f, 51.4f, 50f)
            close()
        }
    }

    /** A closed shape: a wash of [shade] inside a full-strength outline. */
    private fun Sketch.solid(shade: Shade, contour: Contour.() -> Unit) {
        fill(shade, contour)
        outline(contour)
    }

    private fun Sketch.outline(contour: Contour.() -> Unit) = stroke(Shade.FULL, LINE, contour)

    private fun Contour.line(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        moveTo(fromX, fromY)
        lineTo(toX, toY)
    }

    /** Straight lines through the points given as x, y, x, y, … */
    private fun Contour.polyline(vararg xy: Float) {
        moveTo(xy[0], xy[1])
        for (i in 2 until xy.size step 2) lineTo(xy[i], xy[i + 1])
    }

    private fun Contour.polygon(vararg xy: Float) {
        polyline(*xy)
        close()
    }

    private fun Contour.circle(cx: Float, cy: Float, radius: Float) {
        val bulge = radius * QUARTER
        moveTo(cx + radius, cy)
        curveTo(cx + radius, cy + bulge, cx + bulge, cy + radius, cx, cy + radius)
        curveTo(cx - bulge, cy + radius, cx - radius, cy + bulge, cx - radius, cy)
        curveTo(cx - radius, cy - bulge, cx - bulge, cy - radius, cx, cy - radius)
        curveTo(cx + bulge, cy - radius, cx + radius, cy - bulge, cx + radius, cy)
        close()
    }

    /** A curl of steam rising from [bottom] to [top]. */
    private fun Contour.wisp(x: Float, bottom: Float, top: Float) {
        val third = (bottom - top) / 3f
        moveTo(x, bottom)
        curveTo(x - 7f, bottom - third, x + 7f, top + third, x, top)
    }

    /** A four-pointed sparkle reaching [arm] from its centre, pinched in between the points. */
    private fun Contour.twinkle(cx: Float, cy: Float, arm: Float) {
        val waist = arm * 0.26f
        polygon(
            cx, cy - arm, cx + waist, cy - waist, cx + arm, cy, cx + waist, cy + waist,
            cx, cy + arm, cx - waist, cy + waist, cx - arm, cy, cx - waist, cy - waist
        )
    }

    /** A row of [crests] waves, each 28 wide, starting at [left] on the level [y]. */
    private fun Contour.wave(left: Float, y: Float, crests: Int) {
        moveTo(left, y)
        for (crest in 0 until crests) {
            val x = left + 28f * crest
            curveTo(x + 5f, y - 6f, x + 9f, y - 6f, x + 14f, y)
            curveTo(x + 19f, y + 6f, x + 23f, y + 6f, x + 28f, y)
        }
    }

    /** How far a cubic's control points stand off for it to pass as a quarter circle of radius 1. */
    private const val QUARTER = 0.5523f
}

/**
 * A [Sketch] as a Drawable in one colour. The shapes are worked out once, in the design square;
 * drawing only places that square in the bounds ([ArtFit]) and paints them.
 */
open class SketchDrawable(private val color: Int, sketch: (Sketch) -> Unit) : Drawable() {

    /** One painted shape: filled when [width] is null, otherwise a line of that width. */
    private class Part(val path: Path, val shade: Shade, val width: Float?)

    private val parts = ArrayList<Part>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var opacity = 255

    init {
        // Round ends and corners throughout: at 80 dp a mitred peak or a square line end is a
        // stray pixel, and at 400 dp it is what makes a drawing look hard.
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        sketch(object : Sketch {
            override fun fill(shade: Shade, contour: Contour.() -> Unit) {
                parts.add(Part(trace(contour), shade, null))
            }

            override fun stroke(shade: Shade, width: Float, contour: Contour.() -> Unit) {
                parts.add(Part(trace(contour), shade, width))
            }
        })
    }

    private fun trace(contour: Contour.() -> Unit): Path {
        val path = Path()
        object : Contour {
            override fun moveTo(x: Float, y: Float) = path.moveTo(x, y)
            override fun lineTo(x: Float, y: Float) = path.lineTo(x, y)
            override fun curveTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float) =
                path.cubicTo(c1x, c1y, c2x, c2y, x, y)

            override fun close() = path.close()
        }.contour()
        return path
    }

    override fun draw(canvas: Canvas) {
        val area = bounds
        val fit = ArtFit.of(area.left.toFloat(), area.top.toFloat(), area.width().toFloat(), area.height().toFloat())
        if (fit.scale <= 0f) return
        val saved = canvas.save()
        canvas.translate(fit.left, fit.top)
        canvas.scale(fit.scale, fit.scale)
        for (part in parts) {
            paint.style = if (part.width == null) Paint.Style.FILL else Paint.Style.STROKE
            paint.strokeWidth = part.width ?: 0f
            paint.color = color
            paint.alpha = ((color ushr 24) * part.shade.alpha * opacity / 255f).roundToInt()
            canvas.drawPath(part.path, paint)
        }
        canvas.restoreToCount(saved)
    }

    override fun setAlpha(alpha: Int) {
        if (alpha == opacity) return
        opacity = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** The picture on a card or a tile, in the card's tone [color]. It fills its bounds as [ArtFit] says. */
class IllustrationDrawable(illustration: Illustration, color: Int) :
    SketchDrawable(color, { Artwork.draw(illustration, it) })

/** The crown that marks the leader on the scoreboard. */
class CrownDrawable(color: Int) : SketchDrawable(color, Artwork::crown)
