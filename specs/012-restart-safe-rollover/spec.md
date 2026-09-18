# Feature Specification: Deep-Scan Fixes — Restart-Safe Rollover + Honest Full Unlock (spec-012)

**Feature Branch**: `012-restart-safe-rollover`

**Created**: 2026-09-18

**Status**: Implemented

**Input**: Owner directive: "Make a deep scan of the full system and debug
anything we didn't see until now and use every tool." The deep scan (full code
review of all 20 Kotlin files + the 1,797-line worker, 21/21 unit tests,
`node --check`, live worker probes, read-only D1 forensics) produced the
findings below. This spec fixes the code defects; one operational finding
(revoked pairing) is disclosed in the converge notes, not coded.

## Findings (evidence-based)

### F1 — CRITICAL, the v1.2 "won't unlock tomorrow" bug is STILL PRESENT

Owner report (v1.2 era): *"it won't unlock over time or tomorrow and I have to
turn the enforce limit off and on again."*

Root cause (code): `WatchdogService.cycle()` clears a **limit** latch only in
the period-rollover branch, which compares `Prefs.currentPeriodStart()` against
the **in-memory** `lastPeriod` (initialized in `onCreate` to the *current*
period). `latched` is **persisted**. If the process is not alive across
midnight — reboot, OEM battery killer, `START_STICKY` restart landing after
00:00, app update (`MY_PACKAGE_REPLACED`) — the restarted service restores
`latched=true` with reason `limit`, `lastPeriod` already equals the new period,
the rollover branch never fires, and the device stays locked for the whole new
period despite the quota having reset.

Field evidence (read-only D1): device 7 (the real phone, v1.3.1-test era) shows
a 41.8-hour report gap crossing midnight (Sep 13 → Sep 15); the owner's
workaround — toggling "Enforce limit" off/on — is exactly the
`disableEverything()` → `WatchdogService.unlatch()` path, the only manual
unlatch besides "Reset counter".

### F3 — MEDIUM, dashboard truth gap on full unlock (Art. VIII)

When the phone is latched with reason `limit` (the common case), the app
correctly degrades a `{full:true}` unlock to a 15-minute grace window
(constitution Art. II — limit latches survive remote commands) and says so
**locally**. But the device report has no `latch_reason`, so the dashboard:
(a) shows the pend chip "Unlocking… waiting for device" for the full 90 s and
never confirms (`pendConfirmed` waits for `!rep.latched`, which stays true);
(b) the `confirmUnlockFull` text promises "Internet stays open until you lock
it again (or its data limit is reached)" — over-promising vs. the actual
15-minute window.

### F4 — LOW, FA weekday labels wrong in the history chart

`HistoryUi.weekdayLabel` indexes `arrayOf("ج","ش","ی","د","س","چ","پ")` with
Java's `DAY_OF_WEEK-1` (1=Sun..7=Sat): every Persian glyph is off (Sunday shows
ج = Friday). EN labels are correct. The unit test only asserted 7 distinct
glyphs — the mapping slipped through.

### F5 — LOW, undelivered commands for revoked devices never cleaned

Cleanup deletes only `acked_at IS NOT NULL AND created_at < t-7d`. A revoked
device can never poll, so its undelivered commands stay forever (command #7:
config, stuck 139 h). One row exists today; unbounded growth over time.

### F6 — LOW, dead anti-brute-force check

`handleChildPair` checks `row.attempts >= 10` but nothing ever increments
`attempts` (lookup is by code hash; a wrong guess maps to no row). The real
guard is the 30/h/IP rate limit. Dead code that reads as a security control
but is not one.

### F2 — OPERATIONAL (no code): real phone unpaired

D1 shows ALL devices revoked, including the real phone (device 18, SM-A546E,
revoked Sep 15 21:34 during owner testing). Remote lock/unlock/config is inert
until the phone is re-paired (new code). Disclosed in the Art. XI verdict.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — The next day unlocks itself (Priority: P1)

**As a** parent whose kid hit the daily limit yesterday,
**when** the phone restarts (reboot, battery-killer, app update) any time after
midnight,
**then** the watchdog reconciles on startup: a **limit** latch from an older
period is cleared, the counter resets to the fresh period's usage, and the
internet works — no "enforce limit" toggling, no PIN dance.

Guards: a latch with reason `cloud`/`offline`/`clock`/`unpaired` is NEVER
cleared by the reconcile (same rule as the live rollover — Art. II); a clock
rolled **back** past the latch (currentPeriodStart ≤ latchPeriod) is NOT an
unlatch (strictly-greater comparison only).

### User Story 2 — Honest full unlock on the dashboard (Priority: P2)

