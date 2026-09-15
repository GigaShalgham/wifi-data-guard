# Tasks: Safe Unpair — no more locked-orphan trap

**Input**: Design documents from `/specs/002-safe-unpair/`

**Prerequisites**: plan.md (required), spec.md (required for user stories)

**Tests**: No new automated test framework in repo; verification is live-probe + QA-build device test (per plan.md Rollout/Verification). Test tasks below are those verification probes.

**Organization**: Tasks grouped by user story; US4 (cloud source recovery) is the foundational phase because dashboard work depends on it.

## Phase 1: Setup (Shared Infrastructure)

- [x] T001 Create spec directory `specs/002-safe-unpair/` with spec.md + checklists (done in /speckit.specify)
- [x] T002 Download live Worker bundle via CF API → `dataguard-cloud-recovered/worker-download.bin` (done during investigation)

---

## Phase 2: User Story 4 - Cloud source in repo (Priority: P2) 🎯 FOUNDATION

**Goal**: Repo contains a readable, deployable, versioned Worker source under `cloud/`
**Independent Test**: `cloud/worker.js` passes `node --check`; its DASHBOARD_JS equals live `/app.js`; deploy script documented

- [x] T003 [US4] Convert bundle → readable single-file `cloud/worker.js`: unescape template literals (DASHBOARD_JS, DASHBOARD_CSS, DASHBOARD_HTML, DEMO_HTML, SW_JS, ICON_SVG) to raw UTF-8; keep worker logic byte-equivalent
- [x] T004 [US4] Validate conversion: extracted DASHBOARD_JS diffs clean against live `/app.js`; `node --check cloud/worker.js` passes
- [x] T005 [US4] Write `cloud/deploy.py` (multipart PUT, bindings echoed from settings endpoint, token from file) + `cloud/README.md` (structure, bindings, deploy+verify, rollback)
- [x] T006 [US4] Smoke-deploy the UNMODIFIED converted source → all live probes green (proves deploy path + conversion before any edits)

**Checkpoint**: Worker deployable from repo; rollback bundle archived. Dashboard edits can now proceed safely.

---

## Phase 3: User Story 1 - Dashboard warning + button separation (Priority: P1) 🎯 MVP

**Goal**: Impossible to revoke a locked device without a lock-specific bilingual warning; Unpair no longer adjacent to Unlock
**Independent Test**: Lock a paired device, click Unpair → warning names the trap; cancel → nothing destroyed

- [x] T007 [US1] `cloud/worker.js` DASHBOARD_JS i18n: add `revokeConfirmLocked` (EN+FA) — states device stays locked, no remote unlock after unpair, parent-PIN-only recovery
- [x] T008 [US1] DASHBOARD_JS `deviceCard()`: move Unpair control to its own separated bottom row (`.sep` divider class); keep `data-revoke` wiring
- [x] T009 [US1] DASHBOARD_JS `wireDevice()`: branch confirm on `d.report && d.report.latched` → `revokeConfirmLocked` else existing text
- [x] T010 [US1] DASHBOARD_CSS: add `.sep` style (top border, spacing, right-aligned)
- [x] T011 [US1] Deploy + verify: `/app.js` contains `revokeConfirmLocked`, `/styles.css` contains `.sep`, node --check passes, all smoke probes green

**Checkpoint**: The original incident is now prevented at the source.

---

## Phase 4: User Story 2 - Phone tells the truth (Priority: P1)

**Goal**: Device unpaired while locked stays locked BUT relabels status and shows one-time bilingual PIN-recovery notice
**Independent Test**: QA build: pair → remote lock → revoke → force poll → status shows unpaired wording + notice; PIN unlock still works

- [x] T012 [US2] `CloudLink.kt` `unpair(fromServer=true)`: if `WatchdogService.latched` and reason ∈ {cloud, offline, clock} → `Prefs.setLatchReason("unpaired")` + fire notice (FR-003); not latched → silent (FR-004)
- [x] T013 [US2] `WatchdogService.kt`: add companion `notifyRevokedWhileLocked(c)` (bilingual high-priority "alerts" channel notification, id 6, autoCancel, opens app); call from CloudLink 401 path
- [x] T014 [US2] `WatchdogService.kt` `statusLine()`: add `"unpaired"` branch — EN "Locked — device unpaired (PIN unlocks)" / FA equivalent; verify rollover keeps this reason (not in clear-set)
- [x] T015 [US2] Bump versionCode 6 / versionName 1.3.2; CloudLink UA → `DataGuard-Android/1.3.2`
- [x] T016 [US2] Build `assembleQa` + `assembleRelease`; verify signing (SHA-256 c8abfd91…), badging, packages

**Checkpoint**: Both P1 stories done — trap prevented AND honestly recoverable.

---

## Phase 5: User Story 3 - Re-pair discoverability (Priority: P2)

**Goal**: Empty dashboard explains that re-pairing restores control of a previously unpaired device
**Independent Test**: Revoke only device → empty state mentions re-pairing (EN+FA)

- [x] T017 [US3] DASHBOARD_JS i18n `noDevices` text (EN+FA): keep existing meaning + add that an unpaired device returns by generating a new code and entering it in the child app
- [x] T018 [US3] Deploy + verify string present on live `/app.js`

---

## Phase 6: Polish & Release (Cross-Cutting)

- [x] T019 Update `README.md`: v1.3.2 row (safe-unpair), cloud/ in project structure + building/deploying section
- [x] T020 Update `specs/001-project-state-backfill/spec.md` (REQ-003): new deployed state (v1.3.2, cloud/ in repo), roadmap renumbered (demo removal → 003, Play hardening → 004, E2E → 005; v1.3.1-release item closed by v1.3.2)
- [x] T021 Commit + push main; tag `v1.3.2`; GitHub release v1.3.2 (prod APK + notes); prerelease `v1.3.2-test3` (QA APK + test instructions)
- [x] T022 Append Task 15 record to sandbox worklog; final live verification sweep

---

## Dependencies & Execution Order

- **Phase 2 (US4)** BLOCKS Phases 3 & 5 (dashboard edits need repo source + proven deploy path)
- **Phase 3 (US1)** and **Phase 4 (US2)** are independent (different artifacts: Worker vs Android) — but executed sequentially here (single implementer); US1 first so the prevention ships even if device re-test lags
- **Phase 5 (US3)** trivial, bundled with next dashboard deploy (T018 rides along T011's successor — deploy once after T007-T010 + T017 together; T011 is then the combined verification)
- **Phase 6** after all stories verified

### Parallel Opportunities

- T003/T005 [P] different files (conversion vs deploy script) — can interleave
- T012-T014 [P] touch different functions/files in the same module — same-commit sequence
- T019/T020 [P] different files

## Notes

- Deploy cadence: smoke-deploy once (T006, unmodified), then ONE feature deploy after T007-T010+T017 (verified by T011+T018 merged) — minimizes live-service churn
- Rollback: archived pre-change bundle + git history of `cloud/`
- The device end-to-end (plan step 3) is user-assisted QA on v1.3.2-test3; findings feed spec-001's E2E roadmap item
