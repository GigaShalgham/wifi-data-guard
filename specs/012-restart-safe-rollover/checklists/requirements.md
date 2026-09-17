# Requirements Checklist: spec-012 Restart-Safe Period Rollover

## User Stories coverage

- [x] US1: overnight restart (reboot/battery/killer/update/off-at-midnight) releases a yesterday limit latch within one tick — T1
- [x] US2: cloud/offline/clock/unpaired latches survive a restart-across-boundary — T2
- [x] US3: stats-unavailable at the boundary keeps the latch (fail-closed) and retries — T5
- [x] US4: continuous-run rollover, monthly stability, grace, PIN, cloud, hot polling unchanged — T3/T4 + full suite

## Functional requirements

- [x] FR-001: `period_start` pref exists and is written/read by the watchdog only
- [x] FR-002: onCreate seeds from persisted value; absent ⇒ current period persisted (migration-safe)
- [x] FR-003: resolved rollover persists the new period; security reasons kept
- [x] FR-004: limit-latch release gated on `effectiveUsage >= 0`; pending retries; once-per-streak logging; period advanced only on resolution
- [x] FR-005: counter seeding path unchanged and correct after restart (high seed never limit-evaluated)
- [x] FR-006: grace cleared on resolved rollover; persisted grace honored across restarts
- [x] FR-007: zero wire/D1/dashboard/PIN/i18n changes

## Non-functional

- [x] No new permissions/dependencies/schedulers
- [x] Robolectric release gate passes (RolloverRestartTest)
- [x] Bilingual surfaces untouched (no new strings)

## Safety (Constitution)

- [x] Art. II: no fail-open path (revoked stats + reboot stays latched — T5)
- [x] Art. VII: VPN control-channel exemption untouched
- [x] Art. VIII: status line still names the real latch reason
- [x] Art. IX: fix is fully local/offline — no cloud dependency added
- [x] Art. X: signed release, versionCode monotonic (12), tagged, GitHub Release with notes
- [x] Art. XI: disclosure states APP UPDATE REQUIRED + Worker untouched
