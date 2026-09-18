# Feature Specification: Restart-Safe Period Rollover — the Latch Must Release Tomorrow (spec-012)

**Feature Branch**: `012-restart-safe-rollover`

**Created**: 2026-09-17

**Status**: Implemented

**Input**: Owner report: "I found a bug in real use of version 1.2 and I want to
know still we have that bug in the latest version? and that is it won't unlock
over time or tomorrow and I have to turn the enforce limit off and on again."

## Problem Statement

The owner's v1.2 report contains TWO distinct defects; this spec addresses the
one that is still alive in the current build (app v1.4.0 / worker dg-v8):

1. **"Won't unlock over time"** — the timed unlock was ineffective in the v1.2
   era. **Already fixed**: spec-008 (settings draft bug, worker dg-v6), then
   superseded by the duration picker (spec-010, dg-v7) and the merged unlock
   button (spec-011, dg-v8). Verified end-to-end on the live worker. NOT the
   subject of this spec.

2. **"Won't unlock tomorrow"** — after the daily limit latches the device, the
   lock promise shown to the kid is *"Internet is locked until tomorrow"*
   (WatchdogService latched-notification). But the release at the day boundary
   only happens if the watchdog **process runs continuously across midnight**.
   `lastPeriod` — the in-memory variable that detects
   `nowPeriod != lastPeriod` — is seeded in `onCreate()` from
   `Prefs.currentPeriodStart()` (i.e. **"now" at service start**, not the period
   the latch belongs to) and is never persisted.

   Whenever the process is not alive across the boundary — phone rebooted
   overnight, battery died, OEM task killer, force-stop, app update, or the
   phone simply being OFF at midnight and turned on next morning (BootReceiver
   restarts the service after the boundary) — the restarted service sees
   `nowPeriod == lastPeriod` forever. The rollover block never runs; the
   persisted `latch_reason="limit"` latch survives **every following day**,
   because the limit check in `cycle()` only ever *latches*
   (`if (!latched && !grace && used >= limit)`), never releases.

   Field signature: the device shows "LIMIT REACHED (~0 MB used)" — today's
   fresh counter is far below the limit, yet the lock persists. The only
   escapes are the parent toggling the enforce (monitoring) switch off/on —
   `disableEverything()` calls `WatchdogService.unlatch()` — pressing Reset,
   or a timed/full unlock, which for a `limit` latch grants at best a grace
   window that re-arms on expiry. This is exactly the workaround the owner
   described.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Overnight restart still unlocks in the morning (Priority: P1)

**As a** parent,
**when** my kid's phone restarts, reboots, updates, runs out of battery, or is
simply powered off at midnight and turned on the next morning while latched for
the daily limit,
**then** within one watchdog tick (~1 s) of the guard coming back up, the
yesterday-limit latch releases automatically: the status line returns to normal
usage tracking, the notification no longer says LIMIT REACHED, Wi-Fi/VPN
enforcement lifts, and the fresh day's budget applies — **without me toggling
the enforce switch off and on**.

### User Story 2 - Security latches still survive the boundary (Priority: P1)

**As the** owner,
**when** the device is latched for a **cloud** ("Locked by parent"),
**offline** (fail-closed) or **clock** (tamper) reason and the process restarts
across midnight,
**then** the latch is **kept** (fail-closed, Art. II): a restart must never
become a free unlock for security locks. Only remote full-unlock (cloud/offline
reasons) or the parent PIN path releases those, exactly as today.

### User Story 3 - Fail-closed when usage stats are unavailable (Priority: P1)

**As the** owner,
**when** the guard restarts after midnight but usage-access is revoked or the
NetworkStats query fails,
**then** the limit latch is **NOT** released (a restart plus revoked stats must
not become a free unlock — Art. II), the guard retries the rollover every tick,
and it self-heals the moment stats become readable again (permission re-granted
or transient failure cleared).

### User Story 4 - Nothing else changes (Priority: P1)

**As the** owner,
**every** existing behavior is preserved: continuous-run rollover (live
midnight crossing) identical; monthly periods stable across same-month
restarts; grace windows, PIN unlock, cloud commands, hot polling (spec-005),
dashboard/worker wire format — untouched. Devices updating from any older
version migrate safely (missing pref key ⇒ today's behavior on first run, fix
applies from the next boundary onward).

## Requirements

### Functional — Android app only (no worker/dashboard change)

