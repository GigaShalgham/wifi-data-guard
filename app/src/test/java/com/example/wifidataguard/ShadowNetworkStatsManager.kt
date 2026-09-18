package com.example.wifidataguard

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * spec-012 test shadow: Robolectric has NO NetworkStatsManager support
 * (getSystemService returns null -> DataStats reads fail -> effectiveUsage=-1).
 * Injected per-test via setSystemService + @Config(shadows=...).
 *
 *  - default: a REAL empty Bucket with rx=0/tx=0 => authoritative zero usage
 *    (Bucket's field defaults are -1/UNDEFINED, which would read as -2 -> "stats
 *    unavailable"; reflection zeroes them).
 *  - failQueries = true: throw SecurityException => simulated revoked access.
 */
@Implements(NetworkStatsManager::class)
class ShadowNetworkStatsManager {

    companion object {
        @JvmStatic var failQueries = false

        /** Bytes the fake NetworkStats will report for a full-period query. */
        @JvmStatic var wifiBytes: Long = 0L
    }

    @Implementation
    fun querySummaryForDevice(networkType: Int, subscriberId: String?,
                              startTime: Long, endTime: Long): NetworkStats.Bucket {
        if (failQueries) throw SecurityException("simulated stats failure (spec-012)")
        return bucketWith(wifiBytes)
    }

    private fun bucketWith(bytes: Long): NetworkStats.Bucket {
        val b = NetworkStats.Bucket()
        // rx = bytes, tx = 0 => effectiveUsage() == wifiBytes exactly
        for (f in listOf("mRxBytes", "mTxBytes")) {
            try {
                NetworkStats.Bucket::class.java.getDeclaredField(f)
                    .apply { isAccessible = true }
                    .set(b, if (f == "mRxBytes") bytes else 0L)
            } catch (_: Throwable) { /* field layout changed? default -1 reads as unavailable */ }
        }
        return b
    }
}
