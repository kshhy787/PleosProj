package app.gildongmu.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Where everything on the route strip goes: the line, the car, a dot for each place, and which
 * names are written above the line, which below, and which not at all.
 *
 * The strip is the trip drawn as a straight line, so two places a few kilometres apart are a few
 * pixels apart, and names centred on their dots run into each other and into the car's own label.
 * Whether they do depends on the width of the strip and on where the car is, neither of which can
 * be seen from a desk, so the choice is made here, where it can be tested, and the view only
 * draws what it is told. All lengths are in the unit [of] was given its width in (the view works
 * in pixels), measured from the strip's left edge.
 */
data class RouteStripPlan(
    /** The left end of the line: the start of the route. */
    val lineStart: Float,
    /** The right end of the line: the destination. */
    val lineEnd: Float,
    /** The car, on the line. */
    val carX: Float,
    /**
     * The centre of the car's label, [CAR_LABEL], which is always written above the line. It is
     * [carX] unless the car is so near an end that the label would run off the strip.
     */
    val carLabelX: Float,
    /** One for each place, in the order the places were given. */
    val marks: List<Mark>
) {
    /** Where a place's name is written. */
    enum class Side {
        /** Above the line, where the car's label is too. */
        ABOVE,

        /** Below the line: where a name goes when nothing is in its way. */
        BELOW,

        /** Not written: there is no room for it on either side. The dot is still drawn. */
        NONE
    }

    /**
     * A place on the strip: its dot at [x] on the line, and its name on [side], centred on
     * [labelX]. That is [x] unless the place is so near an end that the name would run off the
     * strip.
     */
    data class Mark(val place: PlaceStatus, val x: Float, val side: Side, val labelX: Float)

    companion object {
        /** What is written over the car. */
        const val CAR_LABEL = "지금 여기"

        /**
         * How far in from each edge the line starts and ends, in dp, so that the first and the
         * last dot have room for half a name beside them. The view converts it and hands it to
         * [of].
         */
        const val INSET_DP = 48f

        // A label's width is estimated, not measured, because the fonts differ between cars: a
        // Hangul syllable is taken to be as wide as the text is high and anything else 0.55 of
        // that. In the fonts of the two emulators a syllable measured 0.86 to 0.92 of the text
        // size, a space a quarter and a digit 0.55 to 0.61, and place names are nearly all
        // syllables: the estimate errs towards leaving a name out, not towards an overlap.
        private const val HANGUL_WIDTH = 1f
        private const val OTHER_WIDTH = 0.55f

        /** The least clear space between two labels on the same side, as a share of the text size. */
        private const val GAP = 0.5f

        /** The estimated width of [text] written at [textSize], in the unit of [textSize]. */
        fun textWidth(text: String, textSize: Float): Float {
            val hangul = text.count { it in '가'..'힣' }
            return (hangul * HANGUL_WIDTH + (text.length - hangul) * OTHER_WIDTH) * textSize
        }

        /**
         * The plan for a strip [width] wide whose line starts and ends [inset] from its edges.
         * [places] are the places to mark, [lengthKm] is the length of the route, [carKm] is how
         * far along it the car is, and [textSize] is the size the labels are written at.
         *
         * A name goes below the line if it is clear of the names already there, else above it if it
         * is clear of the names there and of the car's label, else it is left out. The places take
         * their turn in order of what a parked passenger looks for: first the next place ahead of
         * the car, which is the one the card is offering a story about, then the others from the
         * nearest to the car outwards. So when the strip is too crowded for every name, the ones
         * that go are far from the car.
         */
        fun of(
            width: Float,
            inset: Float,
            places: List<PlaceStatus>,
            lengthKm: Double,
            carKm: Double,
            textSize: Float
        ): RouteStripPlan {
            val lineStart = min(inset, width / 2f)
            val lineEnd = max(width - inset, lineStart)
            val length = if (lengthKm > 0.0) lengthKm else 1.0
            fun x(km: Double): Float = lineStart + (lineEnd - lineStart) * (km / length).coerceIn(0.0, 1.0).toFloat()

            // The centre for a label that belongs at x, moved in from the edge if it has to be.
            fun centre(x: Float, labelWidth: Float): Float =
                if (labelWidth >= width) width / 2f else x.coerceIn(labelWidth / 2f, width - labelWidth / 2f)

            val gap = GAP * textSize
            val carX = x(carKm)
            val carLabelWidth = textWidth(CAR_LABEL, textSize)
            val carLabelX = centre(carX, carLabelWidth)

            // What is taken on each side so far, as the left and right edges of each label.
            val above = mutableListOf(carLabelX - carLabelWidth / 2f to carLabelX + carLabelWidth / 2f)
            val below = mutableListOf<Pair<Float, Float>>()
            fun free(taken: List<Pair<Float, Float>>, from: Float, to: Float): Boolean =
                taken.none { (takenFrom, takenTo) -> from < takenTo + gap && takenFrom < to + gap }

            val next = places.indices.filter { places[it].routeKm >= carKm }.minByOrNull { places[it].routeKm }
            val turns = places.indices.sortedWith(compareBy<Int>({ it != next }, { abs(places[it].routeKm - carKm) }))

            val marks = HashMap<Int, Mark>()
            for (index in turns) {
                val place = places[index]
                val dot = x(place.routeKm)
                val labelWidth = textWidth(place.name, textSize)
                val labelX = centre(dot, labelWidth)
                val from = labelX - labelWidth / 2f
                val to = labelX + labelWidth / 2f
                val side = when {
                    labelWidth > width -> Side.NONE
                    free(below, from, to) -> Side.BELOW
                    free(above, from, to) -> Side.ABOVE
                    else -> Side.NONE
                }
                when (side) {
                    Side.BELOW -> below.add(from to to)
                    Side.ABOVE -> above.add(from to to)
                    Side.NONE -> Unit
                }
                marks[index] = Mark(place, dot, side, labelX)
            }
            return RouteStripPlan(lineStart, lineEnd, carX, carLabelX, places.indices.map(marks::getValue))
        }
    }
}
