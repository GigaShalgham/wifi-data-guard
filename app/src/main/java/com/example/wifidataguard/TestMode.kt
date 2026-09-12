package com.example.wifidataguard

import android.content.Context

/**
 * TestMode — state + helpers for the `.test` ("unormal") build.
 *
 * Everything here is inert in release builds: AppClock.testBuild is false,
 * so the virtual clock returns real time and the test panel is hidden.
 */
object TestMode {

    val active: Boolean get() = AppClock.testBuild

    /** When true, CloudLink skips HTTP entirely -> simulates airplane mode. */
    @Volatile var simOffline = false

    // ---------------- usage injection ----------------

    /** Add fake usage to the live counter (watchdog latches within ~1 s). */
    fun addUsageMb(mb: Long) {
        LiveCounter.resetTo(LiveCounter.currentBytes() + mb * 1_048_576L)
    }

    /** Set the counter to a percentage of the configured limit (99 = about to trip). */
    fun setUsagePctOfLimit(c: Context, pct: Int) {
        val lim = Prefs.limitBytes(c)
        if (lim > 0) LiveCounter.resetTo(lim * pct / 100)
    }

    fun usageZero() = LiveCounter.resetTo(0L)

    // ---------------- clock-tamper ----------------

    /**
     * Roll the virtual clock back to 5 minutes BEFORE the last known server
     * time — guaranteed to trip the rollback defense while cloud-paired.
     * Returns false when there is no server time yet (not paired).
     */
    fun rollbackBelowServer(c: Context): Boolean {
        val st = Prefs.cloudServerTime(c)
        if (st <= 0L) return false
        AppClock.jumpTo(st - 5 * 60_000L)
        return true
    }
}
