# Requirements Checklist: 010-ux-revolution

## User Stories
- [x] US1: Parent grants time in one gesture — picker at point-of-use, no settings round-trip, choice remembered
- [x] US2: Kid's phone feels like a 2026 app — tabs, ring, history chart, PIN-gated Parent area, animations
- [x] US3: Battery line renders 🔋 — no mojibake, both languages

## Dashboard FRs
- [x] FR-001: settings panel has NO unlock-window field; draft/bind/payload cleaned; config API still accepts unlock_minutes
- [x] FR-002: picker = chips 15/30/60/120 + custom 1–480 (invalid→15); seed = localStorage → server setting → 15; remembered per device
- [x] FR-003: unlock command carries picked minutes; pend/toast/audit agree
- [x] FR-004: `🔋` battery glyph, corrupted codepoint gone
- [x] FR-005: i18n EN+FA parity; `unlockMin`/`confirmUnlockFor` retired
- [x] FR-006: SW dg-v7
- [x] FR-007: `/api/child/history` device-authed; deltas clamped ≥0; null-no-baseline; 0-no-data; days 1–14; tz ±1440; local-day buckets

## App FRs
- [x] FR-101: BottomNavigationView, 3 tabs, bilingual labels, BLOCKED badge on Status
- [x] FR-102: Parent tab PIN-gated once per session; failed gate reverts tab (suppressed)
- [x] FR-103: UsageRingView on Status; semantics of hero/status/unlock unchanged (Art. VIII)
- [x] FR-104: Usage tab — stats grid, 7-day chart, updated-ago, stale-first, honest unpaired hint
- [x] FR-105: Parent tab — chips replace Spinner (same PIN-gated semantics); Spinner+echo code deleted; all tools present; QA test panel
- [x] FR-106: aurora bg, ripples, entrance, count-up — all reduce-motion gated
- [x] FR-107: versionCode 11 / 1.4.0 / UA bumped
- [x] FR-108: Robolectric smoke (fresh+armed) + new tab-switch smoke pass

## Non-functional
- [x] Zero enforcement change; history cosmetic (no latch/block on failure)
- [x] Bilingual truth (Art. V) — every new string EN+FA
- [x] No test commands to the real device
