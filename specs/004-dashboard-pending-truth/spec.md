# Feature Specification: Dashboard pending truth & old-app honesty

**Feature Branch**: `004-dashboard-pending-truth`

**Created**: 2026-09-15

**Status**: Implemented (worker-only, 2026-09-15 — no app release required)

**Input**: User re-test of v1.3.3 (2026-09-15): "now i can see the true unlock button and it work but it still had a little bug and need some refresh and repeat" + standing request: "if the mobile app don't need to update tell me every time".

D1 evidence from the user's test session (device 11, app reports `1.3.2-test`):

| time (UTC) | event | latency |
|---|---|---|
| 11:42:02 | lock created (tap 1) | — |
| 11:42:29 | delivered on poll | +27.8 s |
| 11:43:02 | acked + latched report lands | +60 s total |
| 11:43:07 | lock created **again** (tap 2 — user refreshed + repeated) | 65 s after tap 1 |
| 11:43:49 | full-unlock created (tap 1) | — |
| 11:43:53 | delivered | +4 s |
| 11:44:12 | acked; report = `latched:true` + 15-min grace | +23 s |
| 11:44:18 | full-unlock created **again** (tap 2) | 29 s after tap 1 |

Root causes verified in code + data:

1. **The phone was never updated to v1.3.3.** The v1.3.2 app reads `minutes` (default 15)
   from the unlock payload, so `{full:true}` becomes a 15-minute grace unlock and the
   latch stays. The dashboard's full-unlock pending confirmation requires `!rep.latched`,
   so the pending chip **never confirms** — the card freezes at "Unlocking… waiting for
   device" with all buttons disabled for the full 90 s TTL. The parent refreshes, and the
   refreshed page (pending state is browser-memory only) re-enables the buttons, so they
   tap again → duplicate commands. Exactly the "refresh and repeat" complaint.
2. **Pending state does not survive a refresh.** `pend` lives in tab memory; any reload
   wipes it even though the command is still in flight.
3. **After a reload, in-flight commands are invisible.** `/api/me` returns only a
   `pending_commands` count ("N cmds") — no type, no explanation.
4. **The dashboard knows `report.app_version` but never uses it.** A parent commanding
   an old app is never told that full unlock degrades — they find out when the phone
   re-locks in 15 minutes.

**Constitution articles touched**: V (bilingual truth — new badge strings EN+FA),
VIII (honest UI — the card shows server-side in-flight truth and the old-app
degradation instead of a frozen chip), XI (NEW — app-update disclosure: every change
states whether the phone app must be updated; this spec also surfaces it in-product).

**Scope**: dashboard + Worker only. **No Android app change** — v1.3.3 already
implements full unlock and the +4 s confirmation poll; it just needs to be installed.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A refresh mid-command no longer lies (Priority: P1)

A parent taps Lock (or Unlock); the card shows the pending chip; they hit F5 out of
habit (or the mobile browser reloads the tab). After the reload the card **still shows
the pending chip and keeps the buttons disabled** — the waiting state survives the
refresh in `sessionStorage` and expires honestly at its original 90 s TTL if the device
never confirms. The parent no longer re-taps "broken-looking" buttons, and duplicate
commands stop.

**Why this priority**: D1 shows the duplicate taps happened right after refreshes —
this is the mechanical half of "need some refresh and repeat".

**Independent Test**: Tap Lock, immediately F5. Within 1 s of the reload the card shows
"Locking… · waiting for device (up to ~1 min)" with disabled action buttons; when the
device confirms (or the TTL expires) the card settles to reported truth. No second tap
is ever needed.

**Acceptance Scenarios**:
- Pending state (type, timed flag, TTL deadline) round-trips through `sessionStorage`
  on reload; the TTL deadline is absolute so reloads never extend it.
- A reload while nothing is pending renders normally (no phantom chip).
- Private-browsing/storage-disabled environments degrade gracefully to today's
  behavior (try/catch around storage access).
- The post-command refresh burst now runs through 90 s (adds 75 s + 90 s ticks), so a
  slow/offline device's card settles via the burst instead of only the 10 s interval.

### User Story 2 - Old phone app tells the truth on the dashboard (Priority: P1)

