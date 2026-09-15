# Tasks: 006-glass-dashboard

## 1. Spec
- [x] T001 spec.md (4 user stories, FR-001..006, failure modes)
- [x] T002 plan.md (wholesale-CSS strategy, toast diff hook, chime design)

## 2. Implementation (scripts/edit_worker_spec006.py)
- [x] T003 New DASHBOARD_CSS (glass tokens, cards, header, badges, buttons, bar, spark, log, settings, code, toasts, animations, reduced-motion, @supports fallback, RTL)
- [x] T004 DASHBOARD_HTML: glow layers + toast container root + theme-color
- [x] T005 DASHBOARD_JS: toast fn + chime + vibrate; pend→confirmed diff in refresh(); entrance class in renderApp(); i18n toast strings EN+FA
- [x] T006 SW cache dg-v4 → dg-v5

## 3. Verification
- [x] T007 node --check module + extracted DASHBOARD_JS; ≥30 marker sweep
- [x] T008 Live screenshot smoke (login page renders, CSS applied, no console errors)
- [x] T009 Deploy + live probes (dg-v5, styles markers, 200/401)

## 4. Converge
- [x] T010 README (super-UI row + section update); spec-001 state + roadmap
- [x] T011 Commit per spec, push; worklog; owner report (Art. XI: no app update)
