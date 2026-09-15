# Tasks: 010-ux-revolution

## 1. Spec / Plan
- [x] T001 spec.md (3 user stories, FR-001..007 + FR-101..108, failure modes, Art. XI plan)
- [x] T002 plan.md (decisions D1–D15; Part A worker-only, Part B app v1.4.0)

## 2. Part A — Worker (cloud/worker.js via asserted edit script, atomic write)
- [x] T010 i18n: add `pickTitle`, `pickCustom`, `pickConfirm`, `pickCancel`, `pickMin`, `timedUnlock` (EN+FA); retire `unlockMin`, `confirmUnlockFor`; keep parity
- [x] T011 `rememberedMins(d)` seed chain (localStorage → clamped server setting → 15) + `openPicker(d)` overlay (chips 15/30/60/120 + custom 1–480 + confirm/cancel + backdrop) wired into the `data-unlock` handler; confirm sends picked minutes, remembers choice, pend+toast as today
- [x] T012 Settings panel: remove `cfg-unlock` field, `unlock` draft key, bind, payload entry (4-field grid)
- [x] T013 Battery glyph `ὐb` → `🔋`
- [x] T014 `bucketDaily(rows, days, tz, nowMs)` pure function + `GET /api/child/history` route (device token, LIMIT 2400, days 1–14, tz ±1440)
- [x] T015 SW bump dg-v6 → dg-v7

## 3. Part B — App v1.4.0
- [x] T020 Theme: `Theme.MaterialComponents.NoActionBar` parent; `bg_aurora.xml`; ripple drawables; tab icon vectors + `menu/bottom_nav.xml`; chip-on drawable
- [x] T021 `activity_main.xml` rewrite: `tabHost` (tabStatus/tabUsage/tabParent ScrollViews) + `bottomNav`; all legacy view ids preserved (etLimit, cbMonthly, cbHard, swMonitor, tvStatus, tvUsageBig, tvUsageSub, heroDot, heroHalo, heroState, btnUnlock, btnSave, btnReset, btnGrant, btnPin, btnLogs, btnOwner, btnCloud, btnTest)
- [x] T022 `UsageRingView.kt` (sweep gradient arc, count-up %, used/limit center, state colors, reduce-motion)
- [x] T023 `HistoryBarView.kt` + `HistoryUi.kt` (pure label/scale math; bars, today outline, over-limit red, null = dashed)
- [x] T024 `CloudLink.fetchHistory()` + `Prefs` history cache; Usage tab wiring (stats grid, chart card, updated-ago, unpaired hint, stale-first)
- [x] T025 MainActivity: tab switching + Parent PIN gate (suppressed revert), unlock-duration chips (Spinner + echo machinery deleted), count-up, ring wiring, entrance/ripple polish, bilingual texts for all new elements
- [x] T026 versionCode 11 / versionName 1.4.0 / UA `DataGuard-Android/1.4.0`

## 4. Verification (release gates)
- [x] T030 scripts/verify_spec010.js: node --check both scopes; bucketDaily matrix; seed chain; i18n parity; retired strings; dg-v7; 🔋 present / ὐb absent; spec-004/005/008 regression markers — ALL PASS
- [x] T031 App unit tests: GuardStateUiTest + LaunchSmokeTest (fresh+armed) + NEW TabSwitchSmokeTest — `./gradlew :app:testDebugUnitTest` PASS
- [x] T032 Build release+qa APKs, apksigner verify, badging check (versionCode 11, UA string in dex)
- [x] T033 Deploy worker; live probes (200/401, picker markers in /app.js, /api/child/history auth path)
- [x] T034 agent-browser dashboard smoke on /demo sim only: picker opens, chip select, custom input, confirm → command+toast; settings has no unlock field; battery line renders; FA/RTL; zero console errors; D1 audit shows no commands to the real device

## 5. Converge
- [x] T040 README version-history row (app v1.4.0 + worker dg-v7); cloud/README.md history endpoint + picker docs
- [x] T041 spec-001 deployed-state row + roadmap renumber; spec-010 status Implemented + boxes
- [x] T042 Commit + push; tags v1.4.0 + v1.4.0-test8; GitHub releases (release_v140.py); Art. XI disclosure; worklog append
