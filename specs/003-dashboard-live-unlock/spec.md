# Feature Specification: Dashboard live truth & real unlock

**Feature Branch**: `003-dashboard-live-unlock`

**Created**: 2026-09-15

**Status**: Implemented (v1.3.3, 2026-09-15)

**Input**: User bug report (2026-09-15, after v1.3.2): "we still can lock from parent panel and any unlock button isn't there after lock, and we should refresh the panel every time, and the only unlock button is not that unlock we want and it's just a rest timed button." Verified against live code — all three complaints are real:

1. **No full unlock exists.** The dashboard's only unlock sends `{type:"unlock", payload:{minutes:15}}`; the Worker clamps minutes to 1–480 (default 15); the app sets a grace window and **keeps the latch** — the phone re-locks itself when the window expires. Every "unlock" in the product is a timed pause.
2. **No feedback after locking.** After tapping Lock, nothing on the card changes: the device reports `latched` only on its next poll (~30 s ± jitter), the *following* report carries the new state (~30 s more), and the dashboard's 30 s auto-refresh may not fire while a mobile tab is backgrounded. Worst case the parent waits ~90 s seeing "ok" — so they conclude the button did nothing.
3. **State-blind buttons.** Lock and Unlock render unconditionally for every device regardless of reported state; there is no pending indication, so the parent cannot tell command sent vs. command delivered vs. command applied.

**Constitution articles touched**: II (fail-closed — full unlock must still never bypass the data limit or clock-tamper defense), V (bilingual truth — all new dashboard strings and notifications EN+FA), VII (control-channel survives locks — fast confirmation poll rides the self-exempted channel), VIII (honest UI — pending states tell the truth instead of a frozen "ok" badge).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Parent can fully unlock a locked device (Priority: P1)

