# Implementation Plan: 004-dashboard-pending-truth

Worker/dashboard-only change (cloud/worker.js). No Android app change — v1.3.3 already
has full-unlock semantics + the +4 s confirmation poll; it only needs to be installed
on the phone.

## Decisions

1. **sessionStorage (not localStorage) for pend persistence.** The refresh case the
   user hit is same-tab. Per-tab semantics also avoid a stale lock-pend from yesterday's
   tab bleeding into a new one; the absolute 90 s TTL bounds any residue either way.
   Storage access is fully guarded (`try/catch`) — private mode degrades to v1.3.3
   behavior.
2. **Degraded confirmation via a per-pend `oldApp` flag** captured at tap time from
   `appOld(d.report)`, rather than re-deriving at confirm time. The parent commanded a
   known-old app; we confirm when that old app's honest outcome (a grace window)
   lands. `pendConfirmed` order: lock → latched; timed → grace; old-app full → grace
   OR unlatch; normal full → unlatch.
3. **`appOld` is strict about parsing.** `"1.3.2-test"` → strip `-suffix` → numeric
   major/minor/patch compare against 1.3.3. Unparseable (`"sim"`, `"1.x"`) → false
   (no badge). The badge is a standing truth on the card (Art. VIII), not a transient
   dialog — and its disappearance after an APK update doubles as the "did the update
   land?" acceptance signal.
4. **Server-truth chip is additive.** `handleMe` groups queued commands by
   `(device_id, type)` and adds `pending_types`; `pending_commands` (total) unchanged
   so old cached dashboards keep working. Chip precedence in `deviceCard`:
   local pend → type-aware server chip (lock/unlock only) → `N cmds` count → none.
   Buttons stay disabled **only** while a local pend is active — a device that is
   offline for hours must never permanently disable commands (duplicates are harmless
   no-ops; a permanently frozen panel is not).
5. **Burst extension** to [1, 5, 15, 30, 45, 60, 75, 90] s covers the full pend TTL,
   so the settle after a slow poll cycle comes from the burst even if the 10 s interval
   is throttled.
6. **SW bump dg-v4** — one cache generation per spec, as established in 002→003.
7. **Constitution Art. XI** codifies the owner's standing request: every future change
   must state whether the phone app needs updating (and which build).

## Risks & mitigations

- **Edit-safety**: all worker.js edits go through `scripts/edit_worker_spec004.py`
  with exactly-one-match assertions (structural guard, per repo convention) and an
  atomic all-or-nothing write. Verified with `node --check` on the module and on the
  extracted+unescaped `DASHBOARD_JS` (template-literal unescaping first — raw regex
  extraction gives false syntax errors).
- **Pure-logic unit test**: `appOld` + `pendConfirmed` are extracted and unit-tested in
  Node (version matrix incl. `sim`, `1.2`, `1.3.1-test`, `1.3.3`, `2.0`) before deploy.
- **No test commands against the user's real device** (repo convention T016);
  acceptance is the owner's re-test.
- **Rollback**: `cloud/deploy.py` archives the live module before overwrite;
  re-deploy the archive to revert.

## Delivery & disclosure (Art. XI)

- Worker deploys live immediately; dashboard clients pick it up on next SW update
  (dg-v4).
- **Phone app: no new APK needed for this spec.** The still-outstanding v1.3.3
  install (spec-003 release) is required for full unlock to truly clear the latch —
  the new dashboard badge now makes that visible in-product.
