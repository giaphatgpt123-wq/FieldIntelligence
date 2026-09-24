package vn.fieldintel.domain.emergency

enum class PositionHealth { GOOD, DEGRADED, POOR, LOST, SUSPECT }

data class PositionFix(
    val latitude: Double,
    val longitude: Double,
    val horizontalAccuracyM: Double?,
    val wallTimeMs: Long,
    val monotonicTimeMs: Long
)

data class PositionAssessment(
    val current: PositionFix?,
    val lastReliable: PositionFix?,
    val health: PositionHealth,
    val fixAgeMs: Long?,
    val reason: String
)

class PositionHealthEvaluator(
    private val goodAccuracyM: Double = 20.0,
    private val degradedAccuracyM: Double = 50.0,
    private val poorAccuracyM: Double = 100.0,
    private val staleAfterMs: Long = 30_000,
    private val lostAfterMs: Long = 120_000,
    private val maxPlausibleSpeedMps: Double = 70.0
) {
    fun assess(
        current: PositionFix?,
        lastReliable: PositionFix?,
        nowMonotonicMs: Long
    ): PositionAssessment {
        if (current == null) {
            return PositionAssessment(null, lastReliable, PositionHealth.LOST, null, "NO_CURRENT_FIX")
        }
        val age = (nowMonotonicMs - current.monotonicTimeMs).coerceAtLeast(0)
        if (age > lostAfterMs) return PositionAssessment(current, lastReliable, PositionHealth.LOST, age, "FIX_TOO_OLD")
        if (age > staleAfterMs) return PositionAssessment(current, lastReliable, PositionHealth.POOR, age, "STALE_FIX")

        if (lastReliable != null && current.monotonicTimeMs > lastReliable.monotonicTimeMs) {
            val dtSec = (current.monotonicTimeMs - lastReliable.monotonicTimeMs) / 1000.0
            val distanceM = haversineM(lastReliable, current)
            if (dtSec > 0 && distanceM / dtSec > maxPlausibleSpeedMps) {
                return PositionAssessment(current, lastReliable, PositionHealth.SUSPECT, age, "IMPLAUSIBLE_JUMP")
            }
        }

        val accuracy = current.horizontalAccuracyM
            ?: return PositionAssessment(current, lastReliable, PositionHealth.POOR, age, "ACCURACY_UNKNOWN")
        val health = when {
            accuracy <= goodAccuracyM -> PositionHealth.GOOD
            accuracy <= degradedAccuracyM -> PositionHealth.DEGRADED
            accuracy <= poorAccuracyM -> PositionHealth.POOR
            else -> PositionHealth.SUSPECT
        }
        return PositionAssessment(current, lastReliable, health, age, "ACCURACY_CLASSIFICATION")
    }

    fun reliableCandidate(assessment: PositionAssessment): PositionFix? =
        if (assessment.health == PositionHealth.GOOD || assessment.health == PositionHealth.DEGRADED) assessment.current
        else assessment.lastReliable

    private fun haversineM(a: PositionFix, b: PositionFix): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(a.latitude)
        val p2 = Math.toRadians(b.latitude)
        val dp = Math.toRadians(b.latitude - a.latitude)
        val dl = Math.toRadians(b.longitude - a.longitude)
        val h = kotlin.math.sin(dp / 2) * kotlin.math.sin(dp / 2) +
            kotlin.math.cos(p1) * kotlin.math.cos(p2) *
            kotlin.math.sin(dl / 2) * kotlin.math.sin(dl / 2)
        return 2 * r * kotlin.math.atan2(kotlin.math.sqrt(h), kotlin.math.sqrt(1 - h))
    }
}
