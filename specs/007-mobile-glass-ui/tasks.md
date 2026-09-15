# Tasks: 007-mobile-glass-ui

## 1. Spec
- [x] T001 spec.md (4 user stories, FR-001..007, failure modes; clarify inherited from spec-006 selections)
- [x] T002 plan.md (resource-level restyle + GlassUi helper, no enforcement changes)

## 2. Implementation
- [x] T003 Design tokens: colors.xml palette, Theme.DataGuard dark glass + Launch theme, status/nav bar
- [x] T004 Drawables: bg_glow, bg_splash, glass cards (+highlight), gradient/ripple buttons (primary, unlock green/amber, tools, cloud, test, danger), progress track + 3 gradient fills, dots + halos, dialog bg, edittext bg, spinner bg/popup, toast bg
- [x] T005 activity_main.xml rebuild: hero status (dot+halo+title), usage block, checklist card, settings card, unlock pill, tools card, footer — ALL existing ids preserved
- [x] T006 dialog_pin.xml + spinner_item.xml + dialog_test.xml glass restyle
- [x] T007 GlassUi.kt: toast stack (in-app + system fallback), chime (AudioTrack, lock/unlock/arm pairs), vibrate, entrance-once, halo breathe, reduce-motion gate, resumed flag; GuardStateUi.kt pure state machine
- [x] T008 MainActivity wiring: hero dot/countdown bindings, progress drawable swap, state-transition detector → GlassUi (single source; unlockFlow toasts deduped), toast() rerouted to GlassUi, onCreate entrance, onResume/onPause halo lifecycle, onDestroy cleanup
- [x] T009 Version bump: versionCode 9, versionName 1.3.5, UA DataGuard-Android/1.3.5; WatchdogService notification accent color
- [x] T010 Unit tests: GuardStateUi matrix (11 tests: precedence, baseline-no-fire, 6 edges, bilingual labels, chime pairs)

## 3. Verification
- [x] T011 ./gradlew test (11/11) + assembleQa + assembleRelease — zero errors
- [x] T012 Badging (vc=9, vn=1.3.5 / 1.3.5-test, package ids) + apksigner verify on both APKs (keystore c8abfd91…)
- [x] T013 Static review sweep: all 48 findViewById ids resolve (26 main + 22 dialogs); diff scope = planned files only; secret scan clean

## 4. Converge
- [ ] T014 README (mobile UI section + version-history row); spec-001 deployed state + roadmap renumber (008 security, 009 play-store, 010 e2e-v1.3.5)
- [ ] T015 Commit, tag v1.3.5 + v1.3.5-test6, GitHub Releases with both APKs; worklog; owner report (Art. XI: app update REQUIRED, worker untouched)
