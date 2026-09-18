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

## Current Deployed State (verified 2026-09-16)

### Android app (this repo)
- **Latest release**: v1.4.1 (versionCode 12) — restart-safe period rollover (spec-012); tags exist for v1.0–v1.3, v1.3-test1/2, v1.3.2, v1.3.2-test3, v1.3.3, v1.3.3-test4, v1.3.4, v1.3.4-test5, v1.3.5, v1.3.5-test6, v1.3.6, v1.3.6-test7, v1.4.0, v1.4.0-test8, v1.4.1, v1.4.1-test9
- **Architecture**: single-module Kotlin app; `WatchdogService` (periodic watchdog + enforcement), `BlockerVpnService` (VPN-based internet blocking with self-exemption), `CloudLink` (optional cloud pairing/poll/commands), `AppClock` + `TestMode` (QA time machine, test builds only), `OwnerEnforcer`, `Prefs`, `Logger`, `LiveCounter`
- **Signing**: v1.3+ keystore (SHA-256 c8abfd91…), backup in `download/wifidataguard-signing-v13/` on the dev sandbox (NOT in repo, per Constitution IV). The pre-v1.3 keystore was lost in a sandbox reset.
- **QA channel**: `qa` build type → `com.example.wifidataguard.test` (installs alongside production), test panel with 1x–3600x virtual clock, usage injection, offline simulation, forced polls, verbose logs

### Cloud backend (Cloudflare Worker, separate from this repo)
- **Live**: https://wifi-data-guard.gigaspaceturnip.workers.dev (D1 `dataguard`, KV `dataguard-kv`)
- **Deployed as of 2026-09-18 (spec-012, SW dg-v9)**: honest full unlock (truthful confirm + window-landing confirmation + "N-min window granted" toast for limit/clock latches), LOCKED badges carry the reason (limit/parent/offline/clock, EN+FA), grace wins over the latch in the card badge, report whitelist accepts `latch_reason`, revoke + 5 % sweep delete undeliverable commands, dead `attempts` check removed
- **Source**: IN THIS REPO at `cloud/worker.js` since 2026-09-15 (spec-002) — recovered from the deployed bundle, validated byte-exact, deployable via `cloud/deploy.py`; never lose it again
- **Endpoints**: pair (6-digit code), poll (auth via device token), dashboard (`/`, `/demo`), revoke
- **Benchmark** (2026-09-15): poll p50 ≈ 42 ms / p95 ≈ 58 ms; pair p50 ≈ 515 ms
- **⚠️ `/demo` simulator is still live** — must be removed before real production use (Roadmap item A, folded into 005 security hardening)
- **⚠️ Bootstrap sign-in fallback is live** — no RESEND_API_KEY, so the one-time link for BOOTSTRAP_EMAIL is shown on screen; that email is exposed via public commit metadata → dashboard takeover chain. Verified NOT exploited (D1 audit 2026-09-15). Fix = user picks a secret alias email (config swap) and/or spec-005 removes/hardens the fallback

