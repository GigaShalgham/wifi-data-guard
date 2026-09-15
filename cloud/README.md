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
| `/api/child/poll` | POST | device token | usage report ⇄ pending commands + config |
| `/api/child/ack` | POST | device token | acknowledge applied commands |
| `/api/devices/{id}/command` | POST | parent session | lock / unlock / config |
| `/api/devices/{id}/revoke` | POST | parent session | unpair device |
| `/api/devices/{id}/reports` | GET | parent session | usage history (sparkline) |
| `/api/me`, `/api/audit` | GET | parent session | dashboard state, activity log |

D1 tables: `parents`, `sessions`, `magic_links`, `pair_codes`, `devices`,
`commands`, `usage_reports`, `audit_log`.

## Known pending work

- `/demo` simulator is still live — removal is roadmap spec
  `003-remove-demo-endpoint` (see `specs/001-project-state-backfill/`).
- Email delivery for magic links is not configured; the bootstrap one-time
  link flow (`BOOTSTRAP_EMAIL`) is the active sign-in path.
