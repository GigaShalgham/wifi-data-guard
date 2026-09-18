package com.example.wifidataguard

/**
 * spec-012 FR-003: pure restart-rollover policy — no Android imports, unit
 * testable on the JVM (mirrors GuardStateUi / HistoryUi conventions).
 *
 * The live WatchdogService loop clears a limit latch at period rollover by
 * comparing the current period with an IN-MEMORY lastPeriod. When the process
 * dies before midnight and restarts after it, that comparison can never fire,
 * so a stale latch from yesterday survives the whole new period (the v1.2
 * "won't unlock tomorrow" bug). This policy answers the restart-time question:
 * given the persisted latch reason and the period the latch was engaged in,
 * may a restarted watchdog release the latch now?
 *
 * Safety rules (constitution Art. II — fail-closed):
 *  - Only limit-based latches ("" or "limit") ever release on rollover;
 *    cloud / offline / clock / unpaired latches must survive the rollover.
 *  - STRICTLY greater current period: a clock rolled back below the latch
 *    period can never fabricate a "new period" release.
 *  - A never-recorded period (0, pre-spec-012 latch) counts as older, so a
 *    stuck limit latch releases on the first start after updating.
 */
object RolloverPolicy {

    fun shouldRelease(
        reason: String,
        latchPeriodStart: Long,
        currentPeriodStart: Long
    ): Boolean =
        (reason == "" || reason == "limit") && currentPeriodStart > latchPeriodStart
}
