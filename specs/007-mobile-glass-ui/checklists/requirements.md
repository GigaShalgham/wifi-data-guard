# Requirements Checklist: 007-mobile-glass-ui

## FR-001 — design tokens + theme
- [x] colors.xml glass palette (bg, glow indigo/cyan, glass fills, strokes, text)
- [x] Theme.DataGuard: dark status/navigation bars #070D1A, no parent change (AppCompat)
- [x] Theme.DataGuard.Launch splash background; no new dependencies

## FR-002 — glass layouts
- [x] bg_glow window background (base + 2 radial glows); bg_splash
- [x] activity_main.xml: hero status (glowing dot + halo + state title), usage, checklist, settings, unlock pill, tools, footer — ALL existing view ids preserved
- [x] gradient buttons (primary indigo→cyan, unlock green / amber), glass tools buttons, ripple everywhere
- [x] gradient progress bar with threshold swap (green/amber/red), taller + rounded
- [x] dialog_pin / spinner_item / dialog_test restyled; dialog theme overlay glass

## FR-003 — state-change feedback
- [x] transition detector on the same truth sources as the status line (grace/latched), prev≠next only
- [x] in-app glass toast stack while resumed (slide-up, ~3.5 s, tap dismiss, cap 3)
- [x] system-toast fallback (glass view, no animation) when paused
- [x] chime: lock 330→220 Hz, unlock 440→660 Hz, arm single tone; ringer-mode guarded
- [x] vibration (unlock one-shot / lock waveform), guarded
- [x] EN+FA toast strings (T map)

## FR-004 — medium animations
- [x] entrance-once stagger (~300 ms, ~45 ms) on create; never on 1 s refreshUi
- [x] hero halo breathe (single ObjectAnimator), start/stop with onResume/onPause
- [x] reduce-motion (animator duration scale 0) kills entrance + pulse + slide; alpha never left at 0

## FR-005 — versioning
- [x] versionCode 9, versionName 1.3.5, UA DataGuard-Android/1.3.5

## FR-006 — zero behavior change
- [x] enforcement/PIN/cloud/poll/test-mode code untouched (only notification setColor)
- [x] all new strings EN+FA; touch targets ≥ 44 dp; RTL start/end paddings

## FR-007 — splash
- [x] launch theme windowBackground (glow + centered launcher foreground)

## Verification gates
- [x] ./gradlew test + assembleQa + assembleRelease pass
- [x] badging: vc=9 vn=1.3.5 (QA 1.3.5-test), both package ids correct
- [x] apksigner verify both APKs (v1.3 keystore)
- [x] id sweep: every findViewById id in MainActivity exists in new layout
- [x] secret scan clean; commit + tags v1.3.5 / v1.3.5-test6 + GitHub Releases
