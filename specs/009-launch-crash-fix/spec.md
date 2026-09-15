# Feature Specification: Fix v1.3.5 Cold-Start Crash (Launch Crash)

**Feature Branch**: `009-launch-crash-fix`

**Created**: 2026-09-16

**Status**: Implemented

**Input**: Owner report: "the new android app didn't open and just crashed out" — the v1.3.5
release (spec-007 mobile glass UI) crashes immediately on launch on the real phone.

## Incident Summary

v1.3.5 (commit `0e24d23`, versionCode 9) was built, signed and released without any
runtime launch test — its verification suite was JVM-pure unit tests (GuardStateUi)
plus build/badging/signature checks. On install, the app crashes during
`MainActivity.onCreate` before the first frame: splash shows, then the process dies.
Every device, every install, 100% deterministic.

## Root Cause (reproduced and proven, not guessed)

**Defect 1 — the launch crash.** `MainActivity.onCreate` ended with:

```kotlin
glass.entrance(listOf(
    findViewById(R.id.headerRow),     // LinearLayout
    findViewById(R.id.cardHero),      // LinearLayout
    findViewById(R.id.cardSettings),  // LinearLayout
    btnUnlock,                        // Button (only concretely-typed element!)
    findViewById(R.id.cardTools)))    // LinearLayout
```

Kotlin infers the `listOf` element type from its arguments. The four `findViewById`
calls are Java-interop generics (`<T extends View> T`) with no constraint of their
own, so the only concrete contributor is `btnUnlock: Button` — the solver pins the
element type to **Button**, the vararg array is allocated as **Button[]**, and
storing the LinearLayout cards throws at runtime:

```
java.lang.ArrayStoreException: android.widget.LinearLayout
    at com.example.wifidataguard.MainActivity.onCreate(MainActivity.kt:183)
```

Reproduced deterministically on the JVM with a Robolectric launch smoke test
(real compiled resources, full cold-start path). No compiler warning fires for
this pattern (platform types + erased varargs).

**Defect 2 — latent re-entrancy hazard in the same launch path (found by the same
test before Defect 1 surfaced).** `revertSpinner()`/`applySpinner()`/
`rebuildSpinnerLabels()` called `spUnlock.setSelection(...)` (and
`notifyDataSetChanged()`) **before** arming `suppressSpinner`. Robolectric's
synchronous selection callback turned this into unbounded
`onItemSelected ↔ revertSpinner` recursion (`StackOverflowError`). On AOSP the
callback is posted async so suppression normally wins, but any OEM/framework path
that fires the listener synchronously turns this into a crash; the ordering is
objectively wrong and is fixed together with the crash.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The app opens again (Priority: P1)

As the kid (or anyone who installed v1.3.5), when I tap the app icon after
updating to v1.3.6, the app must open and show the glass UI instead of crashing.

**Why this priority**: v1.3.5 is functionally dead on arrival; nothing else matters
until the app opens. The parent loses all remote control visibility while the
child app is crashed (fail-closed: a crashed app reports nothing, dashboard shows
stale, old-app badge logic can't distinguish crashed from offline).

**Independent Test**: Install v1.3.6 over a crashing v1.3.5 → app opens, hero state
renders, checklist + status line visible, 1s tick keeps running.

**Acceptance Scenarios**:

1. **Given** a fresh install (no prefs), **When** the app cold-starts, **Then**
   onCreate completes, the entrance animation runs and no exception is thrown.
2. **Given** the kid's armed phone state (monitoring=true, PIN set, latched=true,
   FA language), **When** the app cold-starts, **Then** the app opens (BLOCKED hero,
   RTL texts) with no crash and no PIN dialog triggered by the spinner echo.
3. **Given** v1.3.5 crashed on the device, **When** v1.3.6 is installed over it,
   **Then** prefs/PIN/pairing survive and "View logs" shows the recorded v1.3.5
   crash (`ArrayStoreException` written by GuardApp's uncaught-exception logger).

### User Story 2 - The spinner can't recurse (Priority: P2)

As a developer, I need the unlock-duration spinner's echo-suppression armed
*before* any programmatic selection/data change, so no callback order (including
synchronous OEM callbacks) can produce unbounded recursion.

**Why this priority**: same-launch-path hazard proven by the reproduction; cheap,
zero-behavior-change hardening that eliminates a whole crash class.

**Independent Test**: Robolectric smoke test drives cold start with monitoring=true;
previously this produced StackOverflowError; after the fix it passes.

**Acceptance Scenarios**:

1. **Given** monitoring=true, **When** the spinner fires its initial selection and
   `revertSpinner()` runs, **Then** the re-entrant callback returns immediately
   (suppression already armed) and no recursion occurs.
2. **Given** a language switch (`applyTexts` → `rebuildSpinnerLabels`), **When**
   adapter data changes, **Then** the selection echo is swallowed.

### User Story 3 - Launch regressions can never ship silently again (Priority: P2)

As the maintainer, every future app release must pass a launch smoke test that
actually executes `MainActivity` cold start with the real resources before any
APK is tagged/released.

**Why this priority**: v1.3.5 shipped a 100%-deterministic launch crash because no
runtime test existed. The smoke test that caught this bug becomes permanent.

**Independent Test**: `:app:testDebugUnitTest` includes LaunchSmokeTest (fresh +
armed-phone cold starts); CI/verification runs it before every release.

**Acceptance Scenarios**:

1. **Given** any code change touching MainActivity/resources, **When** unit tests
   run, **Then** both LaunchSmokeTest cases must pass or the build fails.

## Requirements

### Functional

- **FR-001**: `glass.entrance(...)` list element type is pinned to `View`
  (explicit `findViewById<View>` + `btnUnlock as View`); no mixed-type inference.
- **FR-002**: `suppressSpinner` is armed before `setSelection`/`notifyDataSetChanged`
  in `applySpinner`, `revertSpinner` and `rebuildSpinnerLabels`.
- **FR-003**: Robolectric launch smoke test (fresh install + armed FA/latched/PIN
  state) is permanent in the unit-test suite with `isIncludeAndroidResources`.
- **FR-004**: versionCode 10 / versionName 1.3.6 / UA `DataGuard-Android/1.3.6`.
- **FR-005**: no other `listOf/arrayOf` call site relies on mixed-element inference
  (audited: all remaining sites homogeneous).
- **FR-006**: Worker/dashboard untouched (worker-only state dg-v6 stays live).

### Non-functional

- Zero behavior change beyond the crash fix and echo-order hardening (Art. VIII/IX
  untouched: enforcement, PIN, cloud, poll logic identical to v1.3.4 semantics).
- Bilingual strings unchanged (no new user-facing strings except none needed).

## Failure Modes Analyzed

- **Old-app compat**: phones on v1.3.3/1.3.4 are unaffected by this bug (code path
  introduced in v1.3.5); they keep working as before.
- **Update path**: v1.3.6 installs over v1.3.5 (same signature); prefs, PIN, pairing
  survive; the recorded v1.3.5 crash log becomes readable after update (US1.3).
- **Rollback direction**: if v1.3.6 misbehaves, v1.3.4 APK still installs (same
  signature, lower versionCode — requires uninstall first; disclose if asked).
- **Test-only risk**: Robolectric runs on the debug variant; release/qa share the
  identical source+resources (optimization disabled in all variants), so the repro
  validity holds; noted as a known limitation in plan.md.

## Art. XI Disclosure

**APP UPDATE REQUIRED**: install `DataGuard-v1.3.6-release.apk` (or QA
`v1.3.6-test7` alongside). Worker/dashboard: untouched, already live (dg-v6).
