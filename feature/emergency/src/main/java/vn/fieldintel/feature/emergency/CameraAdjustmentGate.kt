package vn.fieldintel.feature.emergency

/**
 * A tap invalidates capture immediately. CameraX focus completion is asynchronous; a short
 * exposure settling window follows success (or a bounded focus timeout).
 * All times use the same monotonic clock and stale callbacks are ignored by generation.
 */
internal class CameraAdjustmentGate(
    private val focusTimeoutNanos: Long = 1_500_000_000L,
    private val exposureSettleNanos: Long = 350_000_000L
) {
    internal enum class State { IDLE, FOCUSING, EXPOSURE_SETTLING, READY }

    private var generation = 0L
    private var startedAt = 0L
    private var settlingAt = 0L
    private var state = State.IDLE

    @Synchronized fun reset() {
        generation++
        state = State.IDLE
        startedAt = 0L
        settlingAt = 0L
    }

    @Synchronized fun begin(nowNanos: Long): Long {
        generation++
        startedAt = nowNanos
        settlingAt = 0L
        state = State.FOCUSING
        return generation
    }

    @Synchronized fun focusCompleted(token: Long, nowNanos: Long, successful: Boolean) {
        if (token != generation || state != State.FOCUSING || !successful) return
        settlingAt = nowNanos
        state = State.EXPOSURE_SETTLING
    }

    @Synchronized fun state(nowNanos: Long): State {
        if (state == State.FOCUSING && nowNanos - startedAt >= focusTimeoutNanos) {
            settlingAt = nowNanos
            state = State.EXPOSURE_SETTLING
        }
        if (state == State.EXPOSURE_SETTLING && nowNanos - settlingAt >= exposureSettleNanos) {
            state = State.READY
        }
        return state
    }

    @Synchronized fun ready(nowNanos: Long): Boolean =
        state(nowNanos) == State.IDLE || state == State.READY
}
