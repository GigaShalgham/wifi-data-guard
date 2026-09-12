# Wi-Fi Data Guard

![Release](https://img.shields.io/github/v/release/GigaShalgham/wifi-data-guard)
![License: MIT](https://img.shields.io/badge/license-MIT-green)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)
![API 26+](https://img.shields.io/badge/API-26%2B-3DDC84?logo=android&logoColor=white)

Wi-Fi Data Guard is a parental-control app for Android that **monitors Wi-Fi data usage
in real time and locks the internet connection the moment a daily or monthly quota is
exhausted** — for example, a 750 MB daily cap. It keeps counting even after reboots,
re-arms itself automatically after every unlock window, and all security-sensitive
settings are protected by a PIN that only the parent knows.

It is deliberately lightweight: no accounts, no analytics, and it works fully
offline. New in v1.3, the app can *optionally* pair with the parent's cloud
dashboard for remote control — leaving the app unpaired changes nothing.

## Table of contents

- [Features](#features)
- [How it works](#how-it-works)
- [Installation](#installation)
- [First-run setup](#first-run-setup)
- [Everyday use](#everyday-use)
- [Lock modes](#lock-modes)
- [Security model](#security-model)
- [Cloud parent dashboard (optional)](#cloud-parent-dashboard-optional)
- [Known limitations](#known-limitations)
- [Building from source](#building-from-source)
- [Project structure](#project-structure)
- [Troubleshooting & FAQ](#troubleshooting--faq)
- [Version history](#version-history)
- [راهنمای سریع فارسی](#راهنمای-سریع-فارسی)
- [License](#license)

## Features

**Usage metering**
- Live counter refreshed every second via `TrafficStats` kernel deltas.
- Authoritative per-period figures from `NetworkStatsManager` (the same source Android
  Settings uses) as a self-correcting baseline.
- Daily or monthly period, automatic reset at midnight / first of month.
- Manual "reset counter" action for starting a fresh period on demand.

**Enforcement**
- Lock triggers the instant usage reaches the limit — no polling gaps.
- Two lock modes (see [Lock modes](#lock-modes)): soft (turn Wi-Fi off) and
  hard (VPN tunnel that drops all traffic), with automatic fallback between them.
- Lock state is latched and persisted — survives reboots (`BootReceiver` re-arms the
  watchdog) and cannot be cleared by force-stopping the app.

**Security**
- Salted SHA-256 PIN gate on every sensitive action (disable guard, unlock, change
  hard mode, change unlock duration while armed).
- 5 wrong PIN attempts trigger a 5-minute cooldown.
- Backups disabled and PIN hash excluded from any backup/transfer channel.

**Interface**
- Bilingual UI: English and Persian (فارسی) with full RTL support.
- Status notification with live usage, grace countdown and lock state.
- On-device rolling log for diagnosing what the guard did and when.

**Cloud (optional, v1.3)**
- Pair the device with the parent dashboard using a 6-digit code.
- Parents lock/unlock the device and change limits remotely.
- Usage reports reach the dashboard every ~30 seconds.
- **Fail-closed:** if the server is unreachable past the tolerance window
  (default 10 minutes), the guard locks by itself.
- Clock-tamper defense: rolling the clock back trips the lock.
- The lock never wedges itself (v1.3.1): the blocking VPN tunnel excludes
  the guard app's own traffic, so usage reports and remote **unlock**
  commands keep flowing even while every other app is blocked.

## How it works

1. **Arm** — `MainActivity` verifies the checklist (PIN set, usage access granted,
   limit configured, VPN consent for hard mode) and starts the watchdog.
2. **Watch** — `WatchdogService`, a foreground service, ticks every second:
   `LiveCounter` accumulates `TrafficStats` deltas while Wi-Fi is up, while
   `DataStats` seeds the counter with authoritative figures for the current period.
3. **Latch** — when usage crosses the limit, the lock *latches*: the state is written
   to persistent storage so killing the app or rebooting does not clear it.
4. **Enforce** — soft mode silently switches Wi-Fi off (device owner required);
   hard mode routes every packet into a local TUN interface
   (`198.18.0.1`) that drops them all.
5. **Unlock** — the parent enters the PIN and picks a grace window
   (5 / 15 / 30 / 60 minutes, or until the period ends). The latch is kept, so the
   guard re-arms itself automatically when the window expires — or instantly via
   "lock again now".
6. **Rollover** — at midnight (daily mode) or on the first of the month (monthly
   mode) the counter, the latch and any grace window are reset for a fresh period.

## Installation

Download the latest signed APK from the
[Releases](https://github.com/GigaShalgham/wifi-data-guard/releases) page and install
it on the device ("install unknown apps" permission is required for sideloading).

Requirements:

| Requirement | Detail |
|---|---|
| Android | 8.0 (API 26) or newer |
| Permissions | Usage access (for `NetworkStatsManager`); VPN consent on first arm |
| Optional | Device-owner provisioning for the soft lock mode (see below) |

> **Upgrading from v1.0/v1.1:** those releases shipped debug-signed APKs. v1.2 and
> later are release-signed with a new key, so uninstall the old version first (app
> settings reset during the swap).

## First-run setup

1. **Set a PIN** — Settings → "Set PIN". This is the parent's key; there is no
   recovery path, so pick one you will not forget.
2. **Grant usage access** — the app links you to the system screen.
3. **Set the limit** — e.g. `750` MB, and choose daily or monthly reset.
4. **Pick the unlock duration** — how long a PIN-unlock suspends enforcement.
5. *(optional, recommended for soft mode)* **Provision device owner** from a computer:

   ```bash
   adb shell dpm set-device-owner com.example.wifidataguard/.AdminReceiver
   ```

   The device must have no Google accounts added for provisioning to succeed
   (this is an Android platform rule). On Android 15+ you may need
   `--user 0` appended. The in-app "Device-owner guide" button copies the exact
   command to your clipboard.

6. Flip the **monitor switch**. The watchdog notification appears and counting starts.

## Everyday use

- The main screen shows a live checklist (PIN / usage access / owner / armed) and the
  current usage bar, e.g. `412.3 MB / 750.0 MB (55%)`.
- **Unlock** — PIN → traffic flows for the chosen window → auto re-arm. "Lock again
  now" cancels the window immediately.
- **Reset counter** — PIN-gated; useful when the billing period is not aligned with
  midnight.
- **Language toggle** — switches the whole UI between English and Persian.
- **Logs** — the log viewer shows recent guard decisions (arm, latch, rollover,
  fallback, re-arm).

## Lock modes

| | Soft mode | Hard mode |
|---|---|---|
| Mechanism | `WifiManager.setWifiEnabled(false)` via device owner | `VpnService` TUN interface that drops every packet |
| Works without device owner | Falls back to the VPN blocker automatically | Yes |
| Coverage | Wi-Fi radio off — mobile data unaffected | All IP traffic (see note below) |
| Best for | Wi-Fi-only tablets, owner-provisioned devices | Devices without owner provisioning, or where all traffic must stop |

> Note: hard mode blocks *all* network traffic, not only Wi-Fi. It is the strictest
> interpretation of "no internet until the quota resets" and is what makes the guard
> meaningful on devices without device-owner provisioning.

## Security model

- **PIN storage** — random 128-bit salt + SHA-256, stored only in private
  preferences; never logged, never backed up (`allowBackup=false`, plus explicit
  backup/data-extraction exclusion rules).
- **Brute force** — 5 consecutive failures lock the PIN prompt for 5 minutes.
- **Tampering** — the latch lives in persistent storage; force-stop, swipe-away and
  reboot all leave the guard armed (`BootReceiver` restarts the watchdog).
- **Device owner** — when provisioned, user restrictions (e.g. no Wi-Fi config
  changes) are applied while the guard is on (`lockdown()`/`relax()`).

## Cloud parent dashboard (optional)

v1.3 adds **CloudLink**, an opt-in bridge to the Wi-Fi Data Guard cloud — a
Cloudflare Worker with a D1 database serving a bilingual parent dashboard at
`https://wifi-data-guard.gigaspaceturnip.workers.dev`. The feature is dormant
until the parent pairs the device; an unpaired app behaves exactly like v1.2.

**Pairing.** On the dashboard the parent creates a device entry and gets a
6-digit code valid for a short window. On the phone: ☁ button → enter the code →
the device exchanges it for a long-lived token (64-hex) stored only in private
preferences. From then on the watchdog thread polls the server roughly every
30 seconds (with jitter) and applies whatever the parent commands.

**Remote commands.** `lock` (optionally with a reason shown on the device),
`unlock` (minutes), and `config` (limit, period, hard mode, unlock duration,
offline tolerance, poll interval). Cloud values are authoritative when they
change; local tweaks keep working in between. Remote locks survive the daily
rollover, and revoking the device from the dashboard returns the phone to
local-only mode on the next poll.

**Fail-closed, not fail-open.** The whole design assumes the child might pull
the plug on connectivity. If the device cannot reach the server for longer than
`offline_tolerance_min`, the guard latches the lock by itself. Likewise, the
server returns its own clock on every response; if the device clock is rolled
back relative to it, the guard latches too. Unpairing on the device requires
the parent PIN.

**Privacy.** The device sends usage numbers (used/limit bytes), lock/grace
state, battery percentage, app version and the device name chosen during
pairing — nothing else. No browsing history, no location, no packet contents.
Tokens are never logged and never leave private storage.

## Known limitations

Honest disclosure for a parental-control tool:

- System clock manipulation backwards can stretch a grace window (mitigated in
  v1.3 *while cloud-paired*, via the server-time check); a period jump forward
  triggers an early rollover.
- Cloud-paired devices need periodic connectivity; after the offline tolerance
  window the guard locks by design (that is the fail-closed trade-off).
- If another VPN is already connected, Android will not hand the tunnel to the
  blocker until that VPN disconnects.
- Android Settings → "clear app data" wipes the PIN and the latch (the device-owner
  restrictions survive, if provisioned).
- A forgotten PIN has no recovery: without owner lockdown, uninstall/reinstall;
  with owner lockdown, a factory reset is required.
- Mobile-data usage is metered only in hard mode's "all traffic" interpretation;
  the quota counter tracks Wi-Fi usage (as measured behind the VPN).

## Building from source

Prerequisites: JDK 21, Android SDK (platform 37 is fetched automatically by AGP if
missing), no IDE required.

```bash
git clone https://github.com/GigaShalgham/wifi-data-guard.git
cd wifi-data-guard
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # release APK (see signing below)
```

Release signing is opt-in via a `keystore.properties` file at the repo root (git-
ignored), so everyone can build without secrets:

```properties
storeFile=keystore/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

**Test build ("time machine").** `./gradlew assembleQa` produces an APK with
the `.test` package suffix that installs *alongside* the real app and unlocks a
🧪 **Test panel**:

- a **virtual clock** with 1x–3600x time scale — grace windows, midnight/month
  rollover and PIN cooldowns can be observed in seconds instead of hours
  (connectivity deadlines stay on the real clock, so a reachable device never
  fakes "offline");
- **usage injection** (+100 MB / +500 MB / 99% of limit / zero) to trip the
  limit latch instantly;
- **simulated offline** and a **backdated-sync** button to exercise the
  fail-closed lock deterministically;
- **clock-rollback simulation** (below the last server time) to trip the
  tamper defense;
- **verbose poll logs** (HTTP status, latency, acks, commands) with in-app
  viewing and one-tap sharing.

Release builds contain none of this behavior — the virtual clock returns real
time and the panel is hidden when the package suffix is absent.

Stack: Kotlin (built-in Kotlin support in AGP 9), Gradle 9.5 wrapper, minSdk 26,
targetSdk 33, compileSdk 37. Mirror-first Maven repositories with official
`google()`/`mavenCentral()` fallbacks.

## Project structure

```
app/src/main/java/com/example/wifidataguard/
├── GuardApp.kt          # Application: notification channel + log init
├── MainActivity.kt      # Setup UI, checklist, PIN gate, bilingual strings
├── WatchdogService.kt   # Foreground service: 1 s supervision loop
├── BlockerVpnService.kt # Hard lock: TUN interface dropping all packets
├── OwnerEnforcer.kt     # Soft lock: device-owner Wi-Fi switching + lockdown
├── AdminReceiver.kt     # DeviceAdminReceiver for owner provisioning
├── BootReceiver.kt      # Re-arms the watchdog after reboot
├── LiveCounter.kt       # TrafficStats delta meter (1 s cadence)
├── DataStats.kt         # NetworkStatsManager authoritative usage source
├── Prefs.kt             # Persistent state: limits, latch, PIN hash
├── CloudLink.kt         # Optional cloud bridge: pair, poll, ack, fail-closed
└── Logger.kt            # Rolling on-device log (guard_log.txt)
```

## Troubleshooting & FAQ

**The switch flips itself back off.** The pre-arm checklist failed (no PIN, no usage
access, no limit) — the toast tells you which. VPN consent denial also reverts it.

**Wi-Fi doesn't actually turn off when locked (Android 10+).** Non-owner apps lost
that permission in Android 10; the guard automatically falls back to the VPN blocker,
and the log records the fallback.

**Usage looks wrong right after midnight.** Fixed in v1.2 — the counter now truly
resets at rollover instead of carrying the previous period's bytes.

**Does it count mobile data?** The quota is Wi-Fi usage. Hard mode blocks all traffic
during a lock, but only Wi-Fi is metered.

**Battery impact?** One foreground service waking once per second with a throttled
notification; negligible on modern devices.

**Where are the logs?** In-app log viewer; the file lives in app-private storage and
is excluded from backups.

## Version history

| Version | Highlights |
|---|---|
| [v1.3.1](https://github.com/GigaShalgham/wifi-data-guard/releases/tag/v1.3-test2) | Hard-lock control-channel survival (VPN tunnel exempts the guard app: remote unlock + live reports during a lock), honest soft-lock fallback notification |
| [v1.3](https://github.com/GigaShalgham/wifi-data-guard/releases/tag/v1.3) | CloudLink: optional cloud pairing, remote lock/unlock/config, ~30 s usage reports, fail-closed offline lock, clock-tamper defense, PIN-gated unpair |
| [v1.2](https://github.com/GigaShalgham/wifi-data-guard/releases/tag/v1.2) | Ultra-debug pass: unlock re-arms, true rollover reset, soft-lock VPN fallback, PIN-gated hard mode / unlock duration, backup hardening, first signed release build |
| [v1.1](https://github.com/GigaShalgham/wifi-data-guard/releases/tag/v1.1) | Unlock timer, rollover reset, block banner |
| [v1.0](https://github.com/GigaShalgham/wifi-data-guard/releases/tag/v1.0) | Initial release: live counter, hard/soft lock, PIN, bilingual UI |

## راهنمای سریع فارسی

۱. آخرین APK امضاشده را از بخش [Releases](https://github.com/GigaShalgham/wifi-data-guard/releases) دانلود و نصب کنید (اندروید ۸ به بالا).
۲. رمز (PIN) تعیین کنید، دسترسی Usage access را بدهید و سهمیه (مثلاً ۷۵۰ مگابایت روزانه) را ذخیره کنید.
۳. کلید «محافظ» را روشن کنید — از این لحظه مصرف وای‌فای لحظه‌ای شمرده می‌شود و با رسیدن به حد، اینترنت قفل می‌شود.
۴. برای باز کردن موقت، رمز را وارد کنید؛ پس از پایان مهلت، قفل خودکار دوباره فعال می‌شود.
۵. برای قفل نرم (خاموش کردن خودکار وای‌فای) یک‌بار Device Owner را با adb از روی کامپیوتر فعال کنید؛ در غیر این صورت قفل سخت (VPN) خودکار استفاده می‌شود.
۶. (اختیاری، نسخه ۱.۳) با دکمه ☁ و کد ۶ رقمی داشبورد والدین، گوشی را به داشبورد وصل کنید تا قفل/بازکردن و تغییر سهمیه از راه دور ممکن شود؛ اگر اینترنت قطع طولانی شود، قفل خودکار فعال می‌شود (fail-closed).

**نکته ارتقا:** اگر نسخه ۱.۰/۱.۱ را نصب دارید، اول آن را حذف کنید (امضای نسخه‌های جدید متفاوت است).

## License

Released under the [MIT License](LICENSE).
