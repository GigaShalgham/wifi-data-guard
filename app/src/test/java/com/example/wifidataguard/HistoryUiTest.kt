package com.example.wifidataguard

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pure JVM tests for HistoryUi (spec-010) — no Android dependencies beyond
 * Calendar, which Robolectric provides. Verifies the local-day math that
 * mirrors the server's bucketDaily().
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryUiTest {

    private fun epochDayOf(utcMs: Long, tzMin: Int): Long =
        (utcMs + tzMin * 60_000L).floorDiv(86_400_000L)   // floor, like the server's Math.floor

    @Test
    fun dayStartInvertsServerBucketing() {
        // for any utc ts and tz: dayStartUtc(epochDay(ts)) <= ts < dayStartUtc(epochDay+1)
        val tzs = intArrayOf(0, 210, -300, 720)
        val samples = longArrayOf(0, 1_700_000_000_000L, 86_399_999L, 86_400_000L)
        for (tz in tzs) for (ts in samples) {
            val day = epochDayOf(ts, tz)
            val start = HistoryUi.dayStartUtc(day, tz)
            val nextStart = HistoryUi.dayStartUtc(day + 1, tz)
            assert(start <= ts && ts < nextStart) { "tz=$tz ts=$ts day=$day start=$start" }
        }
    }

    @Test
    fun weekdayLabelsAreSingleLettersInFa() {
        // 7 consecutive days -> 7 labels, each 1 char, from the Persian set
        val base = 20_000L
        val labels = (0 until 7).map { HistoryUi.weekdayLabel(base + it, 0, fa = true) }
        assert(labels.all { it.length == 1 }) { labels.toString() }
        assert(labels.all { it in "شیدسچپج" }) { labels.toString() }
        assert(labels.toSet().size == 7) { labels.toString() }
    }

    @Test
    fun weekdayLabelsAreTwoLetterInEn() {
        val base = 20_000L
        val labels = (0 until 7).map { HistoryUi.weekdayLabel(base + it, 0, fa = false) }
        assert(labels.all { it.length == 2 }) { labels.toString() }
        assert(labels.toSet().size == 7) { labels.toString() }
    }

    @Test
    fun dayBudgetSplitsMonthlyBy30() {
        assert(HistoryUi.dayBudget(3_000_000_000L, true) == 100_000_000L)
        assert(HistoryUi.dayBudget(3_000_000_000L, false) == 3_000_000_000L)
        assert(HistoryUi.dayBudget(0L, false) == 1L)
    }

    @Test
    fun compactFormatting() {
        assert(HistoryUi.compact(0L) == "")
        assert(HistoryUi.compact(512L) == "0K")
        assert(HistoryUi.compact(12 * 1024 * 1024L) == "12M")
        assert(HistoryUi.compact(870L * 1024 * 1024L) == "870M")
        assert(HistoryUi.compact((1.23 * 1024 * 1024 * 1024).toLong()) == "1.2G")
    }

    @Test
    fun updatedAgoBilingual() {
        assert(HistoryUi.updatedAgo(0, fa = false) == "Updated just now")
        assert(HistoryUi.updatedAgo(5, fa = false) == "Updated 5 min ago")
        assert(HistoryUi.updatedAgo(0, fa = true).contains("همین حالا"))
        assert(HistoryUi.updatedAgo(5, fa = true).contains("۵"))
    }
}
