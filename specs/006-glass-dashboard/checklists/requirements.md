# Requirements Checklist: 006-glass-dashboard

## FR-001 — glass design system
- [x] frosted cards (backdrop-filter + @supports solid fallback)
- [x] gradient glow background (pure CSS, fixed layers)
- [x] sticky glass header; glowing badges (locked pulse / pending pulse / ok / fast)
- [x] glass buttons + gradient primary; taller gradient usage bar
- [x] restyled: spark, log, settings, pair code, messages, footer, login card
- [x] RTL + Vazirmatn first-class; mobile stacking; touch-friendly buttons

## FR-002 — HTML background layers
- [x] glow divs pointer-events:none; theme-color updated

## FR-003 — toasts + sound + vibrate
- [x] glass toast stack, 4 s auto-dismiss, tap dismiss, cap 4
- [x] EN+FA strings; fire ONLY on real device confirmation (pend diff)
- [x] WebAudio chime (lock falling pair / unlock rising pair); vibrate guarded

## FR-004 — medium animations
- [x] entrance stagger only on renderApp (not on 10 s updates)
- [x] chip pulse, dot glow, toast slide (~300 ms)
- [x] prefers-reduced-motion disables all

## FR-005 — SW rollout
- [x] dg-v5; old caches purged on activate

## FR-006 — zero behavior change
- [x] all flows intact (pair, settings, revoke warnings, audit, chips, badges, lang, logout)

## Constitution gates
- [x] Art. V: toasts/chips EN+FA
- [x] Art. VIII: toasts = confirmed truth only; no lying states
- [x] Art. XI: worker-only verdict stated
- [x] Art. IV: no secrets in any artifact

## Verification gates
- [x] node --check module + extracted DASHBOARD_JS; ≥30 markers
- [x] live screenshot smoke OK
- [x] probes: dg-v5, markers, 200/401
