# Tasks: 011-unlock-merge

## 1. Spec / Plan
- [x] T001 spec.md (3 user stories, FR-001..006, failure modes, Art. XI plan)
- [x] T002 plan.md (D1–D10, verification gates, risks)

## 2. Worker (cloud/worker.js via asserted edit script, atomic write)
- [x] T010 i18n EN: rename `unlockFull`→`unlockNow` (value "Unlock"); add `pickFull`, `pickLongHint`, `toastQuick`; retire `timedUnlock`
- [x] T011 i18n FA: same renames/additions/retirement, Persian copy
- [x] T012 Card render: locked state shows ONE `data-unlock` button (class `ok small longable`, title hint); delete `data-unlock-full` button
- [x] T013 `armLongPress(btn, fire)` helper + wiring in `wireDevice`: 550 ms, click-after-fire swallowed, contextmenu suppressed; long-press sends remembered-minutes unlock + `toastQuick`, tap opens picker
- [x] T014 `openPicker`: ordered ladder — chips, custom, divider, `pickFull` row (amber, Enter/Space, `confirmUnlockFull` → `{full:true}` + pend `oldApp`), `pickLongHint` footer with seed minutes
- [x] T015 CSS: `.pickfull`, `.pickdiv`, `.longable` (user-select none, touch-callout none); SW dg-v7 → dg-v8

## 3. Verification (release gates)
- [x] T020 scripts/verify_spec011.js: node compile gates; stub-DOM `armLongPress` tests (fire once, click swallowed, pointerup/leave/cancel abort, disabled inert); i18n parity + retired keys; marker sweep (`data-unlock-full` absent, `pickFull`/`armLongPress`/`dg-v8` present); spec-004/005/008/010 regression markers — ALL PASS
- [x] T021 Deploy; live probes (/, /app.js markers, SW dg-v8, /api/child/history 401 unauth)
- [x] T022 `/demo` sim smoke EN+FA: locked card → single Unlock button; tap → ladder incl. full row + hint; chip → command `{minutes}` to sim; long-press → instant `{minutes}` to sim + quick toast; full row → confirm → `{full:true}`; audit shows only sim traffic; sim revoked + forgotten

## 4. Converge
- [x] T030 README version-history row (dashboard spec-011); cloud/README.md unlock-section amendment
- [x] T031 spec-001 deployed-state bullet + roadmap renumber
- [x] T032 spec-011 status Implemented + all boxes checked; commit + push; worklog append; Art. XI verdict (worker-only)
