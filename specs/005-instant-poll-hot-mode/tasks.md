# Tasks: 005-instant-poll-hot-mode

## 1. Spec
- [x] T001 Read app poll architecture (1 s tick → pollIfDue → serial executor)
- [x] T002 spec.md (4 user stories, FR-001..008, failure modes, Art. XI verdict)
- [x] T003 plan.md (server-gated hold design, D1-over-KV, battery caps)

## 2. Database
- [x] T004 Idempotent D1 migration script (PRAGMA check + ALTER devices ADD hot_until, last_wait_poll_at)

## 3. Worker (scripts/edit_worker_spec005.py, exactly-once)
- [x] T005 handleMe: stamp hot_until=now+75s (parent's non-revoked devices), return per-device fast flag
- [x] T006 handleCommand: extend hot_until to now+120s (lock/unlock/config)
- [x] T007 handleChildPoll: parse wait (clamp 0..25), stamp last_wait_poll_at, hold loop while hot (1.5 s checks), response fast flag
- [x] T008 Dashboard JS: ⚡ fast chip (EN+FA), pause 10 s heartbeat while document.hidden
- [x] T009 cloud/README.md: wait param + columns + hot semantics

## 4. Verification
- [x] T010 node --check module + extracted DASHBOARD_JS + marker greps
- [x] T011 Node unit tests: wait clamp + hold-eligibility logic
- [x] T012 Migrate D1 (live), then deploy worker, live probes (401s, markers, health)

## 5. App v1.3.4 (scripts/edit_android_spec005.py)
- [x] T013 CloudLink: wait=20 on every poll, 35 s read timeout for wait polls, fast pacing (1 s, no jitter), 90 s re-arm, 30 min cap, UA 1.3.4
- [x] T014 gradle versionCode 8 / versionName 1.3.4
- [x] T015 Build + sign QA & release, verify badging/signatures

## 6. Converge
- [x] T016 spec-001 roadmap renumber + deployed state; README rows
- [x] T017 Commits (worker/app per spec), tags v1.3.4 + v1.3.4-test5, GitHub releases
- [x] T018 Worklog append + owner report with Art. XI verdict