### Verified working (user device tests, v1.3-test1/2 rounds)
- Pairing, config push, remote lock/unlock delivery (16–28 s typical, poll interval + jitter)
- Remote unlock DURING a hard VPN lock (v1.3.1 fix — control-channel survival)
- Fail-closed on simulated offline; clock-rollback detection; PIN-gated unpair; dashboard revocation
- Soft-lock VPN fallback announces itself (bilingual)
- Safe unpair (v1.3.2): dashboard lock-aware revoke warning + separated Unpair button; unpaired-while-locked device relabels status and shows the parent-PIN notice
- Real unlock + live dashboard (v1.3.3, deployed + app built): state-aware buttons, full-unlock command (clears cloud/offline latch; limit/clock degrade to timed window with notice), optimistic pending chips, 10 s + focus/visibility refresh, ~4 s confirmation poll — user device re-test pending
- Pending truth + old-app honesty (spec-004, worker-only, deployed 2026-09-15): pending state survives manual refresh (sessionStorage); old phone apps (< v1.3.3) get a persistent amber badge and their full-unlock pending confirms on grace landing (no 90 s freeze); `/api/me` exposes queued command types (type-aware server chip); refresh burst to 90 s; SW dg-v4. **No app release required** — but the phone still needs the v1.3.3 APK installed for full unlock to truly clear the latch (Art. XI disclosure)
- Instant commands (spec-005, deployed 2026-09-15 + app v1.3.4): hot-mode long-poll — the panel's visible-tab heartbeat (10 s `/api/me`) keeps devices hot 75 s; polls with `wait` (1–25 s) are held checking D1 every 1.5 s, so commands land ~1.5 s; `fast` flag in poll + `/api/me` (⌁ chip, honest — only when the device actually long-polls); command creation extends hot 120 s; app: 1 s fast pacing, 90 s re-arm, 30-min cap, 35 s read timeout; dashboard heartbeat pauses while hidden; D1 columns `devices.hot_until` + `last_wait_poll_at`
- Glass super-UI (spec-006, worker-only, deployed 2026-09-15): frosted-glass cards over indigo/cyan glow, sticky glass header, glowing badges, gradient buttons/bars, confirmation toasts (EN+FA) + WebAudio chime (lock/unlock tone pairs) + vibrate — fired only on real device confirmations; calm ~300 ms animations on initial render only; `prefers-reduced-motion` kill-switch; SW dg-v5
- Mobile glass super-UI (spec-007, app v1.3.5, 2026-09-15): the Android app gets the same design language — glow background + glass cards, status hero with glowing breathing dot (protected/grace/blocked), gradient buttons + threshold-colored gradient usage bar, glass dialogs + splash, state-change glass toasts + two-tone AudioTrack chime + vibration (EN+FA, dashboard-parity tone pairs), entrance-once animations with animator-scale reduce-motion kill-switch. **REQUIRES installing the v1.3.5 APK** (worker untouched)
- Settings fixes (spec-008, worker-only, deployed 2026-09-15): the open Settings panel renders from a per-device draft, so heartbeats/post-command refreshes/stale responses can no longer revert in-progress edits (root cause of the "changed 500→200 then it reverted" report — D1 showed the user re-saving the reverted value); focus + save-message preserved across re-renders; save button can't double-fire; failed saves show a red error instead of a lying "Saved"; save flow confirmed by the device ("Settings → device…" chip → "✓ Settings applied" toast + chime); the **Unlock window (min)** setting now actually drives the timed-unlock button/label/confirm/toast (minsFor clamp 1–480, 0/invalid → 15) instead of hardcoded 15; SW dg-v6. **No app release required** — every app ≥ v1.3 applies minutes 1–480. Known cosmetic limitation: the phone's unlock-duration spinner labels non-preset cloud values (e.g. 25) as the first preset while applying the real value — deferred to the next app-side spec
- **Launch-crash hotfix (spec-009, app v1.3.6, 2026-09-16)**: v1.3.5 crashed at cold start on every device — Kotlin inferred `Button` as the element type of the `glass.entrance(listOf(...))` view list (the only concretely-typed element), allocating a `Button[]` vararg array that threw `ArrayStoreException` when the LinearLayout cards were stored (reproduced with the project's first Robolectric launch smoke test; deterministic, pre-first-frame). Fixed by pinning every element to `View`; also hardened the spinner echo suppression order (arm before `setSelection`/`notifyDataSetChanged` — Robolectric exposed a `StackOverflowError` re-entrancy that OEM synchronous callbacks could trigger). **Launch smoke tests are now permanent** (`LaunchSmokeTest`: fresh install + armed kid's phone states; `isIncludeAndroidResources`). **REQUIRES installing the v1.3.6 APK** (worker untouched, dg-v6 live); updating over a crashing v1.3.5 preserves prefs/PIN/pairing, and the v1.3.5 crash entry remains visible in View logs for confirmation)
- **UX revolution (spec-010, app v1.4.0 + worker dg-v7, 2026-09-16)**: the app restructures into three tabs — Status (animated gradient usage ring + count-up, state pill, unlock button, checklist), Usage (count-up big number, stats grid left/limit/period-end/battery, 7-day history chart from the new device-authed `/api/child/history`, stale-cache-first, honest null/0 markers), Parent (one PIN gate per session; settings with unlock-duration CHIPS replacing the spinner — the whole spinner echo-hazard class is deleted; all tools). BottomNavigationView with a blocked badge, aurora background, tab cross-fades; MaterialComponents theme; reduce-motion safe throughout. Dashboard: the **Unlock window (min) settings field is REMOVED** — replaced by a point-of-use duration picker (chips 15/30/60/120 + custom 1–480, remembered per device via localStorage, seed chain remembered→setting→15, EN+FA with Persian digits); battery-line mojibake `ὐb`→🔋 fixed; SW dg-v7. History is cosmetic only (fetch failure never latches). **APP UPDATE REQUIRED for the app side; the dashboard side went live immediately on deploy**
- **Restart-safe rollover (spec-012, app v1.4.1, 2026-09-17)**: the daily-limit latch now releases after an overnight process restart — the rollover detector is persisted (`Prefs.period_start`), so a watchdog that comes up after midnight (reboot, dead battery, OEM killer, app update) processes the boundary identically to a live crossing; a limit latch releases only on an authoritative stats read (unreadable stats ⇒ latch kept + retried each tick — fail-closed, Art. II); security latches survive the boundary as before. Fixes the owner's v1.2 field report ("won't unlock tomorrow; I have to turn the enforce limit off and on again") — the timed-unlock half of that report was already fixed by spec-008/dg-v6. New permanent gate: `RolloverRestartTest` (6 Robolectric cases driving the real service cold start). **APP UPDATE REQUIRED (v1.4.1, versionCode 12); Worker/dashboard untouched (dg-v8)**

