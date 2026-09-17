# Tasks: Restart-Safe Period Rollover (spec-012)

## 1. App implementation

- [x] 1.1 `Prefs.kt`: add `periodStart` / `setPeriodStart` (key `period_start`, default 0L) with the spec-012 comment
- [x] 1.2 `WatchdogService.kt` `onCreate`: seed `lastPeriod` from the persisted `period_start`; absent ⇒ seed+persist current period (safe migration, FR-002)
- [x] 1.3 `WatchdogService.kt` `cycle()`: restructure the rollover block — read `effectiveUsage(nowPeriod)` first; limit-latch release gated on `fresh >= 0` (FR-004); pending state retries without advancing `lastPeriod`/`period_start`, logged once per streak (D4); resolved path persists `period_start` (FR-003) and keeps today's unlatch/keep semantics for all reasons
- [x] 1.4 `app/build.gradle.kts`: versionCode 12, versionName "1.4.1"
- [x] 1.5 `CloudLink.kt`: UA strings `DataGuard-Android/1.4.1` (2 sites)

## 2. Tests (release gate)

- [x] 2.1 `ShadowNetworkStatsManager.kt` (test tree): `@Implements` returning a real empty Bucket; static `failQueries` knob; reset in test setup/teardown
- [x] 2.2 `RolloverRestartTest` T1–T6 per the plan's verification matrix, driving the real service cold start
- [x] 2.3 Full suite green: LaunchSmokeTest (2), TabSwitchSmokeTest, HistoryUiTest, GuardStateUiTest, RolloverRestartTest (6)

## 3. Build & verify

- [x] 3.1 `./gradlew test` (all variants) green
- [x] 3.2 `assembleRelease` + `assembleQa` clean; apksigner verify both (same cert as previous releases)
- [x] 3.3 Badging: prod vc=12 vn=1.4.1, QA vc=12 vn=1.4.1-test; UA string present in prod dex
- [x] 3.4 No test commands or probes to the real device (constraint: never touch the real device)

## 4. Converge

- [x] 4.1 README: version-history row v1.4.1
- [x] 4.2 spec-001: deployed-state bullet + roadmap e2e item version bump (v1.4.1)
- [x] 4.3 spec-012: status Implemented; all task + checklist boxes checked
- [x] 4.4 Worklog append (Task ID 24) per template
- [x] 4.5 Commit + push (Art. IV token hygiene), tags `v1.4.1` + `v1.4.1-test9`
- [x] 4.6 GitHub Releases via persisted script `scripts/release_v141.py` (prod + QA prerelease); assets verified uploaded
- [x] 4.7 Art. XI verdict delivered to the owner: APP UPDATE REQUIRED; Worker/dashboard untouched
