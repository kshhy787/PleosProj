package app.gildongmu.host.platform

import android.app.Activity
import app.gildongmu.host.AppPlatform
import app.gildongmu.host.ui.HostScreen

/** The `mock` build: a scripted drive and Android's own speech services. No car needed. */
object PlatformFactory {
    fun create(activity: Activity, screen: HostScreen): AppPlatform = MockPlatform(activity, screen)
}
