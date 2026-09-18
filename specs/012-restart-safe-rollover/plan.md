# Implementation Plan: spec-012

## Decisions (self-clarified from evidence)

- **D1** Reconcile in `onCreate`, not the first cycle: earliest possible, one
  place, runs on the main thread before any UI/service interaction; a second
  run is a no-op (`latched=false`).
- **D2** Strictly-greater period comparison — a rolled-back clock can never
  fabricate a "new period" (Art. II).
- **D3** `latch_period_start` is written at BOTH latch sites (`cycle()` limit
  block and `latchCloud()`); harmless for non-limit reasons because
  `RolloverPolicy` filters on reason.
- **D4** Migration semantics: missing/0 `latch_period_start` = "older" — a
  stuck limit latch on the owner's phone releases on the first start after
  updating to v1.4.1 (no manual unblock needed).
- **D5** Reconcile mirrors the live rollover exactly: unlatch, clear grace,
  `resetTo(fresh effective usage)`; stats-unavailable keeps the counter.
- **D6** `latch_reason` in the report is coarse enforcement state (Art. III
  compliant); dashboard keys degrade silently when absent (old apps).
- **D7** Worker edits via the established exactly-once-asserted Python edit
  script + atomic write; verification via a new `verify_spec012.js` (compile
  gate, unit tests, i18n parity, regression markers, dg-v9).
- **D8** App edits direct in Kotlin; gates: `./gradlew test` (existing 21 +
  new RolloverPolicy 5 + HistoryUi mapping 2 = 28 tests), then assembleRelease
  + assembleQa, apksigner verify, badging.
- **D9** Release train: v1.4.1 (versionCode 12) + QA v1.4.1-test9; GitHub
  Releases via the established release-script pattern; worker deployed via
  `cloud/deploy.py --token-file`; SW dg-v9.
- **D10** No command is ever sent to the real device for testing; dashboard
  smoke (if any) uses `/demo` sims only, then revoke + forget, audit-verified.

## Verification gates

1. `./gradlew test` — all green (28 tests expected).
2. `node --check cloud/worker.js`.
3. `node scripts/verify_spec012.js` — DASHBOARD_JS compile gate; `pendConfirmed`
   unit vectors incl. degraded full-unlock + old-app + timed; `RolloverPolicy`
   parity vectors (JS mirror of the Kotlin cases); i18n EN/FA parity +
   retired-key check; marker sweep (dg-v9, latch_reason whitelist, revoke
   cleanup SQL, dead-check removed); spec-004/005/008/010/011 regression
   markers.
4. Robolectric cold-start gates stay green (part of gate 1).
5. Live probes after deploy: `/` 200, `/app.js` markers, SW dg-v9, unauth 401s.
6. Read-only D1 probe post-deploy: worker healthy; queue still clean.

## Risks

- R1 Reconcile could free a latch that a parent deliberately set "cloud" —
  impossible: reason filter + unit tests.
- R2 A phone left dead for weeks crosses a MONTH boundary — period start still
  monotonic per D2; releases once (correct: quota reset).
- R3 `monthly` period latched mid-month, phone dies, restarts same month —
  period equal → no release (correct: same period, still over limit).
- R4 Worker upload fails mid-flight — atomic PUT; dg-v8 remains live until
  success; probe before/after.
- R5 FA copy quality — reviewed for parity (Art. V); native-reader phrasing.
