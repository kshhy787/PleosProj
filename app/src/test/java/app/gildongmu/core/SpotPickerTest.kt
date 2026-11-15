package app.gildongmu.core

import kotlin.math.cos
import org.junit.Assert.assertEquals
import org.junit.Test

class SpotPickerTest {

    // Degrees of latitude per kilometre, from the same earth radius Geo uses.
    private val degreesPerKm = 360.0 / (2 * Math.PI * 6371.0088)

    /** The rest areas of the [Fixtures] road, 30, 60 and 90 km along it. */
    private val restAreas = listOf(
        Stop("s1", "하나휴게소", Fixtures.at(30.0), hasCharger = true),
        Stop("s2", "두리휴게소", Fixtures.at(60.0), hasCharger = false),
        Stop("s3", "세모휴게소", Fixtures.at(90.0), hasCharger = true)
    )

    private val everyArea: List<SpotArea?> = listOf<SpotArea?>(null) + SpotArea.entries

    /** A spot [km] along the road and [eastKm] off to the side of it. */
    private fun spot(
        id: String,
        km: Double,
        near: String? = null,
        category: SpotCategory = SpotCategory.FOOD,
        eastKm: Double = 0.0
    ): Spot {
        val road = Fixtures.at(km)
        val lon = road.lon + eastKm * degreesPerKm / cos(Math.toRadians(road.lat))
        return Spot(
            id = id,
            category = category,
            name = "$id 이름",
            area = "",
            location = GeoPoint(road.lat, lon),
            reason = "$id 이유예요.",
            near = near,
            sources = listOf(SourceRef("출처", "https://example.org/$id"))
        )
    }

    /** The made-up road of [Fixtures.route]: 100 km due north, so "at 30 km" is exactly that. */
    private fun route(vararg spots: Spot, stops: List<Stop> = restAreas) = RoutePackage(
        id = "spots",
        title = "추천 시험 경로",
        origin = Place("출발지", Fixtures.at(0.0)),
        destination = Place("도착지", Fixtures.at(100.0)),
        polyline = listOf(Fixtures.at(0.0), Fixtures.at(100.0)),
        pois = emptyList(),
        stops = stops,
        generalQuiz = emptyList(),
        geometryNote = null,
        spots = spots.toList()
    )

    private fun picker(vararg spots: Spot, stops: List<Stop> = restAreas): SpotPicker {
        val pkg = route(*spots, stops = stops)
        return SpotPicker(pkg, RouteGeometry(pkg.polyline))
    }

    /**
     * What [SpotPicker.pick] offers, as ids, so that a failure shows which spots came out in which
     * order. Without [max], pick's own default applies.
     */
    private fun SpotPicker.ids(
        atKm: Double,
        area: SpotArea? = null,
        category: SpotCategory = SpotCategory.FOOD,
        accepted: String? = null,
        max: Int? = null
    ): List<String> {
        val spots = if (max == null) pick(category, area, atKm, accepted) else pick(category, area, atKm, accepted, max)
        return spots.map { it.id }
    }

    // ---- what may be offered at all ----

    @Test
    fun offersOnlySpotsOfTheCategoryAskedFor() {
        // Each sight sits where it would beat the food spot beside it, were it food.
        val picker = picker(
            spot("food-road", 25.0),
            spot("sight-road", 21.0, category = SpotCategory.SIGHT),
            spot("food-s1", 33.0, near = "s1"),
            spot("sight-s1", 30.5, near = "s1", category = SpotCategory.SIGHT),
            spot("cafe-s1", 31.0, near = "s1", category = SpotCategory.CAFE),
            spot("food-dest", 97.0, near = Spot.NEAR_DESTINATION),
            spot("sight-dest", 99.5, near = Spot.NEAR_DESTINATION, category = SpotCategory.SIGHT)
        )
        assertEquals(listOf("food-road", "food-s1", "food-dest"), picker.ids(20.0))
        assertEquals(listOf("food-s1"), picker.ids(20.0, SpotArea.NEXT_STOP))
        assertEquals(listOf("food-dest"), picker.ids(20.0, SpotArea.DESTINATION))
        assertEquals(listOf("food-road", "food-s1"), picker.ids(20.0, SpotArea.HERE))

        assertEquals(listOf("sight-road", "sight-s1", "sight-dest"), picker.ids(20.0, category = SpotCategory.SIGHT))
        assertEquals(listOf("sight-s1"), picker.ids(20.0, SpotArea.NEXT_STOP, SpotCategory.SIGHT))
        assertEquals(listOf("sight-dest"), picker.ids(20.0, SpotArea.DESTINATION, SpotCategory.SIGHT))
        assertEquals(listOf("sight-road", "sight-s1"), picker.ids(20.0, SpotArea.HERE, SpotCategory.SIGHT))

        assertEquals(listOf("cafe-s1"), picker.ids(20.0, category = SpotCategory.CAFE))
        assertEquals(emptyList<String>(), picker.ids(20.0, SpotArea.DESTINATION, SpotCategory.CAFE))
    }

