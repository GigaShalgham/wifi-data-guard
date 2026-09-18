package com.example.wifidataguard

import java.util.Calendar

/**
 * Pure label/scale math for the 7-day history chart (spec-010 FR-104).
 * NO Android imports — unit-testable on the JVM. One row per LOCAL day
 * (epoch-day numbers as computed by the server with the device's tz).
 */
object HistoryUi {

    /** UTC ms at which local day [epochDay] starts, given the tz offset the
     *  server used for bucketing: day = floor((ts + tz*60000) / 86400000). */
    fun dayStartUtc(epochDay: Long, tzMin: Int): Long =
        epochDay * 86_400_000L - tzMin * 60_000L

    /** Short weekday label: EN "Mo".."Su", FA "ش ی د س چ پ ج". */
    fun weekdayLabel(epochDay: Long, tzMin: Int, fa: Boolean): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = dayStartUtc(epochDay, tzMin)
        val dow = cal.get(Calendar.DAY_OF_WEEK)   // 1=Sun .. 7=Sat
        return if (fa) {
            // Persian week: شنبه(ش) یکشنبه(ی) دوشنبه(د) سه‌شنبه(س) چهارشنبه(چ)
            // پنجشنبه(پ) جمعه(ج) — indexed by Java DAY_OF_WEEK (1=Sun..7=Sat),
            // so the array runs Sun..Sat = ی د س چ پ ج ش (spec-012 FR-005 fix;
            // the old array was misordered and every glyph was off).
            arrayOf("ی", "د", "س", "چ", "پ", "ج", "ش")[dow - 1]
        } else {
            arrayOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")[dow - 1]
        }
    }

    /** Daily budget for over-limit coloring: the whole limit for a daily
     *  period; limit/30 for a monthly period (honest approximation). */
    fun dayBudget(limitBytes: Long, monthly: Boolean): Long =
        if (monthly) (limitBytes / 30).coerceAtLeast(1)
        else limitBytes.coerceAtLeast(1)

    /** Compact value label above a bar: "1.2G" / "870M" / "12M". */
    fun compact(bytes: Long): String = when {
        bytes >= 1_073_741_824L -> String.format(java.util.Locale.US, "%.1fG", bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> "${bytes / 1_048_576L}M"
        bytes > 0 -> "${bytes / 1024L}K"
        else -> ""
    }

    /** "Updated X min ago" line, bilingual (Persian digits in FA). */
    fun updatedAgo(mins: Long, fa: Boolean): String {
        val m = mins.coerceAtLeast(0)
        return if (fa) {
            if (m < 1) "همین حالا به‌روز شد" else "به‌روز شده ${faNum(m.toString())} دقیقه پیش"
        } else {
            if (m < 1) "Updated just now" else "Updated $m min ago"
        }
    }

    private fun faNum(s: String): String = buildString {
        for (ch in s) append(when (ch) {
            '0' -> '۰'; '1' -> '۱'; '2' -> '۲'; '3' -> '۳'; '4' -> '۴'
            '5' -> '۵'; '6' -> '۶'; '7' -> '۷'; '8' -> '۸'; '9' -> '۹'
            else -> ch })
    }
}
