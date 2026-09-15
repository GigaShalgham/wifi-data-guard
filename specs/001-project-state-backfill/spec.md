# Feature Specification: Project State Backfill & Roadmap (v1.3.1 baseline)

**Feature Branch**: `001-project-state-backfill`

**Created**: 2026-09-15

**Status**: Approved (backfill — records what already exists; future changes get their own specs)

**Input**: User directive: "Use Spec Kit every time and forever in every step of the wifi-data-guard project." This spec is the entry point that future sessions read first.

## User Scenarios & Testing

**P1 — A future agent session (possibly after a full sandbox reset) opens this repo
and needs complete, accurate context to continue work safely.**
Given the agent reads `specs/001-project-state-backfill/spec.md` and
`.specify/memory/constitution.md`, it knows: what is deployed and where, what
is verified vs. assumed, what the known bugs/limitations are, and which spec
to pick up next — without needing any conversation history.

**P2 — The project owner asks for "the next step" and gets a grounded answer.**
The Roadmap section below ranks pending work; the agent starts the top item
via `/speckit-specify`, not from memory.

## Current Deployed State (verified 2026-09-15)

### Android app (this repo)
- **Latest release**: v1.3.2 (versionCode 6) — safe-unpair (spec-002); tags exist for v1.0–v1.3, v1.3-test1, v1.3-test2, v1.3.2, v1.3.2-test3
- **Architecture**: single-module Kotlin app; `WatchdogService` (periodic watchdog + enforcement), `BlockerVpnService` (VPN-based internet blocking with self-exemption), `CloudLink` (optional cloud pairing/poll/commands), `AppClock` + `TestMode` (QA time machine, test builds only), `OwnerEnforcer`, `Prefs`, `Logger`, `LiveCounter`
- **Signing**: v1.3+ keystore (SHA-256 c8abfd91…), backup in `download/wifidataguard-signing-v13/` on the dev sandbox (NOT in repo, per Constitution IV). The pre-v1.3 keystore was lost in a sandbox reset.
- **QA channel**: `qa` build type → `com.example.wifidataguard.test` (installs alongside production), test panel with 1x–3600x virtual clock, usage injection, offline simulation, forced polls, verbose logs

### Cloud backend (Cloudflare Worker, separate from this repo)
- **Live**: https://wifi-data-guard.gigaspaceturnip.workers.dev (D1 `dataguard`, KV `dataguard-kv`)
- **Source**: IN THIS REPO at `cloud/worker.js` since 2026-09-15 (spec-002) — recovered from the deployed bundle, validated byte-exact, deployable via `cloud/deploy.py`; never lose it again
- **Endpoints**: pair (6-digit code), poll (auth via device token), dashboard (`/`, `/demo`), revoke
- **Benchmark** (2026-09-15): poll p50 ≈ 42 ms / p95 ≈ 58 ms; pair p50 ≈ 515 ms
- **⚠️ `/demo` simulator is still live** — must be removed before real production use (Roadmap item A)

### Verified working (user device tests, v1.3-test1/2 rounds)
- Pairing, config push, remote lock/unlock delivery (16–28 s typical, poll interval + jitter)
- Remote unlock DURING a hard VPN lock (v1.3.1 fix — control-channel survival)
- Fail-closed on simulated offline; clock-rollback detection; PIN-gated unpair; dashboard revocation
- Soft-lock VPN fallback announces itself (bilingual)
- Safe unpair (v1.3.2): dashboard lock-aware revoke warning + separated Unpair button; unpaired-while-locked device relabels status and shows the parent-PIN notice

### Known limitations / accepted trade-offs
- Config latency = poll interval (default ~30 s) + jitter — by design (battery), documented
- Clock-tamper defense requires cloud pairing (local-only has no trusted time source)
- Hard mode (Wi-Fi off) needs device-owner rights; without them it falls back to full-tunnel VPN block
- workers.dev bot filter blocks default python-urllib UA (app's custom UA passes; benchmarks must send a browser-ish UA)

## Roadmap (each item = one future spec, in priority order)

| # | Spec | Why | Source |
|---|------|-----|--------|
| A | `003-remove-demo-endpoint` | Kill the public `/demo` simulator before real families use the Worker | Task 8/11 notes |
| B | `004-play-store-hardening` | allowBackup=false audit, exported components, targetSdk policy, privacy declaration | Task 8 notes |
| C | `005-e2e-verification-v1.3.2` | Device re-tests of v1.3.2-test3: safe-unpair flow (lock → revoke → notice → PIN unlock → re-pair) + carry-over TCs from v1.3-test2 | Task 11 + spec-002 |
| ✓ | ~~`002-safe-unpair`~~ DONE (v1.3.2, 2026-09-15) | Revoked-while-locked trap: warning, separation, honest relabel, PIN notice, cloud source in repo | user bug report |

## Requirements

1. **REQ-001**: No new feature/bugfix work starts outside the Spec Kit flow (Constitution I).
2. **REQ-002**: Every future spec's `spec.md` must state which Constitution articles it touches (esp. II fail-closed, VI testable time, VII control channel).
3. **REQ-003**: This file is updated whenever deployed state changes (new release, Worker change, endpoint removal) — it is the durable "where are we" record, the in-repo equivalent of the sandbox worklog.

## Implementation Notes

- This spec intentionally has **no tasks.md / plan.md**: it records existing reality, it is not built.
- Dev sandbox context (worklog, tokens, keystores) lives OUTSIDE the repo and is ephemeral; this file is the in-repo replacement for that memory.
