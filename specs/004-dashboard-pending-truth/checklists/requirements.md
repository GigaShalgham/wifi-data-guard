# Requirements Checklist: 004-dashboard-pending-truth

## FR-001 — pend survives refresh (P1)
- [x] pend round-trips via sessionStorage; TTL deadline absolute across reloads
- [x] try/catch on all storage access (private mode = v1.3.3 behavior)
- [x] corrupted payload resets to empty pend

## FR-002 — old-app badge (P1)
- [x] renders for parseable app_version < 1.3.3, on every refresh, EN + FA
- [x] never renders for unparseable (`sim`) or >= 1.3.3
- [x] disappears after the phone updates (report-driven)

## FR-003 — degraded full-unlock confirmation (P1)
- [x] full-unlock pend on old app clears when grace_until lands (or real unlatch)
- [x] no 90 s frozen "Unlocking…" for old apps
- [x] normal full unlock still requires !latched (v1.3.3 apps unaffected)

## FR-004 — server-truth pending types (P2)
- [x] /api/me devices include pending_types {lock,unlock,config counts}
- [x] pending_commands total unchanged (old dashboards compatible)
- [x] chip precedence: local pend > type chip > N cmds > none

## FR-005 — extended burst (P2)
- [x] trackRefresh delays [1,5,15,30,45,60,75,90] s

## FR-006 — SW rollout (P2)
- [x] CACHE "dg-v4"; old caches purged on activate; skipWaiting + clients.claim intact

## FR-007 — Art. XI disclosure (P1)
- [x] constitution Article XI added
- [x] README documents the rule; every release note states app-update requirement

## Constitution gates
- [x] Art. V: all new strings EN + FA (badge)
- [x] Art. VIII: card shows server in-flight truth; old-app degradation visible; never stuck optimistic (absolute TTL)
- [x] Art. XI: delivery summary states phone-app verdict explicitly
- [x] Art. IV: no secrets in any artifact

## Verification gates
- [x] node --check: module + extracted DASHBOARD_JS
- [x] unit test appOld/pendConfirmed matrix passes
- [x] live probes: new markers in /app.js, /sw.js dg-v4, / 200, unauth 401
- [x] no commands sent to the real device during verification
