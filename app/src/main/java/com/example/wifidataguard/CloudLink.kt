package com.example.wifidataguard

import android.content.Context
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * CloudLink — Phase 2 cloud bridge.
 *
 * Design rules:
 *  - OFFLINE-FIRST: the cloud only sends *additional* commands/config.
 *    Losing contact with the cloud must NEVER disable the local guard.
 *  - FAIL-CLOSED: if the device is paired and misses its sync window
 *    (offline_tolerance_min, default 10), the watchdog latches the lock.
 *  - CLOCK-TAMPER: the last known server time is persisted; if the local
 *    clock is rolled back below it, the watchdog latches the lock.
 *  - The device token is a bearer secret stored in private prefs, never
 *    logged, never backed up.
 */
object CloudLink {

    const val BASE = "https://wifi-data-guard.gigaspaceturnip.workers.dev"

    private val pool = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val pendingAcks = Collections.synchronizedList(mutableListOf<Int>())

    /** Set by WatchdogService; invoked on the main thread with parsed commands. */
    @Volatile var commandHandler: ((Context, JSONArray) -> Unit)? = null

    @Volatile private var nextDueAt = 0L

    // ------------------------------------------------------------ state

    fun paired(c: Context): Boolean = Prefs.cloudToken(c).isNotEmpty()

    fun unpair(c: Context, fromServer: Boolean = false) {
        // Spec-002 (US2): if the dashboard revoked us while we are latched for a
        // cloud-origin reason, the latch STAYS (fail-closed — revocation is not
        // an unlock), but the reason is relabeled so the status line stops
        // claiming "Locked by parent", and the user gets a one-time notice
        // explaining the parent-PIN escape hatch.
        val wasLatched = WatchdogService.latched
        val reason = Prefs.latchReason(c)
        val cloudOrigin = reason == "cloud" || reason == "offline" || reason == "clock"
        Prefs.setCloudToken(c, "")
        Prefs.setCloudDeviceId(c, 0)
        Prefs.setCloudName(c, "")
        Prefs.setCloudLastSync(c, 0L)
        Prefs.setCloudServerTime(c, 0L)
        Prefs.setCloudApplied(c, "{}")
        pendingAcks.clear()
        if (fromServer && wasLatched && cloudOrigin) {
            Prefs.setLatchReason(c, "unpaired")
            WatchdogService.notifyRevokedWhileLocked(c)
            Logger.d(c, "cloud: unpaired from dashboard while latched ($reason) " +
                    "-> latch kept, reason=unpaired, PIN notice sent")
        } else {
            Logger.d(c, if (fromServer) "cloud: unpaired from dashboard -> local-only mode"
            else "cloud: unpaired locally -> local-only mode")
        }
    }

    // ------------------------------------------------------------ pairing

    fun pair(c: Context, code: String, name: String, cb: (ok: Boolean, msg: String) -> Unit) {
        val appCtx = c.applicationContext
        pool.execute {
            val body = JSONObject().put("code", code).put("name", name).toString()
            val (status, txt) = try {
                httpPost("$BASE/api/child/pair", null, body)
            } catch (_: Exception) { -1 to "" }
            main.post {
                if (status == 200) {
                    try {
                        val res = JSONObject(txt)
                        val tok = res.getString("device_token")
                        if (tok.length != 64) throw Exception("bad token")
                        Prefs.setCloudToken(appCtx, tok)
                        Prefs.setCloudDeviceId(appCtx, res.optInt("device_id"))
                        Prefs.setCloudName(appCtx, name)
                        Prefs.setCloudLastSync(appCtx, System.currentTimeMillis())
                        Prefs.setCloudServerTime(appCtx, res.optLong("server_time"))
                        val cfg = res.optJSONObject("config") ?: JSONObject()
                        Prefs.setCloudApplied(appCtx, cfg.toString())
                        applyConfig(appCtx, cfg)
                        Logger.d(appCtx, "cloud PAIRED as '$name'")
                        cb(true, "ok")
                    } catch (_: Exception) { cb(false, "bad response") }
                } else {
                    val msg = try { JSONObject(txt).optString("error") } catch (_: Exception) { "" }
                    cb(false, msg.ifEmpty { "HTTP $status" })
                }
            }
        }
    }

    // ------------------------------------------------------------ polling

    /** Called from the watchdog tick (main thread); cheap no-op when not due. */
    fun pollIfDue(c: Context) {
        if (!paired(c)) return
        val now = System.currentTimeMillis()
        if (now < nextDueAt) return
        // jitter ±2.5s around the configured interval
        nextDueAt = now + Prefs.pollIntervalSec(c) * 1000L + (0..5000).random()
        val appCtx = c.applicationContext
        pool.execute { doPoll(appCtx) }
    }

    /** Test panel: run a poll immediately (bypasses the interval). */
    fun forcePoll(c: Context) {
        nextDueAt = 0L
        pollIfDue(c)
    }

    /** Test panel: real ms until the next scheduled poll. */
    fun nextPollInMs(): Long = (nextDueAt - System.currentTimeMillis()).coerceAtLeast(0L)