The paired phone reports `app_version` `1.3.2-test`. Its card shows a persistent amber
**"old phone app — full unlock needs v1.3.3+"** badge (EN+FA), because apps before
v1.3.3 silently turn a full unlock into a 15-minute window. When the parent taps the
full unlock anyway, the pending chip **confirms as soon as the grace window lands**
(degraded confirmation) instead of freezing for 90 s; the card then honestly shows the
grace state. When the phone is updated to v1.3.3+, the badge disappears by itself —
the parent can see the update landed.

**Why this priority**: This is the semantic half of the complaint — the "true unlock"
the user saw was actually the timed one in disguise, and nothing on the panel said so.

**Independent Test**: With the phone on v1.3.2, the card shows the old-app badge; tap
Unlock, F5 mid-wait, and watch the chip clear when the 15-minute grace report lands
(~35–60 s). Install v1.3.3-test4, wait one poll (~30 s): the badge disappears and a
full unlock clears the latch for real.

**Acceptance Scenarios**:
- Badge renders for parseable versions < 1.3.3 (1.2, 1.3, 1.3.1, 1.3.2…), EN+FA,
  on every card whose report carries such a version; never for `sim`/unknown strings.
- Full-unlock pending on an old app confirms on `grace_until` (or a real unlatch),
  never freezes for the full TTL.
- The pre-existing confirm dialog text already discloses the old-app degradation; the
  badge makes it persistent, not just a one-time dialog.

### User Story 3 - Server-truth pending after a reload (Priority: P2)

Even with no local pending state (e.g. a brand-new tab opened seconds after someone
else sent a command), `/api/me` now reports **which command types are queued** for each
device, and the card renders "Locking… / Unlocking… · waiting for device" from that
server truth instead of the bare "N cmds" count. Config-only queues keep the count chip.

**Why this priority**: closes the last gap where an in-flight command was invisible —
makes the panel honest from any context, not just the tab that sent the command.

**Independent Test**: Open a second tab, tap Lock in it, look at the first tab: within
10 s it shows the Locking chip from server data. Queue only a settings change: the chip
stays "N cmds".

**Acceptance Scenarios**:
- `/api/me` devices gain `pending_types` (`{lock: n, unlock: n, config: n}`);
  `pending_commands` (total) unchanged for compatibility.
- Chip precedence: local pend > type-aware server chip > count chip > nothing.
- Old cached dashboards (pre-dg-v4) keep working: `pending_types` is additive.

### User Story 4 - "Does my phone need an update?" is now project law (Priority: P1)

The owner asked to be told, every time anything ships, whether the mobile app must be
updated. This is codified as constitution **Article XI (App-Update Disclosure)**: every
spec, release note, and delivery summary must state the app-update requirement
explicitly (and which build: production vs QA). This spec itself follows it: **the
dashboard/Worker fixes ship live instantly; the phone app does NOT need a new APK for
spec-004 — but the still-pending v1.3.3 install (from spec-003) IS required for the
full unlock to truly clear the latch.**

**Acceptance Scenarios**:
- Constitution carries Art. XI; README release-notes template gains an "App update
  needed?" line; future release notes fill it in.

## Requirements Analysis

- **FR-001 (P1)**: Pending command state persists across manual refresh via
  `sessionStorage` (absolute TTL, graceful degradation).
- **FR-002 (P1)**: Cards for devices reporting app versions < 1.3.3 (parseable) show a
  persistent old-app warning badge, EN+FA.
- **FR-003 (P1)**: Full-unlock pending confirms on grace-window landing when the target
  app is old (degraded confirmation) — no 90 s freeze.
- **FR-004 (P2)**: `/api/me` exposes per-device queued command types; the card renders
  a type-aware waiting chip from server truth when local pend is absent.
- **FR-005 (P2)**: Post-command refresh burst extends to 75 s and 90 s.
- **FR-006 (P2)**: Service-worker cache bumped `dg-v3` → `dg-v4` so every client picks
  up the new panel exactly once.
- **FR-007 (P1)**: Constitution Art. XI added; README documents the disclosure rule.

## Failure Modes

- sessionStorage unavailable → wrapped in try/catch; behavior = v1.3.3 dashboard.
- Corrupted sessionStorage payload → JSON.parse guarded; resets to empty pend.
- Old dashboard (cached) + new Worker → `pending_types` ignored, count chip as before.
- New dashboard + old Worker (rollback) → `pending_types` undefined → falls back to
  count chip; `appOld` still works (report-driven).
- Offline device → pend expires at its absolute 90 s deadline; card returns to
  reported truth (Art. VIII — never stuck optimistic).
