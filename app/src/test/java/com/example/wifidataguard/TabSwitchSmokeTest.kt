package com.example.wifidataguard

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Tab-switch smoke test (spec-010 FR-108) — the companion of the launch
 * smoke gate: drives bottom-navigation switches across all three tabs and
 * force-draws the decor after each, so any inflate/measure/draw crash in
 * the new tab content (ring, chart, stats) fails the release here. Also
 * proves the Parent tab PIN gate keeps the panel hidden until unlocked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = GuardApp::class)
class TabSwitchSmokeTest {

    private fun coldStart(): MainActivity {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.setup()
        shadowOf(Looper.getMainLooper()).idle()
        return controller.get()
    }

    private fun forceDraw(a: MainActivity) {
        val decor = a.window.decorView
        decor.measure(0, 0)
        decor.layout(0, 0, 1080, 2340)
        decor.draw(Canvas(Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)))
    }

    private fun visible(a: MainActivity, id: Int): Int =
        a.findViewById<View>(id).visibility

    @Test
    fun usageTabSwitchesAndDraws() {
        val a = coldStart()
        val nav = a.findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.selectedItemId = R.id.nav_usage
        shadowOf(Looper.getMainLooper()).idle()
        forceDraw(a)
        assert(visible(a, R.id.tabUsage) == View.VISIBLE)
        assert(visible(a, R.id.tabStatus) == View.GONE)
        nav.selectedItemId = R.id.nav_status
        shadowOf(Looper.getMainLooper()).idle()
        forceDraw(a)
        assert(visible(a, R.id.tabStatus) == View.VISIBLE)
    }

    @Test
    fun parentTabIsPinGatedWhileArmed() {
        val app = RuntimeEnvironment.getApplication() as Application
        Prefs.setLang(app, "en")
        Prefs.setMonitoring(app, true)
        Prefs.setPin(app, "1234")
        try {
            val a = coldStart()
            val nav = a.findViewById<BottomNavigationView>(R.id.bottomNav)
            nav.selectedItemId = R.id.nav_parent
            shadowOf(Looper.getMainLooper()).idle()
            forceDraw(a)
            // gated: PIN dialog path taken, tab must stay hidden, previous tab visible
            assert(visible(a, R.id.tabParent) == View.GONE)
            assert(visible(a, R.id.tabStatus) == View.VISIBLE)
        } finally {
            Prefs.setMonitoring(app, false)
        }
    }
}
