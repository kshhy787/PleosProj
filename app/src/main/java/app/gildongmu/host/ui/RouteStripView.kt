package app.gildongmu.host.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import app.gildongmu.core.PlaceState
import app.gildongmu.core.PlaceStatus

/**
 * The trip as a straight line: places as dots (told, passed or still ahead), and the car.
 * It is a diagram rather than a map, which is all the parked screen needs to answer "where have we
 * been, what is still to come".
 */
class RouteStripView(context: Context) : View(context) {

    private var places: List<PlaceStatus> = emptyList()
    private var lengthKm = 1.0
    private var progressKm = 0.0

    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        line.strokeCap = Paint.Cap.ROUND
        label.textAlign = Paint.Align.CENTER
        label.typeface = Typeface.DEFAULT
    }

    fun update(places: List<PlaceStatus>, lengthKm: Double, progressKm: Double) {
        this.places = places
        this.lengthKm = if (lengthKm > 0.0) lengthKm else 1.0
        this.progressKm = progressKm
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val inset = 48f * density
        val left = inset
        val right = width - inset
        if (right <= left) return
        val y = height / 2f
        fun x(km: Double) = left + (right - left) * (km / lengthKm).coerceIn(0.0, 1.0).toFloat()

        // The road: what is behind the car in the accent colour, what is ahead in grey.
        line.strokeWidth = 8f * density
        line.color = Palette.OUTLINE
        canvas.drawLine(left, y, right, y, line)
        line.color = Palette.ACCENT
        canvas.drawLine(left, y, x(progressKm), y, line)

        label.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15f, resources.displayMetrics)
        for ((index, place) in places.withIndex()) {
            val px = x(place.routeKm)
            dot.color = when (place.state) {
                PlaceState.TOLD -> Palette.GOOD
                PlaceState.PASSED -> Palette.OUTLINE
                PlaceState.AHEAD -> Palette.TEXT_SECONDARY
            }
            canvas.drawCircle(px, y, 9f * density, dot)
            label.color = if (place.state == PlaceState.TOLD) Palette.TEXT else Palette.TEXT_SECONDARY
            // Alternate above and below the line so neighbouring names do not collide.
            val textY = if (index % 2 == 0) y - 22f * density else y + 36f * density
            canvas.drawText(place.name, px, textY, label)
        }

        // The car.
        dot.color = Palette.TEXT
        canvas.drawCircle(x(progressKm), y, 13f * density, dot)
        dot.color = Palette.ACCENT
        canvas.drawCircle(x(progressKm), y, 8f * density, dot)
    }
}
