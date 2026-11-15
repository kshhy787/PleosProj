package app.gildongmu.core

/**
 * The part of the trip a passenger asks about: the next rest stop ("휴게소 근처"), the destination
 * ("도착해서") or where the car is now ("여기 근처"). Null where one is expected means the passenger
 * did not say, and the host decides.
 */
enum class SpotArea { NEXT_STOP, DESTINATION, HERE }

/**
 * Chooses which spots to recommend. Pure: the engine passes in where the car is.
 *
 * A spot counts as being where it projects onto the route, and once the car is more than 2 km past
 * that point it is never offered, since getting there would mean turning back. The slack is there
 * because a spot lies off the road, so its projection only roughly marks the turn-off. Spots near
 * the destination count as being at the end of the route, wherever they project: the trip ends
 * among them, so the car never passes them before it arrives.
 *
 * [destinationCloseKm] is how much route may be left for the destination to count as close: from
 * then on, the host's own order offers its spots right after those of an accepted stop.
 */
class SpotPicker(
    private val pkg: RoutePackage,
    private val geometry: RouteGeometry,
    private val destinationCloseKm: Double = 40.0
) {
    private companion object {
        /** How far behind the car a spot, or a rest stop, still counts as not passed. */
        const val PASSED_SLACK_KM = 2.0

        /** "여기 근처": this far from the car, in a straight line. */
        const val HERE_RADIUS_KM = 30.0
    }

    /** A spot and where it counts as being on the route. */
    private class Placed(val spot: Spot, val routeKm: Double)

    private val placed: List<Placed> = pkg.spots.map { spot ->
        val km = if (spot.near == Spot.NEAR_DESTINATION) geometry.lengthKm else geometry.project(spot.location).routeKm
        Placed(spot, km)
    }
    private val stopKm: Map<String, Double> = pkg.stops.associate { it.id to geometry.project(it.location).routeKm }
    private val stopsInOrder: List<Stop> = pkg.stops.sortedBy { stopKm.getValue(it.id) }

    /**
     * Up to [max] spots of [category], best first, or none when nothing fits (the caller says so).
     * [progressKm] is where the car is along the route; [acceptedStopId] is a rest stop the
     * passengers agreed to visit, if any.
     *
     * [area] is what the passenger asked about, or null to let the host decide:
     *  - null: spots near the accepted stop, then, once the destination is close, spots near it,
     *    each group closest to its place first; then every other spot, in the order the car
     *    reaches it
     *  - [SpotArea.NEXT_STOP]: the spots of one stop, closest to it first: the accepted stop if it
     *    has any, otherwise the first stop ahead that does
     *  - [SpotArea.DESTINATION]: spots near the destination, closest to it first, however far away
     *    it still is
     *  - [SpotArea.HERE]: spots within 30 km of the car in a straight line, closest first
     */
    fun pick(
        category: SpotCategory,
        area: SpotArea?,
        progressKm: Double,
        acceptedStopId: String?,
        max: Int = 3
    ): List<Spot> {
        val candidates = placed.filter { it.spot.category == category && !isBehind(it.routeKm, progressKm) }
        val ranked = when (area) {
            null -> automaticOrder(candidates, progressKm, acceptedStopId)
            SpotArea.NEXT_STOP -> nearNextStop(candidates, progressKm, acceptedStopId)
            SpotArea.DESTINATION -> nearDestination(candidates)
            SpotArea.HERE -> nearCar(candidates, progressKm)
        }
        return ranked.take(max)
    }

    private fun isBehind(routeKm: Double, progressKm: Double) = routeKm < progressKm - PASSED_SLACK_KM

    private fun automaticOrder(candidates: List<Placed>, progressKm: Double, acceptedStopId: String?): List<Spot> {
        val accepted = pkg.stops.firstOrNull { it.id == acceptedStopId }
        val atStop = if (accepted != null) nearStop(candidates, accepted) else emptyList()
        val destinationClose = geometry.lengthKm - progressKm <= destinationCloseKm
        val atDestination = if (destinationClose) nearDestination(candidates) else emptyList()
        val onTheWay = candidates.sortedBy { it.routeKm }.map { it.spot }
        return (atStop + atDestination + onTheWay).distinct()
    }

    private fun nearNextStop(candidates: List<Placed>, progressKm: Double, acceptedStopId: String?): List<Spot> {
        val stop = pkg.stops.firstOrNull { it.id == acceptedStopId && hasSpots(candidates, it) }
            // Slack as for spots: a rest area the car is pulling into or has just left counts.
            ?: stopsInOrder.firstOrNull { !isBehind(stopKm.getValue(it.id), progressKm) && hasSpots(candidates, it) }
            ?: return emptyList()
        return nearStop(candidates, stop)
    }

    private fun hasSpots(candidates: List<Placed>, stop: Stop) = candidates.any { it.spot.near == stop.id }

    private fun nearStop(candidates: List<Placed>, stop: Stop): List<Spot> =
        candidates.map { it.spot }.filter { it.near == stop.id }.sortedBy { Geo.distanceKm(it.location, stop.location) }

    private fun nearDestination(candidates: List<Placed>): List<Spot> =
        candidates.map { it.spot }.filter { it.near == Spot.NEAR_DESTINATION }
            .sortedBy { Geo.distanceKm(it.location, pkg.destination.location) }

    private fun nearCar(candidates: List<Placed>, progressKm: Double): List<Spot> {
        val car = geometry.pointAt(progressKm)
        return candidates.map { it.spot to Geo.distanceKm(car, it.spot.location) }
            .filter { (_, km) -> km <= HERE_RADIUS_KM }
            .sortedBy { (_, km) -> km }
            .map { (spot, _) -> spot }
    }
}
