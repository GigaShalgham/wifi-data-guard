# Implementation Plan: Restart-Safe Period Rollover (spec-012)

## Clarify (self, evidence-based — no open ambiguity for the owner)

The owner's question ("do we still have that bug?") was answered from code
before planning; the fix scope was decided as follows:

- **Q1: Which half of the v1.2 report is still alive?**
  Evidence: spec-008 fixed the settings-draft bug (worker dg-v6, live); the
  picker (dg-v7) and merged button (dg-v8) replaced the flow entirely and were
  e2e-verified. The rollover release, however, still depends on an in-memory
  `lastPeriod` seeded from "now" at service creation (WatchdogService.kt:122)
  ⇒ only the "won't unlock tomorrow" half is alive. **Scope = rollover only.**
- **Q2: Why not also add a down-release invariant (`used < limit` ⇒ unlatch)?**
  It would fix the sibling annoyance "parent raises the limit while latched,
  kid stays locked", but after a restart the live counter is 0 until seeded,
  so a naive invariant fails OPEN when usage stats are unreadable (revoked
  permission). Doing it safely needs a counter-authority flag — a separate
  design. **Deferred; documented as a known behavior, not part of spec-012.**
- **Q3: Retry-loop or single-shot when stats are unavailable at the boundary?**
  Single-shot (persist period, keep latch, wait for next boundary) would leave
  a transient midnight stats hiccup locking the kid for a whole extra day.
  Retry-every-tick self-heals within seconds of stats recovery and costs one
  binder call/second only in the pending state. **Retry chosen (FR-004).**
- **Q4: Direction-aware rollover (`nowPeriod > lastPeriod` only)?**
  Rejected — it would break the legitimate monthly-toggle re-evaluation
  (period start moves backward mid-month) and changes continuous-run behavior.
  The `!=` trigger is kept; clock-backward self-corrects by re-latching on the
  larger usage window (see spec failure mode 3).

## Decisions

- **D1 — Persisted period, single source of truth**: new pref `period_start`
  (`Prefs.periodStart/setPeriodStart`). `WatchdogService.onCreate` loads it;
  `0` (absent) ⇒ seed+persist `currentPeriodStart()` (identical to today on
  the first run after update — safe migration, fail-closed).
- **D2 — Rollover processing advances the persisted period only when fully
  resolved** (security latch ⇒ always resolvable; limit latch ⇒ resolvable
  only with an authoritative stats read). This is what makes the retry loop
  possible without re-processing already-resolved boundaries.
- **D3 — Authoritative-read gate (Art. II)**: `DataStats.effectiveUsage >= 0`
  is the only acceptable proof that a fresh period is genuinely under budget.
  Anything else keeps the latch.
- **D4 — Pending log throttle**: one log line per pending streak
  (`rolloverPendingLogged` flag), reset on resolution — View logs stay
  readable during a long outage.
- **D5 — onCreate seeding order unchanged**: `LiveCounter.seedWith(
  effectiveUsage(persistedPeriod).coerceAtLeast(0))` — with a persisted
  yesterday this seeds high, then the first cycle's rollover resets to the
  fresh period. The high value is never evaluated against the limit (the
  rollover runs before the limit check in the same cycle) — verified by test.
- **D6 — Test vehicle**: new `RolloverRestartTest` (Robolectric, sdk 34,
  GuardApp) driving the REAL service (`buildService().create().startCommand()`
  + looper idle). A test-local `@Implements(NetworkStatsManager::class)` shadow
  returns a real empty `NetworkStats.Bucket` (authoritative 0 bytes) with a
  `failQueries` knob for the fail-closed case. Companion/static state is
  re-seeded per test via prefs before `create()`.
- **D7 — Versioning**: app v1.4.1, versionCode 12, QA suffix `-test9` tag.
  `CloudLink` UA strings bumped `DataGuard-Android/1.4.0` → `1.4.1`. No worker
  change (Art. XI: APP UPDATE REQUIRED).
- **D8 — Release gate**: full unit suite (incl. LaunchSmokeTest fresh+armed,
  TabSwitchSmokeTest, HistoryUiTest, GuardStateUiTest, NEW
  RolloverRestartTest) must be green before assembleRelease/assembleQa;
  apksigner verify + badging (12 / 1.4.1 / 1.4.1-test) + UA-in-dex check.

## Risks & mitigations

| Risk | Mitigation |
|---|---|
| Retry loop spams binder/log | Log once per streak; query throws fast when revoked; loop only runs while pending |
| Migration leaves currently-stuck devices stuck one more day | Disclosed in release note + spec FM5; next boundary or one manual toggle clears |
| Robolectric shadow drift | Shadow is test-local, minimal surface (one method), no reliance on Robolectric internals |
| Regression in live rollover | Rollover block restructured but branch-equivalent for the stats-OK path; guarded by T1–T4 + existing suite |

## Verification matrix

- T1 restart-after-midnight + limit latch ⇒ releases (US1)
- T2 restart-after-midnight + cloud latch ⇒ kept, period still advances (US2)
- T3 same-day restart ⇒ no rollover, latch kept (fail-closed; documents FM5)
- T4 monthly period ⇒ same-month restart keeps latch (US4)
- T5 stats-unavailable at boundary ⇒ latch kept, `period_start` NOT advanced,
  retry stance proven (US3)
- T6 first-run migration: `period_start` absent ⇒ seeded to now, no rollover
- Full existing suite stays green (US4)


## v1.4.2 Amendment decisions (D11–D14)
- **D11** Keep the released v1.4.1 rollover mechanism (persisted
  `period_start` + fail-closed stats gate); the second session's overlapping
  RolloverPolicy design is dropped at merge.
- **D12** Unstick = seed `lastPeriod = 0` for a restored limit latch with no
  processed-period record; safety comes from the v1.4.1 gate itself (fresh
  authoritative read or keep latch) plus same-tick re-latch on real usage.
- **D13** Worker ships as dg-v9 with report `latch_reason` (old apps simply
  don't send it — every new dashboard path degrades to today's behavior).
- **D14** Version path: v1.4.1 already released (versionCode 12) → amendment
  is v1.4.2 (versionCode 13) so Android offers a real update.