**As a** parent pressing "∞ Full unlock" on a phone locked by its data limit,
**I see** a warning that tells the truth ("its limit lock stays; it gets a
timed window instead"), **and when** the device confirms, the toast says what
actually happened ("15-min window granted — limit lock kept"), not a silent
90-second "Unlocking…" chip. Old apps (no `latch_reason` in report) keep the
exact current behavior.

### User Story 3 — Correct FA chart + clean queue (Priority: P3)

FA weekday glyphs match the actual weekday (Sun=ی … Sat=ش). Revoking a device
also drops its undelivered commands; the sweep keeps the queue clean. The dead
`attempts` check is removed (zero behavior change).

### User Story 4 — Nothing else regresses (Priority: P1)

21/21 existing tests stay green; Robolectric cold-start gates (fresh + armed)
pass; `node --check` passes; DASHBOARD_JS compile gate passes; i18n EN/FA
parity maintained; spec-004/005/008/010/011 regression markers intact.

## Functional Requirements

### App (v1.4.1, versionCode 12)

- **FR-001** Persist `latch_period_start` (epoch ms of `currentPeriodStart()`
  at latch time) at every latch site: the limit-hit block in `cycle()` and
  `latchCloud()`.
- **FR-002** On `WatchdogService.onCreate` (after `restoreLatched`), reconcile
  via FR-003 policy: unlatch + clear grace + `LiveCounter.resetTo(fresh usage)`
  + log `reconcile: latch older than current period -> released`. Migration:
  absent/zero `latch_period_start` counts as "older" (a stuck limit latch from
  any previous version releases on first start after updating).
- **FR-003** New pure object `RolloverPolicy` (no Android imports) with
  `shouldRelease(reason, latchPeriodStart, currentPeriodStart): Boolean` =
  `reason in {"", "limit"} && currentPeriodStart > latchPeriodStart`.
- **FR-004** `CloudLink.buildReport` adds `latch_reason` (the coarse string
  already shown on the local status line; Art. III-compliant enforcement
  state, no PII).
- **FR-005** `HistoryUi` FA array reordered to Sun..Sat =
  `["ی","د","س","چ","پ","ج","ش"]`; `HistoryUiTest` extended to assert the real
  weekday of known dates in both languages.
- **FR-006** `versionCode 12`, `versionName "1.4.1"`, `User-Agent` strings
  bumped to 1.4.1 (two occurrences in CloudLink).

### Worker (dg-v9)

- **FR-101** `handleChildPoll` report whitelist adds
  `latch_reason: String(report.latch_reason || "").slice(0, 16)`.
- **FR-102** `pendConfirmed`: a full-unlock pend (not `timed`, not `oldApp`)
  also confirms when `rep.grace_until > Date.now()` (the degraded window
  landed). `toastConfirmed` then picks an honest message: latched +
  grace-active + `latch_reason ∈ {limit, clock}` → new key
  `toastFullWindow` ("✓ %d-min window granted — limit lock kept" / FA), else
  the existing `toastUnlocked`.
- **FR-103** `confirmUnlockFull` gains a truthful variant when the last report
  shows `latch_reason` `limit`/`clock` (new key `confirmUnlockLimited`:
  explains the phone will get a timed window instead). Old-report fallback =
  current text.
- **FR-104** Locked card badge shows the reason when known — new i18n keys
  `reasonLimit`, `reasonCloud`, `reasonOffline`, `reasonClock` appended to the
  LOCKED label (unknown reason → plain LOCKED as today).
- **FR-105** `handleRevoke` deletes that device's undelivered commands; the
  existing 5 % sweep additionally deletes undelivered commands of revoked
  devices.
- **FR-106** Remove the dead `attempts >= 10` branch in `handleChildPair`.
- **FR-107** SW cache `dg-v8` → `dg-v9`.

## Failure Modes *(mandatory)*

- **Clock rolled back below the latch period**: `currentPeriodStart` ≤
  `latchPeriod` → no release (Art. II). Unit-tested.
- **Cloud/offline/clock latch + restart**: reason not in {`""`,`limit`} → no
  release. Unit-tested.
- **Reconcile runs twice** (service restart storm): second run sees
  `latched=false` → no-op.
- **Usage stats unavailable at reconcile**: keep counter, still unlatch (same
  as the live rollover's fallback).
- **Old app + new worker**: no `latch_reason` in report → all new dashboard
  paths degrade to today's behavior.
- **New app + old worker**: extra report field ignored by old whitelist; app
  still self-fixes the latch (FR-002 is app-local, Art. IX).
- **Worker deploy failure**: dg-v8 stays live (atomic upload; probe after).
- **Robolectric gate**: fresh + armed cold starts must pass (v1.3.5 lesson).

## Art. XI Plan

APP UPDATE REQUIRED for F1/F4 (v1.4.1 release APK + QA `.test` build); worker
side (F3/F5/F6) deploys live instantly as dg-v9 with no APK. The two sides are
independently safe (see Failure Modes) but the honest-full-unlock UX needs
both. F2 requires the owner to re-pair the phone (no code).
