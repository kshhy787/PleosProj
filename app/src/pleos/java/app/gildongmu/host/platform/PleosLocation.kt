package app.gildongmu.host.platform

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.util.Log
import app.gildongmu.core.GeoPoint
import app.gildongmu.core.Signal

/**
 * Position through the Pleos FusedLocation SDK (ai.pleos.playground:FusedLocation).
 *
 * From the guide pages (initialization and release, location data access):
 *   FusedLocation(context); isSupported(); initialize();
 *   registerFusedLocationCallback(object : FusedLocationListener {
 *       override fun onChanged(location: LocationData?); override fun onError(exception: Exception) });
 *   unregisterFusedLocationCallback(listener); release().
 *   Permission: pleos.car.permission.FUSED_LOCATION.
 *
 * VERIFY on first build:
 *   - the property names on LocationData; the guide says it carries latitude, longitude, altitude,
 *     heading, reliability and timestamp, but not what the properties are called
 */
class PleosLocation(context: Context, private val emit: (Signal) -> Unit) {

    private val fused = FusedLocation(context.applicationContext)
    private var registered = false

    private val listener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            location ?: return
            emit(Signal.Location(GeoPoint(location.latitude, location.longitude)))
        }

        override fun onError(exception: Exception) {
            Log.w(TAG, "Location error", exception)
        }
    }

    fun start() {
        if (!fused.isSupported()) {
            Log.w(TAG, "FusedLocation is not supported on this vehicle")
            return
        }
        fused.initialize()
        fused.registerFusedLocationCallback(listener)
        registered = true
    }

    fun shutdown() {
        if (registered) fused.unregisterFusedLocationCallback(listener)
        fused.release()
    }

    private companion object {
        const val TAG = "Gildongmu"
    }
}
