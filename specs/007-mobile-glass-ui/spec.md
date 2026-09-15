# Feature Specification: Mobile glass super-UI (Android app)

**Feature Branch**: `007-mobile-glass-ui`

**Created**: 2026-09-15

**Status**: Implemented (app v1.3.5, 2026-09-15 — REQUIRES installing the new APK; worker/dashboard untouched)

**Input**: Owner request: "now add a super ui upgrade for the mobile app".
Clarification round: **inherited from spec-006** (the owner's recorded
selections for the matching panel upgrade, since this is the mobile
counterpart of the same request): **Glass dark** style (translucent cards over
an indigo/cyan gradient-glow background, glowing status, gradient buttons —
keeps the deep-navy identity), extras: **state-change toasts + sound/vibrate**,
animation level: **medium** (~300 ms, entrance-once). Rings/charts, skeletons
and quick-action bars remain out of scope. All new strings EN+FA.

**Constitution articles touched**: V (all new strings EN+FA: toasts, hero
status), VIII (honest UI — hero state mirrors the real latch/grace state from
the same sources as today's status line; toasts/chime fire only on real
transitions, never on optimistic state), X (signed, versioned, tagged release
v1.3.5 + QA), XI (**APP UPDATE REQUIRED** — this is app-side only; Worker and
dashboard are NOT touched and stay live as-is).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The app looks premium and glassy, matching the parents' panel (Priority: P1)

The main screen keeps its deep-navy night identity but is rebuilt in the same
design language as the glass dashboard (spec-006): the window background is a
layered gradient with soft indigo and cyan radial glows; cards are translucent
glass with 1 dp borders and large radii; primary actions are gradient pills
(indigo→cyan for Save, green gradient for Unlock); the usage bar becomes a
taller rounded gradient bar that keeps its threshold semantics (green < 80 %,
amber ≥ 80 %, red ≥ 100 %). A new **status hero** at the top of the usage card
shows the real guard state with a glowing dot: PROTECTED = steady green glow,
FREE TIME (grace) = amber pulse + live countdown, BLOCKED (latched) = red
pulse, plus the existing bilingual state title. The status line and checklist
stay truthful monospace text (Art. VIII) inside a glass card. The cold start
shows a matching gradient splash background. PIN dialog, cloud-pairing dialog,
logs dialog, spinner, test panel (QA) and the language chip all get the same
glass treatment. RTL/Persian stays first-class (start/end paddings, mirrored
layout, Vazirmatn-equivalent system rendering).

**Independent Test**: open the app → glass cards float over the glow; toggle
فارسی → layout mirrors, countdown digits stay readable; the hero dot color and
label always match the old status line's truth.

### User Story 2 - The phone celebrates real state changes (Priority: P1)

When the guard's real state changes — grace starts (unlock), grace is
cancelled or expires (re-lock), the watchdog latches (blocked), or the guard
arms — an in-app glass toast slides up from the bottom (stacked, auto-dismiss
~3.5 s, tap anywhere to dismiss) with bilingual text, a short two-tone chime
(the same tone pairs as the dashboard: lock = falling 330→220 Hz, unlock =
rising 440→660 Hz, arm = single soft tone) and a short vibration. It fires
ONLY when the 1 s UI tick detects an actual state transition — never on every
refresh, never on optimistic state (Art. VIII). A parent locking remotely
while the child has the app open sees (and hears) "🔒 قفل شد / Locked" within
a second. While the activity is in the background, messages fall back to the
system toast (glass-styled view, no animation) so nothing is ever lost.

**Independent Test**: start a grace window → unlock chime + toast; cancel it
("lock again now") → lock tone + toast; let it expire → lock tone when the
latch re-arms; watch a quiet minute → no repeated toasts.

### User Story 3 - Motion is medium and respectful (Priority: P2)