A parent has locked their child's device from the dashboard and wants the internet
back on — not for 15 minutes, but until they decide to lock it again. The locked
device's card offers a **full unlock** next to the existing timed one, with a
confirmation that says exactly what will happen: the internet stays open until the
next lock (or until the device's own data limit is reached). After confirming, the
card immediately shows an "Unlocking…" state and settles to the unlocked badge once
the device confirms. On the phone, the lock clears for real — no auto re-arm — and
a bilingual notification says the parent unlocked it.

**Why this priority**: This is the core of the user's complaint — "the only unlock button is not that unlock we want". The product currently has no answer to "give me my kid's internet back".

**Independent Test**: Lock a paired device (app v1.3.3+), tap the full unlock, confirm. The card shows "Unlocking…" within 1 s and the "ok" badge within ~40 s without any manual refresh; the phone's notification reports the parent unlock; 20 minutes later the device is still unlocked.

**Acceptance Scenarios**:
- Locked device card shows both "Unlock" (full) and "Unlock 15m" (timed); unlocked card shows "Lock now" instead.
- Full-unlock confirmation (EN+FA) states the unlock persists until the next lock or the data limit, and notes that device apps older than v1.3.3 treat it as a 15-minute window.
- On a cloud-locked device, full unlock clears the latch completely (badge "ok", no re-arm).
- On a device locked by its data limit (or clock-tamper), a full unlock request degrades honestly to the timed grace window and the phone says so — the limit itself is never bypassed (constitution Art. II).

### User Story 2 - Parent sees every command land, without manual refresh (Priority: P1)

A parent taps Lock (or Unlock) and the card reacts **immediately**: the action
buttons switch to a pending state ("Locking… / Unlocking… — waiting for device"),
the status badge flips as soon as the device confirms, and no browser refresh is
ever needed. The dashboard refreshes itself every 10 s, refreshes the moment the
tab becomes visible or focused again (mobile browsers pause timers in background
tabs — this is why the user "had to refresh every time"), and runs a staggered
refresh burst (1 s → 60 s) after each command until it is confirmed.

**Why this priority**: "we should refresh the panel every time" — the panel currently lies by omission, showing stale state as if it were current.

**Independent Test**: Open the dashboard on a phone, lock the device, immediately background the tab for a minute, come back: the card shows LOCKED without pulling-to-refresh. Tap Unlock 15m: the "Unlocking…" chip appears instantly.

**Acceptance Scenarios**:
- Within 1 s of tapping Lock/Unlock the card shows the pending chip and disables the action buttons for that device.
- Tab visibility or focus triggers a refresh; auto-refresh runs every 10 s while visible.
- When the device's report confirms the command, the pending chip disappears and the badge reflects the new state; if the device never confirms within 90 s, the pending state expires and the card returns to reported truth (honest, not stuck).
- Multiple devices: pending state is per-device; commanding one does not freeze the others.

### User Story 3 - Buttons reflect the device's actual state (Priority: P2)

A parent glancing at the dashboard sees controls that match reality: a locked
device offers unlock actions; an unlocked device offers Lock. The pending state is
treated as its intent (a pending lock shows unlock buttons, and vice versa) so the
parent can immediately send a corrective command if they change their mind —
except while a command is already in flight, when the buttons are disabled to
prevent piling up contradictory commands.

**Independent Test**: With the device locked, only unlock actions + Settings show; after a full unlock confirms, "Lock now" returns.

**Acceptance Scenarios**:
- Locked (or pending-lock) card: [Unlock] [Unlock 15m] [Settings].
- Unlocked (or pending-unlock) card: [Lock now] [Settings].
- While a command is pending, all action buttons for that device are disabled until confirmation or timeout.

### User Story 4 - Device confirms in seconds, not a minute (Priority: P2)

When the app applies any cloud command, it schedules a confirmation poll ~4 s
later on the self-exempted control channel, so the acknowledgment and a fresh
report (with the new latched state) reach the dashboard within seconds of the
command being applied — cutting the previous confirm-and-report cycle roughly in
half and making the dashboard's pending chip resolve visibly faster.

**Independent Test**: Send a lock command; the D1 `commands` row for it is `delivered_at`-stamped at the first poll and `acked_at`-stamped ~4 s later (audit trail), and the dashboard badge flips in the same window.

**Acceptance Scenarios**:
- After applying a lock/unlock/config command, the app polls again within ~5 s (not waiting for the full 30 s interval).
- Works during a hard-mode VPN lock (Art. VII self-exemption).
- Release builds (v1.3.2 and older) that receive `{full:true}` ignore the unknown field and apply the 15-minute default — no crash, no wrong promise on the phone; the dashboard's own badge shows the grace truth after the report arrives.

## Edge Cases

- **Device offline when commanded**: pending chip shows until the 90 s timeout, then the card falls back to reported truth ("last seen …"). The command itself waits in the queue and will still be delivered on the next successful poll.
- **Full unlock on a limit/clock-latched device**: latch is NOT cleared (the limit and tamper defense survive — Art. II); the timed window is granted instead and the phone notification explains why.
- **Rapid double-tap / contradictory commands**: buttons disabled while pending prevents duplicates; if a second command still lands (e.g. from another browser tab), the queue is ordered by id and each is applied and acked in turn.
- **Race: report arrives while pending**: the confirmation check runs on every refresh; a matching report clears the pending state even if it arrives early.
- **Old app (≤ v1.3.2) + full unlock**: device applies 15-minute grace; dashboard pending times out after 90 s and the badge honestly shows "grace".
- **Language switch mid-pending**: re-render uses the new language for pending strings too (all strings are i18n keys, no cached markup).
- **Service-worker rollout**: cache version bump force-activates the new dashboard on the next load of any open tab (`skipWaiting` + `clients.claim` already in place).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001** Unlock command payload: the Worker accepts `payload.full === true` for type `unlock`, stores `{full:true}` verbatim, and keeps the existing `minutes` path (clamp 1–480, default 15) for payloads without `full`.
- **FR-002** Dashboard action buttons are state-aware: locked or pending-lock devices show full Unlock + timed Unlock; unlocked or pending-unlock devices show Lock now; Settings always available; action buttons disabled while that device has a pending command.
- **FR-003** Full unlock has its own bilingual confirmation stating: stays open until the next lock or the data limit; device apps older than v1.3.3 treat it as a 15-minute window.
- **FR-004** Optimistic pending state: on command send the card immediately shows a bilingual "Locking…/Unlocking… — waiting for device" chip; the state clears on report confirmation (latched matches intent, or grace active for timed unlock) or after 90 s.
- **FR-005** Dashboard liveness: auto-refresh every 10 s while visible; refresh on `visibilitychange`/`focus`; staggered refresh burst (1, 5, 15, 30, 45, 60 s) after any command.
- **FR-006** Android full-unlock semantics: clears cloud-origin latches (`cloud`, `offline`) completely — latch released, grace zeroed, VPN released, Wi-Fi restored, bilingual notification "unlocked by parent — until the next lock"; for any other latch reason it degrades to the timed grace window with a bilingual notification explaining the fallback. The data limit and clock-tamper defenses are never bypassed.
- **FR-007** Android confirmation poll: after applying any command batch, the app schedules a poll ~4 s later (control channel self-exempted, Art. VII) so ack + fresh state land quickly.
- **FR-008** Rollout hygiene: service-worker cache bumped to `dg-v3`; app version 1.3.3 (versionCode 7); CloudLink user-agent updated to match.
- **FR-009** Backward compatibility: no API shape changes beyond the additive `full` flag; v1.3.2-and-older apps, and the deployed dashboard until redeployed, keep functioning exactly as before.

### Key Entities

- **Command** (existing `commands` table): type `unlock` gains optional `payload.full:boolean`. No schema change.
- **Pending action** (dashboard client state, per device): `{type: lock|unlock, timed: boolean, until: timestamp}` — ephemeral, never persisted server-side.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After tapping Lock on a v1.3.3 device, the pending chip appears < 1 s and the LOCKED badge appears within ~45 s, with zero manual refreshes.
- **SC-002**: A full unlock on a cloud-locked v1.3.3 device leaves it unlocked 20 minutes later (no re-arm), badge "ok", phone notification delivered.
- **SC-003**: Button sets match reported state on every card render (spot-check locked, unlocked, pending).
- **SC-004**: In a supervised 10-minute dashboard session on mobile (including one background/foreground cycle), no manual refresh is needed to see every state change.
- **SC-005**: Post-deploy live probes: `/app.js` serves the new bundle (contains `full` command + pending strings + `dg-v3`), `/api/child/poll` responds 401 unauthenticated, dashboard loads and renders.

## Assumptions

- The Worker and dashboard deploy from `cloud/worker.js` via `cloud/deploy.py` (established in spec-002) — no infrastructure changes.
- Device poll interval stays ~30 s; liveness is improved by dashboard-side refresh cadence and the app-side confirmation poll, not by shortening the interval (battery).
- The parent's browser supports `visibilitychange` and `focus` (all evergreen browsers do).
- The user will re-test on the v1.3.3-test4 QA build before the E2E verification spec records results.
- The security hardening items (bootstrap-email fallback, `/demo` removal) remain a separate spec (004) pending the user's decision on the sign-in email path; this spec does not touch authentication.
