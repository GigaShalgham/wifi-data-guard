# Requirements Checklist: 005-instant-poll-hot-mode

## FR-001 — D1 columns
- [x] devices.hot_until + devices.last_wait_poll_at exist (idempotent migration, verified via PRAGMA)

## FR-002 — heartbeat stamps hot
- [x] every authenticated /api/me sets hot_until=now+75s for parent's non-revoked devices
- [x] /api/me devices include fast (last_wait_poll_at fresh < 45 s)

## FR-003 — command creation extends hot
- [x] handleCommand sets hot_until = max(current, now+120s)

## FR-004 — long-poll
- [x] poll body wait parsed, clamped 0..25
- [x] hold only while hot; checks every 1.5 s; immediate response when not hot
- [x] last_wait_poll_at stamped when wait>0 (same update as last_seen)
- [x] response carries fast boolean; delivery/audit path identical for held vs immediate

## FR-005 — dashboard heartbeat gating
- [x] 10 s interval cleared on document.hidden, restarted + immediate refresh on visible

## FR-006 — app v1.3.4
- [x] wait:20 sent on every poll; read timeout 35 s for wait polls (15 s otherwise)
- [x] fast pacing 1 s no jitter while fast; re-arm 90 s; 30 min continuous cap
- [x] UA DataGuard-Android/1.3.4; versionCode 8; versionName 1.3.4
- [x] pacing uses System.currentTimeMillis (Art. VI real time)

## FR-007 — honest ⚡ chip
- [x] FAST chip (EN+FA) only when d.fast true; old apps never show it

## FR-008 — docs
- [x] cloud/README.md: wait param, columns, hot semantics, battery caps

## Constitution gates
- [x] Art. II: no lock-semantics change; timeout = today's behavior; fail-closed intact
- [x] Art. V: FAST chip EN+FA
- [x] Art. VI: real-time pacing only
- [x] Art. VII: same self-exempted poll channel
- [x] Art. VIII: chip reflects actual long-polling
- [x] Art. XI: verdicts stated (worker live; app v1.3.4 required for instant)

## Verification gates
- [x] node --check module + extracted DASHBOARD_JS; markers present
- [x] unit tests pass (clamp + eligibility)
- [x] migration ran before deploy; live probes green (200/401/503, markers)
- [x] no commands sent to the real device during verification
