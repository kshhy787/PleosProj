package app.gildongmu.host.platform

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import app.gildongmu.core.Signal
import app.gildongmu.core.Waypoint

/**
 * Navigation through the Pleos NaviHelper SDK (ai.pleos.playground:NaviHelper:3.0.5; its data
 * classes come from ai.pleos.playground:navi-data:3.0.4).
 *
 * Checked against the SDK's class files:
 *   NaviHelper(context); initialize(onConnected: () -> Unit, onFailed: (Throwable) -> Unit);
 *   addListener / removeListener(NaviHelperEventListener) - every callback has an empty default;
 *   onRouteStarted(RouteStartInfo); onDestinationArrived(DestinationArrivedInfo); onError(NaviErrorCode);
 *   addWaypoint(RequestWaypointInfo(latitude, longitude, poiName: String?, waypointIndex, poiId,
 *   address, poiSubId, poiProvider = "")) - poiId, address and poiSubId are required.
 *   Permissions: NAVI_ROUTE (the getXxxInfo() queries), NAVI_ROUTE_SEARCH (addWaypoint and the
 *   other route changes); the SDK throws SecurityException at the call when one is missing.
 *   Callbacks arrive on an SDK thread; [emit] hands them to the main thread.
 *
 * VERIFY on a device:
 *   - whether the navigation accepts a stop given only by coordinates and a name (poiId, address
 *     and poiSubId empty), and whether WaypointIndex.FIRST replaces or precedes an existing stop
 *   - onTBTInfo(List<TBTInfo>) is not wired up yet: TBTInfo(type, distance: Double, description)
 *     does not say what unit the distance is in. Once known, emit Signal.TurnGuidance(distance to
 *     the next instruction in metres, or null when it has passed) so the host stops talking for
 *     navigation prompts.
 */
class PleosNavigation(context: Context, private val emit: (Signal) -> Unit) {

    private val navi = NaviHelper(context.applicationContext)

    // Set on the SDK's thread, read on the main thread.
    @Volatile private var connected = false

    private val events = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            // RouteStartInfo has duration and distance, but not their units: left out until known.
            emit(Signal.RouteStarted())
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            emit(Signal.DestinationArrived)
        }

        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "Navigation error: $errorCode")
        }
    }

    init {
        navi.initialize(
            onConnected = {
                connected = true
                navi.addListener(events)
            },
            onFailed = { throwable -> Log.e(TAG, "NaviHelper did not connect", throwable) }
        )
    }

    /** Adds [waypoint], a rest stop or a chosen spot, as the first waypoint of the current route. */
    fun addWaypoint(waypoint: Waypoint) {
        if (!connected) return
        try {
            navi.addWaypoint(
                RequestWaypointInfo(
                    latitude = waypoint.location.lat,
                    longitude = waypoint.location.lon,
                    poiName = waypoint.name,
                    waypointIndex = WaypointIndex.FIRST,
                    // Required by the SDK; stops and spots in the route package have no POI id or address.
                    poiId = "",
                    address = "",
                    poiSubId = ""
                )
            )
        } catch (e: SecurityException) {
            // NAVI_ROUTE_SEARCH not granted: the SDK throws here instead of reporting an error.
            Log.e(TAG, "Not allowed to add a waypoint", e)
        }
    }

    fun shutdown() {
        if (connected) navi.removeListener(events)
        navi.release()
    }

    private companion object {
        const val TAG = "Gildongmu"
    }
}
