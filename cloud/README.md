# Cloud Worker — Wi-Fi Data Guard parent dashboard

The Cloudflare Worker that powers the optional cloud features: parent
dashboard (magic-link sign-in), device pairing (6-digit codes), command
delivery (lock / unlock / config), usage reports, and the `/demo` simulator.

**Live URL**: https://wifi-data-guard.gigaspaceturnip.workers.dev

## Why this exists in the app repo

The Worker source originally lived only on the dev machine and was lost in a
sandbox reset (2026-09); it was recovered on 2026-09-15 by downloading the
deployed module bundle from the Cloudflare API and converting it back to a
readable single-file source (`worker.js`) — validated byte-exact against every
live-served asset. From now on this directory is the source of truth
(project constitution, Article I): **edit here, deploy from here, commit
before deploying.**

## Layout

```
cloud/
├── worker.js     # the entire Worker: one readable ES module
├── deploy.py     # REST-API deploy script (no wrangler needed)
└── README.md     # this file
```

`worker.js` contains, top to bottom:

| Section | Marker | Contents |
|---------|--------|----------|
| Dashboard app JS | `var DASHBOARD_JS` | served at `/app.js` (EN+FA i18n, device cards, commands) |
| Static assets | `var ICON_SVG / MANIFEST_JSON / SW_JS / DASHBOARD_CSS / DASHBOARD_HTML / DEMO_HTML` | served at `/icon.svg`, `/manifest.webmanifest`, `/sw.js`, `/styles.css`, `/`, `/demo` |
| Worker logic | `// src/worker.js` | routing, sessions + magic links, pairing, polling, commands, revoke, audit, D1 queries |

No build step: the file is uploaded as-is.

## Bindings (must be present on every deploy)

| Name | Type | Reference |
|------|------|-----------|
| `DB` | D1 database | `dataguard` (id `42827fef-0906-495f-b1e5-04c4ed2e57c8`) |
| `KV` | KV namespace | id `7f27c5768dcd4ca6a00949bb01e0bd0d` |
| `BOOTSTRAP_EMAIL` | plain text | parent email used before email delivery is configured |

`deploy.py` reads the live settings and echoes the bindings back on upload, so
a deploy cannot drop them. Compatibility date: `2024-09-23`.

## Deploying

```bash
python3 cloud/deploy.py --token-file /path/to/cf-token
# or: CLOUDFLARE_API_TOKEN=... python3 cloud/deploy.py
```

Requirements: a Cloudflare API token with **Workers Scripts:Edit** (read is
not enough) for this account. The token is never committed.

