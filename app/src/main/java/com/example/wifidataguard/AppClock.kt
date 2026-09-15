package com.example.wifidataguard

/**
 * AppClock — one clock for the whole app.
 *
 * RELEASE builds: now() == System.currentTimeMillis() — zero behavior change.
 *
 * TEST builds (`.test` package suffix, set in GuardApp): now() is a VIRTUAL
 * clock that can run faster (time scale) or be jumped, so grace windows,
 * midnight/month rollover, PIN cooldowns and clock-rollback can be tested
 * in seconds instead of hours.
 *
 * Routing rules (IMPORTANT — do not mix up):
 *  - VIRTUAL clock: deadlines the *child* waits out (grace window, period
 *    rollover, PIN brute-force cooldown, cloud unlock minutes).
 *  - REAL clock: connectivity deadlines (offline tolerance, poll interval,
 *    HTTP timings, last-sync age). Acceleration must never make a reachable
 *    device look offline.
 */
object AppClock {

    /** Set once in GuardApp.onCreate: true only for the .test package. */
    @Volatile var testBuild = false

    /** Virtual seconds per real second (1.0 = normal). */
    @Volatile var scale = 1.0
        private set

    @Volatile private var anchorReal = System.currentTimeMillis()
    @Volatile private var anchorVirt = anchorReal

    fun now(): Long {
        if (!testBuild) return System.currentTimeMillis()
        val real = System.currentTimeMillis()
        return anchorVirt + ((real - anchorReal) * scale).toLong()
    }

    /** Change the acceleration factor, keeping the current virtual instant. */
    @Synchronized
    fun setScale(s: Double) {
        if (!testBuild) return
        anchorVirt = now()
        anchorReal = System.currentTimeMillis()
        scale = s.coerceIn(0.0, 3600.0)
    }

    /** Jump the virtual clock by a delta (negative = rollback simulation). */
    @Synchronized
    fun jump(ms: Long) {
        if (!testBuild) return
        anchorVirt = now() + ms
        anchorReal = System.currentTimeMillis()
    }

    /** Force the virtual clock to an absolute instant (e.g. below server time). */
    @Synchronized
    fun jumpTo(whenMs: Long) {
        if (!testBuild) return
        anchorVirt = whenMs
        anchorReal = System.currentTimeMillis()
    }

    /** Reset to 1x with virtual == real. */
    @Synchronized
    fun snapToReal() {
        if (!testBuild) return
        scale = 1.0
        anchorVirt = System.currentTimeMillis()
        anchorReal = anchorVirt
    }
}
