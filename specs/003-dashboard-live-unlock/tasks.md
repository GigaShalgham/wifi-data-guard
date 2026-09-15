# Tasks: Dashboard live truth & real unlock

**Input**: Design documents from `/specs/003-dashboard-live-unlock/`

**Prerequisites**: plan.md (required), spec.md (required for user stories)

**Tests**: No automated test framework in repo; verification is `node --check`, live
probes post-deploy, and QA-build device test (per plan.md Rollout/Verification).

**Organization**: Phase 1 spec scaffolding → Phase 2 Worker (dashboard + API) →
Phase 3 Android → Phase 4 deploy & verify → Phase 5 release & converge.

## Phase 1: Setup

- [x] T001 Create `specs/003-dashboard-live-unlock/` with spec.md, plan.md, tasks.md, checklists/requirements.md

## Phase 2: Worker — full unlock, pending state, live panel (FR-001..005, FR-008)

**Goal**: Deployed dashboard shows state-aware buttons, full unlock command, pending chips, and self-refreshes
**Independent Test**: `node --check` passes; after deploy, `/app.js` contains `dg-v3`, `full: true`, `pendingLock`, visibility handler

- [x] T002 [FR-001] `handleCommand()`: unlock branch accepts `payload.full === true` → store `{full:true}`; minutes path unchanged
- [x] T003 [FR-Vocab] DASHBOARD_JS i18n: add EN+FA — unlockFull, confirmUnlockFull, pendingLock, pendingUnlock, waitDevice (+ needs-app note folded into confirmUnlockFull)
- [x] T004 [FR-004] Pending machinery: `pend` map + `pendFor()` + `pendConfirmed(rep,p)` + TTL 90 s; `refresh()` clears confirmed pends before render
- [x] T005 [FR-002] `deviceCard()`: state-aware button row (locked/pending-lock → Unlock full + Unlock 15m; else Lock now); pending chip replaces pending_commands badge while a local action is in flight
- [x] T006 [FR-002/004] `wireDevice()`: wire `data-unlock-full`; set pend + immediate `updateDynamic()` on send; disable all action buttons while pending
- [x] T007 [FR-005] Refresh cadence: 30 s → 10 s interval; `visibilitychange` + `focus` listeners; `trackRefresh()` staggered burst (1/5/15/30/45/60 s) after commands (lock/unlock/full/save)
- [x] T008 [FR-008] SW_JS cache bump `dg-v2` → `dg-v3`
- [x] T009 Verify: `node --check cloud/worker.js` (module) passes; grep guards: each new string present exactly once in both language blocks

## Phase 3: Android v1.3.3 — full unlock semantics + fast confirm (FR-006..008)

**Goal**: App clears cloud-origin latches on full unlock, degrades honestly otherwise, and confirms commands fast
**Independent Test**: assembleQa + assembleRelease build; badging versionCode 7 / 1.3.3; code review against plan decisions 1 & 5

- [x] T010 [FR-006] `applyCloudCommands()` unlock branch: `payload.full` → clear latch when reason is cloud/offline (unlatch + grace zero + bilingual notice); else grace fallback + bilingual fallback notice
- [x] T011 [FR-007] Confirmation poll: `confirmPoll` runnable (+4 s) scheduled after any applied command batch; rides existing self-exempted channel
- [x] T012 [FR-008] Version bump: gradle versionCode 7 / versionName 1.3.3; CloudLink UA `DataGuard-Android/1.3.3`
- [x] T013 Build both variants; verify signatures + badging

## Phase 4: Deploy & live verification

- [x] T014 Deploy via `cloud/deploy.py`; archive pre-deploy bundle for rollback
- [x] T015 Live probes: `/app.js` new strings + `dg-v3`; `GET /` 200; unauth poll 401; SW header serves new cache name
- [x] T016 D1 evidence: send no test commands against the real device; leave command-flow verification to the user's QA build test (documented)

## Phase 5: Release & converge

- [x] T017 README: v1.3.3 row + dashboard live-state description
- [x] T018 spec-001 roadmap renumber (004 security hardening, 005 Play hardening, 006 E2E)
- [x] T019 Commit + push; tags v1.3.3 + v1.3.3-test4; releases with APKs (release script pattern from v1.3.2)
- [x] T020 Worklog + spec status update
