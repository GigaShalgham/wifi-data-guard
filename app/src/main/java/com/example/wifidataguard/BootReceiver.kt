package com.example.wifidataguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs.monitoring(context)) {
            ContextCompat.startForegroundService(
                context, Intent(context, WatchdogService::class.java))
        }
        // No hidden state to clean anymore — the rule engine decides purely by usage.
    }
}