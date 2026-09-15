# Implementation Plan: 006-glass-dashboard

## Decisions

1. **Wholesale CSS replacement, surgical JS/HTML edits.** `DASHBOARD_CSS` is one
   template literal — replace it entirely (single exactly-once edit, full old block
   → full new block) rather than 30 fragile micro-edits. `DASHBOARD_JS`/`DASHBOARD_HTML`
   get targeted edits (bg layers, toast functions, refresh-diff hook, entrance flag).
2. **Entrance animation only on `renderApp`.** `updateDynamic()` re-innerHTMLs every
   10 s; animating there would replay slides forever. The devlist container gets an
   `entrance` class only inside `renderApp`, removed after the animation settles.
3. **Toast trigger = pend diff in `refresh()`.** Before `clearConfirmedPends()`,
   snapshot which devices have active pends; after `me` updates, compare: a pend
   that disappeared while the report now confirms the target state → toast. This is
   the same truth the chip uses — no new state source (Art. VIII).
4. **Chime via WebAudio** (no asset files, CSP-clean): two short sine notes; lock =
   330→220 Hz (falling, "closing"), unlock = 440→660 Hz (rising, "opening"). Audio
   context created lazily on first confirmation (autoplay policies allow it after a
   user gesture — the command tap was the gesture).
5. **SW dg-v5**; HTML gets two fixed glow `<div>`s (pointer-events:none) so the
   background never repaints with content.
6. **Design tokens**: keep the existing CSS variable names (`--bg --panel --line
   --txt --mut --acc --acc2 --ok --warn --bad --radius`) so any JS inline styles
   keep working; add glass-specific tokens (`--glass --glass-brd --glow1 --glow2`).

## Risks & mitigations

- CSS rewrite is the riskiest edit in the repo's history → verified by:
  `node --check` on the module; extracted+unescaped standalone checks; a marker
  sweep (≥ 30 selectors/functions); manual render smoke via `agent-browser`
  screenshot against the live URL (unauth login page exercises the same CSS);
  rollback = redeploy archived module.
- Persian strings via `\u` escapes in the edit script (established lesson).
- No JS logic changes beyond the toast hook + entrance class + bg markup — buttons,
  commands, refresh cadence untouched.

## Delivery & disclosure (Art. XI)

- **Worker-only. Live immediately after deploy; NO app update needed.** The phone
  app is unaware of the dashboard's looks.
