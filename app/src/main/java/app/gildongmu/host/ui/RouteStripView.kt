package app.gildongmu.host.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import app.gildongmu.core.PlaceState
import app.gildongmu.core.PlaceStatus
import app.gildongmu.core.RouteStripPlan

/**
 * The trip as a straight line: the places already told or passed as filled dots, the places still
 * ahead as hollow rings, and the car, marked "지금 여기".
 * It is a diagram rather than a map, which is all the parked screen needs to answer "where have we
 * been, what is still to come".
 *
 * Where each dot and each name goes is [RouteStripPlan]'s decision, because names that run into
 * each other cannot be seen from a desk; this view only draws the plan. It is meant to be
 * `ScreenFit.ROUTE_STRIP_DP` high: the line in the middle, one row of names above it and one below.
 */
class RouteStripView(context: Context) : View(context) {

    private var places: List<PlaceStatus> = emptyList()
    private var lengthKm = 1.0
    private var progressKm = 0.0

    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        background = Ui.rounded(Palette.SURFACE, Ui.dp(context, 28), Palette.OUTLINE, Ui.dp(context, 2))
        line.strokeCap = Paint.Cap.ROUND
        ring.style = Paint.Style.STROKE
        label.textAlign = Paint.Align.CENTER
    }

    fun update(places: List<PlaceStatus>, lengthKm: Double, progressKm: Double) {
        // The host reports several times a second, and a parked car goes nowhere.
        if (places == this.places && lengthKm == this.lengthKm && progressKm == this.progressKm) return
        this.places = places
        this.lengthKm = lengthKm
        this.progressKm = progressKm
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        label.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, LABEL_SP, resources.displayMetrics)
        val plan = RouteStripPlan.of(width.toFloat(), RouteStripPlan.INSET_DP * density, places, lengthKm, progressKm, label.textSize)
        if (plan.lineEnd <= plan.lineStart) return
        val y = height / 2f
        val above = y - ABOVE_DP * density
        val below = y + BELOW_DP * density

        // The road: what is behind the car in the story colour, what is ahead in grey.
        line.strokeWidth = 8f * density
        line.color = Palette.OUTLINE
        canvas.drawLine(plan.lineStart, y, plan.lineEnd, y, line)
        line.color = Palette.STORY
        canvas.drawLine(plan.lineStart, y, plan.carX, y, line)

        label.typeface = Typeface.DEFAULT
        for (mark in plan.marks) {
            val state = mark.place.state
            // Every place stands a little clear of the road. A told place has the colour of the
            // road behind the car, and without the gap it is a bump in the line; and the road
            // must not show through a ring.
            dot.color = Palette.SURFACE
            canvas.drawCircle(mark.x, y, 12f * density, dot)
            if (state == PlaceState.AHEAD) {
                ring.strokeWidth = 3f * density
                ring.color = Palette.TEXT_SECONDARY
                canvas.drawCircle(mark.x, y, 7.5f * density, ring)
            } else {
                // A story that was told is what the trip was for; a place only driven past is dimmer.
                dot.color = if (state == PlaceState.TOLD) Palette.STORY else Palette.over(Palette.STORY, PASSED_ALPHA)
                canvas.drawCircle(mark.x, y, 9f * density, dot)
            }
            label.color = if (state == PlaceState.TOLD) Palette.TEXT else Palette.TEXT_SECONDARY
            when (mark.side) {
                RouteStripPlan.Side.ABOVE -> canvas.drawText(mark.place.name, mark.labelX, above, label)
                RouteStripPlan.Side.BELOW -> canvas.drawText(mark.place.name, mark.labelX, below, label)
                RouteStripPlan.Side.NONE -> Unit
            }
        }

        // The car, over everything, and its label, which the plan keeps clear of the names.
        dot.color = Palette.TEXT
        canvas.drawCircle(plan.carX, y, 13f * density, dot)
        dot.color = Palette.STORY
        canvas.drawCircle(plan.carX, y, 8f * density, dot)
        label.typeface = Typeface.DEFAULT_BOLD
        label.color = Palette.TEXT
        canvas.drawText(RouteStripPlan.CAR_LABEL, plan.carLabelX, above, label)
    }

    private companion object {
        /** The size of the names. `ScreenFit.ROUTE_STRIP_DP` is the height of two rows of it around the line. */
        const val LABEL_SP = 15f

        /** From the line up to the baseline of the names above it: clear of the car's circle, 13 high. */
        const val ABOVE_DP = 20f

        /** From the line down to the baseline of the names below it: clear of a dot, 9 high. */
        const val BELOW_DP = 34f

        /** How much of the story colour a place that was passed without its story keeps. */
        const val PASSED_ALPHA = 0.45f
    }
}
