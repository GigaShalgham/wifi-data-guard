# Implementation Plan: Safe Unpair — no more locked-orphan trap

**Branch**: `002-safe-unpair` | **Date**: 2026-09-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-safe-unpair/spec.md`

## Summary

Fix the revoke-while-locked trap with a two-sided defense, without weakening fail-closed:
(1) **Dashboard** — lock-specific bilingual warning before unpairing a locked device + move the Unpair control out of the Lock/Unlock button row + empty-state re-pair hint; (2) **Android app** — on receiving revocation while latched for a cloud-origin reason, keep the latch but relabel it ("unpaired") and fire a one-time bilingual notice explaining the parent-PIN escape hatch. Additionally, the lost cloud Worker source is recovered from the live deployed bundle into `cloud/` in this repo, converted to a readable single-file ES module, with a scriptable API deploy path — making this the last recovery-from-bundle ever needed.

## Technical Context

**Language/Version**: Kotlin (Android, AGP 9, minSdk 26); JavaScript ES module (Cloudflare Worker, compat date 2024-09-23)

**Primary Dependencies**: AndroidX/none-new (HttpURLConnection networking as-is); Worker: D1 (`DB`), KV (`KV`), plain-text var `BOOTSTRAP_EMAIL`; deploy via Cloudflare REST API multipart PUT (no wrangler needed)

**Storage**: D1 database `dataguard` (devices/commands/usage_reports/audit_log — NO schema changes); Android SharedPreferences (existing keys + `latch_reason` new value `"unpaired"`)

**Testing**: QA build type (`com.example.wifidataguard.test`) with test panel (force-poll button, verbose logs); live Worker verification via HTTP probes + D1 reads; end-to-end re-pair flow on device

**Target Platform**: Android 8+ (release + QA channels); Cloudflare Workers (single script `wifi-data-guard`)

**Project Type**: mobile-app + web-service (existing)

**Performance Goals**: no regressions — poll path stays ~40 ms p50; dashboard asset sizes unchanged (≤ +1 KB for new strings/styles)

**Constraints**: fail-closed preserved (revoke ≠ unlock); no endpoint behavior changes (FR-008); no secrets in repo; bilingual strings mandatory; D1 schema untouched

**Scale/Scope**: 1 Android app module (4 files touched), 1 Worker file (dashboard JS + CSS strings only), ~6 new i18n strings per language, versionCode 6 / versionName 1.3.2

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Article | Status | How this plan complies |
|---------|--------|------------------------|
| I. Spec-driven mandatory | ✅ | This spec/plan/tasks flow; cloud source enters the repo (specs reference reality) |
| II. Fail-closed | ✅ | Revocation keeps the latch; lock survives midnight rollover (reason `"unpaired"` is not in the rollover-clear set {limit, ""}) |
| III. Privacy by scope | ✅ | No new data collected; warning uses already-reported `latched` flag |
| IV. Secrets never leak | ✅ | `cloud/` ships source + deploy script reading token from file at runtime; bindings documented by name/ID only (IDs are not secrets) |
| V. Bilingual truth | ✅ | New strings in EN+FA on both sides, equivalent meaning |
| VI. Testable time | ✅ | No clock-dependent new logic; notice fires on poll receipt (real-time by nature); QA force-poll covers it |
| VII. Control channel survives locks | ✅ | No VPN/networking changes; poll path untouched |
| VIII. Honest UI | ✅ | Core of the fix: status stops claiming "Locked by parent" once the link is severed; dashboard warns before destroying remote control |
| IX. Offline-first | ✅ | Local-only mode unchanged; unpaired device remains fully functional locally |
| X. Signed, versioned releases | ✅ | versionCode 6 / versionName 1.3.2, both channels built, tagged, released |

## Project Structure

### Documentation (this feature)

```text
specs/002-safe-unpair/
├── spec.md              # This feature's specification
├── plan.md              # This file
└── tasks.md             # Task breakdown (/speckit-tasks output)
```

### Source Code (repository root)

```text
wifi-data-guard/
├── app/src/main/java/com/example/wifidataguard/
│   ├── CloudLink.kt          # unpair(): detect latched+cloud-reason, relabel, notify
│   ├── WatchdogService.kt    # statusLine(): "unpaired" wording; companion notify fn
│   └── MainActivity.kt       # (no functional change expected; only if dialog text needs sync)
├── cloud/                    # NEW — recovered Worker source (was lost in reset)
│   ├── worker.js             # single-file ES module (readable, raw UTF-8 strings)
│   ├── README.md             # structure, bindings, deploy & verify instructions
│   └── deploy.py             # multipart PUT via CF API (token from file, never in repo)
└── scripts/ (dev sandbox)    # d1 queries, release helpers (outside repo)
```

**Structure Decision**: The Worker stays a **single-file ES module** (`cloud/worker.js`) rather than re-creating a multi-file esbuild project. Rationale: the deployed bundle is already one module; a flattened readable file removes build tooling entirely, deploy is a direct API PUT of that one file, and future edits are single-file diffs. The original multi-file layout bought nothing (the bundle was the only artifact that survived — exactly the failure mode this repo-centric shape eliminates).

## Key Technical Decisions

1. **Recovery = unescape, not rebuild**: the bundle's template-literal strings (DASHBOARD_JS, DASHBOARD_CSS, DASHBOARD_HTML, DEMO_HTML, SW_JS, ICON_SVG) are converted to raw UTF-8 (Persian as real characters, not `\uXXXX`), everything else kept byte-equivalent. Correctness gate: converted DASHBOARD_JS must equal the live-served `/app.js` (diff must be empty).
2. **Notice delivery on the 401 path**: `CloudLink.unpair(fromServer=true)` runs on the poll executor thread; it writes `latch_reason="unpaired"` via Prefs (thread-safe `apply()`), and posts the bilingual high-priority notification directly (NotificationManager is thread-safe; "alerts" channel already exists via GuardApp). Status line picks up the new reason on the next watchdog tick (~1 s). No main-thread coupling needed.
3. **Reason scope for relabel**: `{"cloud", "offline", "clock"}` — all cloud-pairing-origin reasons. A `"limit"` latch keeps its wording (pure local reason, still true after unpair). No notice when not latched (FR-004).
4. **Dashboard layout**: Unpair moves to its own visually-separated row at the card bottom (border-top divider, right-aligned, small ghost-danger style) — structural separation, not just spacing. Confirm text branches on `d.report.latched`.
5. **Deploy via REST API**: `PUT /accounts/{acc}/workers/scripts/wifi-data-guard` with multipart: `metadata` part (main_module, compatibility_date 2024-09-23, bindings echoed from the live settings endpoint, observability on) + `worker.js` module part. No wrangler install, no login dance; token read from `/home/z/my-project/.cf-token` at runtime. Post-deploy verification checklist (below) gates the Android release step.
6. **Versioning**: versionCode 6, versionName 1.3.2, UA `DataGuard-Android/1.3.2`. Ship BOTH channels: production release `v1.3.2` (this also settles old roadmap item "v1.3.1 never got a release page" — 1.3.2 supersedes) and prerelease `v1.3.2-test3` with the QA time-machine APK for the user's device re-test.

## Rollout / Verification Plan

1. Deploy Worker → verify: `/` 200 & HTML served; `/app.js` 200 & contains `revokeConfirmLocked`; `/styles.css` 200 & contains `.sep`; `/demo` 200; `POST /api/pair-code` unauth → 401; poll endpoint unauth → 401; D1 `devices` row count unchanged; existing behavior spot-check (dashboard JS fetch + no console syntax errors — node `--check` on the extracted dashboard JS).
2. Build Android (qa + release) → verify signature SHA-256 c8abfd91…, badging versionCode=6 versionName=1.3.2, package ids.
3. End-to-end on device (user-assisted, QA build): pair → remote lock → revoke while locked → force poll → expect: stays locked, status "Locked — device unpaired" (FA equivalent), one-time notice; then PIN unlock works; re-pair restores the card in < 2 min.
4. Rollback path: previous bundle saved at `dataguard-cloud-recovered/worker-download.bin` (and git tag of pre-change cloud state in repo history) — re-PUT the old module if anything regresses.

## Complexity Tracking

> No constitution violations — table not required.
