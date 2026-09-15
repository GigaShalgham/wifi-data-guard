# Tasks: 004-dashboard-pending-truth

## 1. Spec

- [x] T001 D1 evidence pull + code read; root causes confirmed (old app v1.3.2 + browser-only pend)
- [x] T002 spec.md (4 user stories, FR-001..007, failure modes)
- [x] T003 plan.md (decisions incl. sessionStorage choice, oldApp flag, additive API)

## 2. Constitution & repo law

- [x] T004 Add Article XI (App-Update Disclosure) to .specify/memory/constitution.md
- [x] T005 README: document the disclosure rule + v1.3.3-dashboard note

## 3. Implementation (cloud/worker.js via exactly-once edit script)

- [x] T006 i18n: `oldApp` badge string EN + FA (escapes)
- [x] T007 pend persistence: savePend()/restore, all mutation sites covered (3 taps, expiry, confirm)
- [x] T008 pendConfirmed: old-app full unlock confirms on grace (degraded confirm)
- [x] T009 appOld(rep) version gate (< 1.3.3, parseable only)
- [x] T010 deviceCard: type-aware server pending chip + old-app badge
- [x] T011 wireDevice: full-unlock pend carries oldApp flag
- [x] T012 trackRefresh burst +75 s, +90 s
- [x] T013 SW cache dg-v3 → dg-v4
- [x] T014 handleMe: pending_types (group by device_id, type), keep pending_commands

## 4. Verification

- [x] T015 node --check on worker.js module
- [x] T016 node --check on extracted+unescaped DASHBOARD_JS + 12+ marker greps
- [x] T017 Unit test appOld + pendConfirmed (Node, version/outcome matrix)
- [x] T018 Deploy via cloud/deploy.py + live probes (markers, dg-v4, endpoints, auth)

## 5. Converge

- [x] T019 spec-001 roadmap renumber (005 security, 006 play-store, 007 e2e) + deployed state
- [x] T020 Commit + push (no tag: worker-only, no app release)
- [x] T021 Worklog append + user report with explicit app-update verdict
