# Requirements Checklist: Fix v1.3.5 Cold-Start Crash

## User Story 1 — The app opens again

- [x] Cold start (fresh install) completes without exception — Robolectric freshInstallColdStart PASS
- [x] Cold start in armed state (monitoring, PIN, latched, FA) completes without exception — armedKidPhoneColdStart PASS
- [x] The entrance animation list is explicitly typed View at every element
- [x] Fix causes zero behavior change elsewhere (only the array component type changed)
- [x] Prefs/PIN/pairing survive the v1.3.5 → v1.3.6 update path (same signature; no migration touched)

## User Story 2 — The spinner can't recurse

- [x] suppressSpinner armed before setSelection in applySpinner
- [x] suppressSpinner armed before setSelection in revertSpinner
- [x] suppressSpinner armed before notifyDataSetChanged in rebuildSpinnerLabels
- [x] Robolectric armed-state test (previously StackOverflowError) now passes
- [x] Spinner behavior on AOSP unchanged (suppression window identical)

## User Story 3 — Launch regressions can never ship silently again

- [x] LaunchSmokeTest committed to app/src/test with both cold-start scenarios
- [x] isIncludeAndroidResources enabled for unit tests
- [x] Robolectric pinned in the version catalog (4.16)
- [x] Smoke test exercises: theme apply, setContentView inflate, findViewById wiring,
      applyTexts, entrance, onResume, refreshUi (all three hero states), checklist,
      decor measure/layout/draw (window background + drawable inflation)

## Cross-cutting

- [x] FR-004 versionCode 10 / versionName 1.3.6 / UA updated
- [x] FR-005 mixed-inference audit of all listOf/arrayOf sites clean
- [x] FR-006 Worker untouched — dashboard stays dg-v6 live
- [x] Art. IV: no secrets in code/specs/logs
- [x] Art. V: no new user-facing strings (none needed)
- [x] Art. X: signed APK, monotonic versionCode, tag + GitHub release
- [x] Art. XI: disclosure states APP UPDATE REQUIRED (prod v1.3.6 / QA v1.3.6-test7);
      worker/dashboard already live and unaffected