    @Test
    fun neverOffersASpotTheCarHasPassed() {
        val picker = picker(
            spot("behind", 47.9),
            spot("level", 48.1),
            spot("ahead", 55.0),
            // In a town the road goes through before it reaches the rest area.
            spot("s2-town", 46.0, near = "s2"),
            spot("s2-food", 61.0, near = "s2")
        )
        // A spot lies off the road, so it may project up to 2 km behind the car and still be on the
        // way; any further back, getting there would mean turning round.
        assertEquals(listOf("level", "ahead", "s2-food"), picker.ids(50.0, max = 10))
        assertEquals(listOf("s2-food", "level", "ahead"), picker.ids(50.0, accepted = "s2", max = 10))
        assertEquals(listOf("s2-food"), picker.ids(50.0, SpotArea.NEXT_STOP, max = 10))
        assertEquals(listOf("level", "ahead", "s2-food"), picker.ids(50.0, SpotArea.HERE, max = 10))
    }

    @Test
    fun spotsNearTheDestinationAreNeverPassedBeforeArrival() {
        // A market the road passes 4 km before its end. The car is beyond it, but the trip ends in
        // that town, so it is still on offer; a spot along the way at the same place is not.
        val picker = picker(spot("market", 96.0, near = Spot.NEAR_DESTINATION), spot("road", 96.0))
        for (area in listOf(null, SpotArea.DESTINATION, SpotArea.HERE)) {
            assertEquals("$area", listOf("market"), picker.ids(99.0, area))
        }
    }

    // ---- when the host decides ----

    @Test
    fun withoutAnAreaSpotsNearTheStopThePassengersAgreedToComeFirst() {
        val picker = picker(
            spot("s2-far", 64.0, near = "s2"),
            spot("road", 40.0),
            spot("s2-near", 60.5, near = "s2"),
            spot("dest", 98.0, near = Spot.NEAR_DESTINATION),
            spot("s2-before", 58.0, near = "s2"),
            spot("s3-food", 91.0, near = "s3")
        )
        assertEquals(
            "Closest to the stop first, then the rest without repeating them",
            listOf("s2-near", "s2-before", "s2-far", "road", "s3-food", "dest"),
            picker.ids(35.0, accepted = "s2", max = 10)
        )
        assertEquals(listOf("road", "s2-before", "s2-near"), picker.ids(35.0))
    }

    @Test
    fun withoutAnAreaSpotsNearTheDestinationComeNextOnceItIsWithin40Km() {
        val spots = arrayOf(
            spot("road", 70.0),
            spot("dest-far", 95.0, near = Spot.NEAR_DESTINATION),
            spot("s3-food", 91.0, near = "s3"),
            spot("dest-near", 99.0, near = Spot.NEAR_DESTINATION)
        )
        val picker = picker(*spots)
        // 40.5 km to go: they wait at the end of the route, in the order the package lists them.
        assertEquals(listOf("road", "s3-food", "dest-far", "dest-near"), picker.ids(59.5, max = 10))
        // 39.5 km to go: they come first, closest to the destination first.
        assertEquals(listOf("dest-near", "dest-far", "road", "s3-food"), picker.ids(60.5, max = 10))
        assertEquals(
            "A stop the passengers agreed to still comes before them",
            listOf("s3-food", "dest-near", "dest-far", "road"),
            picker.ids(60.5, accepted = "s3", max = 10)
        )

        val pkg = route(*spots)
        val closeAt10Km = SpotPicker(pkg, RouteGeometry(pkg.polyline), destinationCloseKm = 10.0)
        assertEquals(listOf("road", "s3-food", "dest-far", "dest-near"), closeAt10Km.ids(60.5, max = 10))
    }

