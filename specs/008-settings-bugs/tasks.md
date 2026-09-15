# Tasks: 008-settings-bugs

## 1. Spec
- [x] T001 spec.md (3 user stories, FR-001..008, failure modes, D1 evidence table; clarify self-answered from evidence — owner asked to proceed)
- [x] T002 plan.md (draft model + honest save + minsFor; worker-only; design decisions 1–7)

## 2. Implementation (all in cloud/worker.js via asserted edit scripts, atomic writes)
- [x] T003 i18n: `unlockFor`, `confirmUnlockFor`, `savedSyncing`, `saveFailed`, `settingsSync`, `toastSettings`, `appliedOnDevice`, `toastTimedFor` (EN + FA with Persian digits via `faNum`); retired `unlock15`, `confirmUnlock`, `saved`, `toastTimed` (incl. FA key-rename fixup)
- [x] T004 Draft model: `cfgDraft` map; `settingsPanel(d)` renders from draft (lazy init from server settings); `bindCfg` input/change listeners in `wireDevice`; panel close + `updateDynamic` prune drop drafts
- [x] T005 Save path: draft-driven payload (empty field = not sent) → POST → `res.ok` check; success = "Saved — syncing to device…" + `cfgSync[id]` (90 s cap); failure = red `saveFailed`; button disabled in flight (kills the double-save race seen in D1)
- [x] T006 Device-confirmed sync feedback: `pending_types.config` chip on card; `toastConfirmed()` fires toast + chime + updates panel msg to "✓ Applied on device." when a tracked sync drains
- [x] T007 Unlock window: `minsFor(s)` (clamp 1..480, 0/invalid → 15) drives button label, confirm dialog, POST payload and timed-unlock pend toast
- [x] T008 SW cache bump dg-v5 → dg-v6
- [x] T008b Fixups found in live testing: template-safe focus-restore regex (`[0-9]` not `\d` — the template literal ate the backslash), focus + save-msg preservation across re-renders, confirm-state msg

## 3. Verification
- [x] T009 `node --check` module + extracted/unescape DASHBOARD_JS; 20 unit tests (minsFor matrix, faNum) + 16 markers + retired-string sweep + EN/FA key parity + spec-004/005 regression suites — ALL PASS
- [x] T010 Deployed ×3 (final etag 7bd9a730); live probes: markers in /app.js, dg-v6, 200/401
- [x] T011 agent-browser live smoke (bootstrap session): draft + FOCUS survive 2+ heartbeats on the real device's panel (no save clicked); msg/error preserved across re-renders; panel close → fresh draft; sim device: save → "Saved — syncing…" + chip → device-confirmed toast "✓ Settings applied" (instrumented capture) → "✓ Applied on device." msg; button label "Unlock 15m" → "Unlock 45m"/"باز ۴۵ دقیقه‌ای" after saves; blocked-endpoint save shows red error + re-enabled button; FA/RTL + Persian digits; ZERO console/page errors; D1 audit: zero commands to the real device
- [x] T011b Screenshots: download/dashboard-spec008-{en,fa}.png; test sim revoked + forgotten

## 4. Converge
- [x] T012 README version-history row (worker-only, no APK); cloud/README.md unlock_minutes semantics; spec-001 deployed-state row + roadmap renumber (009 security, 010 play-store, 011 e2e); spec-008 status Implemented + all boxes
- [x] T013 Commit + push; worklog Task 21; owner report (Art. XI: worker-only → LIVE, NO app update needed; spinner-label limitation disclosed)
