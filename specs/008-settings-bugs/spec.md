# Feature Specification: Settings form stops reverting + unlock window becomes real

**Feature Branch**: `008-settings-bugs`

**Created**: 2026-09-15

**Status**: Implemented (worker-only, 2026-09-15 — no app release required; deployed + live-browser-verified)

**Input**: Owner bug report (2026-09-15), two defects in the dashboard **Settings** section:

1. *"When I change a setting such as Limit from 500 to 200, the value initially changes
   to 200, but after a few moments it automatically reverts back to 500. The revert
   happens very quickly, so if I don't act fast enough, my change is lost."*
2. *"The Unlock Window option in the Settings section appears to be practically useless.
   Changing/configuring this setting does not produce the expected effect."*

## Evidence (live D1, device 14 = SM-A546E, app reports `1.3.3`)

The command log for the user's test session shows the signature of a parent fighting a
reverting form — 10 `config` commands in ~3.5 minutes, values flip-flopping, saves
double-clicked seconds apart:

| id | payload (limit / hard) | created (UTC) | gap | delivered / acked |
|---|---|---|---|---|
| 41 | 200 / off | 13:28:18 | — | +0.6 s / +1.2 s |
| 42 | 200 / off | 13:28:54 | +35 s | pair 42+43: +0.4 s / +2.0 s |
| 43 | 200 / **on** | 13:28:56 | **+2.1 s** | (same delivery) |
| 44 | 200 / off | 13:29:17 | +21 s | pair 44+45: +2.5 s / +7.7 s |
| 45 | **500** / off | 13:29:21 | **+3.5 s** | (same delivery) |
| 46 | **500** / on | 13:30:02 | +41 s | pair 46+47: +1.6 s / +5.1 s |
| 47 | **500** / on | 13:30:14 | +11 s | (same delivery) |
| 48 | **500** / off | 13:31:18 | +64 s | pair 48+49: +1.3 s / +5.0 s |
| 49 | **200** / off | 13:31:27 | +8.6 s | (same delivery) |
| 50 | **200** / on | 13:31:37 | +10 s | +0.3 s / +3.3 s |

Key observations:

- cmd 44 saves limit **200**, then cmd 45 saves **500** 3.5 s later — the user re-saved
  what the reverted form showed them, locking the OLD value back in. This is exactly
  "my change is lost".
- Server-side persistence is **correct**: `devices.settings_json` always equals the last
  saved payload, and every config command is delivered+acked within 0.3–7.7 s. The bug
  is purely client-side rendering.

- D1 audit after implementation: **zero commands to the real device** during the entire verification session; all test traffic went to a paired `/demo` simulator (device 15, revoked after testing).

## Root causes verified in code

**Bug 1 — settings values revert.** `updateDynamic()` rebuilds the entire device list
(`devlist.innerHTML = deviceCard(d) …`) on EVERY refresh — the 10 s heartbeat
(spec-005), the post-command `trackRefresh()` burst (1 s, 5 s, 15 s … 90 s), and every
tab focus. `deviceCard()` re-renders the open Settings panel from server `me` data, so:

1. Any in-progress edit is wiped ≤10 s after typing (before Save is clicked).
2. A heartbeat `/api/me` response that was READ by the worker *before* the save POST
   landed can ARRIVE in the browser *after* it — the just-saved 200 flips back to 500
   until the next refresh; the parent re-saves the stale value they see (cmd 44→45).
3. The save handler shows "Saved" even when the POST fails — `api()` only throws on
   401; any 4xx/5xx body still reaches the `.then` (dishonest success, Art. VIII).

