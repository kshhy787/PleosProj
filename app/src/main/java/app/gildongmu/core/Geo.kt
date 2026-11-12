package app.gildongmu.core

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A WGS84 position in decimal degrees. */
data class GeoPoint(val lat: Double, val lon: Double)

object Geo {
    private const val EARTH_RADIUS_KM = 6371.0088

    /** Great-circle distance in kilometres. */
    fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * asin(min(1.0, sqrt(h)))
    }
}

/** Where a position falls on the route: how far along it, and how far off to the side. */
data class Projection(val routeKm: Double, val offsetKm: Double)

/**
 * The route as a polyline with cumulative distance.
 *
 * Everything the host decides is expressed in "kilometres along the route", so a GPS fix is first
 * projected onto this line. The polyline comes from the navigation route; the bundled demo package
 * uses a simplified hand-placed line, which is why distances there are approximate.
 */
class RouteGeometry(points: List<GeoPoint>) {

    val points: List<GeoPoint> = points.toList()
    private val cumulativeKm: DoubleArray

    /** Total length of the polyline in kilometres. */
    val lengthKm: Double

    init {
        require(this.points.size >= 2) { "A route needs at least two points" }
        cumulativeKm = DoubleArray(this.points.size)
        for (i in 1 until this.points.size) {
            cumulativeKm[i] = cumulativeKm[i - 1] + Geo.distanceKm(this.points[i - 1], this.points[i])
        }
        lengthKm = cumulativeKm.last()
        require(lengthKm > 0.0) { "A route needs a non-zero length" }
    }

    /** The position [km] kilometres along the route, clamped to its ends. */
    fun pointAt(km: Double): GeoPoint {
        val target = km.coerceIn(0.0, lengthKm)
        var i = 1
        while (i < points.size - 1 && cumulativeKm[i] < target) i++
        val segmentKm = cumulativeKm[i] - cumulativeKm[i - 1]
        val t = if (segmentKm <= 0.0) 0.0 else (target - cumulativeKm[i - 1]) / segmentKm
        val a = points[i - 1]
        val b = points[i]
        return GeoPoint(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
    }

    /**
     * Projects [p] onto the route.
     *
     * With [hintKm] (the previous progress) the search stays close to where the car already was, so
     * a route that doubles back near itself cannot make progress jump. If nothing near the hint is
     * within [maxOffsetKm], the whole route is searched instead (for example after a detour).
     */
    fun project(p: GeoPoint, hintKm: Double? = null, maxOffsetKm: Double = 5.0): Projection {
        if (hintKm != null) {
            val near = projectWithin(p, hintKm - 2.0, hintKm + 40.0)
            if (near != null && near.offsetKm <= maxOffsetKm) return near
        }
        return projectWithin(p, 0.0, lengthKm) ?: Projection(0.0, Geo.distanceKm(p, points.first()))
    }

    private fun projectWithin(p: GeoPoint, fromKm: Double, toKm: Double): Projection? {
        var best: Projection? = null
        for (i in 1 until points.size) {
            if (cumulativeKm[i] < fromKm || cumulativeKm[i - 1] > toKm) continue
            val a = points[i - 1]
            val b = points[i]
            // Flat-earth approximation around the segment start: accurate to metres at this scale.
            val kmPerDegLat = 110.574
            val kmPerDegLon = 111.320 * cos(Math.toRadians(a.lat))
            val bx = (b.lon - a.lon) * kmPerDegLon
            val by = (b.lat - a.lat) * kmPerDegLat
            val px = (p.lon - a.lon) * kmPerDegLon
            val py = (p.lat - a.lat) * kmPerDegLat
            val lengthSq = bx * bx + by * by
            val t = if (lengthSq <= 0.0) 0.0 else ((px * bx + py * by) / lengthSq).coerceIn(0.0, 1.0)
            val dx = px - bx * t
            val dy = py - by * t
            val offset = sqrt(dx * dx + dy * dy)
            val along = cumulativeKm[i - 1] + (cumulativeKm[i] - cumulativeKm[i - 1]) * t
            if (best == null || offset < best.offsetKm) best = Projection(along, offset)
        }
        return best
    }

    /** Kilometres from [fromKm] to [toKm] along the route, never negative. */
    fun aheadKm(fromKm: Double, toKm: Double): Double = max(0.0, toKm - fromKm)
}
