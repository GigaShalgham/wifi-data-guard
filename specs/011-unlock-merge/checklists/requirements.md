# Requirements Checklist: 011-unlock-merge

## User Stories
- [x] US1: Locked card → ONE Unlock button; tap → ordered picker (chips → custom → ∞ full row); both command paths work exactly as before
- [x] US2: Long-press Unlock → instant remembered-minutes unlock, quick toast, no double click, no context menu
- [x] US3: No regressions: lock button, settings drafts, pend chips, hot polling, picker seeds, battery glyph, audit, i18n parity

## Dashboard FRs
- [x] FR-001: single `data-unlock` button on locked cards; `data-unlock-full` gone; key `unlockNow` (EN+FA); `timedUnlock` retired
- [x] FR-002: picker ladder with full-unlock row last (amber, keyboard-operable, `confirmUnlockFull` guard, `{full:true}` + pend `oldApp` semantics preserved)
- [x] FR-003: long-press 550 ms → remembered minutes; click swallowed after fire; contextmenu/selection suppressed; inert while disabled; `toastQuick` feedback
- [x] FR-004: i18n EN+FA parity; new keys `unlockNow`/`pickFull`/`pickLongHint`/`toastQuick`
- [x] FR-005: SW dg-v8
- [x] FR-006: zero payload/route/schema changes; spec-004/005/008/010 regression markers verified

## Verification
- [x] Compile gates + stub-DOM long-press unit tests pass
- [x] Deployed live probes healthy; `/demo` sim end-to-end EN+FA; real device untouched (audit clean)