    @Test
    fun withoutAnAreaTheRestComeInTheOrderTheCarReachesThem() {
        // A spot near the destination counts as being at the end of the route, even one in a town
        // the road reaches a few kilometres earlier.
        val picker = picker(
            spot("dest-early", 95.0, near = Spot.NEAR_DESTINATION),
            spot("s2-food", 61.0, near = "s2"),
            spot("road-97", 97.0),
            spot("road-12", 12.0),
            spot("s1-food", 31.0, near = "s1")
        )
        assertEquals(listOf("road-12", "s1-food", "s2-food", "road-97", "dest-early"), picker.ids(10.0, max = 10))
    }

    @Test
    fun anAgreedWaypointThatIsNotAStopIsIgnored() {
        // Once the passengers have picked a spot, the waypoint they agreed to is that spot.
        val picker = picker(spot("s2-food", 61.0, near = "s2"), spot("road", 40.0))
        assertEquals(listOf("road", "s2-food"), picker.ids(10.0, accepted = "road"))
        assertEquals(listOf("s2-food"), picker.ids(10.0, SpotArea.NEXT_STOP, accepted = "road"))
    }

    // ---- when the passenger names the area ----

    @Test
    fun destinationOffersOnlySpotsNearItClosestToItFirst() {
        val picker = picker(
            spot("far", 92.0, near = Spot.NEAR_DESTINATION),
            // Two stalls in one market: equally close, so they keep the package's order.
            spot("market-b", 98.0, near = Spot.NEAR_DESTINATION),
            spot("market-a", 98.0, near = Spot.NEAR_DESTINATION),
            spot("close", 99.5, near = Spot.NEAR_DESTINATION),
            // Right by the destination, but listed as somewhere along the way.
            spot("road", 100.0),
            spot("s3-food", 90.0, near = "s3")
        )
        // However far away the destination still is.
        assertEquals(listOf("close", "market-b", "market-a", "far"), picker.ids(0.0, SpotArea.DESTINATION, max = 10))
    }

    @Test
    fun nextStopMeansTheFirstStopAheadWithSuchSpotsAndOnlyItsSpots() {
        val picker = picker(
            spot("s1-sight", 31.0, near = "s1", category = SpotCategory.SIGHT),
            spot("s2-far", 63.0, near = "s2"),
            spot("s2-before", 58.5, near = "s2"),
            spot("s2-near", 60.5, near = "s2"),
            spot("s3-food", 91.0, near = "s3"),
            spot("road", 40.0),
            // Listed out of order: which stop comes next is a matter of the road.
            stops = listOf(restAreas[2], restAreas[0], restAreas[1])
        )
        // Nothing to eat at 하나휴게소, so 두리휴게소: its spots, closest to it first, and nothing
        // from further on although there is room for more.
        assertEquals(listOf("s2-near", "s2-before", "s2-far"), picker.ids(10.0, SpotArea.NEXT_STOP, max = 10))
        // A rest area the car is pulling into, or has just left, is still the one meant...
        assertEquals(listOf("s2-near", "s2-far"), picker.ids(61.5, SpotArea.NEXT_STOP, max = 10))
        // ...until it is more than 2 km behind.
        assertEquals(listOf("s3-food"), picker.ids(62.5, SpotArea.NEXT_STOP, max = 10))
    }

