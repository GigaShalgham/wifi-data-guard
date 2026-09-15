# Wi-Fi Data Guard Constitution

> Ratified 2026-09-15 by the project owner (GigaShalgham).
> This document governs ALL work on this repository. Every agent (AI or human)
> MUST read it before touching anything, and MUST follow it in every step.

## Core Principles

### I. Spec-Driven Development Is Mandatory (NON-NEGOTIABLE)

Every change to this project — feature, bugfix, refactor, release, docs, or
infrastructure — MUST go through the Spec Kit workflow, in order:

`specify → clarify (if ambiguity) → plan → tasks → analyze (recommended) → implement → converge`

No step may be skipped because a change "is small". Small changes get small
specs, not zero specs. If the sandbox has been reset and the `specify` CLI is
missing, reinstall instantly with `uv tool install specify-cli` (or use
`uvx --from specify-cli specify <cmd>`) — a missing tool is never a reason to
skip the process. Specs live in this repo, so they are the single source of
truth that survives any environment reset.

### II. Fail-Closed Safety (NON-NEGOTIABLE)

This is a parental-control safety tool. When the system cannot verify state
(cloud unreachable beyond `offline_tolerance_min`, clock tampering detected,
unknown errors), the device MUST lock, not stay open. Availability is never
traded for safety. Any feature that would "fail open" is rejected at spec time.

### III. Privacy By Scope

The app monitors data-usage counters and enforcement state ONLY. No browsing
history, no content inspection, no location. The cloud link carries the
minimum: usage totals, limit config, lock/unlock commands. Any spec proposing
broader data collection is rejected.

### IV. Secrets Never Leak

Tokens (GitHub, Cloudflare), keystores, and credentials are NEVER committed,
logged, or echoed into specs/reports. Keystore files stay gitignored. If a
secret appears in any artifact, the artifact is rewritten, not the .gitignore.

### V. Bilingual Truth

User-facing strings ship in English AND Persian (Farsi). Status lines and
alerts must mean the same thing in both languages. The Persian rendering is a
first-class deliverable, not an afterthought.

### VI. Testable Time

All time-dependent behavior routes through `AppClock`. In release builds it is
the real clock; in QA builds (`.test` applicationId) it is virtual and
acceleratable (1x–3600x). Child-waits-it-out deadlines (grace, rollover,
cooldowns) run VIRTUAL; connectivity deadlines (offline tolerance, poll
interval) stay REAL so acceleration never fakes "offline". Release builds must
be behaviorally identical to pre-AppClock code.

### VII. Control Channel Survives Locks

The guard app's own traffic (cloud poll/pair, DNS) MUST bypass its own VPN
tunnel (`addDisallowedApplication`). A hard lock that kills remote unlock is a
deadlock bug of the highest severity (fixed in v1.3.1; guard with specs and
tests in any future networking change).

### VIII. Honest UI

The device must tell the truth about its state: soft-lock VPN fallback
announces itself; the status line names the real latch reason; the dashboard
reflects actual last-reported state, not optimistic state.

### IX. Offline-First Architecture

The app is fully functional with no cloud. Cloud is an optional enhancement
(v1.3+), never a dependency. Local enforcement (watchdog, rollover, PIN) must
work with zero connectivity forever.

### X. Signed, Versioned, Reproducible Releases

Every shipped APK is release-signed, versioned (`versionCode` monotonic),
tagged in git, and published as a GitHub Release with notes. Test builds are
pre-releases with `.test` applicationId so they install alongside production.

## Maintenance

Update this constitution via `/speckit-constitution` when principles evolve.
Existing specs inherit the constitution in force when they were written;
conflicts resolve in favor of the newer constitution only after re-planning.
