# Feature Specification: UX Revolution — Unlock Picker + Aurora App (spec-010)

**Feature Branch**: `010-ux-revolution`

**Created**: 2026-09-16

**Status**: Implemented

**Input**: Owner verdict on v1.3.6: "The latest app update still hasn't introduced anything
new, impressive, or revolutionary. You only slightly improved the previous UI, and all the
existing bugs and shitty parts are still there." + "The useless and inefficient
`Unlock Window (min)` input is still there in the Parent Panel. You still haven't
replaced it."

## Problem Statement

Two owner-reported failures, plus one defect found in the live audit:

1. **The dashboard's `Unlock window (min)` settings input is the wrong abstraction.**
   It makes the parent pre-configure an abstract duration in a Settings panel, then
   go press a button that uses it. The efficient UX is to pick the duration **at the
   moment of unlocking**. spec-008 wired the field to real behavior; the owner's
   verdict is that the field itself must GO and be replaced by a point-of-use picker.
   (Verified live: dg-v6 HAS the spec-008 fixes — `cfgDraft`, `confirmUnlockFor`,
   honest save are all in the served `/app.js` — so this is a design complaint, not
   a deployment gap.)
2. **The mobile app's spec-007 "super UI" read as a slight reskin.** Same single
   scrolling page, same flat progress bar, same raw Spinner, same wall-of-buttons
   tools list. Nothing structural changed. The owner expects a *revolutionary*
   restructure, not a palette swap.
3. **Live defect found during audit**: every device card renders the battery line
   with a corrupted glyph — `ὐb 87%` instead of a battery icon (mojibake shipped in
   the bundle). Visible on every refresh; exactly the kind of "shitty part" the
   owner is calling out.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Parent grants time in one gesture (Priority: P1)

As the parent, when I tap the timed-unlock button on a device card, I want a
duration picker right there — preset chips (15/30/60/120 min) plus a custom field —
so I never touch Settings for this, and my last choice is remembered per device.

**Why this priority**: the owner explicitly demanded this input's replacement; it is
the headline dashboard change of this spec.

**Independent Test**: open the live dashboard → a locked device card → tap the
timed-unlock button → picker opens (not a `confirm()`), chips + custom input show,
last-used chip pre-selected → pick 45 → confirm → command lands, pend chip shows,
toast confirms the picked duration. The Settings panel no longer contains any
unlock-window field.

**Acceptance Scenarios**:

1. **Given** a locked device, **When** I tap "⏱ Timed unlock", **Then** a glass
   picker opens with chips 15/30/60/120 + a custom minutes field (1–480), the
   remembered choice pre-selected, EN/FA per current language.
2. **Given** the picker, **When** I choose a duration and confirm, **Then** the
   unlock command carries exactly that duration; the toast names it; no Settings
   round-trip happened.
3. **Given** any device, **When** I open Settings, **Then** there is no
   `Unlock window (min)` input anymore (limit / period / tolerance / hard mode only).
