# Requirements Checklist: 008-settings-bugs

## FR-001/002 — settings draft survives refreshes
- [x] `cfgDraft` map; open panel renders from draft, lazy-initialized from server settings
- [x] input/change listeners (bindCfg) in wireDevice update the draft
- [x] panel close drops the draft; re-open re-initializes from current server settings
- [x] `updateDynamic()` prunes drafts for device ids absent from /api/me (revoke-safe)
- [x] focused settings input restored after every re-render (template-safe `[0-9]` regex)

## FR-003 — honest save feedback
- [x] save handler checks parsed `res.ok`; failure shows red error, never "Saved"
- [x] success copy = "Saved — syncing to device…" (EN+FA)
- [x] save button disabled while in flight (double-save race killed)
- [x] empty number field = key not sent (no accidental limit=1)

## FR-004 — device-confirmed sync feedback
- [x] `pending_types.config > 0` chip "Settings → device…" on the card
- [x] tracked sync (cfgSync, 90 s cap) fires toast + chime when pending count clears
- [x] panel msg flips to "✓ Applied on device." on confirmation
- [x] msg text + color survive re-renders
- [x] toast/chime only on real device confirmation (Art. VIII), EN+FA

## FR-005/006 — unlock window honored
- [x] `minsFor()` clamp 1..480, 0/invalid → 15 (20 unit tests)
- [x] POST payload `{minutes: minsFor(d.settings)}`
- [x] button label "Unlock %dm" / FA with Persian digits reflects the setting live
- [x] confirm dialog reflects the setting; timed toast reflects granted minutes (pend.mins)

## FR-007 — bilingual truth
- [x] all new strings EN + FA with identical meaning (key-parity test)

## FR-008 — cache
- [x] SW `dg-v6`; stale dg-v5 edges self-clear

## Verification gates
- [x] node --check module + extracted DASHBOARD_JS; unit tests pass; marker sweep; EN/FA parity
- [x] deploy probes green; spec-004/005 suites re-run PASS (no regression)
- [x] agent-browser: draft + focus survive ≥2 heartbeats; save → chip → confirmed toast; EN+FA/RTL; zero console errors
- [x] no test commands sent to the owner's real device (D1-audited: zero commands to device 14)
- [x] Art. XI disclosure in owner report: worker-only, NO app update
