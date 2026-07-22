package com.example.netspeedoverlay.speed

import android.net.TrafficStats
import java.util.Locale
import kotlin.math.max

/**
 * Samples total device RX/TX bytes via [TrafficStats] — a public Android
 * API, no root and no special permission needed — and turns two consecutive
 * samples into a bytes/sec reading.
 *
 * NOTE ON TETHERING/HOTSPOT TRAFFIC:
 * On Qualcomm-based devices, tethering/hotspot traffic is routed via
 * "IPA" (Internet Protocol Accelerator) hardware offload. This hardware
 * bypasses the CPU entirely, forwarding packets directly between the modem
 * and the Wi-Fi chip. Consequently, this traffic is completely invisible to
 * the CPU in real-time, and won't be counted by [TrafficStats] or /proc/net/dev.
 *
 * Disabling "Tethering hardware acceleration" in Developer Options allows the
 * packets to pass through the network interfaces of the CPU, but due to how
 * Android forwards packets at the IP/routing layer, they still bypass the
 * local-socket level eBPF filters used by [TrafficStats], meaning they cannot
 * be measured in real-time by this app on non-root devices.
 */
class SpeedSampler {

    private var lastRxBytes: Long = -1L
    private var lastTxBytes: Long = -1L
    private var lastTimestampMs: Long = -1L

    data class Sample(
        val rxBytesPerSec: Long,
        val txBytesPerSec: Long,
        val rxBytesDelta: Long,
        val txBytesDelta: Long
    )

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
                txBytesPerSec = (txDelta / elapsedSec).toLong(),
                rxBytesDelta = rxDelta,
                txBytesDelta = txDelta
            )
        }

        lastRxBytes = rx
        lastTxBytes = tx
        lastTimestampMs = now
        return result
    }

    companion object {
        /** Auto-scales between B, KB and MB, one decimal once past KB. */
        fun format(bytesPerSec: Long, showPerSecondSuffix: Boolean, compactUnit: Boolean = false): String {
            val suffix = if (showPerSecondSuffix) "/s" else ""
            val unit = if (compactUnit) "K" else "KB"
            val mbUnit = if (compactUnit) "M" else "MB"
            return when {
                bytesPerSec == 0L -> {
                    val sep = if (compactUnit) "" else " "
                    "0$sep$unit$suffix"
                }
                bytesPerSec < 1024 -> {
                    val num = String.format(Locale.US, "%.1f", bytesPerSec / 1024.0)
                    val sep = if (compactUnit && num.length >= 3) "" else " "
                    "$num$sep$unit$suffix"
                }
                bytesPerSec < 1024 * 1024 -> {
                    val num = String.format(Locale.US, "%.0f", bytesPerSec / 1024.0)
                    val sep = if (compactUnit && num.length >= 3) "" else " "
                    "$num$sep$unit$suffix"
                }
                else -> {
                    val num = String.format(Locale.US, "%.1f", bytesPerSec / (1024.0 * 1024.0))
                    val sep = if (compactUnit && num.length >= 3) "" else " "
                    "$num$sep$mbUnit$suffix"
                }
            }
        }

        /**
         * Very short form for tight spaces like a notification icon: no
         * unit suffix, single-letter scale (K/M), 3-4 characters max.
         */
        fun formatCompact(bytesPerSec: Long): String = when {
            bytesPerSec == 0L -> "0K"
            bytesPerSec < 1024 -> String.format(Locale.US, "%.1fK", bytesPerSec / 1024.0)
            bytesPerSec < 1024 * 1024 -> "${bytesPerSec / 1024}K"
            else -> String.format(Locale.US, "%.1fM", bytesPerSec / (1024.0 * 1024.0))
        }

        /**
         * Formatta un conteggio di byte assoluto (non un rate/sec) come i
         * totali giornalieri di DailyUsageRepository — scala fino ai GB,
         * a differenza di format()/formatCompact() che si fermano a MB
         * perché una VELOCITÀ in GB/s non è realistica su un device.
         */
        fun formatTotalBytes(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
