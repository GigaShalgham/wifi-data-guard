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

/** HARD-MODE internet killer. Crash-proof: never throws, never restart-loops. */
class BlockerVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var dropper: Thread? = null

    companion object {
        private const val CH_ID = "lock_notif"
        private const val NOTIF_ID = 77
        const val ACT_RELEASE = "release"

        @Volatile private var lastStartMs = 0L

        @Volatile var running = false
            private set
        @Volatile var lastError: String? = null

        fun start(context: Context) {
            val now = System.currentTimeMillis()
            if (now - lastStartMs < 5000) {
                Logger.d(context, "VPN start suppressed (cooldown)")
                return
            }
            lastStartMs = now
            try {
                context.startForegroundService(
                    Intent(context, BlockerVpnService::class.java))
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
            Logger.d(this, "VPN onStartCommand action=${intent?.action ?: "START"}")
            startForeground(NOTIF_ID, notif("ALL internet locked (hard mode)."))

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

    private fun notif(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CH_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Data guard (hard mode)")
            .setContentText(text)
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

        val b = Builder()
        b.setSession("data-guard").setMtu(1500)
        b.addAddress("198.18.0.1", 32)
        b.addRoute("0.0.0.0", 0)
        try { b.addRoute("::", 0) } catch (_: Exception) {}

        val fd = try { b.establish() } catch (t: Throwable) {
            lastError = "establish exception: $t"
            Logger.d(this, "block(): $lastError"); null
        }
        if (fd == null) {
            if (lastError == null) lastError = "establish returned null"
            Logger.d(this, "TUNNEL REFUSED -> $lastError")
            notifyFail(lastError!!)
            stopSelf(); return
        }

        tun = fd; running = true; lastError = null
        Logger.d(this, "*** TUNNEL UP - all traffic captured ***")

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