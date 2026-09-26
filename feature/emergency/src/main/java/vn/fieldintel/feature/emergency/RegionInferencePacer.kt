package vn.fieldintel.feature.emergency

/**
 * Chooses a conservative live-inference cadence so slower models do not keep the CPU/GPU busy
 * continuously. The camera preview still runs normally; only expensive model calls are paced.
 *
 * The maximum interval is intentionally capped at 500 ms. RegionFrameStabilityGate needs several
 * observations before auto-capture, so keeping this cap prevents a stale cached classification from
 * surviving an entire capture window without a fresh model pass.
 */
object RegionInferencePacer {
    const val MIN_INTERVAL_NANOS = 250_000_000L
    const val MAX_INTERVAL_NANOS = 500_000_000L

    fun nextIntervalNanos(
        inferenceDurationNanos: Long,
        detections: List<VisualDetection>
    ): Long {
        val duration = inferenceDurationNanos.coerceAtLeast(0L)
        val hasVerifying = detections.any { it.regionVerificationProgress() != null }
        val hasStable = detections.any { it.isStableRegionCandidate() }

        val stateInterval = when {
            hasVerifying -> MIN_INTERVAL_NANOS
            hasStable -> 350_000_000L
            detections.isEmpty() -> 400_000_000L
            else -> 300_000_000L
        }

        // Leave roughly one inference-duration of idle time after a heavy pass.
        val loadInterval = (duration * 2L).coerceIn(MIN_INTERVAL_NANOS, MAX_INTERVAL_NANOS)
        return maxOf(stateInterval, loadInterval).coerceIn(MIN_INTERVAL_NANOS, MAX_INTERVAL_NANOS)
    }

    fun shouldRun(
        frameTimestampNanos: Long,
        lastInferenceTimestampNanos: Long,
        intervalNanos: Long
    ): Boolean {
        if (lastInferenceTimestampNanos == Long.MIN_VALUE) return true
        if (frameTimestampNanos <= lastInferenceTimestampNanos) return true
        return frameTimestampNanos - lastInferenceTimestampNanos >= intervalNanos
    }
}
