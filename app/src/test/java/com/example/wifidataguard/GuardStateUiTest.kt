package com.example.wifidataguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * spec-007: pure state machine for the glass hero — precedence, transition
 * detection, bilingual labels and chime pairings (Art. V + VIII).
 */
class GuardStateUiTest {

    // ---------- stateOf: same precedence as the legacy status line ----------

    @Test fun graceWinsOverLatch() {
        // latch is deliberately kept during grace (spec-003) — grace shows
        assertEquals(GuardStateUi.S.GRACE, GuardStateUi.stateOf(1_000L, true))
        assertEquals(GuardStateUi.S.GRACE, GuardStateUi.stateOf(60_000L, true))
    }

    @Test fun latchOnlyWhenNoGrace() {
        assertEquals(GuardStateUi.S.BLOCKED, GuardStateUi.stateOf(0L, true))
        assertEquals(GuardStateUi.S.BLOCKED, GuardStateUi.stateOf(-5_000L, true))
    }

    @Test fun protectedWhenIdle() {
        assertEquals(GuardStateUi.S.PROTECTED, GuardStateUi.stateOf(0L, false))
        assertEquals(GuardStateUi.S.PROTECTED, GuardStateUi.stateOf(-1L, false))
    }

    // ---------- isTransition: never fires on the cold-start baseline ----------

    @Test fun noTransitionOnFirstRead() {
        for (s in GuardStateUi.S.values()) assertFalse(GuardStateUi.isTransition(null, s))
    }

    @Test fun noTransitionWhenUnchanged() {
        for (s in GuardStateUi.S.values()) assertFalse(GuardStateUi.isTransition(s, s))
    }

    @Test fun transitionOnEveryEdge() {
        assertTrue(GuardStateUi.isTransition(GuardStateUi.S.PROTECTED, GuardStateUi.S.GRACE))
        assertTrue(GuardStateUi.isTransition(GuardStateUi.S.GRACE, GuardStateUi.S.BLOCKED))
        assertTrue(GuardStateUi.isTransition(GuardStateUi.S.BLOCKED, GuardStateUi.S.PROTECTED))
        assertTrue(GuardStateUi.isTransition(GuardStateUi.S.GRACE, GuardStateUi.S.PROTECTED))
        assertTrue(GuardStateUi.isTransition(GuardStateUi.S.BLOCKED, GuardStateUi.S.GRACE))
        assertTrue(GuardStateUi.isTransition(GuardStateUi.S.PROTECTED, GuardStateUi.S.BLOCKED))
    }

    // ---------- labels: bilingual, non-empty, edge-specific (Art. V) ----------

    @Test fun labelsAreBilingualAndDistinct() {
        val seen = mutableSetOf<String>()
        val edges = listOf(
            GuardStateUi.S.PROTECTED to GuardStateUi.S.GRACE,
            GuardStateUi.S.BLOCKED to GuardStateUi.S.GRACE,
            GuardStateUi.S.GRACE to GuardStateUi.S.BLOCKED,
            GuardStateUi.S.PROTECTED to GuardStateUi.S.BLOCKED,
            GuardStateUi.S.GRACE to GuardStateUi.S.PROTECTED,
            GuardStateUi.S.BLOCKED to GuardStateUi.S.PROTECTED)
        for ((from, to) in edges) {
            val (en, fa) = GuardStateUi.label(from, to)
            assertTrue("EN empty for $from->$to", en.isNotBlank())
            assertTrue("FA empty for $from->$to", fa.isNotBlank())
            assertTrue("FA must contain Persian script for $from->$to",
                fa.any { it.code in 0x0600..0x06FF })
            seen += en
        }
        // 6 directed edges collapse to 4 distinct labels BY DESIGN:
        // both ->GRACE edges share the unlock wording, and both GRACE->*
        // edges share the "locked again" wording.
        assertEquals("expected exactly 4 distinct labels, got ${seen.size}", 4, seen.size)
    }

    @Test fun unlockLabel() {
        val (en, fa) = GuardStateUi.label(GuardStateUi.S.PROTECTED, GuardStateUi.S.GRACE)
        assertEquals("✓ Unlocked — free time", en)
        assertEquals("✓ باز شد — زمان آزاد", fa)
    }

    @Test fun relockAfterGraceUsesLockAgainWording() {
        val (en, _) = GuardStateUi.label(GuardStateUi.S.GRACE, GuardStateUi.S.BLOCKED)
        assertEquals("🔒 Locked again", en)
        val (en2, _) = GuardStateUi.label(GuardStateUi.S.GRACE, GuardStateUi.S.PROTECTED)
        assertEquals("🔒 Locked again", en2)
    }

    // ---------- chime: dashboard-parity tone pairs ----------

    @Test fun chimePairings() {
        assertEquals(GuardStateUi.Chime.UNLOCK,
            GuardStateUi.chime(GuardStateUi.S.PROTECTED, GuardStateUi.S.GRACE))
        assertEquals(GuardStateUi.Chime.UNLOCK,
            GuardStateUi.chime(GuardStateUi.S.BLOCKED, GuardStateUi.S.GRACE))
        assertEquals(GuardStateUi.Chime.LOCK,
            GuardStateUi.chime(GuardStateUi.S.PROTECTED, GuardStateUi.S.BLOCKED))
        assertEquals(GuardStateUi.Chime.LOCK,
            GuardStateUi.chime(GuardStateUi.S.GRACE, GuardStateUi.S.BLOCKED))
        assertEquals(GuardStateUi.Chime.LOCK,   // grace cancelled -> re-lock
            GuardStateUi.chime(GuardStateUi.S.GRACE, GuardStateUi.S.PROTECTED))
        assertEquals(GuardStateUi.Chime.UNLOCK, // unlatched (reset / remote full unlock)
            GuardStateUi.chime(GuardStateUi.S.BLOCKED, GuardStateUi.S.PROTECTED))
    }
}
