package app.gildongmu.host

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import app.gildongmu.host.platform.PlatformFactory
import app.gildongmu.host.ui.HostScreen
import app.gildongmu.host.ui.Palette

/**
 * The app's only screen. It builds the UI, hands it to whichever platform this build was made for
 * (see [PlatformFactory] in the `mock` and `pleos` source sets), and otherwise stays out of the way.
 */
class MainActivity : Activity() {

    private var platform: AppPlatform? = null

    /**
     * Holds the app's text at the size it was given, whatever font size the system is set to.
     * `ScreenFit` plans every height in dp and takes a line of text to be as high as its size in
     * sp makes it at a scale of 1. Text the system had made larger would be higher than the room
     * planned for it, and what gives way then is the last piece in the card: the bottom row with
     * the skip button. The diagnostics still show the system's own scale (`HostScreen.describe`).
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Only the font scale is overridden, so everything else keeps following the system.
        val onlyFontScale = Configuration()
        onlyFontScale.fontScale = 1f
        applyOverrideConfiguration(onlyFontScale)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val screen = HostScreen(this)
        // From Android 15 the window of an app that targets it extends under the system bars. This
        // frame takes the bars as padding, so the host screen is laid out in the space that is
        // actually visible. On earlier versions the system keeps the content clear of the bars
        // itself and the frame adds nothing.
        val frame = FrameLayout(this)
        frame.fitsSystemWindows = true
        frame.setBackgroundColor(Palette.BACKGROUND)
        frame.addView(screen.root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(frame)
        platform = PlatformFactory.create(this, screen).also { it.start() }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        platform?.onPermissionsChanged()
    }

    override fun onDestroy() {
        platform?.shutdown()
        platform = null
        super.onDestroy()
    }
}