    private fun doPoll(c: Context) {
        val token = Prefs.cloudToken(c)
        if (token.isEmpty()) return
        if (TestMode.simOffline) {
            Logger.d(c, "TEST poll skipped: simulated offline")
            return
        }
        val acks: List<Int> = synchronized(pendingAcks) { pendingAcks.toList() }
        val body = JSONObject().apply {
            put("report", buildReport(c))
            put("acks", JSONArray(acks))
        }
        val t0 = System.currentTimeMillis()
        val (status, txt) = try {
            httpPost("$BASE/api/child/poll", token, body.toString())
        } catch (_: Exception) { -1 to "" }
        val elapsedMs = System.currentTimeMillis() - t0

        main.post {
            when {
                status == 200 -> {
                    Prefs.setCloudLastSync(c, System.currentTimeMillis())
                    synchronized(pendingAcks) { pendingAcks.clear() }
                    try {
                        val res = JSONObject(txt)
                        val serverTime = res.optLong("server_time", 0L)
                        Prefs.setCloudServerTime(c, serverTime)
                        applyConfigIfChanged(c, res.optJSONObject("config") ?: JSONObject())
                        val cmds = res.optJSONArray("commands")
                        if (cmds != null && cmds.length() > 0)
                            commandHandler?.invoke(c, cmds)
                        if (TestMode.active)
                            Logger.d(c, "TEST poll ok: HTTP 200 in ${elapsedMs}ms " +
                                    "acks=${acks.size} cmds=${cmds?.length() ?: 0}")
                        val drift = AppClock.now() - serverTime
                        if (serverTime > 0 && abs(drift) > 300_000)
                            Logger.d(c, "clock drift vs server: ${drift / 1000}s")
                    } catch (_: Exception) {
                        Logger.d(c, "cloud poll: parse error")
                    }
                }
                status == 401 -> unpair(c, fromServer = true)
                else -> Logger.d(c, "cloud poll failed: HTTP $status")
            }
        }
    }

    private fun buildReport(c: Context): JSONObject = JSONObject().apply {
        put("used_bytes", LiveCounter.currentBytes())
        put("limit_bytes", Prefs.limitBytes(c))
        put("latched", WatchdogService.latched)
        put("grace_until", Prefs.graceUntil(c))
        put("battery_pct", batteryPct(c))
        put("app_version", appVersion(c))
    }

    private fun batteryPct(c: Context): Int = try {
        c.getSystemService(BatteryManager::class.java)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
    } catch (_: Exception) { 0 }

    private fun appVersion(c: Context): String = try {
        c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "?"
    } catch (_: Exception) { "?" }

    // ------------------------------------------------------------ fail-closed / tamper

    /** True when the device is paired and has missed its sync window. */
    fun offlineLatchDue(c: Context): Boolean {
        val last = Prefs.cloudLastSync(c)
        if (!paired(c) || last <= 0L) return false
        return System.currentTimeMillis() - last > Prefs.offlineTolMin(c) * 60_000L
    }

    /** True when the local clock was rolled back below the last known server time. */
    fun clockRolledBack(c: Context): Boolean {
        val st = Prefs.cloudServerTime(c)
        return paired(c) && st > 0L && AppClock.now() + 120_000L < st
    }

    // ------------------------------------------------------------ commands & config

    fun ack(ids: List<Int>) {
        synchronized(pendingAcks) { pendingAcks.addAll(ids) }
    }

    /** Applies a cloud config payload to local prefs (parent is the authority). */
    fun applyConfig(c: Context, cfg: JSONObject) {
        if (cfg.length() == 0) return
        if (cfg.has("limit_mb"))
            Prefs.setLimitBytes(c, (cfg.optLong("limit_mb") * 1048576L).coerceAtLeast(1L))
        if (cfg.has("period"))
            Prefs.setMonthlyReset(c, cfg.optString("period") == "monthly")
        if (cfg.has("hard_mode"))
            Prefs.setHardMode(c, cfg.optBoolean("hard_mode"))
        if (cfg.has("unlock_minutes"))
            Prefs.setUnlockMinutes(c, cfg.optInt("unlock_minutes").coerceIn(0, 1440))
        if (cfg.has("offline_tolerance_min"))
            Prefs.setOfflineTolMin(c, cfg.optInt("offline_tolerance_min").coerceIn(1, 1440))
        if (cfg.has("poll_interval_sec"))
            Prefs.setPollIntervalSec(c, cfg.optInt("poll_interval_sec").coerceIn(15, 600))
        Logger.d(c, "cloud config applied: $cfg")
    }

    /** Re-applies only when the cloud value actually changed, so local
     *  PIN-gated tweaks survive until the parent pushes something new. */
    fun applyConfigIfChanged(c: Context, cfg: JSONObject) {
        val s = cfg.toString()
        if (s == Prefs.cloudApplied(c)) return
        Prefs.setCloudApplied(c, s)
        applyConfig(c, cfg)
    }

    // ------------------------------------------------------------ http

    private fun httpPost(url: String, token: String?, body: String): Pair<Int, String> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("User-Agent", "DataGuard-Android/1.3.2")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val txt = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            return code to txt
        } finally {
            conn.disconnect()
        }
    }
}
