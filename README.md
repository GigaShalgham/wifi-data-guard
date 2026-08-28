🛡 Wi-Fi Data Guard

A Kotlin Android app that locks a phone's internet when its dailyWi-Fi limit (e.g. 750 MB) is reached — built for my little brother 😈Unlocking requires a PIN only I know.
Features

    Real-time usage counter (TrafficStats, ~1s refresh)
    Auto-lock the moment the limit is crossed
    Two lock modes: VPN hard-lock or Wi-Fi soft-lock
    PIN-gated settings with brute-force cooldown
    Configurable unlock timer (5/15/30/60 min)
    Bilingual UI: English / Persian 🇮🇷
    Built-in logger

Tech

Kotlin • VpnService • TrafficStats • DevicePolicyManager
Build

Open in Android Studio → Build → Build APK(s).Min SDK 26.
Notes

My first Android project — feedback welcome! 🙌