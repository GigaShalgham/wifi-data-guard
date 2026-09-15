# Implementation Plan: 005-instant-poll-hot-mode

## Decisions

1. **Server-gated hold, client-declared willingness.** The app sends `wait: 20` on
   EVERY poll; the Worker holds only while `hot_until > now`. This avoids the
   chicken-and-egg of response-driven fast mode (the device must already be holding
   when the parent taps) without any push infrastructure.
2. **`fast` is a boolean computed server-side** (`hot_until > now` at response time),
   not an epoch the app must compare — immune to device clock skew. The app re-arms
   `fastUntil = now + 90 s` on each `fast: true` and clears it on `fast: false`.
3. **D1 (not KV) for hot state** — strong consistency; KV's eventual reads (up to
   60 s stale) would break 75 s windows. Two new columns via idempotent ALTER.
4. **Heartbeat gating by tab visibility** — the dashboard's 10 s `/api/me` interval
   stops while `document.hidden` (this also saves worker invocations). Combined with
   the 75 s server window, hot mode ≈ "a human is looking at the panel right now".
5. **Battery caps**: app-side 30-minute continuous-fast hard cap; 90 s re-arm window;
   read timeout 35 s only for wait polls (15 s unchanged otherwise).
6. **Poll pacing stays REAL time** (`System.currentTimeMillis()`), never `AppClock`
   (Art. VI — acceleration must never fake connectivity).
7. **Hold loop**: `while (now < deadline) { query undelivered; if found break; await
   sleep(1500) }` — ~13 D1 reads max per held poll, sequential (no connection-limit
   issues), well under subrequest budgets.
8. **Migration order**: D1 ALTER first → worker deploy → app release. A worker
   referencing missing columns would 500 every poll — the deploy script runs after
   the migration gate passes.

## Risks & mitigations

- All worker edits via `scripts/edit_worker_spec005.py` (exactly-once assertions,
  atomic write); `node --check` on module + extracted `DASHBOARD_JS`; pure-logic
  unit tests (wait clamp, hold-decision) in Node before deploy.
- The held response path reuses the exact delivery+audit code of the immediate path
  (single function, no copy-paste divergence).
- No commands sent to the real device during verification (repo convention);
  `wait` behavior verified by unit test + code review + the owner's v1.3.4 re-test.
- Rollback: `cloud/deploy.py` archives the pre-change module; D1 columns are
  additive and harmless to old code.

## Delivery & disclosure (Art. XI)

- Worker + dashboard: live immediately after deploy (dg-v4 stays — JS changes only;
  SW bump not needed for a chip + heartbeat change… reassessed at implementation:
  the ⚡ chip + paused heartbeat ship inside app.js which is `cache-control:
  no-store`, so no SW bump required. Kept dg-v4.)
- Phone app: **v1.3.4 required** for instant delivery (versionCode 8, UA 1.3.4).
