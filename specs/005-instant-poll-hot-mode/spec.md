# Feature Specification: Instant command delivery (hot-mode long-poll)

**Feature Branch**: `005-instant-poll-hot-mode`

**Created**: 2026-09-15

**Status**: Implemented (worker live 2026-09-15; app side ships as v1.3.4)

**Input**: Owner request: "reduce the poll time and make it better". Clarification round
selected **Instant ⚡** (over "15s default" and "Both"): commands land in 1–2 s while
the parent is using the panel; idle pacing stays at the configured interval (default
30 s) to protect the phone's battery. The plain-interval option was deliberately NOT
chosen — battery-smart was preferred over blanket speed.

**How it works (design)**:

1. **The dashboard is the signal.** Every authenticated `/api/me` call (the panel's
   10 s heartbeat, only while the tab is visible) stamps `hot_until = now + 75 s` on
   all of that parent's non-revoked devices. Close/background the tab → heartbeat
   stops → hot expires within 75 s.
2. **Devices opt in to waiting.** Every poll may carry `"wait": <seconds>` (clamp
   0–25). The Worker holds the request — checking D1 for undelivered commands every
   1.5 s — **only while the device is hot**; otherwise it responds immediately,
   exactly as today. So the radio cost of holding happens only while the parent is
   actually watching.
3. **Fast loop.** A held poll that returns (with or without commands) carries
   `fast: true` while the device is hot; the app then re-polls immediately
   (1 s pacing, no jitter), keeping a held connection open essentially all the time
   the panel is in use. A command inserted at any moment lands in ~1.5 s.
4. **Command creation extends the window** (`now + 120 s`) so the ack + report phase
   is fast too — the parent sees the confirmed state in seconds, not a poll cycle.
5. **Honest indicator.** `last_wait_poll_at` is stamped whenever a poll with
   `wait > 0` arrives; `/api/me` exposes `fast: true` when it is fresh (< 45 s) and
   the card shows a ⚡ FAST chip. Old apps (≤ v1.3.3) never send `wait`, so they never
   show ⚡ — no lying badge (Art. VIII).

**Known limitation (documented, accepted)**: the *first* command after opening the
panel can still wait up to one poll cycle (~30 s) if the parent taps before the
device's next poll enters its held state. Every command after that is instant for
the whole session. True push (FCM) was rejected — it would add a Google-services
dependency to a privacy-scoped, offline-first app (Art. III/IX).

**Constitution articles touched**: II (fail-closed — hot mode changes pacing only,
never lock semantics; a timed-out hold = today's behavior), III/IX (no new data
collected; cloud stays optional; no push dependency), VI (poll pacing stays REAL
time — `System.currentTimeMillis()`, never `AppClock`), VII (long-poll rides the
self-exempted control channel), VIII (⚡ chip shows only actual long-polling),
XI (app update REQUIRED for the instant mode: v1.3.4; worker parts live now).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Parent taps Lock and it lands in ~2 seconds (Priority: P1)

The parent has the dashboard open (visible tab). Within ~30 s of opening, the phone
app (v1.3.4+) is continuously holding a poll. The parent taps Lock; within ~1.5 s
the command is delivered; the phone applies it and re-polls immediately; the ack and
the latched report land within ~2–4 s total; the card's pending chip clears and the
LOCKED badge shows — no manual refresh, no re-tap.

**Independent Test**: open the panel, wait ~30 s, tap Lock. The "Locking…" chip
should clear to LOCKED within ~5 s. D1 shows `command_created → command_delivered`
within ~2 s.

### User Story 2 - Idle battery is unchanged (Priority: P1)

The parent closes the dashboard. The heartbeat stops; the device's hot window
expires (≤ 75 s server-side; the app clears fast mode on the next non-fast response).
The phone returns to its normal 30 s ± jitter poll cycle — identical battery profile
to v1.3.3. A hard cap additionally stops fast mode after 30 minutes of continuous
fast polling (runaway protection).

**Independent Test**: close the tab; after ~2 min the ⚡ chip is gone from the card
(later, on a fresh dashboard open) and D1 poll spacing returns to ~30 s.

### User Story 3 - Old apps and rollbacks stay safe (Priority: P1)

A phone still on v1.3.2/v1.3.3 never sends `wait`; the Worker responds to it exactly
as before (immediate, no hold), `fast` is never true for it, no ⚡ chip appears. A
v1.3.4 app against a rolled-back Worker (no `wait` support) sends `wait` the worker
ignores, gets an immediate normal response with no `fast` field → app stays at normal
pacing. Neither direction degrades (Art. II).

### User Story 4 - The panel tells the truth about the link (Priority: P2)

The device card shows a ⚡ FAST chip only while the device is actually long-polling
(`last_wait_poll_at` fresh). Combined with the spec-004 old-app badge, the parent can
see at a glance: old app (needs update), fast link (instant commands), or plain
polling (~30 s).

## Requirements Analysis

- **FR-001 (P1)**: D1 `devices` gains `hot_until` and `last_wait_poll_at` (INTEGER
  NOT NULL DEFAULT 0), migrated idempotently before the worker deploy.
- **FR-002 (P1)**: `handleMe` stamps `hot_until = now + 75 s` for all the parent's
  non-revoked devices and returns per-device `fast` (fresh `last_wait_poll_at`).
- **FR-003 (P1)**: `handleCommand` extends `hot_until` to `now + 120 s` (max with
  current) on command creation.
- **FR-004 (P1)**: `handleChildPoll` accepts `wait` (clamp 0–25 s); when hot, holds
  up to the deadline checking every 1.5 s; responds immediately when not hot;
  stamps `last_wait_poll_at` when `wait > 0`; response includes `fast`.
- **FR-005 (P1)**: Dashboard pauses its 10 s heartbeat while the tab is hidden
  (visibilitychange) and resumes on show — hot mode is paid for only by attention.
- **FR-006 (P1)**: App v1.3.4: always sends `wait: 20`; read timeout 35 s for wait
  polls; 1 s re-poll pacing + no jitter while `fast`; 90 s re-arm window; 30 min
  continuous-fast hard cap; UA `DataGuard-Android/1.3.4`; versionCode 8.
- **FR-007 (P2)**: ⚡ FAST chip on the device card (EN+FA) when `fast` is true.
- **FR-008 (P2)**: `cloud/README.md` documents the `wait` param, the columns, and
  the hot semantics.

## Failure Modes

- Hold times out (no command) → normal response with `fast` → app re-polls: by design.
- Network drop mid-hold → read timeout → treated like any failed poll today (retry
  next cycle; offline latch logic unchanged, tolerance still minutes).
- Worker rollback → `wait` ignored, `fast` absent → app paces normally (US3).
- Old app + new worker → no `wait` sent → behavior identical to v1.3.3 (US3).
- Parent leaves tab visible for days → server hot keeps refreshing, but the app-side
  30 min cap forces a return to normal pacing until the next non-fast poll re-arms
  it (single normal cycle, then hot resumes if still signaled) — bounded radio cost.
- D1 migration failure → deploy is blocked (worker references columns); migration
  script checks PRAGMA first and is idempotent.

## App-Update Disclosure (Art. XI)

- **Worker/dashboard parts (FR-001..005, 007): LIVE immediately, no APK.**
- **Instant delivery on the phone (FR-006): requires app v1.3.4** (QA:
  `v1.3.4-test5`, production: `v1.3.4`). Until installed, commands behave exactly
  as v1.3.3 (≤ one poll cycle) — nothing breaks, nothing gets faster either.
