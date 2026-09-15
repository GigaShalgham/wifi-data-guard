package com.example.wifidataguard

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.net.wifi.WifiManager
import android.os.UserManager

object OwnerEnforcer {

    private fun dpm(c: Context) = c.getSystemService(DevicePolicyManager::class.java)
    private fun admin(c: Context) = ComponentName(c, AdminReceiver::class.java)

    fun isDeviceOwner(c: Context): Boolean = try {
        dpm(c)?.isDeviceOwnerApp(c.packageName) == true
    } catch (_: Throwable) { false }

    private val restrictions = arrayOf(
        UserManager.DISALLOW_CONFIG_WIFI,
        UserManager.DISALLOW_SAFE_BOOT,
        UserManager.DISALLOW_ADD_USER,
        UserManager.DISALLOW_FACTORY_RESET,
        UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA
    )

    fun lockdown(c: Context) {
        if (!isDeviceOwner(c)) return
        val d = dpm(c) ?: return
        val adm = admin(c)
        restrictions.forEach { r ->
            try { d.addUserRestriction(adm, r) } catch (_: Throwable) {}
        }
        try { d.setUninstallBlocked(adm, c.packageName, true) } catch (_: Throwable) {}
        Logger.d(c, "lockdown applied")
    }

    fun relax(c: Context) {
        if (!isDeviceOwner(c)) return
        val d = dpm(c) ?: return
        val adm = admin(c)
        restrictions.forEach { r ->
            try { d.clearUserRestriction(adm, r) } catch (_: Throwable) {}
        }
        try { d.setUninstallBlocked(adm, c.packageName, false) } catch (_: Throwable) {}
        Logger.d(c, "restrictions cleared")
    }

    @Suppress("DEPRECATION")
    fun trySilentWifiOff(c: Context): Boolean = try {
        val wm = c.getSystemService(WifiManager::class.java)
        val ok = wm?.setWifiEnabled(false) ?: false
        if (ok) markWifiForced(c, true)
        ok
    } catch (_: Throwable) { false }

    @Suppress("DEPRECATION")
    fun trySilentWifiOn(c: Context): Boolean = try {
        c.getSystemService(WifiManager::class.java)?.setWifiEnabled(true) ?: false
    } catch (_: Throwable) { false }

    // ---- tiny memory: did WE turn wifi off? ----
    private fun markWifiForced(c: Context, v: Boolean) =
        c.getSharedPreferences("guard_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("wifi_forced", v).apply()

    fun wifiWasForced(c: Context): Boolean =
        c.getSharedPreferences("guard_prefs", Context.MODE_PRIVATE)
            .getBoolean("wifi_forced", false)

    fun clearWifiForced(c: Context) = markWifiForced(c, false)
}