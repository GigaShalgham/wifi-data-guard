# Feature Specification: Safe Unpair — no more locked-orphan trap

**Feature Branch**: `002-safe-unpair`

**Created**: 2026-09-15

**Status**: Implemented (v1.3.2, 2026-09-15)

**Input**: User bug report (2026-09-15, live device + D1 evidence): "In panel, when I click on the lock button it locked but it never shows me the unlock button and I fucked up." Investigation: the parent tapped **Unpair device** on a LOCKED device (audit #76, 07:56:58 UTC) — the device card vanished from the dashboard, the phone stayed locked (fail-closed, by design), and there was no remote path back and no on-phone explanation. Real incident, real recovery cost (parent had to use the PIN escape hatch blind).

**Constitution articles touched**: II (fail-closed — must NOT auto-unlock on revoke), V (bilingual truth), VIII (honest UI — the phone must stop claiming "Locked by parent" after the parent link is gone), I (cloud source recovered into the repo as part of this spec — specs are the source of truth and the worker source was lost in a sandbox reset).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Parent is warned before creating a locked orphan (Priority: P1)

A parent viewing a LOCKED device's card wants to unpair it. Before the unpair
happens, the dashboard must tell them plainly what they are about to do: the
device is currently locked, unpairing removes every remote control, and the
phone can only be unlocked on the device itself with the parent PIN. The
confirmation must be noticeably harder to click through than a routine
"OK" dialog, and the destructive action must be moved away from the
frequently-used Unlock button so a mis-tap cannot trigger it.

**Why this priority**: This is the exact trap that occurred. It is the difference between a 5-second informed decision and a locked phone with no remote recovery.

**Independent Test**: Lock a paired device from the dashboard, then attempt to unpair it. The warning appears before anything is destroyed; the Unpair control is not adjacent to the Unlock control.

**Acceptance Scenarios**:

1. **Given** a paired device whose last report says `latched=true`, **When** the parent clicks Unpair, **Then** a confirmation names the lock state and the PIN-only recovery path, in both dashboard languages, and nothing is revoked if the parent cancels.
2. **Given** a paired device that is NOT locked, **When** the parent clicks Unpair, **Then** the confirmation is the ordinary one (no lock warning).
3. **Given** any device card, **When** rendered, **Then** the Unpair control is visually separated from the Lock/Unlock controls (not in the same button row), so accidental taps on Unlock can never hit Unpair.

---

### User Story 2 - Phone tells the truth after being unpaired while locked (Priority: P1)

The child's phone that was locked when the parent revoked it must stop
displaying "Locked by parent" — the parent can no longer unlock it. The phone
must relabel the lock (e.g. "Locked — device unpaired"), must surface a clear
one-time bilingual notice that explains: the cloud link was removed while the
device was locked, remote unlock is no longer possible, and the way out is the
parent PIN on this device (Unlock button for a grace window, or turning
monitoring off / resetting the counter). The lock itself must stay ON
(fail-closed) — revocation is not an unlock.

**Why this priority**: Without this, the user on the phone has no idea what happened or how to recover — which is precisely what made the incident feel like being "fucked up".

**Independent Test**: Pair a test device, lock it remotely, revoke from the dashboard, force a poll. The status line changes from the parent-locked wording to the unpaired wording, a notice appears once, and the local PIN unlock still works.

**Acceptance Scenarios**:

1. **Given** a device latched with reason "cloud", **When** its next poll receives "revoked", **Then** the device stays latched (no auto-unlock), the status line changes to an unpaired-aware wording (EN + FA), and a high-visibility notice explains the PIN escape hatch.
2. **Given** a device that is NOT latched, **When** revoked, **Then** it returns to local-only mode silently (no scary notice needed — nothing is trapped).
3. **Given** a device unpaired-while-locked, **When** the user unlocks with the parent PIN, **Then** enforcement resumes normally afterwards (grace expiry re-arms, local limit still applies).

---

### User Story 3 - Re-pairing after an accidental unpair is discoverable (Priority: P2)

After this incident the parent's dashboard shows no device (it was revoked).
The dashboard already supports generating a new pairing code; this story makes
the recovery path explicit: when a parent has zero devices, the empty state
tells them pairing brings a device back — including after an accidental
unpair — so recovery does not depend on tribal knowledge.

**Why this priority**: Recovery is possible today but undocumented on-screen; this closes the loop after the warning failed.

**Independent Test**: Revoke the only device; the empty-state card explains that generating a code and entering it in the child app restores control.

**Acceptance Scenarios**:

1. **Given** a parent account with zero active devices, **When** the dashboard renders, **Then** the empty state mentions that a previously unpaired device can be re-paired by generating a new code (EN + FA).

---

### User Story 4 - Cloud source lives in the repository (Priority: P2)

The dashboard fix requires editing the cloud Worker. The Worker source was
lost in a sandbox reset (only the deployed bundle survived). As part of this
spec, the recovered source must be checked into the app repository under
`cloud/` with deploy instructions, so future changes (like this one) start
from versioned source — not from a downloaded bundle. The deployed Worker
must keep all existing behavior except the changes this spec requires.

**Why this priority**: Prerequisite for the dashboard side of the fix, and permanent insurance against the next reset (Constitution I: repo is the source of truth).

**Independent Test**: `cloud/` exists in the repo, documents the deploy command and required bindings (D1, KV, vars), and the next deploy from it serves a working dashboard with the new warning.

**Acceptance Scenarios**:

1. **Given** the repo, **When** a developer follows `cloud/README` deploy steps, **Then** the live Worker serves the updated dashboard and all existing endpoints behave as before (pair, poll, commands, revoke, audit).
2. **Given** the recovered source, **When** reviewed, **Then** it contains no secrets (tokens are provided at deploy time, never stored in the repo).

---

### Edge Cases

- **Revoke races an in-flight lock command**: device revoked while a `lock` command is still pending — the command may still be delivered by a poll that started before revocation; the device applies it and stays latched; acceptable (fail-closed direction), and the PIN path still works.
- **Revoked while in grace (unlock window)**: device is temporarily unlocked; revocation keeps current state (grace continues, re-arm at expiry) — no trap, since the device is usable and local rules resume.
- **Device never polls again after revocation** (powered off / app killed): status line fix cannot appear because the phone never learns it was revoked. Mitigation: story 1 prevents the trap at the source; the local PIN escape hatch exists regardless of cloud state.
- **Offline-tolerance latch vs revoke**: if the device is latched because it was offline too long (not a parent lock), the unpair-while-locked warning still applies — the phone is locked either way.
- **Multiple devices**: warning applies per-device; other cards unaffected.
- **Dashboard language switching**: warning texts must exist in both languages before deploy.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The dashboard MUST require explicit confirmation before unpairing any device, and that confirmation MUST state — when the device's last report shows it locked — that the device will remain locked, that remote unlock will be impossible after unpairing, and that only the parent PIN on the device can unlock it.
- **FR-002**: The dashboard MUST place the Unpair control outside the row containing Lock and Unlock controls, separated visually or structurally, so a single mis-tap cannot trigger unpair.
- **FR-003**: On receiving revocation while latched with a cloud-origin reason, the device MUST keep the latch (no auto-unlock), MUST change its status wording to reflect that the parent link is gone, and MUST show a one-time bilingual notice describing the PIN-based recovery path.
- **FR-004**: On receiving revocation while NOT latched, the device MUST return to local-only mode exactly as it does today (no new notices).
- **FR-005**: All new user-facing strings MUST ship in English and Persian with equivalent meaning (dashboard and app).
- **FR-006**: The empty-devices dashboard state MUST explain that a device can be re-paired by generating a new pairing code and entering it in the child app.
- **FR-007**: The recovered cloud Worker source MUST be committed under `cloud/` in the app repository, with deploy instructions and required bindings documented, and MUST NOT contain credentials.
- **FR-008**: The deployed Worker MUST preserve all existing endpoint behavior (pair, poll, commands, revoke, reports, audit, demo) except for the changes required by FR-001/FR-002/FR-006.

### Key Entities *(include if feature involves data)*

- **Device report** (existing): periodic `latched` / `grace_until` state sent by the app; the dashboard uses its latest value to decide whether the unpair warning is needed.
- **Revocation** (existing event, semantics unchanged): severs the parent-device link; device token becomes invalid; device reverts to local-only mode.
- **Latch reason** (existing attribute, extended): distinguishes why enforcement is active (limit, cloud lock, offline tolerance, clock tamper, and now "unpaired while locked") so status wording can be honest.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Revoking a locked device without reading the warning is impossible — every revoke of a locked device passes through a lock-specific confirmation (100% of attempts).
- **SC-002**: After revocation-while-locked, the phone shows the unpaired-aware status wording within one poll cycle (~30 s) and shows the recovery notice exactly once.
- **SC-003**: A parent who cancels the warning keeps full remote control (device card, lock/unlock, settings all unchanged).
- **SC-004**: The complete recovery flow (re-pair a revoked device: generate code → enter in app → card returns with commands) works in under 2 minutes.
- **SC-005**: Deploying from the recovered `cloud/` source reproduces the live service with the new dashboard behavior and no endpoint regressions (spot-check all existing endpoints).

## Assumptions

- The parent PIN escape hatch on the device (Unlock button, monitoring off, counter reset) is trusted and stays as-is; this spec only makes it discoverable, not weaker.
- Fail-closed is non-negotiable: revocation must never silently unlock a device (Constitution II). The fix is warning + honesty + discoverability, not auto-unlock.
- The live Worker is the only deployed instance; no versioning/migration of D1 schema is needed for this change (no schema changes).
- The demo simulator (`/demo`) stays live for now; its removal is a separate roadmap spec (spec-001 item A).
- Dashboard continues to support EN/FA only; other languages are out of scope.
- The sandbox has (or can reinstall) the deploy tooling; the Cloudflare token on file permits Worker script updates.
