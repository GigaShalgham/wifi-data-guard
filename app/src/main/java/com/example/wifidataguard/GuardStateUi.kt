package com.example.wifidataguard

/**
 * Pure state machine for the glass hero UI (spec-007). NO Android imports —
 * unit-testable on the JVM. Truth sources are identical to the legacy status
 * line (Art. VIII): [stateOf] uses the same precedence the status line always
 * used — grace window wins over the latch (the latch is deliberately kept
 * during grace, spec-003).
 */
object GuardStateUi {

    enum class S { PROTECTED, GRACE, BLOCKED }

    enum class Chime { LOCK, UNLOCK, ARM }

    /** Same precedence as the legacy status line: grace wins over latch. */
    fun stateOf(graceLeftMs: Long, latched: Boolean): S = when {
        graceLeftMs > 0 -> S.GRACE
        latched -> S.BLOCKED
        else -> S.PROTECTED
    }

    /** A transition only exists between two known states — never on the first
     *  baseline read (app cold start) and never when nothing changed. */
    fun isTransition(prev: S?, next: S): Boolean = prev != null && prev != next

    /** Bilingual toast label for a real transition (Art. V + VIII).
     *  Returns (english, farsi). */
    fun label(from: S, to: S): Pair<String, String> = when (to) {
        S.GRACE -> "✓ Unlocked — free time" to "✓ باز شد — زمان آزاد"
        S.BLOCKED -> when (from) {
            S.GRACE -> "🔒 Locked again" to "🔒 دوباره قفل شد"
            else -> "🔒 Locked — guard active" to "🔒 قفل شد — محافظ فعال"
        }
        S.PROTECTED -> when (from) {
            S.GRACE -> "🔒 Locked again" to "🔒 دوباره قفل شد"
            else -> "🛡 Protected" to "🛡 محافظت فعال"
        }
    }

    /** Sound + vibration pairing for a transition (same tone pairs as the
     *  dashboard, spec-006). */
    fun chime(from: S, to: S): Chime = when (to) {
        S.GRACE -> Chime.UNLOCK
        S.BLOCKED -> Chime.LOCK
        S.PROTECTED -> when (from) {
            S.GRACE -> Chime.LOCK      // grace cancelled -> re-lock
            else -> Chime.UNLOCK       // unlatched (reset / remote full unlock)
        }
    }
}