Cards enter once with a staggered slide-fade (~300 ms, ~45 ms stagger) on
activity creation — the 1 s `refreshUi()` tick never replays entrance
animations, so the screen does not flicker while data updates land. The hero
dot breathes softly; toasts slide. Battery-safe: the pulse uses one cheap
ObjectAnimator and pauses automatically with the activity (onPause removes
the UI tick). The system "remove animations" setting (animator duration
scale = 0) disables every animation including the pulse — views are never
left at zero alpha.

**Independent Test**: leave the app open a minute — usage updates land with
no visual replay; the first launch after cold start slides in; setting
animator scale to 0 in developer options kills all motion.

### User Story 4 - Everything that worked still works (Priority: P1)

PIN gate + cooldown, save/reset, unlock flow + "lock again now", unlock
duration spinner (PIN-gated while armed), hard-mode PIN gate, monitor switch
flow, permissions helper, cloud pairing/unpair, logs, owner guide, test panel
(QA only), language toggle — all present, all restyled, **zero behavior
change**. Enforcement, fail-closed, clock-tamper and poll logic are not
touched (Art. II/VII/IX). `versionCode` 9, `versionName` 1.3.5, UA
`DataGuard-Android/1.3.5`; QA keeps the `.test` suffix and installs
alongside production (Art. X). Because 1.3.5 > 1.3.3, a phone updated to
this build also clears the dashboard's old-app amber badge.

**Independent Test**: exercise each control after the upgrade — identical
flows and guards; `aapt` badging shows vc=9 vn=1.3.5 (QA: 1.3.5-test);
both APKs pass `apksigner verify` with the v1.3 keystore.

## Requirements Analysis

- **FR-001 (P1)**: Design tokens + theme: extended `colors.xml` palette,
  dark glass `Theme.DataGuard` (status/navigation bar colors, launch
  background), no new dependencies — AppCompat stays.
- **FR-002 (P1)**: Glass layouts: `activity_main.xml` rebuilt (hero status
  with glowing dot + countdown, usage, checklist, settings, gradient
  buttons, tools card, footer), plus `dialog_pin.xml`, `spinner_item.xml`,
  `dialog_test.xml` and all backing drawables (glow background, glass cards,
  gradient/ripple buttons, gradient progress track, dialog background).
- **FR-003 (P1)**: State-change feedback: transition detector fed by the
  same truth sources as the status line; glass toast system (in-app
  slide-up stack while resumed, system-toast fallback otherwise); AudioTrack
  sine chime generator (no asset files, no permissions); vibration guarded.
- **FR-004 (P2)**: Medium animations (entrance-once stagger, dot breathe,
  toast slide) + reduce-motion kill-switch (animator duration scale 0).
- **FR-005 (P1)**: Version bump: `versionCode` 9, `versionName` 1.3.5, UA
  `DataGuard-Android/1.3.5`.
- **FR-006 (P1)**: Zero behavior change to enforcement/PIN/cloud/poll
  logic; all new user-visible strings EN+FA; notification accent tint only.
- **FR-007 (P2)**: Cold-start splash: launch theme windowBackground with the
  glow gradient + centered launcher foreground.

## Failure Modes

- Chime cannot play (silent/vibrate mode, audio focus loss, exotic ROM) →
  toast still shows; every audio call is try/catch-guarded and volume-aware.
- Vibration unsupported/disabled → guarded no-op.
- Custom in-app toast unavailable (activity destroyed mid-show) → falls back
  to the system toast; callbacks cancelled in onDestroy.
- Reduce-motion (animator scale 0) → no entrance, no pulse, no slide; views
  render fully opaque immediately.
- RTL / long Persian strings → single-column layout, wrap text, touch
  targets stay ≥ 44 dp; countdown uses locale-safe digits as today.
- Old app on the phone (v1.3.2/1.3.4) → keeps working exactly as before;
  installing v1.3.5 is an ordinary update (dashboard badge logic unaffected
  — its gate is < 1.3.3).
- QA `.test` build → same UI, test panel restyled but functionally identical
  (virtual clock etc. untouched).
