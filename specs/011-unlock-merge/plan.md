# Implementation Plan: 011-unlock-merge

## Approach

Worker-only spec: one asserted Python edit script (`scripts/edit_worker_spec011.py`,
parent-sandbox path `/home/z/my-project/scripts/`) applies exactly-once edits to
`cloud/worker.js`, writes atomically after all assertions pass, keeps a backup.
Verification reuses the spec-010 harness pattern (`scripts/verify_spec011.js`):
extract DASHBOARD_JS, template-evaluate, compile-gate, stub-DOM unit test of the
long-press state machine, i18n parity, retired-marker sweep, regression markers,
then deploy + live smoke on the `/demo` simulator only (never the real device).

## Design decisions

- **D1 — Merge direction: one button + ordered picker ladder.** A single primary
  "Unlock" button replaces the pair. The picker becomes the *only* unlock surface,
  ordered quick → custom → full. Chosen over "tap=full, hold=timed" because the
  full (unbounded) action must never sit on a hidden gesture, and over keeping
  two buttons because that is the reported defect.
- **D2 — Long-press = repeat remembered minutes.** 550 ms threshold (industry
  ~500 ms). Fires the bounded, auto-relocking action (`rememberedMins(d)`), so
  the shortcut can never leave the internet open indefinitely by accident.
- **D3 — i18n key rename `unlockFull` → `unlockNow`** (same value) so the merged
  label is honestly named; `confirmUnlockFull` keeps its name (it really is the
  full-unlock warning). `timedUnlock` retired.
- **D4 — Full-unlock row is last + amber + keyboard-operable**, gated by the
  existing `confirm()` text including the v1.3.3+ old-app caveat.
- **D5 — Immediate toast on quick unlock** ("Quick unlock: %d min") because a
  gesture with no visible panel change otherwise feels dead; the existing
  drain-confirmation toast still fires later (both are honest, different events).
- **D6 — Gesture plumbing**: Pointer Events (`pointerdown/up/leave/cancel`) with a
  timer; click-after-fire swallowed once; `contextmenu` prevented on the button;
  CSS `user-select:none; -webkit-touch-callout:none` scoped via a `longable`
  class. No Pointer Events → no long-press, tap still works (graceful).
- **D7 — No app change.** The app's Parent tab is already one Unlock button +
  chips; payloads unchanged (Art. XI: worker-only, instant live).
- **D8 — SW dg-v7 → dg-v8**; deploy via `cloud/deploy.py`; rollback = redeploy
  git dg-v7 source. No D1/schema/route/payload changes.
- **D9 — Verification gates (release-blocking)**: node compile gates both
  scopes; stub-DOM long-press unit tests (fire-once, click-swallow, cancel,
  disabled); i18n EN/FA key parity incl. new keys, retired keys absent; marker
  sweep (old `data-unlock-full` gone, `pickFull`/`armLongPress`/`dg-v8` present);
  regression sweep for spec-004/005/008/010 markers; live probes (/, /app.js, SW
  version, /api/child/history 401) + `/demo` sim end-to-end incl. FA rendering.
- **D10 — Live smoke policy**: pair a throwaway simulator device, exercise merged
  button + picker + long-press, verify the exact command in the sim audit, revoke
  + forget; zero commands to the real kid's device (audit-verified).

## Verification gates (release-blocking)

1. `node --check` on the worker module + compile-gate of evaluated DASHBOARD_JS.
2. Stub-DOM unit tests of `armLongPress` — all pass.
3. i18n parity + retired/new key sweep — all pass.
4. Regression markers for spec-004/005/008/010 — all present.
5. Deploy succeeds; live probes healthy; `/demo` sim flow works EN+FA; real
   device untouched (audit query clean).

## Risks & mitigations

- **Touch quirk: long-press opens context menu / selects text** → prevented
  (D6); if an OEM browser still misbehaves, the tap→picker path is unaffected.
- **Owner expected a different "special order"** → the order is explicit
  (quick → custom → full) and taught in-UI (picker hint + button title); the
  ladder is trivially re-orderable in one template block if the owner wants a
  different arrangement.
- **Stale SW serving old card** → dg-v8 bump + skipWaiting as before.
