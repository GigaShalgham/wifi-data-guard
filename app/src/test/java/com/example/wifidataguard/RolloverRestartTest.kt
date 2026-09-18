package com.example.wifidataguard

import android.app.Application
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.os.Looper
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import java.util.Calendar

/**
 * spec-012 release gate (spec-009 precedent: Robolectric drives the REAL
 * service). The owner's v1.2 field report: a limit latch survived into the
 * next day ("won't unlock tomorrow") whenever the process was not alive
 * across midnight — reboot, dead battery, OEM killer, app update — and the
 * only escape was toggling the enforce switch off and on.
 *
 * Root cause: the rollover detector (`lastPeriod`) was seeded from "now" at
 * service creation and never persisted, so a restart after the boundary never
 * saw the boundary. These tests cold-start the real WatchdogService in the
 * exact stuck state and assert the spec-012 behavior.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = GuardApp::class,
        shadows = [ShadowNetworkStatsManager::class])
class RolloverRestartTest {

    private val app get() = RuntimeEnvironment.getApplication() as Application

    @Before fun setUp() {
        ShadowNetworkStatsManager.failQueries = false
        ShadowNetworkStatsManager.wifiBytes = 0L
        // Robolectric has no NetworkStatsManager — inject ours so DataStats
        // gets an authoritative (controllable) answer.
        val nsm = Shadow.newInstanceOf(NetworkStatsManager::class.java)
        shadowOf(app).setSystemService(Context.NETWORK_STATS_SERVICE, nsm)
        // armed kid-phone baseline
        Prefs.setMonitoring(app, true)
        Prefs.setLimitBytes(app, 500L * 1024 * 1024)
        Prefs.setGraceUntil(app, 0L)
    }

    @After fun tearDown() {
        ShadowNetworkStatsManager.failQueries = false
        ShadowNetworkStatsManager.wifiBytes = 0L
        Prefs.setMonthlyReset(app, false)
        WatchdogService.unlatch(app)
        Prefs.setMonitoring(app, false)
    }

    // ---------------- helpers ----------------

    /** Mirrors Prefs.currentPeriodStart's daily math for an arbitrary instant. */
    private fun dayStartAt(t: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = t
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun todayStart(): Long = Prefs.currentPeriodStart(app)   // daily mode

    private fun yesterdayStart(): Long =
        dayStartAt(System.currentTimeMillis() - 24L * 3_600_000)

    private fun monthStart(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        return cal.timeInMillis
    }

    /** Cold-start the watchdog exactly as BootReceiver / START_STICKY would. */
    private fun startWatchdogOnce() {
        val ctrl = Robolectric.buildService(WatchdogService::class.java)
        ctrl.create()
        ctrl.startCommand(0, 0)
        shadowOf(Looper.getMainLooper()).idle()
        ctrl.destroy()
    }

    // ---------------- T1 (US1): the reported bug ----------------

    @Test fun t1_restartAfterMidnightReleasesLimitLatch() {
        WatchdogService.latchCloud(app, "limit")
        Prefs.setPeriodStart(app, yesterdayStart())
        startWatchdogOnce()
        assert(!WatchdogService.latched) { "limit latch must release after restart-rollover" }
        assert(!WatchdogService.restoreLatched(app)) { "persisted latch must be cleared" }
        assert(Prefs.latchReason(app) == "") { "latch reason must be cleared" }
        assert(Prefs.periodStart(app) == todayStart()) { "period must advance AND persist" }
    }

    // ---------------- T2 (US2): security latches survive ----------------

    @Test fun t2_cloudLatchSurvivesRestartRollover() {
        WatchdogService.latchCloud(app, "cloud")
        Prefs.setPeriodStart(app, yesterdayStart())
        startWatchdogOnce()
        assert(WatchdogService.latched) { "cloud latch must survive the boundary (Art. II)" }
        assert(Prefs.latchReason(app) == "cloud")
        assert(Prefs.periodStart(app) == todayStart()) { "security latch kept BUT period advances" }
    }

    // ---------------- T3: fail-closed when no boundary was crossed ----------------

    @Test fun t3_sameDayRestartKeepsLimitLatch() {
        WatchdogService.latchCloud(app, "limit")
        Prefs.setPeriodStart(app, todayStart())
        startWatchdogOnce()
        assert(WatchdogService.latched) { "same-day restart crosses no boundary — keep latch" }
        assert(Prefs.periodStart(app) == todayStart())
    }

    // ---------------- T4 (US4): monthly periods are stable ----------------

    @Test fun t4_monthlyPeriodStableAcrossSameMonthRestart() {
        Prefs.setMonthlyReset(app, true)
        WatchdogService.latchCloud(app, "limit")
        Prefs.setPeriodStart(app, monthStart())   // == currentPeriodStart(monthly)
        startWatchdogOnce()
        assert(WatchdogService.latched) { "monthly budget spans the month — no mid-month reset" }
        assert(Prefs.periodStart(app) == monthStart())
    }

    // ---------------- T5 (US3): unreadable stats never release ----------------

    @Test fun t5_statsUnavailableKeepsLatchAndRetries() {
        ShadowNetworkStatsManager.failQueries = true
        WatchdogService.latchCloud(app, "limit")
        Prefs.setPeriodStart(app, yesterdayStart())
        startWatchdogOnce()
        assert(WatchdogService.latched) { "Art. II: unreadable stats must NOT release the latch" }
        assert(Prefs.periodStart(app) == yesterdayStart()) {
            "period must NOT advance — the rollover stays pending for retry"
        }
    }

    // ---------------- T6: migration from a pre-spec-012 build ----------------

    @Test fun t6_migrationFromOldBuildSeedsNowNoFabricatedRollover() {
        WatchdogService.latchCloud(app, "limit")
        // period_start stays absent (0) — a device updating in place
        startWatchdogOnce()
        assert(WatchdogService.latched) { "no fabricated rollover on migration (spec FM5)" }
        assert(Prefs.periodStart(app) == todayStart()) {
            "seeded to now so the NEXT boundary is processed"
        }
    }
}
