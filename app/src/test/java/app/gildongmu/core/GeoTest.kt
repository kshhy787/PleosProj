package app.gildongmu.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {

    @Test
    fun distanceMatchesAKnownPair() {
        // Seoul City Hall to Busan City Hall is about 325 km in a straight line.
        val seoul = GeoPoint(37.5663, 126.9779)
        val busan = GeoPoint(35.1798, 129.0750)
        assertEquals(325.0, Geo.distanceKm(seoul, busan), 3.0)
    }

    @Test
    fun aRouteKnowsItsLengthAndPointsAlongIt() {
        val route = RouteGeometry(listOf(Fixtures.at(0.0), Fixtures.at(40.0), Fixtures.at(100.0)))
        assertEquals(100.0, route.lengthKm, 0.01)
        assertEquals(Fixtures.at(25.0).lat, route.pointAt(25.0).lat, 1e-6)
        assertEquals(Fixtures.at(70.0).lat, route.pointAt(70.0).lat, 1e-6)
        assertEquals("Beyond the end clamps to the end", Fixtures.at(100.0).lat, route.pointAt(250.0).lat, 1e-9)
    }

    @Test
    fun projectsAPositionBesideTheRoadOntoIt() {
        val route = RouteGeometry(listOf(Fixtures.at(0.0), Fixtures.at(100.0)))
        val onRoad = Fixtures.at(42.0)
        val beside = GeoPoint(onRoad.lat, onRoad.lon + 0.03) // roughly 2.7 km east of the road
        val projection = route.project(beside)
        assertEquals(42.0, projection.routeKm, 0.05)
        assertEquals(2.66, projection.offsetKm, 0.1)
    }

    @Test
    fun aHintKeepsProgressOnTheRightPassOfARouteThatDoublesBack() {
        // Out 50 km north, then back along a road 1 km to the east.
        val out = Fixtures.at(0.0)
        val turn = Fixtures.at(50.0)
        val back = GeoPoint(out.lat, out.lon + 0.0113)
        val turnEast = GeoPoint(turn.lat, turn.lon + 0.0113)
        val route = RouteGeometry(listOf(out, turn, turnEast, back))

        val position = GeoPoint(Fixtures.at(20.0).lat, out.lon + 0.0056) // halfway between the two passes
        val outbound = route.project(position, hintKm = 18.0)
        val inbound = route.project(position, hintKm = 78.0)
        assertEquals(20.0, outbound.routeKm, 0.5)
        assertTrue("Expected the return leg, got ${inbound.routeKm}", inbound.routeKm > 75.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aRouteNeedsTwoPoints() {
        RouteGeometry(listOf(Fixtures.at(0.0)))
    }
}