4. **Given** I unlocked for 45 min yesterday, **When** I open the picker today,
   **Then** the custom field shows 45 (remembered per device, survives re-login;
   falls back to the device's saved `unlock_minutes` setting, then 15).

### User Story 2 - Kid's phone feels like a 2026 app (Priority: P1)

As the owner, when I open the updated app (v1.4.0), I want a structurally different
experience: bottom navigation (Status / Usage / Parent), an animated usage ring
instead of a flat bar, my 7-day usage history as a chart, PIN-gated Parent tools in
one place — with count-up numbers, ripples and an aurora background.

**Why this priority**: the owner's core complaint — "nothing new, impressive, or
revolutionary". This is the app-side headline.

**Independent Test**: install v1.4.0 → cold start shows the Status tab with the
animated ring (percent counting up, gradient sweep); tap Usage → stats grid +
7-day bar chart (when cloud-paired; honest hint when not); tap Parent → PIN gate →
settings with duration chips (no Spinner) + all tools; tabs switch with animation;
no crash (Robolectric launch smoke: fresh + armed states + tab-switch case).

**Acceptance Scenarios**:

1. **Given** a cold start with data, **When** the Status tab renders, **Then** the
   ring animates 0→pct once (not on every 1 s tick), the big number counts up once,
   and the hero pill/halo still reflect the same truth sources as before (Art. VIII).
2. **Given** the app is cloud-paired, **When** I open the Usage tab, **Then** the
   7-day chart loads from the new device-authenticated history endpoint (day
   boundaries in the device's timezone), shows stale cache instantly and refreshes
   in the background; days without a baseline render as "no data" markers, never
   fake numbers.
3. **Given** the app is NOT paired, **When** I open the Usage tab, **Then** the
   history card honestly says pairing is needed — no fake local history.
4. **Given** monitoring is armed, **When** I first switch to the Parent tab,
   **Then** the PIN gate runs once per session; a failed PIN returns me to the
   previous tab (suppressed listener, no echo); the Enforce switch and hard-mode
   controls keep their existing PIN-gated semantics exactly.
5. **Given** reduce-motion is on (animator scale 0), **When** any view refreshes,
   **Then** ring/counter/tab animations snap instantly to final values.

### User Story 3 - No more mojibake battery line (Priority: P2)

As the parent, every device card must render `🔋 87%` — a real battery glyph, in
both languages, on every theme.

**Why this priority**: visible-on-every-load defect in the live product; found in
this spec's audit; one-line fix bundled with the picker deploy.

**Independent Test**: hard-refresh dashboard → device card shows `🔋 NN%` (SW bumped
to dg-v7 so even cached clients move).

**Acceptance Scenarios**:

1. **Given** a device with a battery report, **When** the card renders, **Then** the
   battery line starts with `🔋 ` and the number is the reported percentage.

## Requirements

### Functional — dashboard (worker-only)

- **FR-001**: `settingsPanel()` no longer renders the unlock-window field; draft
  init/bind/save-payload drop `unlock` accordingly. The server config API keeps
  accepting `unlock_minutes` (old apps + device-side default unchanged — backward
  compatible), it is simply no longer edited from the dashboard UI.
- **FR-002**: The timed-unlock button opens an in-page glass picker: chips
  15/30/60/120 min + custom minutes input (1–480, invalid → 15), confirm + cancel.
  Default pre-selection = `localStorage["unlockMin:<deviceId>"]` → else clamped
  saved `unlock_minutes` → else 15. Confirmed choice is remembered per device.
- **FR-003**: The unlock command payload carries the picked minutes; pend chip,
  toast and audit reflect it (`toastTimedFor`).
- **FR-004**: Battery line glyph `ὐb` → `🔋`.
- **FR-005**: New/updated i18n keys EN + FA (picker title, chips, custom label,
  confirm, cancel, generic timed-unlock button label); retired keys removed
  (`unlockMin`, `confirmUnlockFor`); EN/FA key parity maintained.
- **FR-006**: SW cache bump dg-v6 → dg-v7.
- **FR-007**: New app-facing endpoint `GET /api/child/history?days=7&tz=<offsetMin>`
  (device bearer token): returns `{ok, days:[{day:<epoch-day>, used_bytes:<int|null>}]}`,
  `used_bytes` = the day's last report minus the last observed report before it,
  clamped ≥ 0 (period reset → 0, never negative; usage across an offline gap
  lands on the first observed day after it); `null` when the day has data but no
  baseline at all (first ever report); 0 when the day has no data. `days` clamped
  1–14, `tz` clamped ±1440. Buckets are computed in the device's timezone. Pure
  logic extracted into a unit-testable function.

### Functional — app (v1.4.0, versionCode 11)

- **FR-101**: Bottom navigation with three destinations — Status, Usage, Parent —
  using Material `BottomNavigationView`; fragment-less (three content frames in a
  weighted FrameLayout); bilingual labels; BLOCKED badge dot on Status while
  latched.
- **FR-102**: First entry to Parent per activity session runs the existing PIN-gate
  flow; failure reverts to the previous tab (suppressed listener, no echo).
- **FR-103**: Status tab: hero pill + halo + animated `UsageRingView` (sweep
  gradient, state colors green/amber/red, count-up percent + used/limit in center)
  replacing the flat progress bar; status line, unlock button and checklist
  unchanged in semantics (same truth sources, Art. VIII).
- **FR-104**: Usage tab: count-up big number; stats grid (Used, Limit, Left,
  Period ends, Battery, App version); 7-day `HistoryBarView` (day labels, today
  highlighted, over-limit days red, no-data days dashed); "updated X ago" +
  refresh-on-enter; stale-cache-first rendering.
- **FR-105**: Parent tab: settings card (limit, monthly, hard mode, unlock-duration
  **chips** 5/15/30/60/End-of-period replacing the Spinner — same PIN-gated
  semantics, same persistence), tools (permissions, PIN, logs, owner guide, cloud
  pairing, test panel in QA builds, Enforce switch). The Spinner and its echo
  suppression are deleted.
- **FR-106**: Aurora background (layered radial gradients), ripple feedback on all
  primary buttons, staggered entrance on tab content, tab-switch cross-fade;
  all animations gated on animator-duration-scale (reduce-motion safe).
- **FR-107**: `versionCode 11`, `versionName "1.4.0"`, UA `DataGuard-Android/1.4.0`.
- **FR-108**: Robolectric launch smoke tests (fresh + armed) remain the release
  gate; a tab-switch smoke case is added (switch to all three tabs, force measure/
  layout/draw — would have caught view-level inflate/draw crashes).

### Non-functional

- Zero enforcement-behavior change (Art. II/VIII/IX): ring/charts/tabs are
  presentation over the exact same truth sources; PIN gates, grace math, latch
  precedence, cloud poll/cadence untouched. History is cosmetic: fetch failure
  never latches, never blocks, degrades to cached/hint state.
- Bilingual truth (Art. V): every new string EN + FA; FA picker/digits via existing
  `faNum`; app FA strings follow the existing in-code map pattern.
- The history endpoint is read-only, device-token scoped, rate-cheap (one indexed
  query per call, called at most on Usage-tab entry + manual refresh).
- No test commands to the real device; QA verified on Robolectric/simulator only.

## Failure Modes Analyzed

- **Old app + new worker**: v1.3.x apps ignore `/api/child/history` (never call it);
  config API unchanged; timed unlock still carries explicit minutes → old apps
  fully compatible.
- **New app + old worker (rollback)**: history fetch 404s → card shows the
  not-paired/hint state; everything else works (picker is worker-side and
  independent). No crash path.
- **Picker on pending device**: buttons already disable while `pend` is active;
  picker refuses to double-open (single overlay element reused).
- **localStorage unavailable (private mode)**: wrapped in try/catch → default 15
  fallback; picker still works.
- **Robolectric + Material BottomNavigationView**: theme switched to
  `Theme.MaterialComponents.NoActionBar` (dark) — AppCompat widgets unaffected;
  dialog overlays keep their own ThemeOverlay; smoke tests prove inflate+draw.
- **Battery glyph regression**: the fix uses a literal `🔋` in the source; the
  verification script asserts its presence and the absence of the corrupted
  codepoint.
- **SW staleness**: dg-v7 + skipWaiting (existing behavior) means one reload at
  most to see the picker; disclosed in the delivery note.

## Art. XI Disclosure (planned)

**APP UPDATE REQUIRED** for the app side (v1.4.0 APK, install-over, prefs/PIN/
pairing survive). Dashboard side goes live immediately on deploy (worker-only,
dg-v7): picker, settings-field removal, battery fix, history endpoint — no app
update needed for those.
