package com.example.wifidataguard

import org.junit.Test

/**
 * spec-012 FR-003: pure JVM tests for the restart-time rollover policy —
 * the fix for the v1.2 "won't unlock tomorrow" bug (a limit latch engaged in
 * an older period must release when the watchdog restarts after the period
 * reset; cloud/offline/clock latches and clock-rollback must never release).
 */
class RolloverPolicyTest {

    private val day = 86_400_000L
    private val yesterday = 1_700_000_000L      // any epoch-ms period start
    private val today = yesterday + day

    @Test
    fun limitLatchFromOlderPeriodReleases() {
        assert(RolloverPolicy.shouldRelease("limit", yesterday, today))
    }

    @Test
    fun limitLatchFromSamePeriodKeeps() {
        // restart within the same period: the quota has NOT reset -> keep
        assert(!RolloverPolicy.shouldRelease("limit", today, today))
    }

    @Test
    fun clockRolledBackNeverReleases() {
        // current period BELOW the latch period = rolled-back clock (Art. II)
        assert(!RolloverPolicy.shouldRelease("limit", today, yesterday))
        assert(!RolloverPolicy.shouldRelease("", today, yesterday))
    }

    @Test
    fun cloudOfflineClockUnpairedLatchesNeverRelease() {
        for (reason in listOf("cloud", "offline", "clock", "unpaired")) {
            assert(!RolloverPolicy.shouldRelease(reason, yesterday, today)) {
                "reason=$reason must survive the rollover"
            }
        }
    }

    @Test
    fun emptyReasonReleasesAndMigrationZeroCountsAsOlder() {
        // legacy latches never recorded a period (0) -> release on first start
        assert(RolloverPolicy.shouldRelease("", yesterday, today))
        assert(RolloverPolicy.shouldRelease("limit", 0L, today))
        assert(!RolloverPolicy.shouldRelease("cloud", 0L, today))
    }
}
