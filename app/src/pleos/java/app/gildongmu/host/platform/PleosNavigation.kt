package app.gildongmu.host.platform

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import app.gildongmu.core.Signal
import app.gildongmu.core.Waypoint

/**
 * Navigation through the Pleos NaviHelper SDK (ai.pleos.playground:NaviHelper).
 *
 * From the guide pages (initialization, event listeners, waypoint settings):
 *   NaviHelper(applicationContext); initialize(onConnected = {}, onFailed = { throwable -> });
 *   addListener(NaviHelperEventListener) - override only the callbacks needed;
 *   onRouteStarted(routeStartInfo: RouteStartInfo); onError(errorCode: NaviErrorCode);
 *   addWaypoint(RequestWaypointInfo(latitude =, longitude =, waypointIndex = WaypointIndex.FIRST, ...)).
 *   Permissions: NAVI_ROUTE (information), NAVI_ROUTE_SEARCH (addWaypoint).
 *
 * VERIFY on first build:
 *   - onDestinationArrived: the guide lists it without parameters
 *   - RequestWaypointInfo: the guide sample also passes poiId, poiName, poiSubId and address;
 *     if they are required, give them here
 *   - onTBTInfo is not wired up yet: its parameter type is not shown in the public guides. Once
 *     known, emit Signal.TurnGuidance(distance to the next instruction in metres, or null when
 *     it has passed) so the host stops talking for navigation prompts.
 */
class PleosNavigation(context: Context, private val emit: (Signal) -> Unit) {

    private val navi = NaviHelper(context.applicationContext)
    private var connected = false

    private val events = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            emit(Signal.RouteStarted())
        }

        override fun onDestinationArrived() {
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
        navi.addWaypoint(
            RequestWaypointInfo(
                latitude = waypoint.location.lat,
                longitude = waypoint.location.lon,
                waypointIndex = WaypointIndex.FIRST,
                poiName = waypoint.name
            )
        )
    }

    fun shutdown() {
        if (connected) navi.removeListener(events)
        navi.release()
    }

    private companion object {
        const val TAG = "Gildongmu"
    }
}
