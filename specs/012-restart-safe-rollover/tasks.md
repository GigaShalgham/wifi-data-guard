# Tasks: 012-restart-safe-rollover

## 1. Spec / Plan
- [x] T001 spec.md (findings F1–F6 + F2 disclosure, 4 user stories, FR-001..006 + FR-101..107, failure modes, Art. XI plan)
- [x] T002 plan.md (D1–D10, gates, risks)

## 2. App (v1.4.1)
- [x] T010 Prefs: `latchPeriodStart` / `setLatchPeriodStart`
- [x] T011 New `RolloverPolicy.kt` (pure, no Android imports) + `RolloverPolicyTest` (5 cases: limit+newer releases; limit+equal keeps; limit+older keeps (rollback); cloud/offline/clock+newer keep; empty-reason+newer releases; migration zero releases)
- [x] T012 WatchdogService: persist latch period at both latch sites; `onCreate` reconcile (unlatch, clear grace, resetTo fresh usage, log)
- [x] T013 CloudLink.buildReport adds `latch_reason`; UA bump to 1.4.1 (x2)
- [x] T014 HistoryUi FA weekday array fix + HistoryUiTest real-date mapping assertions (EN + FA)
- [x] T015 build.gradle.kts versionCode 12 / versionName 1.4.1

## 3. Worker (dg-v9, via asserted edit script + atomic write)
- [x] T020 i18n EN+FA: add `toastFullWindow`, `confirmUnlockLimited`, `reasonLimit`, `reasonCloud`, `reasonOffline`, `reasonClock`
- [x] T021 handleChildPoll whitelist: `latch_reason` (≤16 chars)
- [x] T022 pendConfirmed: full-unlock also confirms on active grace; toastConfirmed picks `toastFullWindow` when latched+grace+reason limit/clock (uses reported window minutes)
- [x] T023 doFull warning: `confirmUnlockLimited` when `d.report.latch_reason` is limit/clock
- [x] T024 deviceStatus/locked badge appends reason label when known
- [x] T025 handleRevoke deletes undelivered commands; 5 % sweep also deletes undelivered commands of revoked devices
- [x] T026 remove dead `attempts >= 10` branch
- [x] T027 SW dg-v8 → dg-v9

## 4. Verification (release gates)
- [x] T030 `./gradlew test` all green (28 tests)
- [x] T031 `node --check` + `node scripts/verify_spec012.js` ALL PASS (compile gate, unit vectors, i18n parity 74/74, markers, regressions)
- [x] T032 Build: assembleRelease + assembleQa; apksigner verify (same cert); badging 12/1.4.0→1.4.1 (+ .test); UA in dex
- [x] T033 Deploy worker; live probes (/, /app.js markers, SW dg-v9, 401s); read-only D1 health probe

## 5. Converge
- [x] T040 README version-history row (app v1.4.1 + dashboard dg-v9); cloud/README.md amendments (report field, revoke cleanup)
- [x] T041 spec-001 deployed-state bullet + roadmap row
- [x] T042 spec-012 status Implemented + all boxes checked; commit + push + tags v1.4.1 (+ QA); GitHub Releases; worklog append; Art. XI verdict + F2 re-pair instruction
