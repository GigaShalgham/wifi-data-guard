# Implementation Plan: Fix v1.3.5 Cold-Start Crash

**Feature Branch**: `009-launch-crash-fix`
**Created**: 2026-09-16
**Status**: Implemented

## Context

Owner reported v1.3.5 crashes at launch. Investigation sequence (all evidence in
spec.md): full static review of the spec-007 diff (45 files) found no smoking gun →
shipped-APK integrity verified (release asset byte-size + badging + compiled theme
table + dex contents all correct) → reproduction attempted for real: emulator
blocked by sandbox (no KVM, TCG guest dies) → **Robolectric launch smoke test
reproduced two crashes in sequence**: (1) StackOverflowError from
`onItemSelected ↔ revertSpinner` recursion, (2) after fixing that,
`ArrayStoreException: android.widget.LinearLayout` at MainActivity.kt:183 — the
actual device crash.

## Goals / Non-Goals

**Goals**: make the app open on every device; harden the spinner echo ordering;
add a permanent launch smoke test; ship v1.3.6.

**Non-Goals**: any UI redesign, new features, worker changes, or touching
enforcement/PIN/cloud logic (Art. II/VII/VIII/IX untouched).

## Design Decisions

1. **Fix by pinning the list type, not by restructuring the entrance call.**
   `listOf(findViewById<View>(...), ..., btnUnlock as View, ...)` keeps the
   spec-007 design intact and makes the element type explicit at every element.
   Alternative (building the list with explicit `listOf<View>(...)` alone) was
   considered; the chosen form also documents the trap at each call site.

2. **Spinner suppression order: arm before mutate.** `suppressSpinnerBriefly()`
   moved to before `setSelection`/`notifyDataSetChanged` in all three helpers.
   The 300 ms window is unchanged; behavior on AOSP is identical (callback was
   already suppressed), but synchronous callbacks (Robolectric, potential OEM
   paths) can no longer re-enter.

3. **Robolectric 4.16 + `isIncludeAndroidResources = true` as permanent
   verification.** The smoke test drives `Robolectric.buildActivity(
   MainActivity).setup()` (create→start→resume), idles the main looper, then
   forces a full measure/layout/draw of the decor view (inflates window
   background + drawables). Two cases: fresh install; armed kid's phone (FA
   language, monitoring, PIN, latched, 500 MB limit) — the second exercises the
   spinner echo + hero BLOCKED branch + checklist binder calls.

4. **Known limitation**: AGP 9.3 only wires unit-test tasks for the debug
   variant; debug shares source+resources with release/qa (optimization disabled
   in all), so debug-variant evidence is valid for the release APK. Recorded
   here so nobody mistakes this for a release-variant test.

5. **Version bump to versionCode 10 / 1.3.6** (Art. X: signed, tagged,
   released). QA channel becomes v1.3.6-test7 to stay ahead of the phone's
   old installs.

6. **In-repo permanence**: the smoke test and the gradle test config are part of
   the committed fix (not investigation scripts), because the constitution's
   lesson from this incident is that launch verification must live in the repo.

## Risks / Trade-offs

- Robolectric cannot catch device/brand-specific framework behavior (Samsung One
  UI). Accepted: the reproduced crash is deterministic and framework-generic.
- `btnUnlock as View` is a no-op cast used to defeat inference; documented
  inline so future readers understand why it's there.
- No emulator verification possible in this sandbox (no KVM); the smoke test +
  resource-identical variants + owner install test (US1) are the verification.

## Rollout Plan

Single PR/commit: fix + tests + version bump. Tag `v1.3.6` + `v1.3.6-test7`,
GitHub releases with both APKs, README/spec-001 converge, worklog append.
Owner installs v1.3.6 over the crashing v1.3.5 and confirms the app opens;
optional confirmation via "View logs" showing the v1.3.5 ArrayStoreException
entry (written by GuardApp's uncaught handler before the crash).
