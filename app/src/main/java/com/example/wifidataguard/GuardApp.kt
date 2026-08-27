package com.example.wifidataguard

import android.app.Application
import android.util.Log

class GuardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                Logger.d(this, "CRASH in ${t?.name}: ${Log.getStackTraceString(e)}")
            } catch (_: Throwable) {}
            default?.uncaughtException(t, e)
        }
    }
}