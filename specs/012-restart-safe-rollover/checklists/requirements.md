# Requirements Checklist: spec-012

## User Stories
- [x] US1: Restart after midnight releases a stale limit latch automatically (reconcile on service create; reason/monotonic guards; migration for pre-v1.4.1 latches)
- [x] US2: Full unlock on a limit-locked phone is honest end-to-end (truthful confirm text, pend confirms on the degraded window, honest toast, reason-aware card badge; old apps unchanged)
- [x] US3: FA weekday labels correct; revoked-device commands cleaned; dead code removed
- [x] US4: Zero regressions (tests, compile gates, i18n parity, SW bump, regression markers)

## Functional Requirements — App
- [x] FR-001 latch_period_start persisted at both latch sites
- [x] FR-002 onCreate reconcile (unlatch + grace clear + counter reset + log; migration via zero-default)
- [x] FR-003 RolloverPolicy pure object + 5 unit cases
- [x] FR-004 buildReport latch_reason
- [x] FR-005 FA weekday fix + real-date test assertions
- [x] FR-006 versionCode 12 / 1.4.1 / UA bump

## Functional Requirements — Worker
- [x] FR-101 report whitelist latch_reason
- [x] FR-102 pendConfirmed grace-window confirm + toastFullWindow honest toast
- [x] FR-103 confirmUnlockLimited truthful warning
- [x] FR-104 locked badge reason labels (4 keys, EN+FA)
- [x] FR-105 revoke + sweep delete undelivered commands
- [x] FR-106 dead attempts check removed
- [x] FR-107 SW dg-v9

## Safety / Constitution
- [x] Art. II: cloud/offline/clock latches never released by reconcile; rollback-safe comparison
- [x] Art. III: latch_reason is coarse enforcement state only, no PII
- [x] Art. IV: no secrets in scripts/logs/specs; tokens via files
- [x] Art. V: EN/FA parity for every new string
- [x] Art. VIII: dashboard shows confirmed device truth, never optimistic claims
- [x] Art. IX: app-side fix works fully offline
- [x] Art. X: signed, versioned, tagged, released; QA build alongside
- [x] Art. XI: explicit update verdict + re-pair instruction (F2)
- [x] No test command ever sent to the real device
