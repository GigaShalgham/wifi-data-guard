# Implementation Plan: 010-ux-revolution

## Approach

Two independent deliverables in one spec (same owner complaint, different artifacts):

- **Part A — worker-only** (live on deploy, no APK): unlock picker, settings-field
  removal, battery glyph fix, SW bump, `/api/child/history` endpoint.
- **Part B — app v1.4.0** (needs APK install): structural UI restructure
  (tabs + ring + history + parent redesign).

Implementation order: Part A first (the history endpoint unblocks Part B's chart),
then Part B, then verification gates, then release/deploy.

## Part A — Dashboard/Worker design decisions

**D1. Picker as a single overlay element.** `renderApp()` adds `<div id="pickWrap"
class="hidden"></div>` at the end of `.wrap`. The timed-unlock click handler calls
`openPicker(d)` which fills it with the picker markup and wires chip/custom/confirm/
cancel. One element, re-filled per open — no double-open, no listener leaks (onclick
reassignment, same pattern as the rest of the file). Backdrop click = cancel.

**D2. Chip model.** Presets `[15,30,60,120]`. Selection state in a local var seeded
from `rememberedMins(d)` = `localStorage["unlockMin:"+d.id]` → `minsFor(d.settings)`
(server setting, clamped 1–480, invalid→15). If the seed matches no chip, the custom
field holds it and no chip is selected. Confirm: custom field wins if it parses
(1–480, else 15); chip click clears the custom field; custom typing deselects chips.
On confirm: save `localStorage`, close, send command with picked minutes.

**D3. Command path unchanged.** Same POST `{type:"unlock", payload:{minutes:m}}`,
same `pend` + `trackRefresh()` flow, same `toastTimedFor` — only the source of `m`
changes (picker instead of saved setting).

**D4. Settings panel simplification.** Remove the `cfg-unlock` field, the `unlock`
draft key, its `bindCfg`, and the `unlock_minutes` payload entry. The grid drops to
4 fields (limit, period, tolerance, hard). The device `settings_json` keeps whatever
`unlock_minutes` it already has (read-only seed for D2, still pushed to old apps).

**D5. History endpoint math (pure function `bucketDaily`).**
```
rows = SELECT ts, used_bytes FROM usage_reports WHERE device_id=? AND ts >= now-(days+1)*864e5 ORDER BY ts ASC LIMIT 2400
lastByDay = Map(epochDay -> last used_bytes seen)   // ASC scan, later writes overwrite
for k in [todayLocal-(days-1) .. todayLocal]:
  cur=lastByDay.get(k); prev=lastByDay.get(k-1)
  used = cur==null ? 0 : (prev==null ? null : max(0, cur-prev))
  emit {day:k, used_bytes:used}
```
`todayLocal = floor((now + tz*60000)/86400000)`. The extra baseline day comes free
from the `days+1` fetch window. `null` (data but no baseline) → app draws a dashed
"no data" bar. Named function `__name`-wrapped like the rest of the bundle, routed
at `GET /api/child/history` **before** the static handler, authed via
`deviceFromToken` (existing helper). Query is bounded (LIMIT 2400 ≈ 16 days of
10-min reports) and indexed by `(device_id, ts)`.

**D6. i18n.** New keys: `pickTitle`, `pickCustom`, `pickConfirm`, `pickCancel`,
`pickMin` (chip label "%d min"), `timedUnlock` (button). Retire: `unlockMin`,
`confirmUnlockFor`. FA translations with `faNum` digits at render time. Key-parity
test stays in the verify script.

## Part B — App design decisions

**D7. Fragment-less tabs.** Root `LinearLayout(vertical)`: `FrameLayout#tabHost`
(weight=1) containing three `ScrollView` children (`tabStatus`, `tabUsage`,
`tabParent`), plus `BottomNavigationView#bottomNav`. Tab switch = visibility toggle
+ cross-fade (animate alpha 0→1 on the incoming content, 160 ms, reduce-motion
snap). No fragments → no lifecycle surprises, Robolectric-friendly, and the
existing single-Activity logic (uiTick, GlassUi stack) keeps working unchanged.

**D8. Theme move to MaterialComponents.** `Theme.DataGuard` parent →
`Theme.MaterialComponents.NoActionBar` (dark) so `BottomNavigationView` gets its
default styles; all existing color items stay; Launch/Dialog overlays untouched.
Bottom nav styled via `app:itemIconTint`/`itemTextColor` selectors (g_txt3 → g_acc).

**D9. UsageRingView.** Custom View, ~200 lines: `setUsage(usedBytes, limitBytes,
state: S)` → computes pct, picks color set (ok/warn/bad/grace), rebuilds
`SweepGradient(colored→transparent)` and animates `sweep` 0→pct with a
`ValueAnimator` (~800 ms, DecelerateInterpolator) only when pct changed ≥1 since the
last animation. Center content drawn in `onDraw`: big `NN%` (bold, tabular), small
`used / limit` line, both via `Paint`. Track ring at 12% white. Reduce-motion (via
`animationsEnabled()` callback from the activity) → snap. Halo/legend stay outside.

**D10. HistoryBarView.** Custom View: `setDays(days: List<Day>)` where
`Day(label, bytes: Long?, isToday, overLimit)`; draws rounded-top bars scaled to
max, today outlined, over-limit bars red, `null` days dashed-outline empty, value
labels (compact MB/GB) above bars, weekday letters below. Entrance animation: bars
grow bottom-up once per data set (reduce-motion snap). Pure label math in a
`HistoryUi` object (JVM-testable).

**D11. History fetch + cache.** `CloudLink.fetchHistory(c, cb)` on the existing
single-thread pool: `GET $BASE/api/child/history?days=7&tz=<offsetMin>` with bearer
token; tz from `TimeZone.getDefault().getOffset(now)/60000` (matches server math).
Response cached in prefs (`history_cache` JSON + `history_cache_at` real-clock ms).
Usage tab enter → render cache instantly (if any) → fetch → re-render + update
"updated X ago". Unpaired → hint card. All failures silent- degrade (cosmetic).

**D12. Unlock-duration chips replace the Spinner.** Horizontal `LinearLayout` of
toggle-styled `Button`s (bg_chip / bg_chip_on drawables): 5m/15m/30m/60m/∞(end of
period). Tap: if monitoring → PIN gate (existing `guarded()`) then
`Prefs.setUnlockMinutes`; selection visuals via `suppressChips` guard (simple, no
adapter echo class at all — the v1.3.6 spinner hazard is deleted with the Spinner).

**D13. PIN gate on Parent tab.** `bottomNav.setOnItemSelectedListener`: Parent
selected & !`parentAuthed` → run `guarded()`-equivalent: no PIN set & unarmed →
pass through; PIN set → `askPin()`; fail → `revertTab()` with suppression armed
BEFORE `setSelectedItemId` (lesson from spec-009 Defect 2). Success → `parentAuthed
= true` for the session. The gate is skipped when `TestMode.active` (QA friction)
— no: keep the gate in QA too (same behavior everywhere; Art. VI parity).

**D14. Entrance/ripple polish.** `bg_aurora.xml` (layer-list: 3 radial gradients
indigo/cyan/violet over `g_bg`) replaces `bg_glow` as window background. Ripple
drawables `btn_primary_rip`/`btn_unlock_rip`/`btn_glass_rip`/`btn_cloud_rip`/
`btn_test_rip` wrap the existing gradient bodies in `<ripple>`. Count-up on
`tvUsageBig`: `ValueAnimator` 400 ms when the displayed value changes by ≥1 MB
(reduce-motion snap).

**D15. Version + UA.** versionCode 11, versionName 1.4.0, UA
`DataGuard-Android/1.4.0` (CloudLink.httpPost header).

## Verification gates (release-blocking)

1. `node --check cloud/worker.js` + extracted-DASHBOARD_JS check (existing harness
   pattern, scripts/verify_spec010.js).
2. Pure JS tests: `bucketDaily` matrix (normal week / reset mid-week / missing
   baseline / tz=+1260 day-boundary / empty DB / clamps), `rememberedMins` seed
   chain, i18n key parity, retired-string sweep, spec-004/005/008 regression
   markers.
3. App unit tests: existing `GuardStateUiTest` + `LaunchSmokeTest` (fresh + armed)
   + new `TabSwitchSmokeTest` (switch all tabs, force draw) — `testDebugUnitTest`.
4. APK build (release + qa), apksigner verify, badging (versionCode/Name, UA in
   dex).
5. Live deploy probes (200/401/markers) + agent-browser dashboard smoke (picker
   open/confirm on the /demo sim device ONLY — never the real device).

## Risks & mitigations

- **Material theme reskin side effects** (checkbox/switch colors shift): colors are
  pinned via existing theme items + explicit view attributes; smoke test forces a
  full draw pass.
- **History data sparseness**: device 11 polls every ~10 min → a day has ~144 rows;
  even a paired-yesterday device renders honest `null`/0 bars per D5.
- **D1 picker on tiny screens**: max-width 420px, chips wrap (flex-wrap), tested at
  360px in agent-browser.
