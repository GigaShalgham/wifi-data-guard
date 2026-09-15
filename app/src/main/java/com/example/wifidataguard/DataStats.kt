package com.example.wifidataguard

import android.app.AppOpsManager
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Process
import android.provider.Settings
import java.util.Locale

object DataStats {

    fun wifiUsageBytes(context: Context, from: Long,
                       to: Long = System.currentTimeMillis()): Long {
        val nsm = context.getSystemService(NetworkStatsManager::class.java) ?: return -1
        return try {
            @Suppress("DEPRECATION")
            val b = nsm.querySummaryForDevice(
                ConnectivityManager.TYPE_WIFI, null, from, to)
            b.rxBytes + b.txBytes
        } catch (_: Throwable) { -1 }
    }

    fun effectiveUsage(c: Context, period: Long): Long {
        val raw = wifiUsageBytes(c, period)
        if (raw < 0) return -1
        val rp = Prefs.resetPeriod(c)
        val rb = Prefs.resetBytes(c)
        return if (rp == period && raw >= rb) raw - rb else raw
    }

    fun resetBaseline(c: Context) {
        val period = Prefs.currentPeriodStart(c)
        val raw = wifiUsageBytes(c, period)
        Prefs.setResetPeriod(c, period)
        Prefs.setResetBytes(c, if (raw > 0) raw else 0L)
        Logger.d(c, "Reset: baseline=" +
                String.format(Locale.US, "%.1f MB", (raw.coerceAtLeast(0)) / 1048576.0))
    }

    fun hasUsageAccess(c: Context): Boolean {
        val ops = c.getSystemService(AppOpsManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        return ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessScreen(c: Context) {
        c.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}