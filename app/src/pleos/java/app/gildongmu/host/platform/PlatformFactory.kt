package app.gildongmu.host.platform

import android.app.Activity
import app.gildongmu.host.AppPlatform
import app.gildongmu.host.ui.HostScreen

/** The `pleos` build: the host talks to the car through the Pleos Connect SDK. */
object PlatformFactory {
    fun create(activity: Activity, screen: HostScreen): AppPlatform = PleosPlatform(activity, screen)
}
