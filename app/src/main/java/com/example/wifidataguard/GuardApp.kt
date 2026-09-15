package com.example.wifidataguard

import android.app.Application
import android.util.Log

class GuardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Test builds carry the ".test" package suffix — flip on the virtual
        // clock there (release builds stay 100% real-time).
        AppClock.testBuild = packageName.endsWith(".test")
        if (AppClock.testBuild)
            Logger.d(this, "TEST build active: virtual clock + test panel + injections")
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                Logger.d(this, "CRASH in ${t?.name}: ${Log.getStackTraceString(e)}")
            } catch (_: Throwable) {}
            default?.uncaughtException(t, e)
        }
    }
}