### Post-deploy verification (minimum)

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://wifi-data-guard.gigaspaceturnip.workers.dev/          # 200
curl -s https://wifi-data-guard.gigaspaceturnip.workers.dev/app.js | head -c 200                        # dashboard JS
curl -s -o /dev/null -w "%{http_code}\n" https://wifi-data-guard.gigaspaceturnip.workers.dev/demo      # 200
curl -s -o /dev/null -w "%{http_code}\n" -X POST https://wifi-data-guard.gigaspaceturnip.workers.dev/api/pair-code   # 401 (no session) = alive
```

Also `node --check cloud/worker.js` before every deploy.

### Rollback

The Worker keeps deployment history in the dashboard; simplest rollback is
`git checkout <previous-commit> -- cloud/worker.js && python3 cloud/deploy.py …`.

## API surface (consumed by the Android app, see `CloudLink.kt`)

| Endpoint | Method | Auth | Purpose |
|----------|--------|------|---------|
| `/api/auth/request-link` | POST | — | parent magic-link sign-in |
| `/api/pair-code` | POST | parent session | generate 6-digit pairing code |
| `/api/child/pair` | POST | pairing code | device ↔ parent pairing, returns device token |
| `/api/child/poll` | POST | device token | usage report ⇄ pending commands + config; optional `"wait": 1..25` (s) hot-mode long-poll (see below); the report whitelist accepts the coarse `latch_reason` (spec-012: `""`/`limit`/`cloud`/`offline`/`clock`, ≤ 16 chars — old apps simply don't send it) |
| `/api/child/history` | GET | device token | own daily usage history (spec-010, see below) |
| `/api/child/ack` | POST | device token | acknowledge applied commands |
| `/api/devices/{id}/command` | POST | parent session | lock / unlock / config |
| `/api/devices/{id}/revoke` | POST | parent session | unpair device (spec-012: also deletes its undelivered commands — a revoked device can never poll again) |
| `/api/devices/{id}/reports` | GET | parent session | usage history (sparkline) |
| `/api/me`, `/api/audit` | GET | parent session | dashboard state, activity log |

D1 tables: `parents`, `sessions`, `magic_links`, `pair_codes`, `devices`,
`commands`, `usage_reports`, `audit_log`. `devices` also carries `hot_until`
and `last_wait_poll_at` (spec-005 hot-mode state).

## Hot-mode long-poll (spec-005, instant commands)

The parent panel's 10 s heartbeat (only while the tab is visible) stamps
`devices.hot_until = now + 75 s` for that parent's devices. A device poll with
`"wait": N` (app v1.3.4+, clamped 0–25 s) is HELD by the worker — checking D1
every 1.5 s — while the device is hot, so a command created from the dashboard
is delivered in ~1.5 s instead of the next poll cycle. Command creation extends
the hot window to `now + 120 s` (fast ack + report). The poll response carries
`fast: true|false` (server-side truth, clock-skew-immune); the app re-polls at
1 s pacing while fast and falls back to the configured interval otherwise.
Battery caps: hold only while hot, app-side 90 s re-arm + 30 min continuous cap.
Older apps never send `wait` and are answered immediately, exactly as before.

## Timed-unlock duration picker (spec-010, amended by spec-011)

The Settings field **Unlock window (min)** is **gone from the UI**, and as of
spec-011 the two card buttons are merged into **one Unlock button**. Tapping
it opens a glass picker with an ordered ladder: chips 15/30/60/120 min →
custom field (1–480, invalid → 15) → a distinct amber **∞ Full unlock —
until you lock it again** row (guarded by the same `confirm()` warning as the
old full-unlock button, keyboard-operable). **Press-and-hold (550 ms)** the
card's Unlock button fires an instant timed unlock with the remembered
minutes — a "Quick unlock: N min" toast confirms, and the synthetic click
after the hold is swallowed. Only the bounded, auto-relocking action sits on
the fast gesture. The last confirmed choice is remembered per device in
`localStorage["unlockMin:<id>"]`; the seed chain is remembered → saved
`unlock_minutes` setting (`minsFor()`, clamp 1–480) → 15. Commands still
carry explicit `payload.minutes` / `payload.full`, so old and new apps alike
behave exactly as before (zero payload changes in spec-011).
`unlock_minutes` remains a valid config key (app-side default + API
compatibility); it is simply no longer edited from the dashboard.

## `/api/child/history` (spec-010)

`GET /api/child/history?days=7&tz=<offsetMinutes>` (device bearer token)
returns `{ok, days:[{day, used_bytes}]}` — one row per **local day**
(epoch-day numbers computed with the device's tz offset, so day boundaries
match the phone). `used_bytes` is the day's last report minus the last
observed report before it, clamped ≥ 0 (a counter reset reads as 0, never
negative; usage across an offline gap lands on the first observed day after
the gap). `null` = data exists but no baseline at all (first ever report);
`0` = no data that day. `days` clamps 1–14, `tz` clamps ±1440. The math
lives in the pure function `bucketDaily()` (unit-tested in
`scripts/verify_spec010.js`). Called by the app's Usage tab only —
purely cosmetic, never enforcement-relevant.

## `unlock_minutes` semantics (spec-008 → amended by spec-010)

The config key survives as the app-side default (the phone's own PIN unlock
uses it; 0 = until period end on the phone) and as the picker's fallback
seed, but the dashboard no longer shows an Unlock window input — see the
picker section above. Every app since v1.3 applies remote `minutes` 1–480.

## Known pending work

- `/demo` simulator is still live — removal is roadmap spec
  `009-cloud-security-hardening` (see `specs/001-project-state-backfill/`).
- Email delivery for magic links is not configured; the bootstrap one-time
  link flow (`BOOTSTRAP_EMAIL`) is the active sign-in path.
