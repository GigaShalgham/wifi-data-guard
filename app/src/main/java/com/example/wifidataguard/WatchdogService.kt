package com.example.wifidataguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.Locale

class WatchdogService : Service() {

    companion object {
        @Volatile var latched = false
            private set

        fun unlatch(c: Context) {
            latched = false
            c.getSharedPreferences("guard_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("latched", false).apply()
            if (BlockerVpnService.running) BlockerVpnService.release(c)
            OwnerEnforcer.trySilentWifiOn(c)
            Logger.d(c, "LATCH released (manual)")
        }

        private fun persistLatched(c: Context, v: Boolean) {
            c.getSharedPreferences("guard_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("latched", v).apply()
        }

        fun restoreLatched(c: Context): Boolean =
            c.getSharedPreferences("guard_prefs", Context.MODE_PRIVATE)
                .getBoolean("latched", false)
    }

    private lateinit var handler: Handler
    private val tick = Runnable { cycle() }
    private var lastPeriod = 0L
    private var lastNotifLine: String? = null

    private val wifiGuard = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                WifiManager.WIFI_STATE_UNKNOWN)
            if (state == WifiManager.WIFI_STATE_ENABLED && latched &&
                !Prefs.hardMode(this@WatchdogService)) {
                Logger.d(this@WatchdogService, "wifiGuard: ON while latched -> OFF")
                OwnerEnforcer.trySilentWifiOff(context)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        handler = Handler(Looper.getMainLooper())
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("watchdog", "Usage monitor",
                NotificationManager.IMPORTANCE_LOW))
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("alerts", "Limit warnings",
                NotificationManager.IMPORTANCE_HIGH))
        ContextCompat.registerReceiver(this, wifiGuard,
            IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED)

        lastPeriod = Prefs.currentPeriodStart(this)
        latched = restoreLatched(this)
        LiveCounter.seedWith(
            DataStats.effectiveUsage(this, lastPeriod).coerceAtLeast(0))
        Logger.d(this, "Watchdog created (latched=$latched)")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try { startForeground(1, notifLow(statusLine())) } catch (_: Throwable) {}
        handler.removeCallbacks(tick); handler.post(tick)
        return START_STICKY
    }

    private fun wifiActive(): Boolean = try {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false)
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true)
            getSystemService(WifiManager::class.java)?.isWifiEnabled == true
        else caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    } catch (_: Throwable) { false }

    private fun cycle() {
        if (!Prefs.monitoring(this)) {
            if (BlockerVpnService.running) BlockerVpnService.release(this)
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return
        }

        LiveCounter.poll(wifiActive())

        val limit = Prefs.limitBytes(this)

        // period rollover (midnight / month) -> fresh start AND clear grace
        val nowPeriod = Prefs.currentPeriodStart(this)
        if (nowPeriod != lastPeriod) {
            lastPeriod = nowPeriod
            latched = false
            persistLatched(this, false)
            Prefs.setGraceUntil(this, 0L)          // <<< FIX: kill "period-end" grace
            // FIX: force-reset the counter; seedWith() never lowers a value, so
            // yesterday's usage survived the rollover and re-latched instantly
            val fresh = DataStats.effectiveUsage(this, nowPeriod)
            if (fresh >= 0) LiveCounter.resetTo(fresh)
            else Logger.d(this, "rollover: usage stats unavailable, keeping live counter")
            Logger.d(this, "new period -> latch + grace + counter reset")
        }

        // FIX: read AFTER the rollover block, otherwise a fresh period is judged
        // against the previous period's stale usage
        val used = LiveCounter.currentBytes()
        val grace = System.currentTimeMillis() < Prefs.graceUntil(this)

        if (!latched && !grace && limit > 0 && used >= limit) {
            latched = true
            persistLatched(this, true)
            Logger.d(this, "*** LIMIT HIT (${humanize(used)}) -> LATCHED ***")

            val banner = Notification.Builder(this, "alerts")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(if (Prefs.lang(this) == "fa") "🔒 مصرف به پایان رسید"
                else "🔒 Limit reached")
                .setContentText(if (Prefs.lang(this) == "fa")
                    "اینترنت تا پایان امروز قفل شد"
                else "Internet is locked until tomorrow")
                .setAutoCancel(true)
                .setContentIntent(openApp())
                .build()
            getSystemService(NotificationManager::class.java).notify(5, banner)
        }

        Logger.d(this, "tick live=${humanize(used)} limit=${humanize(limit)} " +
                "latched=$latched grace=$grace hard=${Prefs.hardMode(this)}")

        if (latched && !grace) {
            if (Prefs.hardMode(this)) {
                if (!BlockerVpnService.running) BlockerVpnService.start(this)
            } else {
                // FIX: soft lock — try to turn Wi-Fi off; on Android 10+ that only
                // works for device owners. Fall back to the VPN blocker when it
                // fails, otherwise the "lock" is purely cosmetic.
                val wifiOn = getSystemService(WifiManager::class.java)?.isWifiEnabled == true
                if (wifiOn && !OwnerEnforcer.trySilentWifiOff(this)
                    && !BlockerVpnService.running) {
                    BlockerVpnService.start(this)
                    Logger.d(this, "soft lock unavailable -> VPN fallback")
                }
            }
        } else {
            if (BlockerVpnService.running) BlockerVpnService.release(this)
            if (latched && grace) OwnerEnforcer.trySilentWifiOn(this)
        }

        val line = statusLine()
        if (line != lastNotifLine) {              // throttle: update only on change
            lastNotifLine = line
            getSystemService(NotificationManager::class.java).notify(1, notifLow(line))
        }
        handler.postDelayed(tick, 1_000L)
    }

    private fun statusLine(): String {
        val fa = Prefs.lang(this) == "fa"
        val limit = Prefs.limitBytes(this)
        if (limit <= 0) return if (fa) "حدی تعیین نشده" else "No limit set"
        val used = LiveCounter.currentBytes()
        val graceLeft = (Prefs.graceUntil(this) - System.currentTimeMillis()) / 1000
        val minsLeft = graceLeft / 60 + 1
        return when {
            graceLeft > 0 -> if (fa) "مهلت آزاد: قفل مجدد تا ~$minsLeft دقیقه"
                             else "Grace: re-arms in ~$minsLeft min"
            latched -> if (fa) "به حد رسید (${humanize(used)} مصرف)"
                       else "LIMIT REACHED (${humanize(used)} used)"
            else -> "${humanize(used)} / ${humanize(limit)} (${
                ((used * 100.0 / limit).toInt()).coerceIn(0, 100)}%)"
        }
    }

    private fun humanize(b: Long) =
        if (b >= 1073741824) String.format(Locale.US, "%.2f GB", b / 1073741824.0)
        else String.format(Locale.US, "%.1f MB", b / 1048576.0)

    private fun notifLow(text: String): Notification =
        Notification.Builder(this, "watchdog")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Wi-Fi Data Guard").setContentText(text)
            .setOngoing(true).setContentIntent(openApp()).build()

    private fun openApp() = PendingIntent.getActivity(this, 0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        try { unregisterReceiver(wifiGuard) } catch (_: Throwable) {}
        super.onDestroy()
    }
}