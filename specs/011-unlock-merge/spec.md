# Feature Specification: Merge Unlock Buttons — One Button, Ordered Choices (spec-011)

**Feature Branch**: `011-unlock-merge`

**Created**: 2026-09-16

**Status**: Implemented

**Input**: Owner report: "A bug detected ⚠️ the timed unlock and the main unlock
button can be merged with a special order, fix that."

## Problem Statement

After spec-010, a **locked** device card on the dashboard shows TWO side-by-side
buttons that both lead to an unlock:

1. **"Unlock"** (primary, `data-unlock-full`) — full unlock: internet stays open
   until the parent locks it again (or the data limit is reached). Guarded by a
   `confirm()` with a long warning.
2. **"⏱ Timed unlock"** (ghost, `data-unlock`) — opens the spec-010 duration
   picker (chips 15/30/60/120 + custom 1–480), then sends
   `{ type: "unlock", payload: { minutes } }`.

Two buttons for one intent is redundant UI: the parent must decide *which button*
before deciding *what they actually want* (how long). The owner's directive is to
**merge them into a single button with a special order** — one entry point, and an
ordered flow of choices inside it.

Verified live before this spec: dg-v7 serves both buttons on locked cards
(`data-unlock-full` + `data-unlock`); the app (v1.4.0) Parent tab already uses a
single Unlock button + duration chips — the redundancy is **dashboard-only**.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - One button, ordered choices (Priority: P1)

**As a** parent using the dashboard,
**when** my kid's device shows LOCKED,
**I see** exactly ONE primary "Unlock" button (no second "⏱ Timed unlock" button),
**and when** I tap it, a picker opens with the choices in a deliberate order:
quick durations (15/30/60/120, my remembered choice highlighted) → custom minutes
(1–480) → a visually distinct "∞ Full unlock — until you lock it again" row at the
bottom. Choosing a duration or the full row executes exactly the same commands as
the two old buttons did.

- Tap chip "30" → `unlock {minutes:30}`, pend chip + drain toast as today.
- Tap "∞ Full unlock" → the old full-unlock `confirm()` warning → `unlock {full:true}`.
- Cancel/backdrop → nothing sent.

### User Story 2 - Long-press repeats the last duration (Priority: P2)

**As a** parent who always grants the same amount,
**when** I **press and hold** the card's Unlock button (~½ second),
**then** a timed unlock with my remembered minutes fires instantly (no picker), a
"Quick unlock: N min" toast appears, and the following click does NOT also open
the picker (no double action). A hint inside the picker teaches the gesture.

### User Story 3 - Nothing else regresses (Priority: P1)

**As the** owner,
**every** existing behavior is unchanged: lock button, settings panel (spec-008
drafts), pend chips (spec-004), hot polling (spec-005), picker seeds/memory
(spec-010), battery glyph, i18n parity, audit trail. App versions ≥ v1.3.3 need
no update (payloads identical).

## Requirements

### Functional — dashboard (worker-only)

- **FR-001**: A locked card renders exactly ONE unlock entry: the primary
  `data-unlock` button labeled "Unlock" / "باز کردن قفل" (i18n key renamed
  `unlockFull` → `unlockNow`; value unchanged). The `data-unlock-full` button and
  the `timedUnlock` i18n key are deleted.
- **FR-002**: The picker (spec-010 `openPicker`) gains an ordered ladder:
  chips → custom input → divider → **full-unlock row** ("∞ Full unlock — until
  you lock it again", distinct amber styling, keyboard-operable via Enter/Space)
  → footer hint line. The full row reuses the existing `confirmUnlockFull`
  warning and sends the same `{full:true}` command with the same pend semantics
  (incl. `oldApp` flag) as the deleted button.
- **FR-003**: Long-press (550 ms) on the card's Unlock button fires an instant
  timed unlock with `rememberedMins(d)`; the synthetic click that follows a fired
  long-press is swallowed; `contextmenu` and text-selection are suppressed on the
  button; while a command is pending (button disabled) the gesture is inert.
  Immediate feedback via a new "Quick unlock: %d min" toast (the existing drain
  toast still confirms).
- **FR-004**: i18n EN+FA parity for all new keys (`unlockNow`, `pickFull`,
  `pickLongHint`, `toastQuick`); `timedUnlock` retired from both languages.
- **FR-005**: Service-worker cache bump dg-v7 → dg-v8 (one reload at most).
- **FR-006**: Zero changes to commands, payloads, D1 schema, routes, pend/audit
  logic (Art. VIII + rollback safety). spec-004/005/008/010 behaviors guarded by
  regression markers.

### Non-functional

- Long-press must not trigger the browser context menu or text selection on
  desktop or mobile touch.
- Pointer-events based implementation; browsers without Pointer Events degrade
  gracefully (tap → picker still works, long-press unavailable).
- No new dependencies; ES5-style dashboard code preserved.

## Failure Modes Analyzed

1. **Double action (long-press then click)** — the pointerup after a fired
   long-press dispatches a click; guard: `fired` flag checked in the click
   handler, click swallowed once. Tested by unit harness.
2. **Touch context-menu / lasso selection** — suppressed via `oncontextmenu`
   preventDefault + CSS `user-select:none; -webkit-touch-callout:none` scoped to
   the unlock button.
3. **Accidental full unlock** — full unlock stays the LAST, visually distinct
   row behind the existing `confirm()` warning; hidden gestures never trigger it
   (Art. II spirit: the bounded action is the fast path, the unbounded one is
   deliberate).
4. **Old apps misread merged button** — impossible: wire-level payloads are
   byte-identical to before; only the entry UI changed.
5. **Rollback** — worker-only: redeploy the dg-v7 source from git; no schema or
   data migration involved.
6. **Disabled-state gesture** — disabled buttons emit no pointer events; also
   guard `fired` reset on pointerdown only.

## Art. XI Disclosure (planned)

**WORKER-ONLY.** No APK is needed; every app ≥ v1.3.3 already speaks both
payload shapes. The merged button goes live the moment the worker deploys; the
browser picks it up after at most one reload (SW dg-v8).
