# Implementation Plan: Dashboard live truth & real unlock

**Feature**: specs/003-dashboard-live-unlock/spec.md
**Created**: 2026-09-15

## Summary

Close the loop between "parent taps a button" and "phone changes state" with (a) a
real full-unlock command end-to-end, (b) state-aware dashboard buttons with an
optimistic pending state, and (c) a dashboard that keeps itself current (10 s
auto-refresh, focus/visibility refresh, staggered post-command refresh burst) plus
an app-side confirmation poll ~4 s after applying any command.

## Technical Context

- Worker is the single-file module `cloud/worker.js` (deployed via `cloud/deploy.py`,
  bindings echoed from live settings — spec-002 established this pipeline; rollback
  archive procedure unchanged).
- Dashboard JS lives inside `DASHBOARD_JS` (template literal in `cloud/worker.js`);
  i18n via `I18N` map (en/fa); cards rendered by `deviceCard()` + wired by
  `wireDevice()`; state source is `/api/me` (`me.devices[].report.latched`,
  `.pending_commands`).
- Command path: dashboard → `POST /api/devices/:id/command` → `handleCommand()`
  (validates payload, inserts row) → device poll (`handleChildPoll`) delivers →
  `WatchdogService.applyCloudCommands()` applies + `CloudLink.ack()` on next poll.
- App: `WatchdogService.latched` is the live flag; companion `unlatch(c)` already
  releases VPN + restores Wi-Fi + clears prefs; `CloudLink.forcePoll()` exists
  (test-panel path, safe for production use).
- Service worker `SW_JS` uses `skipWaiting()` + `clients.claim()` — bumping
  `CACHE` to `dg-v3` force-rolls the new dashboard to every open tab.

## Constitution Check

- **Art. II (fail-closed)**: full unlock clears only `cloud`/`offline` latches. Limit
  and clock latches degrade to the timed window — the data limit and the tamper
  defense are never bypassed by a remote command.
- **Art. V (bilingual)**: every new dashboard string and every new Android
  notification has EN+FA forms.
- **Art. VII (control channel survives locks)**: the confirmation poll rides the
  VPN-self-exempted path added in v1.3.1.
- **Art. VIII (honest UI)**: pending chips say "waiting for device"; timeout returns
  to reported truth; fallback notifications explain *why* a full unlock degraded.
- **Art. IV (secrets)**: no new secrets; deploy uses the stored token file.

## Project Structure

### Documentation (this feature)
- `specs/003-dashboard-live-unlock/spec.md` — this feature's contract
- `specs/003-dashboard-live-unlock/plan.md` — this file
- `specs/003-dashboard-live-unlock/tasks.md` — execution checklist
- `specs/003-dashboard-live-unlock/checklists/requirements.md` — quality gates
- `specs/001-project-state-backfill/spec.md` — roadmap renumber (004 security hardening, 005 Play hardening, 006 E2E)

### Source Code (repository root)
- `cloud/worker.js` — DASHBOARD_JS (i18n, pending machinery, state-aware buttons,
  refresh cadence), handleCommand (full flag), SW_JS (dg-v3)
- `app/src/main/java/com/example/wifidataguard/WatchdogService.kt` — unlock full
  semantics + confirm-poll scheduling
- `app/src/main/java/com/example/wifidataguard/CloudLink.kt` — UA version string
- `app/build.gradle.kts` — versionCode 7 / versionName 1.3.3
- `README.md` — v1.3.3 row + dashboard behavior notes

## Key Technical Decisions

1. **Full unlock = clear cloud-origin latch only** (`cloud`, `offline`), via the
   existing `unlatch()` companion. Anything else (limit/clock/unpaired) falls back
   to the timed grace window with an explanatory notification. Rationale: the
   parent's authority is over *their* lock; the device's own limit and the tamper
   defense outlive it (Art. II).
2. **Optimistic pending state lives in the browser only** (`pend` map, per device,
   90 s TTL). Confirmed when a report matches the intent; never persisted — the
   server already owns truth via `pending_commands` and report fields.
3. **Timed-unlock confirmation rule**: report shows `grace_until > now` (latch
   kept); full-unlock rule: `latched == false`; lock rule: `latched == true`.
4. **Refresh cadence 10 s** (not shorter): `/api/me` is a cheap D1 read (p50 ~40 ms);
   10 s is imperceptible vs. the ~30 s device poll and kind to the free tier.
5. **Confirmation poll at +4 s** on the app side: balances "dashboard sees it fast"
   against hammering the Worker; one extra poll per applied command only.
6. **No poll-interval change**: battery stays as-is; liveness comes from the
   dashboard and the one-shot confirmation poll.
7. **Backward compatibility by field ignorance**: v1.3.2 apps read
   `payload.minutes` (absent → default 15) and ignore `full`. No version gate
   needed server-side; the dashboard confirm dialog tells the parent about the
   v1.3.3 requirement.

## Rollout / Verification Plan

1. Edit `cloud/worker.js`; `node --check` (module mode) must pass.
2. Deploy via `cloud/deploy.py`; live probes: `/app.js` contains `dg-v3` +
   `full:true` command path + pending strings; `GET /` 200; unauth `POST
   /api/child/poll` 401; dashboard HTML loads.
3. Build `assembleQa` + `assembleRelease`; verify signatures + badging
   (versionCode 7 / 1.3.3 / 1.3.3-test, packages).
4. Commit + push; tags `v1.3.3` and `v1.3.3-test4`; GitHub releases with APKs.
5. User device test on the QA build (US1/US2/US3 flows); results feed the E2E
   verification spec.
6. Rollback: worker rollback archive procedure (spec-002), app = previous tag.

## Complexity Tracking

- Metric: files touched — 6 (worker, WatchdogService, CloudLink, gradle, README, spec-001)
- Metric: new user-visible strings — 10 EN + 10 FA
- Complexity score: **Medium** (surgical edits to well-understood code paths, no
  schema or infra change, additive protocol flag)
