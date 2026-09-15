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
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class WatchdogService : Service() {

    companion object {
        @Volatile var latched = false
            private set

        fun unlatch(c: Context) {
            latched = false
            c.getSharedPreferences("guard_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("latched", false).putString("latch_reason", "").apply()
            if (BlockerVpnService.running) BlockerVpnService.release(c)
            OwnerEnforcer.trySilentWifiOn(c)
            Logger.d(c, "LATCH released (manual)")
        }

        /** Latch from the cloud / fail-closed path. Runs on any thread. */
        fun latchCloud(c: Context, reason: String) {
            latched = true
            c.getSharedPreferences("guard_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("latched", true).putString("latch_reason", reason).apply()
            Logger.d(c, "LATCHED (reason=$reason)")
        }

        private fun persistLatched(c: Context, v: Boolean) {
            c.getSharedPreferences("guard_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("latched", v).apply()
        }

        fun restoreLatched(c: Context): Boolean =
            c.getSharedPreferences("guard_prefs", Context.MODE_PRIVATE)
                .getBoolean("latched", false)

        /**
         * Spec-002 (US2): the dashboard unpaired this device while it was
         * locked. The latch is kept (fail-closed), but the user must be told
         * the truth: the parent link is gone, remote unlock is impossible,
         * and the parent PIN on this device is the way out. Runs on any
         * thread (called from the CloudLink poll executor).
         */
        fun notifyRevokedWhileLocked(c: Context) {
            val fa = Prefs.lang(c) == "fa"
            val title = if (fa) "\u26a0\ufe0f \u062f\u0633\u062a\u06af\u0627\u0647 \u062c\u062f\u0627 \u0634\u062f \u2014 \u0642\u0641\u0644 \u0628\u0627\u0642\u06cc \u0645\u0627\u0646\u062f"
                        else "\u26a0\ufe0f Device unpaired \u2014 still locked"
            val text = if (fa)
                "\u0627\u062a\u0635\u0627\u0644 \u0627\u0628\u0631\u06cc \u062d\u0630\u0641 \u0634\u062f\u061b \u062f\u06cc\u06af\u0631 \u0646\u0645\u06cc\u200c\u062a\u0648\u0627\u0646 \u0627\u0632 \u0631\u0627\u0647 \u062f\u0648\u0631 \u0628\u0627\u0632\u0634 \u06a9\u0631\u062f. " +
                "\u0628\u0631\u0627\u06cc \u0628\u0627\u0632\u06a9\u0631\u062f\u0646\u060c \u062f\u06a9\u0645\u0647\u0654 \u00ab\u0628\u0627\u0632\u06a9\u0631\u062f\u0646\u00bb \u0631\u0627 \u062f\u0631 \u0647\u0645\u06cc\u0646 \u0627\u067e \u0628\u0632\u0646 \u0648 \u0631\u0645\u0632 \u0648\u0627\u0644\u062f \u0631\u0627 \u0648\u0627\u0631\u062f \u06a9\u0646 " +
                "(\u06cc\u0627 \u0645\u062d\u0627\u0641\u0638 \u0631\u0627 \u062e\u0627\u0645\u0648\u0634 \u06a9\u0646)."
            else
                "The cloud link was removed, so remote unlock is no longer possible. " +
                "To unlock: tap Unlock in this app and enter the parent PIN " +
                "(or turn monitoring off)."
            val open = PendingIntent.getActivity(c, 0,
                Intent(c, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val banner = Notification.Builder(c, "alerts")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(title).setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true).setContentIntent(open).build()
            c.getSystemService(NotificationManager::class.java).notify(6, banner)
        }
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
        // cloud bridge: commands arrive on the main thread via this handler
        CloudLink.commandHandler = { ctx, cmds -> applyCloudCommands(ctx, cmds) }
        if (CloudLink.paired(this) && Prefs.cloudLastSync(this) == 0L)
            Prefs.setCloudLastSync(this, System.currentTimeMillis())
        Logger.d(this, "Watchdog created (latched=$latched reason=${Prefs.latchReason(this)})")
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

        // ---- cloud sync (Phase 2): poll if due + fail-closed checks ----
        CloudLink.pollIfDue(this)
        if (!latched && CloudLink.offlineLatchDue(this)) {
            latchCloud(this, "offline")
            notifyAlert(
                if (Prefs.lang(this) == "fa") "☁ ارتباط ابر قطع است" else "☁ Cloud unreachable",
                if (Prefs.lang(this) == "fa") "قفل امنیتی فعال شد (fail-closed)"
                else "Fail-closed lock engaged")
        }
        if (!latched && CloudLink.clockRolledBack(this)) {
            latchCloud(this, "clock")
            notifyAlert(
                if (Prefs.lang(this) == "fa") "🕐 ساعت دستگاه دستکاری شد" else "🕐 Clock tampered",
                if (Prefs.lang(this) == "fa") "قفل امنیتی فعال شد" else "Fail-closed lock engaged")
        }

        val limit = Prefs.limitBytes(this)

        // period rollover (midnight / month) -> fresh start AND clear grace.
        // Security: a cloud/offline/clock latch must SURVIVE the rollover —
        // otherwise waiting for midnight would defeat a remote lock.
        val nowPeriod = Prefs.currentPeriodStart(this)
        if (nowPeriod != lastPeriod) {
            lastPeriod = nowPeriod
            val reason = Prefs.latchReason(this)
            if (reason == "" || reason == "limit") {
                latched = false
                persistLatched(this, false)
                Prefs.setLatchReason(this, "")
            } else {
                Logger.d(this, "rollover: latch reason=$reason KEPT (not limit-based)")
            }
            Prefs.setGraceUntil(this, 0L)          // <<< FIX: kill "period-end" grace
            // FIX: force-reset the counter; seedWith() never lowers a value, so
            // yesterday's usage survived the rollover and re-latched instantly
            val fresh = DataStats.effectiveUsage(this, nowPeriod)
            if (fresh >= 0) LiveCounter.resetTo(fresh)
            else Logger.d(this, "rollover: usage stats unavailable, keeping live counter")
            Logger.d(this, "new period -> grace + counter reset")
        }

        // FIX: read AFTER the rollover block, otherwise a fresh period is judged
        // against the previous period's stale usage
        val used = LiveCounter.currentBytes()
        val grace = AppClock.now() < Prefs.graceUntil(this)

        if (!latched && !grace && limit > 0 && used >= limit) {
            latched = true
            persistLatched(this, true)
            Prefs.setLatchReason(this, "limit")
            Logger.d(this, "*** LIMIT HIT (${humanize(used)}) -> LATCHED ***")

            notifyAlert(
                if (Prefs.lang(this) == "fa") "🔒 مصرف به پایان رسید"
                else "🔒 Limit reached",
                if (Prefs.lang(this) == "fa")
                    "اینترنت تا پایان امروز قفل شد"
                else "Internet is locked until tomorrow")
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
                    BlockerVpnService.start(this, softFallback = true)
                    Logger.d(this, "soft lock unavailable -> VPN fallback")
                    // v1.3.1: say it out loud — the child/parent must know why a
                    // "soft" lock is behaving like a full internet cut-off.
                    val fa2 = Prefs.lang(this) == "fa"
                    notifyAlert(
                        if (fa2) "🔒 قفل نرم — کنترل Wi-Fi در دسترس نیست"
                        else "🔒 Soft lock — no Wi-Fi control",
                        if (fa2) "بدون امتیاز Device Owner وای‌فای خاموش نمی‌شود؛ " +
                            "به‌جای آن کل اینترنت قطع شد"
                        else "Wi-Fi cannot be switched off without device-owner " +
                            "rights; ALL internet is blocked instead")
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
        val graceLeft = (Prefs.graceUntil(this) - AppClock.now()) / 1000
        val minsLeft = graceLeft / 60 + 1
        return when {
            graceLeft > 0 -> if (fa) "مهلت آزاد: قفل مجدد تا ~$minsLeft دقیقه"
                             else "Grace: re-arms in ~$minsLeft min"
            latched -> when (Prefs.latchReason(this)) {
                "cloud"   -> if (fa) "قفل از راه دور (والد)" else "Locked by parent"
                "offline" -> if (fa) "بدون ارتباط ابر — قفل امنیتی" else "Cloud unreachable - fail-closed"
                "clock"   -> if (fa) "ساعت دستکاری شد — قفل" else "Clock tampered - locked"
                "unpaired"-> if (fa) "قفل باقی مانده — دستگاه جدا شد (رمز والد باز می‌کند)"
                            else "Locked — device unpaired (parent PIN unlocks)"
                else      -> if (fa) "به حد رسید (${humanize(used)} مصرف)"
                             else "LIMIT REACHED (${humanize(used)} used)"
            }
            else -> "${humanize(used)} / ${humanize(limit)} (${
                ((used * 100.0 / limit).toInt()).coerceIn(0, 100)}%)"
        }
    }

    // ---- cloud command application (main thread) ----

    private fun applyCloudCommands(c: Context, cmds: JSONArray) {
        val ackIds = mutableListOf<Int>()
        val fa = Prefs.lang(c) == "fa"
        for (i in 0 until cmds.length()) {
            val cmd = try { cmds.getJSONObject(i) } catch (_: Exception) { continue }
            val id = cmd.optInt("id", 0)
            when (cmd.optString("type")) {
                "lock" -> {
                    Prefs.setGraceUntil(c, 0L)      // a remote lock cancels any grace
                    latchCloud(c, "cloud")
                    notifyAlert(
                        if (fa) "🔒 قفل از راه دور" else "🔒 Locked by parent",
                        if (fa) "والد اینترنت را قفل کرد" else "The parent locked the internet")
                }
                "unlock" -> {
                    val mins = cmd.optJSONObject("payload")?.optInt("minutes", 15) ?: 15
                    // same semantics as the local PIN unlock: grace window,
                    // latch kept, auto re-arm when it expires
                    Prefs.setGraceUntil(c, AppClock.now() + mins * 60_000L)
                    Logger.d(c, "cloud UNLOCK: grace ${mins}min (latch kept)")
                }
                "config" -> CloudLink.applyConfig(c, cmd.optJSONObject("payload") ?: JSONObject())
            }
            if (id > 0) ackIds.add(id)
        }
        if (ackIds.isNotEmpty()) CloudLink.ack(ackIds)
    }

    private fun notifyAlert(title: String, text: String) {
        val banner = Notification.Builder(this, "alerts")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        getSystemService(NotificationManager::class.java).notify(5, banner)
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
        CloudLink.commandHandler = null
        try { unregisterReceiver(wifiGuard) } catch (_: Throwable) {}
        super.onDestroy()
    }
}