- **FR-001**: Persist the last period start the watchdog actually processed:
  new pref `period_start` (`Prefs.periodStart` / `Prefs.setPeriodStart`).
- **FR-002**: `WatchdogService.onCreate` seeds its rollover detector
  (`lastPeriod`) from the **persisted** `period_start`; when the key is absent
  (first run / migration from older builds) it seeds and persists the current
  period — behaviorally identical to today for that first run (safe migration).
- **FR-003**: When `cycle()` observes `nowPeriod != lastPeriod` (live crossing
  **or** restart-after-boundary), it processes the rollover exactly as today
  for security latches (reasons `cloud`/`offline`/`clock`/`unpaired` are KEPT)
  and additionally persists the new `period_start`.
- **FR-004**: The `""`/`limit`-latch release at rollover is gated on an
  **authoritative usage read**: `DataStats.effectiveUsage(nowPeriod) >= 0`.
  If stats are unavailable, the latch is kept and the rollover is retried on
  every tick (self-heal); the pending state is logged once per streak (not
  once per tick) so View logs stay readable. `period_start` is only advanced
  once the rollover fully resolves.
- **FR-005**: In the restart case the live counter is seeded (as today) from
  `effectiveUsage(persistedPeriod)` in `onCreate`; the rollover then resets it
  to `effectiveUsage(nowPeriod)` — unchanged code path, now also correct when
  the boundary was crossed while dead.
- **FR-006**: Grace semantics at the boundary are unchanged (grace force-cleared
  when a rollover processes; a grace window that spans a restart keeps working
  because `grace_until` is persisted).
- **FR-007**: Zero changes to: cloud commands/payloads, D1, worker routes,
  dashboard, PIN flow, VPN control channel exemption (Art. VII), i18n strings
  (no new user-facing strings — the existing notification already promises
  "locked until tomorrow"; this spec makes that promise true).

### Non-functional

- No new permissions, no new dependencies, no background-scheduling changes
  (no AlarmManager needed: the 1 s tick + START_STICKY + BootReceiver already
  restart the service; the fix is purely state-derivation logic).
- The per-tick retry in the stats-unavailable state costs one NetworkStats
  binder call per second only while a limit latch is pending release with
  unreadable stats — acceptable vs a 1 s tick that already polls TrafficStats.
- Robolectric release gate (spec-009): a new `RolloverRestartTest` drives the
  real `WatchdogService` cold start in the stuck state and must pass before any
  APK ships.

## Failure Modes Analyzed

1. **Free unlock via revoked stats + reboot** — a restart seeds the counter
   from stats; if stats are unreadable the naive release would unlatch on
   `0 < limit`. Guard: FR-004 gates the release on `effectiveUsage >= 0`;
   pending state stays latched and retries. Tested (T5).
2. **Free unlock via clock-forward** — setting the clock +1 day advances the
   period and releases a limit latch with a ~0-usage read. **Pre-existing**
   (the live rollover has the same property today) and **out of scope**; a
   server-time-authoritative period is the fix and belongs to the upcoming
   security spec (roadmap 013). Documented here so it is not forgotten.
3. **Clock-backward while dead** — restart with `nowPeriod < persisted`:
   `!=` fires, the release would unlatch, but the counter is reset to
   `effectiveUsage(earlier period)` which includes MORE usage, so the limit
   check re-latches within the same tick (self-correcting). Paired devices
   additionally have the server-time `clock` latch. Unchanged vs today.
4. **Monthly toggle mid-period** — switching daily↔monthly legitimately moves
   `currentPeriodStart` (possibly backward); the `!=` trigger treats it as a
   boundary and re-evaluates the budget from the new period start — identical
   to today's continuous behavior; `period_start` follows. Unchanged.
5. **Migration from old builds** — `period_start` absent ⇒ FR-002 seeds "now":
   a device that is already stuck at update time stays stuck until the NEXT
   boundary (or one manual off/on). Disclosed in the release note; fail-closed
   bias preferred over a risky one-shot migration heuristic.
6. **Kid opens app while service dead** — MainActivity does not start the
   watchdog; nothing enforces while dead. On the next service start (boot,
   START_STICKY revive, parent action) the rollover processes. Unchanged
   exposure, now with a correct outcome at the next start.
7. **Robolectric stats** — the test JVM has no real NetworkStats; a test-local
   `@Implements(NetworkStatsManager::class)` shadow returns a real empty
   `Bucket` (0 bytes = authoritative) and can be switched to throw (failure
   simulation) so both the release and the fail-closed path are testable.