### Known limitations / accepted trade-offs
- Config latency = poll interval (default ~30 s) + jitter when idle — by design (battery). With app v1.3.4+ AND the panel visible, hot-mode long-poll cuts command delivery to ~1.5 s (spec-005)
- Clock-tamper defense requires cloud pairing (local-only has no trusted time source)
- Hard mode (Wi-Fi off) needs device-owner rights; without them it falls back to full-tunnel VPN block
- workers.dev bot filter blocks default python-urllib UA (app's custom UA passes; benchmarks must send a browser-ish UA)

## Roadmap (each item = one future spec, in priority order)

| # | Spec | Why | Source |
|---|------|-----|--------|
| ✓ | ~~`011-unlock-merge`~~ DONE (worker-only dg-v8, 2026-09-16) | "Unlock" + "⏱ Timed unlock" merged into ONE button: tap → ordered picker ladder (chips/custom/∞ full row), press-and-hold → instant remembered-minutes unlock | owner bug report ("can be merged with a special order") |
| ✓ | ~~`012-restart-safe-rollover`~~ DONE (app v1.4.1 2026-09-17 + v1.4.2/dg-v9 amendment 2026-09-18) | Persisted period rollover: a limit latch releases after an overnight restart (reboot/battery/OEM killer/update); fail-closed when stats unreadable. **v1.4.2 amendment (deep scan):** stuck-phone unstick (pre-v1.4.1 limit latch releases on first launch, same-day over-limit re-locks on the same tick), honest full unlock on limit latches (report `latch_reason` + truthful warning/toast + reason badges + grace precedence, dg-v9), FA weekday fix, revoked-device command cleanup, dead pair-code counter removed | owner bug report ("won't unlock over time or tomorrow… enforce off and on again") + owner directive ("deep scan of the full system and debug anything we didn't see") |
| A | `013-cloud-security-hardening` | Remove public `/demo`; harden/remove the bootstrap on-screen sign-in link (leaked bootstrap email = takeover chain); commit-email hygiene (noreply); optional: server-time-authoritative period (clock-forward unlock hole documented in spec-012 FM2) | Task 16 audit + user decision pending on email path |
| B | `014-play-store-hardening` | allowBackup=false audit, exported components, targetSdk policy, privacy declaration | Task 8 notes |
| C | `015-e2e-verification-v1.4.2` | Device re-tests (AFTER installing **v1.4.2** AND **re-pairing** — D1 shows the phone's pairing was revoked Sep 15, so remote control is inert until a new code is entered): app opens with tabs + ring + history chart, ONE merged Unlock button (tap → ladder incl. ∞ full row; hold → quick unlock), reason-aware LOCKED badge + honest full-unlock warning (spec-012 amendment), **overnight-restart unlock (reboot the phone while limit-latched in the evening → next morning it must be unlocked without toggling enforce — the v1.2 report)**, settings has no unlock field, instant commands + ⚡ chip (spec-005), full unlock (spec-003), refresh-mid-command (spec-004), safe-unpair carry-over (spec-002), settings draft save (spec-008), launch stability (spec-009) | specs 002–012 |
| ✓ | ~~`010-ux-revolution`~~ DONE (app v1.4.0 + worker dg-v7, 2026-09-16) | Unlock picker replaces the Settings field; three-tab app with ring + 7-day history chart + chips | owner verdict ("nothing revolutionary"; "Unlock Window input still there") |
| ✓ | ~~`009-launch-crash-fix`~~ DONE (app v1.3.6, 2026-09-16) | v1.3.5 cold-start ArrayStoreException fixed + spinner re-entrancy hardening + permanent Robolectric launch smoke tests | user bug report ("didn't open and just crashed out") |
| ✓ | ~~`008-settings-bugs`~~ DONE (worker-only, 2026-09-15) | Settings draft (no more reverting edits) + honest save feedback + unlock window wired to the timed-unlock button | user bug report (reverting values + dead Unlock Window) |
| ✓ | ~~`007-mobile-glass-ui`~~ DONE (app v1.3.5, 2026-09-15) | Mobile glass super-UI matching the parents' panel | owner request ("super ui upgrade for the mobile app") |
| ✓ | ~~`006-glass-dashboard`~~ DONE (worker-only, 2026-09-15) | Glass super-UI + confirmation toasts/chime/vibrate | owner request ("super ui upgrade") |
| ✓ | ~~`005-instant-poll-hot-mode`~~ DONE (worker + app v1.3.4, 2026-09-15) | ~1.5 s command delivery while panel in use; battery-safe | owner request ("reduce the poll time") |
| ✓ | ~~`004-dashboard-pending-truth`~~ DONE (worker-only, 2026-09-15) | Pending state survives refresh; old-app badge + degraded confirm; server-truth pending types | user re-test of v1.3.3 ("refresh and repeat") |
| ✓ | ~~`003-dashboard-live-unlock`~~ DONE (v1.3.3, 2026-09-15) | Real unlock + live dashboard | user bug report |
| ✓ | ~~`002-safe-unpair`~~ DONE (v1.3.2, 2026-09-15) | Revoked-while-locked trap: warning, separation, honest relabel, PIN notice, cloud source in repo | user bug report |

## Requirements

1. **REQ-001**: No new feature/bugfix work starts outside the Spec Kit flow (Constitution I).
2. **REQ-002**: Every future spec's `spec.md` must state which Constitution articles it touches (esp. II fail-closed, VI testable time, VII control channel).
3. **REQ-003**: This file is updated whenever deployed state changes (new release, Worker change, endpoint removal) — it is the durable "where are we" record, the in-repo equivalent of the sandbox worklog.

## Implementation Notes

- This spec intentionally has **no tasks.md / plan.md**: it records existing reality, it is not built.
- Dev sandbox context (worklog, tokens, keystores) lives OUTSIDE the repo and is ephemeral; this file is the in-repo replacement for that memory.
