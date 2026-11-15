package app.gildongmu.host.platform

import ai.pleos.playground.vehicle.Vehicle
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.gildongmu.core.LightCue
import app.gildongmu.core.Signal

/**
 * Trip time, trip distance and battery through the Pleos Vehicle SDK (ai.pleos.playground:Vehicle:2.3.2).
 *
 * Checked against the SDK's class files:
 *   Vehicle(context); initialize(); getOdometer(), getEvBattery(); release().
 *   Odometer.getTripCurrentDrivingTime(onComplete: (Int?) -> Unit, onFailed: (Exception) -> Unit)
 *   Odometer.getTripCurrentDrivingDistance(onComplete: (Float?) -> Unit, onFailed)
 *   EvBattery.getEvBatteryStateOfCharge(onComplete: (Int?) -> Unit, onFailed)
 *   EvBattery.getEvChargeSwitchState(onComplete: (Boolean?) -> Unit, onFailed)
 *   Permissions: CAR_DRIVE (Odometer), CAR_ENERGY (EvBattery); a missing one is reported through
 *   onFailed, not thrown. A value is null when the service sends none.
 *   Values arrive on an SDK background thread, so each one is posted to the main thread first.
 *
 * The values are polled. The SDK also has register... listeners for time, distance and state of
 * charge (not for the charge switch); polling keeps all four on one path and is quick enough for
 * "two hours of driving" and "battery at 20%".
 *
 * VERIFY on a car or the emulator:
 *   - the UNITS: the SDK passes the service's numbers through unconverted; the code assumes
 *     minutes, kilometres and percent
 *   - that the charge switch is on while charging (no public read of the charging state exists)
 *   - ambient light for [light]: Light.setAmbientLightSwitch / setAmbientLightBrightnessColor take
 *     a SeatArea and need CONTROL_CAR_LIGHTS, which the manifest does not declare yet
 */
class PleosVehicle(context: Context, private val emit: (Signal) -> Unit) {

    private val vehicle = Vehicle(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())

    private var running = false
    private var tripMinutes: Double? = null
    private var tripKm: Double? = null
    private var batteryPercent: Int? = null
    private var charging = false

    /** Values already logged as unavailable. Touched on the main thread only. */
    private val reported = HashSet<String>()

    private val poll = object : Runnable {
        override fun run() {
            read()
            main.postDelayed(this, POLL_MS)
        }
    }

    fun start() {
        vehicle.initialize()
        running = true
        main.post(poll)
    }

    private fun read() {
        val odometer = vehicle.getOdometer()
        odometer.getTripCurrentDrivingTime(
            onComplete = { value: Int? ->
                onMain {
                    tripMinutes = value?.toDouble()
                    reportTrip()
                }
            },
            onFailed = { e -> unavailable("Trip time", e) }
        )
        odometer.getTripCurrentDrivingDistance(
            onComplete = { value: Float? ->
                onMain {
                    tripKm = value?.toDouble()
                    reportTrip()
                }
            },
            onFailed = { e -> unavailable("Trip distance", e) }
        )

        val battery = vehicle.getEvBattery()
        battery.getEvBatteryStateOfCharge(
            onComplete = { value: Int? ->
                onMain {
                    batteryPercent = value
                    reportBattery()
                }
            },
            onFailed = { e -> unavailable("Battery level", e) }
        )
        battery.getEvChargeSwitchState(
            onComplete = { value: Boolean? ->
                onMain {
                    charging = value == true
                    reportBattery()
                }
            },
            onFailed = { e -> unavailable("Charging state", e) }
        )
    }

    /**
     * A value the car does not provide fails on every poll (the Pleos Connect emulator answers
     * ERROR_NOT_SUPPORTED_PROPERTY for trip time and distance), so each one is logged once.
     */
    private fun unavailable(what: String, e: Exception) {
        onMain {
            if (reported.add(what)) Log.w(TAG, "$what unavailable; still asking every ${POLL_MS / 1000} s, without logging again", e)
        }
    }

    /** SDK callbacks come on a background thread and may come after [shutdown]. */
    private fun onMain(block: () -> Unit) {
        main.post { if (running) block() }
    }

    private fun reportTrip() {
        val minutes = tripMinutes ?: return
        emit(Signal.Trip(minutes, tripKm))
    }

    private fun reportBattery() {
        val percent = batteryPercent ?: return
        emit(Signal.Battery(percent, charging))
    }

    /** Not implemented yet: see VERIFY above. The host works without it. */
    fun light(cue: LightCue) {
        Log.i(TAG, "Ambient light cue: $cue")
    }

    fun shutdown() {
        running = false
        // Only this class's own Handler: the poll and any callbacks still waiting to run.
        main.removeCallbacksAndMessages(null)
        vehicle.release()
    }

    private companion object {
        const val TAG = "Gildongmu"
        const val POLL_MS = 5_000L
    }
}
