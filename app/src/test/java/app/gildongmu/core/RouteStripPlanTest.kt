package app.gildongmu.core

import app.gildongmu.core.RouteStripPlan.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteStripPlanTest {

    private fun place(name: String, km: Double, state: PlaceState = PlaceState.AHEAD) = PlaceStatus(name, name, km, state)

    /** The places of the bundled Seoul → Gangneung route, where they are along its 205.5 km. */
    private val trip = listOf(
        place("에버랜드", 28.6, PlaceState.TOLD),
        place("이천 도자기", 54.7, PlaceState.PASSED),
        place("세종대왕릉", 70.0, PlaceState.TOLD),
        place("치악산", 113.0, PlaceState.TOLD),
        place("봉평 메밀꽃밭", 150.3),
        place("대관령", 186.4),
        place("오죽헌", 201.7)
    )

    /** A plan in dp at the strip's own text size: a 15 dp label, the line 48 in from each edge. */
    private fun plan(width: Float, carKm: Double, places: List<PlaceStatus>, lengthKm: Double = 200.0, inset: Float = 48f, textSize: Float = 15f) =
        RouteStripPlan.of(width, inset, places, lengthKm, carKm, textSize)

    private fun sides(plan: RouteStripPlan) = plan.marks.map { it.side }

    @Test
    fun aHangulSyllableIsAsWideAsTheTextIsHighAndAnythingElseAboutHalf() {
        assertEquals(60f, RouteStripPlan.textWidth("대관령", 20f), 0.001f)
        assertEquals("Four syllables and a space", 91f, RouteStripPlan.textWidth("지금 여기", 20f), 0.001f)
        assertEquals("Digits, Latin letters and signs", 44f, RouteStripPlan.textWidth("A1(%", 20f), 0.001f)
        assertEquals(0f, RouteStripPlan.textWidth("", 20f), 0f)
        assertEquals("Twice the size, twice the width", 182f, RouteStripPlan.textWidth("지금 여기", 40f), 0.001f)
    }

    @Test
    fun onAWideStripEveryNameGoesBelowTheLineAndOnlyTheCarLabelAbove() {
        // The strip of the Pleos emulator's parked screen in the car build, at the rest stop of
        // the demo drive.
        val plan = plan(1634f, carKm = 127.0, places = trip, lengthKm = 205.5)

        assertEquals(List(trip.size) { Side.BELOW }, sides(plan))
        assertEquals(48f, plan.lineStart, 0f)
        assertEquals(1586f, plan.lineEnd, 0f)
        assertEquals("127 of 205.5 km along a line of 1538", 48f + 1538f * 127f / 205.5f, plan.carX, 0.01f)
        assertEquals("The label sits over the car", plan.carX, plan.carLabelX, 0f)
        for ((mark, place) in plan.marks.zip(trip)) {
            assertEquals(place, mark.place)
            assertEquals(place.name, 48f + 1538f * place.routeKm.toFloat() / 205.5f, mark.x, 0.01f)
            assertEquals("The name sits under its dot", mark.x, mark.labelX, 0f)
        }
    }

    @Test
    fun besideTheDemoControlsOneNameOfTheTripGoesAbove() {
        // The same moment in the mock build, whose strip is 1230 wide: 이천 and 여주 are 15 km
        // apart, 84 on this line, and their names need 87.
        val plan = plan(1230f, carKm = 127.0, places = trip, lengthKm = 205.5)
        assertEquals(
            "세종대왕릉 is nearer the car and keeps the place under the line",
            listOf(Side.BELOW, Side.ABOVE, Side.BELOW, Side.BELOW, Side.BELOW, Side.BELOW, Side.BELOW),
            sides(plan)
        )
    }

    @Test
    fun aNameThatWouldRunIntoItsNeighbourGoesAboveTheLine() {
        // 504 of line for 200 km: these two dots are 13 apart and each name is 45 wide.
        val plan = plan(600f, carKm = 0.0, places = listOf(place("가나다", 150.0), place("라마바", 155.0)))
        assertEquals(listOf(Side.BELOW, Side.ABOVE), sides(plan))
    }

    @Test
    fun namesOnTheSameSideKeepHalfALetterBetweenThem() {
        // Two names of 45, so 45 between their centres would have them touch; the gap is 7.5.
        // 504 of line for 200 km is 2.52 a km: 20 km apart is 50.4, 21 km is 52.9.
        val touching = plan(600f, carKm = 0.0, places = listOf(place("가나다", 100.0), place("라마바", 120.0)))
        assertEquals("Clear of each other by 5.4, which reads as one word", listOf(Side.BELOW, Side.ABOVE), sides(touching))
        val apart = plan(600f, carKm = 0.0, places = listOf(place("가나다", 100.0), place("라마바", 121.0)))
        assertEquals(listOf(Side.BELOW, Side.BELOW), sides(apart))
    }

    @Test
    fun aNameWithNoRoomOnEitherSideIsLeftOutButItsDotStays() {
        val plan = plan(600f, carKm = 0.0, places = listOf(place("가나다", 150.0), place("라마바", 153.0), place("사아자", 156.0)))
        assertEquals(listOf(Side.BELOW, Side.ABOVE, Side.NONE), sides(plan))
        assertEquals(48f + 504f * 156f / 200f, plan.marks[2].x, 0.01f)
    }

    @Test
    fun noNameIsWrittenOverTheCarLabel() {
        val places = listOf(place("가나다", 98.0), place("라마바", 102.0))

        // With the car far away the second name has the room above the line...
        assertEquals(listOf(Side.BELOW, Side.ABOVE), sides(plan(600f, carKm = 0.0, places = places)))
        // ...but with the car between the two, "지금 여기" is there.
        assertEquals(listOf(Side.NONE, Side.BELOW), sides(plan(600f, carKm = 100.0, places = places)))
    }

    @Test
    fun theNextPlaceAheadKeepsItsNameBeforeANearerOneBehind() {
        // The car has just passed the first place and the card is offering the story of the
        // second. Both names cannot be written; the one behind is nearer, but it is the one to go.
        val plan = plan(600f, carKm = 100.0, places = listOf(place("지난 곳", 99.0, PlaceState.TOLD), place("다음 곳", 104.0)))
        assertEquals(listOf(Side.NONE, Side.BELOW), sides(plan))
    }

    @Test
    fun whenTheStripIsCrowdedTheNamesFarFromTheCarAreTheOnesLeftOut() {
        val crowd = listOf(place("가나다", 150.0), place("라마바", 153.0), place("사아자", 156.0))

        // The car before them: the first is the next place ahead, then the second is nearer.
        assertEquals(listOf(Side.BELOW, Side.ABOVE, Side.NONE), sides(plan(600f, carKm = 20.0, places = crowd)))
        // The car beyond them: none is ahead, and the last is the nearest.
        assertEquals(listOf(Side.NONE, Side.ABOVE, Side.BELOW), sides(plan(600f, carKm = 200.0, places = crowd)))
    }

    @Test
    fun aLabelNearAnEndOfTheStripIsMovedInNotCutOff() {
        // A name of 150 on a dot 48 from the edge would lose 27 of itself.
        val long = "대관령 양떼목장 전망대"
        val width = RouteStripPlan.textWidth(long, 15f)
        val plan = plan(600f, carKm = 100.0, places = listOf(place(long, 0.0), place(long, 200.0)))
        assertEquals(listOf(Side.BELOW, Side.BELOW), sides(plan))
        assertEquals(48f, plan.marks[0].x, 0f)
        assertEquals(width / 2f, plan.marks[0].labelX, 0.001f)
        assertEquals(552f, plan.marks[1].x, 0f)
        assertEquals(600f - width / 2f, plan.marks[1].labelX, 0.001f)

        // The car's label too, on a strip whose line starts nearer the edge than half of it.
        val carWidth = RouteStripPlan.textWidth(RouteStripPlan.CAR_LABEL, 15f)
        val atStart = plan(600f, carKm = 0.0, places = emptyList(), inset = 10f)
        assertEquals(10f, atStart.carX, 0f)
        assertEquals(carWidth / 2f, atStart.carLabelX, 0.001f)
        val atEnd = plan(600f, carKm = 200.0, places = emptyList(), inset = 10f)
        assertEquals(590f, atEnd.carX, 0f)
        assertEquals(600f - carWidth / 2f, atEnd.carLabelX, 0.001f)
    }

    @Test
    fun aNameIsKeptClearOfTheCarLabelWhereTheLabelWasMovedTo() {
        // The car is at the start, so its label (68 wide) is moved in to span 0 to 68, and the
        // next place takes the room below. The third name, 45 wide over a dot at 85, would be
        // clear of a label centred on the car at 10, but not of where the label really is.
        val places = listOf(place("가나다", 20.0), place("라마바", 26.0))
        val plan = plan(600f, carKm = 0.0, places = places, inset = 10f)
        assertEquals(listOf(Side.BELOW, Side.NONE), sides(plan))
    }

    @Test
    fun aNameWiderThanTheWholeStripIsLeftOut() {
        val plan = plan(140f, carKm = 0.0, places = listOf(place("대관령 양떼목장 전망대", 100.0), place("치악산", 180.0)))
        assertEquals(listOf(Side.NONE, Side.BELOW), sides(plan))
    }

    @Test
    fun aStripNarrowerThanItsInsetsAndARouteWithoutLengthStillHaveAPlan() {
        val narrow = plan(60f, carKm = 50.0, places = listOf(place("가", 100.0)), inset = 48f)
        assertEquals("The line is a point in the middle", 30f, narrow.lineStart, 0f)
        assertEquals(30f, narrow.lineEnd, 0f)
        assertEquals(30f, narrow.carX, 0f)
        assertEquals(30f, narrow.marks[0].x, 0f)

        // Before the route is known its length is zero, and a place beyond the end is drawn at it.
        val unknown = plan(600f, carKm = 0.0, places = listOf(place("가나다", 300.0)), lengthKm = 0.0)
        assertEquals(48f, unknown.carX, 0f)
        assertEquals(552f, unknown.marks[0].x, 0f)
    }

    @Test
    fun namesNeverOverlapWhateverTheStripAndWhereverTheCar() {
        // The trip with a knot of places added at each end and around its middle, on strips from
        // a sliver to the widest window, with the car at every few kilometres.
        val places = trip + listOf(
            place("양재", 1.0), place("만남의 광장", 3.0), place("횡성 한우 거리", 120.0), place("Pyeongchang 2018", 150.0),
            place("경포대", 203.0), place("경포해변", 206.0)
        )
        for (textSize in listOf(15f, 22f)) {
            val gap = textSize / 2f
            val carWidth = RouteStripPlan.textWidth(RouteStripPlan.CAR_LABEL, textSize)
            for (width in 100..1700 step 37) {
                for (carKm in 0..206 step 2) {
                    val plan = RouteStripPlan.of(width.toFloat(), 48f, places, 206.0, carKm.toDouble(), textSize)
                    val label = "width $width, car at $carKm km, text $textSize"

                    assertEquals(label, places, plan.marks.map { it.place })
                    for (mark in plan.marks) assertTrue(label, mark.x in plan.lineStart..plan.lineEnd)

                    // The left and right edge of every label on each side, the car's included.
                    fun spans(side: Side) = plan.marks.filter { it.side == side }.map {
                        val half = RouteStripPlan.textWidth(it.place.name, textSize) / 2f
                        it.labelX - half to it.labelX + half
                    }
                    val above = spans(Side.ABOVE) + (plan.carLabelX - carWidth / 2f to plan.carLabelX + carWidth / 2f)
                    val below = spans(Side.BELOW)
                    for (side in listOf(above, below)) {
                        val inOrder = side.sortedBy { it.first }
                        for ((left, right) in inOrder.zipWithNext()) {
                            assertTrue("Labels $left and $right are closer than $gap: $label", right.first - left.second >= gap - 0.001f)
                        }
                    }
                    for ((from, to) in spans(Side.ABOVE) + below) {
                        assertTrue("A label runs off the strip: $label", from >= -0.001f && to <= width + 0.001f)
                    }
                }
            }
        }
    }
}
