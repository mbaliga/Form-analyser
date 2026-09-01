package xyz.mdhv.formanalyser.wellness

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import xyz.mdhv.formanalyser.wellness.WellnessConstants as K

enum class CyclePhase { MENSTRUAL, FOLLICULAR, OVULATORY, LUTEAL }

/** Always carries uncertainty + the ESTIMATE tag — consuming copy is pattern-discovery framed. */
data class CycleEstimate(
    val cycleLengthDays: Double,
    val uncertaintyDays: Double,
    val dayInCycle: Int,
    val phase: CyclePhase,
    val isEstimate: Boolean = true,
)

/**
 * Cycle estimator (Phase 2 §A6). Gated behind ≥ 3 completed cycles. cycle_len = median of the last
 * ≤ 6 start-to-start intervals; uncertainty = MAD of those intervals. Phase from day-in-cycle.
 * Pattern discovery, never prescription.
 */
object CycleEstimator {
    fun estimate(
        cycleStarts: List<LocalDate>,
        today: LocalDate,
        bleedLengthDays: Int = K.DEFAULT_BLEED_LEN,
    ): CycleEstimate? {
        val starts = cycleStarts.distinct().sorted()
        if (starts.size < 2) return null
        val intervals = starts.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toDouble() }
        if (intervals.size < K.CYCLE_GATE) return null

        val window = intervals.takeLast(K.CYCLE_WINDOW)
        val cycleLen = Stats.median(window)
        val uncertainty = Stats.mad(window)

        val latestStart = starts.last()
        val dayInCycle = (ChronoUnit.DAYS.between(latestStart, today) + 1).toInt().coerceAtLeast(1)

        val ovulatory = cycleLen - K.LUTEAL_OFFSET_DAYS
        val phase = when {
            dayInCycle <= bleedLengthDays -> CyclePhase.MENSTRUAL
            dayInCycle in (ovulatory - K.OVULATORY_WINDOW).toInt()..(ovulatory + K.OVULATORY_WINDOW).toInt() -> CyclePhase.OVULATORY
            dayInCycle < ovulatory -> CyclePhase.FOLLICULAR
            else -> CyclePhase.LUTEAL
        }
        return CycleEstimate(cycleLen, uncertainty, dayInCycle, phase)
    }
}
