package com.example.wifidataguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream

/** HARD-MODE internet killer. Crash-proof: never throws, never restart-loops.
 *
 *  v1.3.1 — CONTROL-CHANNEL SURVIVAL: the tunnel excludes THIS app's own
 *  traffic (addDisallowedApplication). Before, a hard lock also killed the
 *  cloud control channel, so a remote lock could never be lifted remotely
 *  (unlock commands sat undelivered forever) and the dashboard went stale.
 *  Now every OTHER app stays blocked while the guard app itself keeps its
 *  poll connection to the worker alive. If establish() refuses the
 *  self-exemption, we fall back to the old plain full tunnel.
 */
class BlockerVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var dropper: Thread? = null

    companion object {
        private const val CH_ID = "lock_notif"
        private const val NOTIF_ID = 77
        const val ACT_RELEASE = "release"
        const val EXTRA_MODE = "mode"
        const val MODE_SOFT = "soft"

        @Volatile private var lastStartMs = 0L

        @Volatile var running = false
            private set
        @Volatile var lastError: String? = null

        /** @param softFallback true when the VPN engages as the soft lock's
         *  fallback (Wi-Fi could not be switched off) — labels the
         *  notification honestly instead of claiming "hard mode". */
        fun start(context: Context, softFallback: Boolean = false) {
            val now = System.currentTimeMillis()
            if (now - lastStartMs < 5000) {
                Logger.d(context, "VPN start suppressed (cooldown)")
                return
            }
            lastStartMs = now
            try {
                context.startForegroundService(
                    Intent(context, BlockerVpnService::class.java)
                        .putExtra(EXTRA_MODE, if (softFallback) MODE_SOFT else "hard"))
            } catch (t: Throwable) {
                Logger.d(context, "start failed: $t")
            }
        }

        fun release(context: Context) {
            try {
                context.startForegroundService(
                    Intent(context, BlockerVpnService::class.java).setAction(ACT_RELEASE))
            } catch (t: Throwable) {
                Logger.d(context, "release failed: $t")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CH_ID, "Internet lock",
                    NotificationManager.IMPORTANCE_LOW))
        } catch (_: Throwable) {}
        Logger.d(this, "VPN service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val soft = intent?.getStringExtra(EXTRA_MODE) == MODE_SOFT
            Logger.d(this, "VPN onStartCommand action=${intent?.action ?: "START"} soft=$soft")
            startForeground(NOTIF_ID, notif(soft))

            if (intent?.action == ACT_RELEASE) {
                shutDown()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } else {
                block()
            }
        } catch (t: Throwable) {
            Logger.d(this, "onStartCommand error absorbed: $t")
            try { stopSelf() } catch (_: Throwable) {}
        }
        return START_NOT_STICKY
    }

    private fun notif(soft: Boolean): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CH_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(if (soft) "Data guard (soft-lock fallback)"
                             else "Data guard (hard mode)")
            .setContentText(
                if (soft) "Wi-Fi control unavailable - ALL internet blocked " +
                    "(guard app stays online)."
                else "ALL internet locked (guard app stays online for " +
                    "remote unlock).")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun block() {
        if (running) { Logger.d(this, "block(): already running"); return }

        val prep = try { VpnService.prepare(this) } catch (t: Throwable) {
            Logger.d(this, "prepare threw: $t"); null
        }
        if (prep != null) {
            lastError = "VPN consent missing (open app > Fix permissions)"
            Logger.d(this, "block(): $lastError")
            notifyFail(lastError!!)
            stopSelf(); return
        }

        // 1st attempt: a tunnel that excludes our own package, so CloudLink's
        // polls (remote unlock / config / usage reports) survive the lock.
        // 2nd attempt (only if establish refused): plain full tunnel.
        var fd = establishFd(selfExempt = true)
        if (fd == null) {
            Logger.d(this, "establish with self-exemption failed -> plain retry")
            fd = establishFd(selfExempt = false)
        }
        if (fd == null) {
            if (lastError == null) lastError = "establish returned null"
            Logger.d(this, "TUNNEL REFUSED -> $lastError")
            notifyFail(lastError!!)
            stopSelf(); return
        }

        tun = fd; running = true; lastError = null
        Logger.d(this, "*** TUNNEL UP - traffic captured (self-exempt) ***")

        dropper = Thread {
            try {
                val input = FileInputStream(fd.fileDescriptor)
                val buf = ByteArray(16384)
                while (!Thread.currentThread().isInterrupted) {
                    if (input.read(buf) < 0) break
                }
            } catch (_: Throwable) {
            } finally {
                shutDown()
                Logger.d(this, "dropper ended -> tunnel down")
            }
        }.also { it.start() }
    }

    private fun establishFd(selfExempt: Boolean): ParcelFileDescriptor? {
        val b = Builder()
        b.setSession("data-guard").setMtu(1500)
        b.addAddress("198.18.0.1", 32)
        b.addRoute("0.0.0.0", 0)
        try { b.addRoute("::", 0) } catch (_: Exception) {}
        var applied = false
        if (selfExempt) {
            try {
                b.addDisallowedApplication(packageName)
                applied = true
            } catch (t: Throwable) {
                Logger.d(this, "self-exemption unavailable: $t")
            }
        }
        val fd = try { b.establish() } catch (t: Throwable) {
            lastError = "establish exception: $t"
            Logger.d(this, "block(): $lastError"); null
        }
        if (fd != null) Logger.d(this, "tunnel established (selfExempt=$applied)")
        return fd
    }

    private fun shutDown() {
        dropper?.interrupt()
        try { tun?.close() } catch (_: Exception) {}
        tun = null; dropper = null; running = false
    }

    private fun notifyFail(msg: String) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(
                "alerts", "Limit warnings", NotificationManager.IMPORTANCE_HIGH))
            nm.notify(99, Notification.Builder(this, "alerts")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("HARD MODE note")
                .setContentText(msg)
                .build())
        } catch (_: Throwable) {}
    }

    override fun onDestroy() { shutDown(); super.onDestroy() }
}