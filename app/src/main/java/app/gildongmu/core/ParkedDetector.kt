package app.gildongmu.core

/**
 * Works out "the car is parked" from position alone, for a platform that does not report the
 * gear or the parking brake.
 *
 * The car counts as parked once it has stayed within [stillRadiusM] of one spot for
 * [stillForMs], or as soon as it starts charging. It counts as driving again when it has moved
 * more than [movedRadiusM] from where it stood. A red light or a traffic jam is shorter than
 * [stillForMs], so neither is mistaken for a stop.
 */
class ParkedDetector(
    private val stillForMs: Long = 120_000,
    private val stillRadiusM: Double = 30.0,
    private val movedRadiusM: Double = 60.0
) {
    private var anchor: GeoPoint? = null
    private var anchorSinceMs = 0L
    private var charging = false

    var parked = false
        private set

    /** Feeds a position fix. Returns the new state if it changed, otherwise null. */
    fun onLocation(point: GeoPoint, nowMs: Long): Boolean? {
        val reference = anchor
        if (reference == null) {
            anchor = point
            anchorSinceMs = nowMs
            return null
        }
        val movedM = Geo.distanceKm(reference, point) * 1000.0
        if (parked) {
            if (movedM > movedRadiusM && !charging) {
                anchor = point
                anchorSinceMs = nowMs
                return change(false)
            }
            return null
        }
        if (movedM > stillRadiusM) {
            anchor = point
            anchorSinceMs = nowMs
            return null
        }
        return if (nowMs - anchorSinceMs >= stillForMs) change(true) else null
    }

    /** Feeds the charging state. A charging car is parked, whatever the position fixes say. */
    fun onCharging(nowCharging: Boolean): Boolean? {
        charging = nowCharging
        return if (nowCharging && !parked) change(true) else null
    }

    private fun change(nowParked: Boolean): Boolean {
        parked = nowParked
        return nowParked
    }
}
