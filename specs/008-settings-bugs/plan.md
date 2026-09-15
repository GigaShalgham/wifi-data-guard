# Implementation Plan: Settings form stops reverting + unlock window becomes real

**Branch**: `008-settings-bugs` | **Date**: 2026-09-15 | **Spec**: `specs/008-settings-bugs/spec.md`

## Summary

Fix two owner-reported dashboard bugs with a **worker-only** change: (1) the per-device
Settings panel currently re-renders from server truth on every refresh (10 s heartbeat
+ post-command bursts + focus), silently destroying in-progress edits and letting stale
in-flight `/api/me` responses visually revert just-saved values — fixed by rendering
open panels from a per-device **draft** (`cfgDraft[id]`) that only the user (inputs) and
a successful Save update; (2) the timed-unlock button/label/confirm/toast hardcode
15 minutes and ignore the saved `unlock_minutes` setting — fixed by deriving all four
from the device's saved settings (clamped 1..480, 0/invalid → 15). Plus honest save
feedback (error state on failed POST, "syncing to device…" chip, device-confirmed toast
reusing spec-006 infra) and a SW cache bump `dg-v5 → dg-v6`.

## Technical Context

**Language/Version**: Cloudflare Worker (ES module, bundled single-file `cloud/worker.js`
with an inline `DASHBOARD_JS` template string + `DASHBOARD_CSS`); dashboard JS is
ES5-style vanilla (no framework, no build step on the browser side).

**Primary Dependencies**: Cloudflare Workers runtime, D1 (`devices.settings_json`,
`commands`, `audit_log`), KV rate limits. Dashboard: sessionStorage `dgPend`
(spec-004), toast/chime stack (spec-006), heartbeat/hot-mode (spec-005).

**Storage**: unchanged — `devices.settings_json` (already correct server-side; verified
against live D1), `commands` table for config delivery, no schema migration.

**Testing**: `node --check` on the module; extract+template-unescape `DASHBOARD_JS`
then `node --check` again (established `scripts/verify_spec00*.js` pattern); pure-function
unit tests for the new helpers (`minsFor()`, draft prune) in `scripts/verify_spec008.js`;
live deploy probes (markers in `/app.js`, `/api/me` 401 unauth, `dg-v6` in `/sw.js`);
agent-browser smoke (open settings → heartbeat fires → draft survives; save → chip →
toast). **No commands are ever sent to the owner's real device** (worklog standing rule).

**Target Platform**: parents' dashboard (any modern browser; EN+FA, RTL-safe).

**Constraints**: no app release (Art. XI — apps ≥ v1.3 already honor `minutes` 1..480);
all strings EN+FA (Art. V); honest UI (Art. VIII); edits via exactly-once asserted
Python edit scripts with atomic writes (established pattern, avoids heredoc quoting bugs).

## Constitution Check

| Article | Status |
|---|---|
| I Spec-driven | This spec/plan/tasks flow |
| II Fail-closed | Untouched — enforcement paths unchanged; config remains parent-authoritative |
| III Privacy | No new data; same fields |
| IV Secrets | Edit script reads tokens from disk only; never echoed |
| V Bilingual | All new strings EN + FA (Persian digits for FA numerals) |
| VI Testable time | No time logic changed (draft TTL = panel open lifetime) |
| VII Control channel | Untouched |
| VIII Honest UI | **Improved** — core of this spec |
| IX Offline-first | Untouched (app unchanged) |
| X Releases | Worker-only: no tag/APK; version-history row in README |
| XI Disclosure | Worker-only → live on deploy; **no app update needed** |

## Project Structure

```text
cloud/worker.js                    # ALL code changes (DASHBOARD_JS i18n + render + wire, SW dg-v6)
specs/008-settings-bugs/           # this spec (spec/plan/tasks/checklists)
scripts/edit_worker_spec008.py     # exactly-once asserted edits, atomic write
scripts/verify_spec008.js          # extraction + syntax + pure-function unit tests
README.md                          # version-history row (no app release)
specs/001-project-state-backfill/spec.md  # deployed-state row + roadmap renumber
cloud/README.md                    # unlock_minutes semantics note
```

**Structure Decision**: single-file worker edit per the established spec-004/005/006
pattern (asserted, atomic, reviewable diff before deploy).

## Design Decisions

1. **Draft model, not DOM preservation.** Alternative considered: capture/restore input
   values + focus around each `updateDynamic()` rebuild. Rejected — fragile against the
   stale-response race (a restore would re-apply the user's value over a *newer* server
   truth with no way to tell them apart) and against select elements. The draft makes
   the open panel a single source of truth owned by the user; the server truth lands
   next time the panel opens (or after Save). Card chrome (badges/usage) keeps
   re-rendering live as today.
2. **Draft lifecycle**: created lazily by `settingsPanel(d)` when the panel is open and
   no draft exists; updated by `input`/`change` listeners wired in `wireDevice(d)`;
   deleted on panel close (the same `data-settings` toggle) and pruned for ids absent
   from `/api/me` in `updateDynamic()`. No TTL needed (open panel = live draft; the
   90 s pend TTL pattern does not apply — a draft is not a command).
3. **Save path**: read the draft (not the DOM) → POST → on `res.ok === true` show
   "Saved — syncing to device…" (FR-004 i18n `savedSyncing` replaces the old `saved`
   copy) and set `cfgSync[id] = {until: Date.now() + 90s}`; on anything else render
   `saveFailed` in red. `pending_types.config` (server truth, exists since spec-004)
   drives the card chip; toast + chime fire from `toastConfirmed()` when a tracked
   sync's pending count drops to 0 (Art. VIII: device-confirmed, never optimistic).
4. **Unlock minutes**: `minsFor(s) = (s.unlock_minutes >= 1 && s.unlock_minutes <= 480)
   ? s.unlock_minutes : 15` — pure, unit-tested, reused by button label, confirm text,
   payload, and the timed toast (`pend[id].mins` recorded at click time so the toast
   says what was actually granted even if the setting changes afterwards).
5. **Strings**: new keys `unlockFor` (EN "Unlock %dm" / FA "باز %d دقیقه‌ای" with
   Persian-digit conversion), `confirmUnlockFor`, `savedSyncing`, `saveFailed`,
   `settingsSync` chip, `toastSettings`; `unlock15`/`confirmUnlock`/`saved` retired.
   FA numerals via a tiny `faNum()` helper (Western → Persian digits), matching the
   app's FA convention.
6. **SW bump** `dg-v5 → dg-v6` (app.js changed; stale edges self-clear in ~6 s as
   observed in spec-004/006 deploys).
7. **No server-side change**: `handleCommand` already persists config before queuing
   the command and clamps minutes; `/api/me` already returns `pending_types`.

## Complexity Tracking

No constitution violations.