## Art. XI Disclosure (delivered)

**APP UPDATE REQUIRED** — install `DataGuard-v1.4.1-release.apk` (versionCode
12) over v1.4.0; settings, PIN and pairing survive the update. **Worker and
dashboard: UNTOUCHED** — no deploy, no reload, dg-v8 stays. Devices already
stuck at update time clear on the next midnight boundary or one final
enforce-off/on toggle (see Failure Mode 5).


---

# v1.4.2 Amendment — Deep-Scan Fixes (2026-09-18, second session)

**Status**: Implemented

**Input**: Owner directive: "Make a deep scan of the full system and debug
anything we didn't see until now and use every tool." A full-system scan
(all 20 Kotlin files, the 1,797-line worker, live probes, read-only D1
forensics) ran the day after v1.4.1 shipped. Reconciliation note: this
session started from a pre-v1.4.1 context and produced an overlapping
RolloverPolicy design; the merge KEEPS the released v1.4.1 mechanism and adds
the non-overlapping fixes below as **v1.4.2 (versionCode 13) + worker dg-v9**.

## Findings

- **F3 (MEDIUM, Art. VIII)** — full unlock on a limit-locked phone: the app
  degrades `{full:true}` to a 15-minute window (correct, Art. II) but the
  report had no `latch_reason`, so the dashboard never confirmed the pend
  (90 s "Unlocking…" then silence) and the confirm text over-promised
  ("stays open until you lock it again").
- **F4 (LOW)** — `HistoryUi.weekdayLabel` FA array misordered: every Farsi
  weekday glyph off by two days (Sunday showed جمعه). The test only checked
  distinctness.
- **F5 (LOW)** — undelivered commands for revoked devices never cleaned
  (command #7 stuck 139 h; unbounded growth).
- **F6 (LOW)** — dead `attempts >= 10` check in `handleChildPair` (never
  incremented by design; reads as a security control but is not one).
- **F2 (OPERATIONAL, disclosed)** — D1 shows the real phone's pairing was
  revoked Sep 15 21:34; remote control inert until re-paired.
- **FM5 AMENDED** — v1.4.1 chose "stuck stays stuck until next boundary".
  v1.4.2 implements the one-shot unstick SAFELY: a restored limit latch with
  no processed-period record makes the first cycle process the boundary;
  genuinely fresh usage releases it (the stuck phone self-unblocks), same-day
  over-limit usage re-locks on the SAME tick (counter re-seeds from the
  current period — never a free unlock), unreadable stats keep it latched
  (the v1.4.1 fail-closed gate). Guarded by rewritten T6 + new T7.

## v1.4.2 Functional Requirements

### App (v1.4.2, versionCode 13)
- **FR-201** onCreate: restored limit latch + absent `period_start` → seed
  `lastPeriod = 0` so the boundary processes once (T6/T7 semantics).
- **FR-202** `CloudLink.buildReport` adds `latch_reason` (coarse, Art. III).
- **FR-203** HistoryUi FA weekday array Sun..Sat = ی د س چ پ ج ش; test pins
  real dates (1970-01-01 = Thursday) in both languages.
- **FR-204** versionCode 13 / 1.4.2 / UA bumped.

### Worker (dg-v9)
- **FR-205** report whitelist accepts `latch_reason` (≤ 16 chars).
- **FR-206** `pendConfirmed`: full-unlock pend confirms on the degraded
  window landing; `toastConfirmed` says "✓ N-min window granted — limit lock
  kept" for limit/clock latches (EN+FA).
- **FR-207** `confirmUnlockLimited` truthful warning when the report shows a
  limit/clock latch.
- **FR-208** LOCKED badge carries the reason (limit/parent/offline/clock,
  EN+FA); grace wins over the latch in the badge (matches the app hero).
- **FR-209** revoke + 5 % sweep delete undeliverable commands.
- **FR-210** dead attempts check removed; SW dg-v8 → dg-v9.

## v1.4.2 Art. XI Disclosure (delivered)

**APP UPDATE REQUIRED** — install `DataGuard-v1.4.2-release.apk`
(versionCode 13; the unstick + FA chart + latch_reason report need it; the
v1.4.1 rollover fix is included). **Worker: ALREADY LIVE** (dg-v9 deployed
2026-09-18; one browser reload at most). **F2**: re-pair the phone (new code
from the dashboard) to restore remote control.