**Bug 2 — "Unlock window (min)" is dead on the dashboard.** The timed-unlock button
hardcodes `payload: { minutes: 15 }` in `wireDevice()`, the button label hardcodes
"Unlock 15m" (`unlock15`), and the confirm dialog hardcodes "Grant a 15-minute unlock
window?" (`confirmUnlock`). The `unlock_minutes` setting currently affects only the
phone-side PIN unlock (via config propagation to `Prefs.unlockMinutes`, used by the
app's own `unlockFlow`) — never the dashboard's own unlock command, which is the
parent's primary flow. The server already validates `minutes` 1..480 (default 15), and
every app since v1.3 applies any value in that range (`WatchdogService`: grace =
`now + minutes*60_000`), so **the fix is Worker-only**.

**Constitution articles touched**: V (bilingual truth — new dynamic strings EN+FA),
VIII (honest UI — edits stop being silently destroyed; save feedback stops lying;
device-confirmed settings sync feedback), XI (disclosure: worker-only change, no APK).

**Scope**: dashboard JS + Worker only. **No Android app change.**

## Clarify (self-answered from evidence — owner asked to proceed without questions)

- *Which "Settings" section?* The dashboard's per-device Settings panel — the report's
  "Unlock Window" label matches `t("unlockMin")` verbatim, and the D1 command trail is
  dashboard-generated `config` commands.
- *Semantics of `unlock_minutes = 0`?* Kept as-is for the phone (0 = until period end,
  the app spinner's last option). The dashboard's timed-unlock button treats 0/invalid
  as 15 (server's own default), so the button always grants 1..480 minutes.
- *Should the save wait for device confirmation?* No behavior change to enforcement —
  but the UI now *says* the truth: "Saved — syncing to device…" until the device's
  poll picks the config up, then a device-confirmed toast (same Art. VIII pattern as
  spec-006 lock/unlock toasts).
- *Phone spinner shows a preset label for non-preset cloud values (e.g. 25)?* Known
  cosmetic limitation (display-only; the actual grace uses the real value) — deferred
  to the next app-side spec, documented below.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Edits survive open-tab refreshes (Priority: P1)

A parent opens Settings on a device card, changes Limit from 500 to 200, and takes
their time (or gets distracted). The value they typed **stays in the form** — the 10 s
heartbeat, post-command refresh bursts, and tab refocuses re-render the card around
the open panel but never overwrite the draft. When they click Save, the value that is
actually sent is the one they typed and can see.

**Why this priority**: this is the reported data-loss bug (cmd 44→45 in the evidence —
the user re-saved the reverted 500). Every other improvement is secondary to "the form
stops eating my edits".

**Independent Test**: open the live dashboard, open Settings, type a new limit, wait
30+ s with the tab visible (≥3 heartbeats), verify the input still shows the typed
value, then Save and verify `settings_json` in D1 matches.

**Acceptance Scenarios**:

1. **Given** the Settings panel is open with unsaved edits, **When** the 10 s heartbeat
   fires, **Then** the input keeps the user's value and focus is not stolen.
2. **Given** a Save just landed (server has 200), **When** a stale in-flight `/api/me`
   response (read pre-save) arrives, **Then** the form still shows the draft (200), not
   the stale server value (500).
3. **Given** the panel is closed and re-opened, **When** it renders, **Then** it starts
   fresh from current server settings (no stale draft leaks across open/close).

### User Story 2 - Save feedback is honest (Priority: P2)

Clicking Save shows "Saved — syncing to device…" while the config command is in
flight, a small "Settings → device…" chip on the card while it is undelivered, and a
device-confirmed toast (+ soft chime) when the device's poll picks it up. A failed
POST shows an error, never "Saved".

**Why this priority**: closes the Art. VIII gap (lying "Saved") that made the revert
indistinguishable from success during the incident.

**Independent Test**: save settings with the network tab open; kill connectivity
mid-save and verify the error message appears instead of "Saved".

**Acceptance Scenarios**:

1. **Given** a successful Save, **When** the device has not yet polled, **Then** the
   card shows the syncing chip and the panel shows "syncing to device…".
2. **Given** the device polls and receives the config, **When** the next `/api/me`
   lands, **Then** the chip clears and a toast confirms (EN+FA).
3. **Given** the POST returns 4xx/5xx, **When** the response arrives, **Then** the
   panel shows a red error and never claims success.

### User Story 3 - Unlock Window actually sets the window (Priority: P1)

The parent sets "Unlock window (min)" to 25 and saves. The timed-unlock button now
reads "Unlock 25m", the confirm dialog asks for a 25-minute window, the command sends
`minutes: 25`, and the phone grants exactly 25 minutes (verified by the grace countdown
on the device / `grace_until` in the next report). The toast also says 25 minutes.

**Why this priority**: the second reported bug — the setting is currently wired to
nothing on the dashboard.

**Independent Test**: set unlock window to 5, save, use the timed-unlock button on the
(latched) test device, and verify the phone's grace countdown shows ~5 minutes.

**Acceptance Scenarios**:

1. **Given** `unlock_minutes = 25` (saved), **When** the card renders, **Then** the
   button label and confirm dialog both say 25 (EN+FA).
2. **Given** the button is clicked, **When** the command is created, **Then** the D1
   payload contains `"minutes":25` and the device's `grace_until` ≈ now + 25 min.
3. **Given** `unlock_minutes` is 0 or > 480, **When** the button renders/clicks,
   **Then** it falls back to 15 minutes (server clamp semantics, label matches).

## Requirements

**Functional**

- **FR-001**: While a device's Settings panel is open, its inputs render from a
  per-device draft (`cfgDraft[id]`) initialized from server settings at open time;
  `input` events update the draft; refreshes never overwrite the draft.
- **FR-002**: Closing the panel (or the device disappearing from `/api/me`) drops the
  draft; re-opening re-initializes from current server settings.
- **FR-003**: The Save handler checks the parsed response body's `ok`; on failure it
  renders a red error message and does not run the success path.
- **FR-004**: On success the panel shows "Saved — syncing to device…"; a
  `pending_types.config > 0` chip ("Settings → device…") shows on the card while the
  config command is undelivered; when it clears, a device-confirmed toast + chime
  fires (spec-006 infra, EN+FA).
- **FR-005**: The timed-unlock button sends
  `minutes = clamp(unlock_minutes, 1, 480) || 15` from the device's saved settings.
- **FR-006**: Button label ("Unlock %dm" / FA equivalent with Persian digits), confirm
  dialog, and timed-unlock toast all reflect the actual minutes granted.
- **FR-007**: All new/changed strings ship EN + FA with identical meaning (Art. V).
- **FR-008**: SW cache bumped `dg-v5 → dg-v6` so every open dashboard updates its
  cached `app.js`.

**Non-functional**

- No new endpoints; no schema changes; no app changes. Worker-only deploy.
- Config-command semantics unchanged (server remains the authority; app versions ≥1.3
  already apply `minutes` 1..480 — Art. XI: no app update required).

## Failure Modes

- Draft leak after revoke → drafts for device ids absent from `/api/me` are pruned on
  every `updateDynamic()`.
- Two tabs editing the same device → last save wins (server merge is per-field);
  drafts are per-tab, so no cross-tab clobbering of inputs (each tab sees its own
  draft; server truth lands on next open).
- Stale `/api/me` response racing a save → irrelevant to the open panel (draft wins);
  card usage/badges may lag one cycle as today.
- User saves while offline → honest error (FR-003), draft intact, retry works.

## Known limitation (deferred)

The phone's unlock-duration spinner offers presets {5, 15, 30, 60, until-period}; a
cloud-set `unlock_minutes` outside that set (e.g. 25) is **applied** correctly (PIN
unlock uses the real value) but the spinner label falls back to the first preset.
Display-only; fix deferred to the next app-side spec (needs an APK).

## Constitution articles touched

V (bilingual strings), VIII (honest UI: no silent edit destruction, no lying "Saved",
device-confirmed sync feedback), XI (disclosure: worker-only, no APK).