    @Test
    fun nextStopPrefersTheStopThePassengersAgreedTo() {
        val picker = picker(
            spot("s1-sight", 31.0, near = "s1", category = SpotCategory.SIGHT),
            spot("s2-food", 61.0, near = "s2"),
            spot("s3-food", 91.0, near = "s3")
        )
        assertEquals(listOf("s3-food"), picker.ids(10.0, SpotArea.NEXT_STOP, accepted = "s3"))
        // Nothing to eat at the agreed stop: then the first stop ahead that has something.
        assertEquals(listOf("s2-food"), picker.ids(10.0, SpotArea.NEXT_STOP, accepted = "s1"))
    }

    @Test
    fun hereMeansWithin30KmOfTheCarInAStraightLineClosestFirst() {
        val picker = picker(
            spot("road-22", 72.0),
            spot("side-25", 65.0, eastKm = 20.0), // 15 km on and 20 km off the road: 25 km away
            spot("side-36", 60.0, eastKm = 35.0), // only 10 km on, but 36 km away
            spot("edge-in", 79.5),
            spot("edge-out", 80.5),
            spot("level", 49.0),
            spot("passed", 47.0),
            spot("dest", 99.0, near = Spot.NEAR_DESTINATION)
        )
        assertEquals(listOf("level", "road-22", "side-25", "edge-in"), picker.ids(50.0, SpotArea.HERE, max = 10))
        // Near the end of the route, a spot near the destination is as good as any.
        assertEquals(listOf("edge-in", "edge-out", "dest"), picker.ids(75.0, SpotArea.HERE, max = 10))
    }

    // ---- how many ----

    @Test
    fun offersThreeAtMostUnlessAskedForMore() {
        val picker = picker(
            spot("s1-a", 28.0, near = "s1"),
            spot("s1-b", 29.0, near = "s1"),
            spot("s1-c", 31.2, near = "s1"),
            spot("s1-d", 32.5, near = "s1"),
            spot("dest-a", 96.0, near = Spot.NEAR_DESTINATION),
            spot("dest-b", 97.0, near = Spot.NEAR_DESTINATION),
            spot("dest-c", 98.0, near = Spot.NEAR_DESTINATION),
            spot("dest-d", 99.0, near = Spot.NEAR_DESTINATION)
        )
        assertEquals(listOf("s1-a", "s1-b", "s1-c"), picker.ids(10.0))
        assertEquals(listOf("s1-b", "s1-c", "s1-a"), picker.ids(10.0, SpotArea.NEXT_STOP))
        assertEquals(listOf("dest-d", "dest-c", "dest-b"), picker.ids(10.0, SpotArea.DESTINATION))
        assertEquals(listOf("s1-a", "s1-b", "s1-c"), picker.ids(10.0, SpotArea.HERE))
        assertEquals(listOf("s1-a"), picker.ids(10.0, max = 1))
        assertEquals(8, picker.ids(10.0, max = 10).size)
    }

    @Test
    fun offersNothingWhenNothingFits() {
        val noSpots = picker()
        val onlySights = picker(
            spot("sight", 31.0, near = "s1", category = SpotCategory.SIGHT),
            spot("dest-sight", 99.0, near = Spot.NEAR_DESTINATION, category = SpotCategory.SIGHT)
        )
        val allPassed = picker(spot("s1-food", 31.0, near = "s1"), spot("road", 40.0))
        for (area in everyArea) {
            assertEquals("no spots, $area", emptyList<String>(), noSpots.ids(10.0, area))
            assertEquals("no food, $area", emptyList<String>(), onlySights.ids(10.0, area))
            assertEquals("all passed, $area", emptyList<String>(), allPassed.ids(70.0, area))
        }
        // Something to eat, but not in the part of the trip asked about.
        val onTheWay = picker(spot("road", 70.0))
        assertEquals(listOf("road"), onTheWay.ids(10.0))
        assertEquals(emptyList<String>(), onTheWay.ids(10.0, SpotArea.NEXT_STOP))
        assertEquals(emptyList<String>(), onTheWay.ids(10.0, SpotArea.DESTINATION))
        assertEquals(emptyList<String>(), onTheWay.ids(10.0, SpotArea.HERE))
    }
}
