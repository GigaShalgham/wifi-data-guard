# Tasks: Fix v1.3.5 Cold-Start Crash

**Feature Branch**: `009-launch-crash-fix`
**Created**: 2026-09-16
**Status**: Implemented

## 1. Investigation (done)

- [x] Read worklog + git history; confirm v1.3.5 = spec-007 glass UI, never runtime-tested
- [x] Full static review of the spec-007 diff (Kotlin + resources + manifest + themes)
- [x] Verify shipped release asset integrity (size/badging/dex/theme table vs local build)
- [x] Attempt emulator reproduction (blocked: no KVM; TCG guest dies — documented)
- [x] Add Robolectric launch smoke test (fresh + armed kid's phone states)
- [x] Reproduce crash #1: StackOverflowError (onItemSelected ↔ revertSpinner recursion)
- [x] Reproduce crash #2 (the device crash): ArrayStoreException at MainActivity.kt:183

## 2. Fix (done)

- [x] Pin entrance list element type to View (findViewById<View> + btnUnlock as View)
  with an inline comment explaining the inference trap
- [x] Arm suppressSpinner BEFORE setSelection/notifyDataSetChanged in applySpinner,
  revertSpinner, rebuildSpinnerLabels
- [x] Audit all other listOf/arrayOf sites for mixed-element inference (all homogeneous)
- [x] Bump versionCode 10 / versionName 1.3.6 / UA DataGuard-Android/1.3.6

## 3. Verification (done)

- [x] Full unit suite green: LaunchSmokeTest 2/2, GuardStateUiTest 10/10, Example 1/1
- [x] assembleQa + assembleRelease clean
- [x] Badging: prod vc=10 vn=1.3.6; QA .test vc=10 vn=1.3.6-test
- [x] Signatures verified (project keystore)
- [x] No test commands sent to the real device (D1/worker untouched; worker dg-v6 live)

## 4. Converge (done)

- [x] README version-history row (v1.3.6 hotfix)
- [x] spec-001 deployed-state row + roadmap renumber
- [x] spec-009 checklist all boxes checked; status Implemented
- [x] Commit pushed; tags v1.3.6 + v1.3.6-test7; GitHub releases with both APKs
- [x] Worklog appended; Art. XI verdict delivered (APP UPDATE REQUIRED)
