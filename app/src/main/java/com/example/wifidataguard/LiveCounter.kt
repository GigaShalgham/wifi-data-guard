package com.example.wifidataguard

import android.net.TrafficStats
import java.util.concurrent.atomic.AtomicLong

/**
 * REAL-TIME usage counter from kernel counters (TrafficStats).
 * Deltas are counted only while Wi-Fi is the active network.
 * Updates instantly (no Android stats lag).
 */
object LiveCounter {

    private val live = AtomicLong(0L)
    private var lastRx = Long.MIN_VALUE
    private var lastTx = Long.MIN_VALUE

    /** Call every ~1s. */
    fun poll(wifiActive: Boolean) {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        if (rx == TrafficStats.UNSUPPORTED.toLong() ||
            tx == TrafficStats.UNSUPPORTED.toLong()) return

        if (lastRx == Long.MIN_VALUE || lastTx == Long.MIN_VALUE) {
            lastRx = rx; lastTx = tx; return
        }

        val dRx = rx - lastRx
        val dTx = tx - lastTx
        lastRx = rx; lastTx = tx

        if (dRx < 0 || dTx < 0) return   // counter reset (reboot) -> skip
        if (wifiActive && (dRx > 0 || dTx > 0)) live.addAndGet(dRx + dTx)
    }

    fun currentBytes(): Long = live.get()

    fun resetToZero() {
        live.set(0L)
        lastRx = TrafficStats.getTotalRxBytes()
        lastTx = TrafficStats.getTotalTxBytes()
    }

    /** Seed once (service create / new period) from authoritative stats. */
    fun seedWith(bytes: Long) {
        if (bytes > live.get()) live.set(bytes)
        lastRx = TrafficStats.getTotalRxBytes()
        lastTx = TrafficStats.getTotalTxBytes()
    }
}