package com.example.netspeedoverlay.speed

import android.net.TrafficStats
import kotlin.math.max

/**
 * Samples total device RX/TX bytes via [TrafficStats] — a public Android
 * API, no root and no special permission needed beyond normal internet
 * access — and turns two consecutive samples into a bytes/sec reading.
 * This is the same primitive CustoMIUIzer's hook reads from; the
 * difference is entirely in how the number gets drawn on screen.
 */
class SpeedSampler {

    private var lastRxBytes: Long = -1L
    private var lastTxBytes: Long = -1L
    private var lastTimestampMs: Long = -1L

    data class Sample(val rxBytesPerSec: Long, val txBytesPerSec: Long)

    /**
     * Call this on a timer. Returns null on the very first call (nothing to
     * diff against yet) or if TrafficStats is unsupported on this device.
     */
    fun sample(): Sample? {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val now = System.currentTimeMillis()

        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) {
            return null
        }

        val result = if (lastTimestampMs < 0) {
            null
        } else {
            val elapsedSec = max(0.001, (now - lastTimestampMs) / 1000.0)
            val rxDelta = (rx - lastRxBytes).coerceAtLeast(0)
            val txDelta = (tx - lastTxBytes).coerceAtLeast(0)
            Sample(
                rxBytesPerSec = (rxDelta / elapsedSec).toLong(),
                txBytesPerSec = (txDelta / elapsedSec).toLong()
            )
        }

        lastRxBytes = rx
        lastTxBytes = tx
        lastTimestampMs = now
        return result
    }

    companion object {
        /** Auto-scales between B, KB and MB, one decimal once past KB. */
        fun format(bytesPerSec: Long, showPerSecondSuffix: Boolean): String {
            val suffix = if (showPerSecondSuffix) "/s" else ""
            return when {
                bytesPerSec < 1024 -> "$bytesPerSec B$suffix"
                bytesPerSec < 1024 * 1024 -> String.format("%.0f KB%s", bytesPerSec / 1024.0, suffix)
                else -> String.format("%.1f MB%s", bytesPerSec / (1024.0 * 1024.0), suffix)
            }
        }
    }
}
