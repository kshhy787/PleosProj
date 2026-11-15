package app.gildongmu.host.platform

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import app.gildongmu.core.GeoPoint
import app.gildongmu.core.Signal

/**
 * Position through the Pleos FusedLocation SDK (ai.pleos.playground:FusedLocation:1.1.3).
 *
 * Checked against the SDK's class files and sources:
 *   FusedLocation(context); isSupported() only looks for the location service's package;
 *   initialize() binds the service in the background (retrying for up to 15 s), and calls made
 *   before it is bound are queued; registerFusedLocationCallback(FusedLocationListener);
 *   release() unregisters the callback and unbinds.
 *   FusedLocationListener.onChanged(LocationData?) and onError(Exception) arrive on a binder
 *   thread; [emit] hands them to the main thread.
 *   LocationData(latitude, longitude, altitude: Double, heading, confidence: Float, timestampMillis: Long).
 *   register/unregister throw SecurityException unless pleos.car.permission.FUSED_LOCATION is
 *   granted, and the SDK calls it a dangerous permission: the app has to ask for it at run time
 *   ([PleosPlatform] does, and calls [start] again once it is answered).
 *
 * VERIFY on a device:
 *   - that the location service (ai.pleos.playground.service.location.fused) is on the image and
 *     binds; the SDK only logs it if the bind keeps failing, and no position ever arrives
 *   - that asking for FUSED_LOCATION shows the usual permission dialog
 */
class PleosLocation(context: Context, private val emit: (Signal) -> Unit) {

    private val appContext = context.applicationContext
    private val fused = FusedLocation(appContext)
    private var initialized = false
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

    /** Starts the position updates. Safe to call again, for instance once the permission is granted. */
    fun start() {
        if (registered) return
        if (!fused.isSupported()) {
            Log.w(TAG, "FusedLocation is not supported on this vehicle")
            return
        }
        if (appContext.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "FusedLocation needs $PERMISSION")
            return
        }
        if (!initialized) {
            fused.initialize()
            initialized = true
        }
        fused.registerFusedLocationCallback(listener)
        registered = true
    }

    fun shutdown() {
        // release() also unregisters the callback, without the permission check that
        // unregisterFusedLocationCallback makes (which would throw if the permission was revoked).
        if (initialized) fused.release()
        initialized = false
        registered = false
    }

    companion object {
        const val PERMISSION = "pleos.car.permission.FUSED_LOCATION"
        private const val TAG = "Gildongmu"
    }
}
