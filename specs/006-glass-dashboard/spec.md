# Feature Specification: Glass dark dashboard super-UI

**Feature Branch**: `006-glass-dashboard`

**Created**: 2026-09-15

**Status**: Implemented (worker-only, live 2026-09-15 — NO app update needed)

**Input**: Owner request: "make a new super ui upgrade for parents panel".
Clarification round selected: **Glass dark** style (frosted translucent cards,
gradient glow background, glowing badges — keeps the dark identity), extras:
**toasts + sound/vibrate on device-confirmed commands**, animation level:
**medium** (~300 ms slides/flips/pulses, tasteful). Rings/charts, skeletons and
the quick-action bar were shown and NOT selected — deliberately out of scope.

**Constitution articles touched**: V (all new strings EN+FA: toasts, chips),
VIII (honest UI preserved — every state means what it says; toasts fire only on
real device confirmations, never on optimistic state), XI (worker-only change —
live instantly, no APK).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The panel looks premium and glassy (Priority: P1)

The dashboard keeps its deep-navy night identity but everything is now
translucent frosted glass: cards float on a soft indigo/cyan gradient-glow
background with blur; the header is a sticky glass bar; status badges glow
(locked = red pulse, pending = amber pulse, ok = steady green, ⚡ fast = cyan);
buttons are glass pills with a gradient primary; the usage bar is taller with a
gradient fill. On browsers without `backdrop-filter` the glass falls back to a
solid panel (feature-detected via `@supports`), so nothing ever becomes
unreadable. RTL/Persarian rendering stays first-class (Vazirmatn stack, mirrored
layout).

**Independent Test**: load the dashboard → frosted cards over a glowing
background; toggle EN/فارسی → layout mirrors correctly; shrink to phone width →
cards stack, buttons stay touch-friendly.

### User Story 2 - The panel celebrates confirmations (Priority: P1)

When a pending Lock or Unlock is **confirmed by the device** (the pend chip
clears because the report matches), a glass toast slides up: "✓ Locked —
confirmed by device" / "✓ Unlocked — confirmed by device" (EN+FA), a short
two-tone chime plays (WebAudio, distinct tone pair for lock vs unlock), and the
phone vibrates where supported (`navigator.vibrate`, guarded). Toasts stack at
the bottom, auto-dismiss after 4 s, tap to dismiss. They fire ONLY on real
confirmations — never on optimistic state (Art. VIII).

**Independent Test**: lock a paired device → within seconds of the badge
flipping to LOCKED the toast appears with the chime; background the tab during
the wait → on return, refresh settles and the toast fires.

### User Story 3 - Motion is medium and respectful (Priority: P2)

Card entrance uses a staggered slide-fade (~300 ms, 40 ms stagger) on initial
app render (login / language switch) — but NOT on the 10 s data refresh, so the
panel never flickers while idle. The pending chip pulses; badge dots glow;
toasts slide. `prefers-reduced-motion` disables all of it. No bouncing, no
confetti, no re-triggering animations on data updates.

**Independent Test**: watch the panel for a minute — data updates land without
visual replays; the first render after login slides in; enabling OS
reduce-motion kills all animation.

### User Story 4 - Everything that worked still works (Priority: P1)

Pairing box + countdown, per-device settings, safe-unpair warnings, audit log,
usage sparkline, pending chips (local + server-truth), old-app badge, ⚡ fast
chip, language toggle, logout — all present, all restyled, zero behavior change.
The service worker cache bumps `dg-v4` → `dg-v5` so every client picks the new
panel up exactly once.

**Independent Test**: after reload, `sw.js` reports dg-v5; every control from
v1.3.3-panel is still operable.

## Requirements Analysis

- **FR-001 (P1)**: Full glass-dark design system in `DASHBOARD_CSS` (tokens,
  glass cards with `@supports` fallback, gradient-glow background layers,
  sticky glass header, glowing badges, glass buttons, restyled bar/spark/log/
  settings/code/pair elements).
- **FR-002 (P1)**: Background glow layers in `DASHBOARD_HTML` (pure CSS, no
  external assets; CSP untouched).
- **FR-003 (P1)**: Toast system in `DASHBOARD_JS` (stack, auto-dismiss, tap to
  dismiss, i18n EN+FA) + WebAudio chime (lock/unlock tone pairs) +
  `navigator.vibrate` guard, fired on pend→confirmed transitions detected in
  `refresh()`.
- **FR-004 (P2)**: Medium animations (entrance stagger on `renderApp` only,
  chip pulse, dot glow, toast slide) + `prefers-reduced-motion` kill-switch.
- **FR-005 (P1)**: SW cache `dg-v5`.
- **FR-006 (P1)**: No functional changes to any flow; all strings EN+FA.

## Failure Modes

- No `backdrop-filter` → solid fallback panels ( readability first).
- WebAudio blocked/absent → toast still shows, sound silently skipped.
- `navigator.vibrate` unsupported (iOS) → no-op guard.
- reduce-motion → all animations disabled.
- Old cached dashboard (dg-v4) → keeps working; picks dg-v5 on next SW update.
- Toast storm (many devices confirming at once) → stack caps at 4 visible;
  older toasts drop.
