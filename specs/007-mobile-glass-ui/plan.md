# Implementation Plan: 007-mobile-glass-ui

## Decisions

1. **Pure resource-level restyle + one new UI helper; enforcement code is
   off-limits.** All visuals come from `res/` (drawables, layouts, theme,
   colors) and a small `GlassUi.kt` helper (toast stack, chime, vibrate,
   entrance/pulse animations, reduce-motion check). `MainActivity.refreshUi()`
   keeps its exact truth sources (`LiveCounter`, `Prefs.graceUntil`,
   `WatchdogService.latched`) and gains: hero dot/title/countdown bindings,
   progress-bar drawable swap, and a transition check that calls `GlassUi`.
   `WatchdogService`, `CloudLink`, `BlockerVpnService`, `AppClock`, `Prefs`,
   `TestMode` are NOT touched (Art. II/VII/IX). The only WatchdogService
   change is `.setColor(0xFF6C7CFF.toInt())` on the two notification builders.
2. **Glass without blur.** Android has no cheap backdrop blur; the illusion
   is: window-level gradient-glow background (`bg_glow.xml` layer-list: base
   vertical gradient + indigo radial glow top-start + cyan radial glow
   bottom-end) + semi-translucent card fill (`#D9101A2E`-class, ~85 % alpha)
   + 1 dp stroke `#33415F` + 22 dp radius + 6 % white top-edge highlight.
   Cards keep readability (fill alpha high enough); glow bleeds through.
3. **State hero = same truth, richer surface.** The hero row sits inside the
   usage card: dot (12 dp) + halo (animated) + status title (12 sp caps,
   like today) + big usage number + sub + gradient bar + status/checklist
   text below. Dot variants are drawables (`dot_protected` steady green with
   soft glow, `dot_grace` amber, `dot_latched` red) — swapped in
   `refreshUi()`'s existing `when` branches. Countdown text reuses the
   unlock button text already computed (`T.rearm`) — no second clock.
4. **Toast system.** `GlassUi.toast(ctx, msg, kind)`:
   - Activity resumed → in-app stack: a `LinearLayout` overlay added once via
     `addContentView` (bottom|center_horizontal, RTL-safe), max 3 items,
     slide-up + fade in ~260 ms, auto-dismiss 3.5 s with fade-out, tap to
     dismiss, all callbacks on a private handler cleared in `onDestroy`.
   - Activity paused → system `Toast` with the same glass-styled item view
     (no animation). Detect via a `resumed` flag set in onResume/onPause.
   - **Transition chime policy (Art. VIII)**: `GlassUi.chime(kind)` fires
     only from `onGuardStateChanged` transitions computed as
     `prev != next` on a sealed state (`PROTECTED / GRACE / BLOCKED`), plus
     explicit arm/disarm toasts that already exist today (they just gain the
     chime + glass look). Remote locks land here naturally: poll → latch →
     next 1 s tick sees `WatchdogService.latched`.
5. **Chime via AudioTrack** (no assets, no permissions): 44.1 kHz mono 16-bit
   PCM, two 140 ms sine notes with 20 ms cosine ramps (no clicks); lock =
   330→220 Hz falling, unlock = 440→660 Hz rising (identical pairs to the
   dashboard), arm = one soft 440 Hz/180 ms note. Volume modest (0.25
   amplitude), wrapped in try/catch, skipped when `audioManager.ringerMode`
   is silent. Vibration: `VibrationEffect` one-shot 80 ms (unlock) /
   waveform 0-60-50-60 (lock), guarded, only on transitions.
6. **Animations.** Entrance-once in `onCreate` (after `setContentView`):
   only when `Settings.Global.ANIMATOR_DURATION_SCALE > 0`, zero the alpha
   of the four glass cards + unlock button, then staggered (~45 ms)
   `translateY(28dp→0) + alpha(0→1)`, 300 ms, `withEndAction` guarantees
   alpha=1. Dot halo breathe: one `ObjectAnimator`
   (scaleX/Y 1→1.9 + alpha .45→0, 1600 ms, restart on reverse) started in
   `onResume`, cancelled in `onPause`; never started when reduce-motion.
   Nothing animates inside `refreshUi()`.
7. **Versioning/UA:** `versionCode` 9, `versionName` "1.3.5",
   `CloudLink` UA `DataGuard-Android/1.3.5`. QA unchanged (`.test` suffix).
8. **Splash + chrome:** `Theme.DataGuard` gains
   `statusBarColor/navigationBarColor #070D1A`,
   `windowLightStatusBar=false`; `Theme.DataGuard.Launch` (parent = main
   theme) overrides only `windowBackground` → `bg_splash` (glow + centered
   launcher foreground, inset 120 dp) and is set on `MainActivity` in the
   manifest; content covers it once inflated (no theme switching code).

## Risks & mitigations

- **Layout rewrite breaks a binding** → every existing `findViewById` id is
  preserved verbatim in the new XML; compile + `assembleQa/Release` is the
  hard gate; unit tests still pass (`./gradlew test`).
- **Animation leaves views invisible** (interrupted) → alpha zeroed only
  when animations are available, `withEndAction` + `onWindowFocusChanged`
  safety reset to alpha 1.
- **Custom toasts on exotic ROMs** → per-toast try/catch; system-toast
  fallback path always available; background messages always system toast.
- **RTL mirroring** → start/end paddings everywhere, translations vertical
  only, FA strings through the existing `T` map + `\u` escapes in edit
  scripts where needed; countdown format unchanged (`%d:%02d`, LTR-safe).
- **Persian glyph rendering in toasts** → system default font renders FA
  (as today); toast text colors ≥ 4.5:1 on the glass fill.
- **Silent/vibrate modes** → chime suppressed by ringer mode; toast+vibrate
  still fire.
- **QA panel** (`dialog_test.xml`) restyled cosmetically only — buttons keep
  ids/handlers; virtual-clock behavior untouched.

## Delivery & disclosure (Art. XI)

- **App-side only: REQUIRES installing a new APK** — production
  `v1.3.5-release.apk` (replaces the real app) or QA `v1.3.5-test6.apk`
  (alongside, `.test`). **Worker/dashboard: no changes — already live and
  untouched** (SW stays dg-v5). Signed with the v1.3 keystore, tags
  `v1.3.5` + `v1.3.5-test6`, GitHub Releases with notes (Art. X).
