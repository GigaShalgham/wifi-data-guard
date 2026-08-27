package com.example.wifidataguard

import android.content.Context
import java.security.MessageDigest
import java.util.Calendar
import java.util.UUID

object Prefs {
    private fun sp(c: Context) =
        c.getSharedPreferences("guard_prefs", Context.MODE_PRIVATE)

    fun limitBytes(c: Context): Long = sp(c).getLong("limit_bytes", 0L)
    fun setLimitBytes(c: Context, v: Long) = sp(c).edit().putLong("limit_bytes", v).apply()

    fun monthlyReset(c: Context): Boolean = sp(c).getBoolean("monthly_reset", false)
    fun setMonthlyReset(c: Context, v: Boolean) = sp(c).edit().putBoolean("monthly_reset", v).apply()

    fun monitoring(c: Context): Boolean = sp(c).getBoolean("monitoring", false)
    fun setMonitoring(c: Context, v: Boolean) = sp(c).edit().putBoolean("monitoring", v).apply()

    fun hardMode(c: Context): Boolean = sp(c).getBoolean("hard_mode", false)
    fun setHardMode(c: Context, v: Boolean) = sp(c).edit().putBoolean("hard_mode", v).apply()

    // grace: minutes after unlock/reset where blocking is paused
    fun graceUntil(c: Context): Long = sp(c).getLong("grace_until", 0L)
    fun setGraceUntil(c: Context, v: Long) = sp(c).edit().putLong("grace_until", v).apply()

    // baseline for reset button
    fun resetPeriod(c: Context): Long = sp(c).getLong("reset_period", 0L)
    fun setResetPeriod(c: Context, v: Long) = sp(c).edit().putLong("reset_period", v).apply()
    fun resetBytes(c: Context): Long = sp(c).getLong("reset_bytes", 0L)
    fun setResetBytes(c: Context, v: Long) = sp(c).edit().putLong("reset_bytes", v).apply()

    // UI language: "en" or "fa"
    fun lang(c: Context): String = sp(c).getString("lang", "en") ?: "en"
    fun setLang(c: Context, v: String) = sp(c).edit().putString("lang", v).apply()

    // ---------- PIN ----------
    fun pinSet(c: Context): Boolean =
        sp(c).getString("pin_hash", null) != null &&
                sp(c).getString("pin_salt", null) != null

    private fun digest(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest((salt + ":" + pin).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun setPin(c: Context, pin: String) {
        val salt = UUID.randomUUID().toString()
        sp(c).edit()
            .putString("pin_salt", salt)
            .putString("pin_hash", digest(pin, salt))
            .putInt("fail_count", 0)
            .apply()
    }

    fun checkPin(c: Context, pin: String): Boolean {
        val salt = sp(c).getString("pin_salt", "") ?: ""
        val ok = digest(pin, salt) == sp(c).getString("pin_hash", "")
        if (ok) sp(c).edit().putInt("fail_count", 0).apply()
        else sp(c).edit()
            .putInt("fail_count", sp(c).getInt("fail_count", 0) + 1)
            .putLong("last_fail", System.currentTimeMillis())
            .apply()
        return ok
    }

    fun pinCooldownLeftMs(c: Context): Long {
        if (sp(c).getInt("fail_count", 0) < 5) return 0L
        val left = 5 * 60 * 1000L -
                (System.currentTimeMillis() - sp(c).getLong("last_fail", 0L))
        if (left > 0) return left
        sp(c).edit().putInt("fail_count", 0).apply()
        return 0L
    }

    fun currentPeriodStart(c: Context): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        if (monthlyReset(c)) cal.set(Calendar.DAY_OF_MONTH, 1)
        return cal.timeInMillis
    }
    // unlock duration in minutes (0 = until end of period)
    fun unlockMinutes(c: Context): Int = sp(c).getInt("unlock_minutes", 5)
    fun setUnlockMinutes(c: Context, v: Int) = sp(c).edit().putInt("unlock_minutes", v).apply()
}