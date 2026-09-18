package com.example.wifidataguard

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Launch smoke test (investigation for the v1.3.5 launch-crash report).
 * Drives the EXACT cold-start path of MainActivity with the real compiled
 * resources: theme apply -> setContentView inflate -> findViewById ->
 * applyTexts -> entrance -> onResume -> refreshUi -> startHalo -> uiTick,
 * then force-draws the decor view so window/drawable inflation runs too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = GuardApp::class)
class LaunchSmokeTest {

    private fun coldStart(): MainActivity {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.setup() // create -> start -> resume
        shadowOf(Looper.getMainLooper()).idle()
        val a = controller.get()
        // force a full measure/layout/draw pass (inflates window bg + drawables)
        val decor = a.window.decorView
        decor.measure(0, 0)
        decor.layout(0, 0, 1080, 2340)
        decor.draw(Canvas(Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)))
        return a
    }

    @Test
    fun freshInstallColdStart() {
        val a = coldStart()
        assert(a.findViewById<View>(android.R.id.content) != null)
    }

    @Test
    fun armedKidPhoneColdStart() {
        val app = RuntimeEnvironment.getApplication() as Application
        // seed the kid's phone state: armed, PIN set, latched, FA language
        Prefs.setLang(app, "fa")
        Prefs.setLimitBytes(app, 500L * 1024 * 1024)
        Prefs.setMonitoring(app, true)
        Prefs.setHardMode(app, true)
        Prefs.setPin(app, "1234")
        Prefs.setUnlockMinutes(app, 15)
        WatchdogService.latchCloud(app, "cloud")
        try {
            val a = coldStart()
            assert(a.findViewById<View>(android.R.id.content) != null)
        } finally {
            WatchdogService.unlatch(app)
        }
    }
}
