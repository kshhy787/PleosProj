package app.gildongmu.host.platform

import ai.pleos.playground.vehicle.Vehicle
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.gildongmu.core.LightCue
import app.gildongmu.core.Signal

/**
 * Trip time, trip distance and battery through the Pleos Vehicle SDK (ai.pleos.playground:Vehicle).
 *
 * From the guide pages (obtain domain object, get property):
 *   Vehicle(context); initialize(); domain objects through getXxx(); release().
 *   A read is asynchronous: getSomething(onComplete = { value -> }, onFailed = { e: Exception -> }).
 *   Odometer methods: getTripCurrentDrivingTime, getTripCurrentDrivingDistance.
 *   EvBattery methods: getEvBatteryStateOfCharge, getEvChargeSwitchState.
 *   Permissions: CAR_DRIVE (Odometer), CAR_ENERGY (EvBattery).
 *
 * The values are polled rather than subscribed to, because the names of the listener interfaces
 * for these properties are not in the public guides. A host that reacts within a few seconds is
 * enough for "two hours of driving" and "battery at 20%".
 *
 * VERIFY on first build:
 *   - getOdometer() and getEvBattery() as the domain accessors (the guide shows getDoor/getDisplay)
 *   - the value types and UNITS of the four reads; the conversions below assume minutes,
 *     kilometres, percent, and a truthy "charging" value
 *   - the ambient-light setters for [light] (Light domain; needs CONTROL_CAR_LIGHTS approval)
 */
class PleosVehicle(context: Context, private val emit: (Signal) -> Unit) {

    private val vehicle = Vehicle(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())

    private var tripMinutes: Double? = null
    private var tripKm: Double? = null
    private var batteryPercent: Int? = null
    private var charging = false

    private val poll = object : Runnable {
        override fun run() {
            read()
            main.postDelayed(this, POLL_MS)
        }
    }

    fun start() {
        vehicle.initialize()
        main.post(poll)
    }

    private fun read() {
        val odometer = vehicle.getOdometer()
        odometer.getTripCurrentDrivingTime(
            onComplete = { value ->
                tripMinutes = number(value)
                reportTrip()
            },
            onFailed = { e -> Log.w(TAG, "Trip time unavailable", e) }
        )
        odometer.getTripCurrentDrivingDistance(
            onComplete = { value ->
                tripKm = number(value)
                reportTrip()
            },
            onFailed = { e -> Log.w(TAG, "Trip distance unavailable", e) }
        )

        val battery = vehicle.getEvBattery()
        battery.getEvBatteryStateOfCharge(
            onComplete = { value ->
                batteryPercent = number(value)?.toInt()
                reportBattery()
            },
            onFailed = { e -> Log.w(TAG, "Battery level unavailable", e) }
        )
        battery.getEvChargeSwitchState(
            onComplete = { value ->
                charging = truthy(value)
                reportBattery()
            },
            onFailed = { e -> Log.w(TAG, "Charging state unavailable", e) }
        )
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
        main.removeCallbacks(poll)
        vehicle.release()
    }

    // The reads are converted through Any? so that this file compiles whatever numeric or boolean
    // type the SDK turns out to use; an enum would need a line of its own here.
    private fun number(value: Any?): Double? = (value as? Number)?.toDouble()

    private fun truthy(value: Any?): Boolean = value == true || (number(value) ?: 0.0) > 0.0

    private companion object {
        const val TAG = "Gildongmu"
        const val POLL_MS = 5_000L
    }
}
