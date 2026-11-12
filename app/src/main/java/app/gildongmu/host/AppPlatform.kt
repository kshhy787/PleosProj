package app.gildongmu.host

/**
 * What [MainActivity] needs from a build flavour. The `mock` and `pleos` source sets each provide
 * a `PlatformFactory` object with the same name that returns one of these, so the activity and the
 * screen are identical in both builds.
 */
interface AppPlatform {
    /** Called once, after the screen exists. */
    fun start()

    /** The user answered a permission request. */
    fun onPermissionsChanged()

    /** The activity is going away: release speech engines, listeners and timers. */
    fun shutdown()
}
