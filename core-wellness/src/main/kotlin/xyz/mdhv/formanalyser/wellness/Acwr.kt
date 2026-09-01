package xyz.mdhv.formanalyser.wellness

import java.time.LocalDate
import xyz.mdhv.formanalyser.wellness.WellnessConstants as K

enum class AcwrZone { DETRAINING, SWEET, CAUTION, SPIKE }

data class AcwrPoint(
    val date: LocalDate,
    val acuteEwma: Double,
    val chronicEwma: Double,
    val acwr: Double?,        // null when chronic below guard
    val zone: AcwrZone?,
)

data class AcwrSeries(
    val points: List<AcwrPoint>,
    val warmupComplete: Boolean,
    val calendarDays: Int,
    val trainingDays: Int,
) {
    val latest: AcwrPoint? get() = points.lastOrNull()
    /** Days remaining in the calendar warm-up (for the "building your baseline (d/21)" copy). */
    val warmupDaysElapsed: Int get() = calendarDays.coerceAtMost(K.WARMUP_DAYS)
}

/**
 * ACWR via EWMA (Phase 2 §A2). A continuous daily series is built from the first session day to
 * [today] (missing days are real zeros); acute/chronic EWMAs run over it; the ratio is gated
 * behind a warm-up window and a chronic-denominator guard.
 */
object Acwr {
    fun zoneOf(acwr: Double): AcwrZone = when {
        acwr < K.ACWR_DETRAIN_HI -> AcwrZone.DETRAINING
        acwr <= K.ACWR_SWEET_HI -> AcwrZone.SWEET
        acwr <= K.ACWR_CAUTION_HI -> AcwrZone.CAUTION
        else -> AcwrZone.SPIKE
    }

    fun compute(
        dailyLoads: List<DailyLoad>,
        today: LocalDate,
        loadOf: (DailyLoad) -> Double = { it.shotLoad },
    ): AcwrSeries {
        if (dailyLoads.isEmpty()) return AcwrSeries(emptyList(), warmupComplete = false, calendarDays = 0, trainingDays = 0)
        val sorted = dailyLoads.sortedBy { it.date }
        val byDate = sorted.associateBy { it.date }
        val first = sorted.first().date
        val days = Stats.datesInclusive(first, today)

        var acute = 0.0
        var chronic = 0.0
        var seeded = false
        val points = ArrayList<AcwrPoint>(days.size)
        for (d in days) {
            val load = byDate[d]?.let(loadOf) ?: 0.0
            if (!seeded) {
                acute = load
                chronic = load
                seeded = true
            } else {
                acute = K.LAMBDA_ACUTE * load + (1 - K.LAMBDA_ACUTE) * acute
                chronic = K.LAMBDA_CHRONIC * load + (1 - K.LAMBDA_CHRONIC) * chronic
            }
            val acwr = if (chronic < K.CHRONIC_GUARD) null else acute / chronic
            points.add(AcwrPoint(d, acute, chronic, acwr, acwr?.let(::zoneOf)))
        }

        val trainingDays = sorted.count { loadOf(it) > 0.0 }
        val calendarDays = days.size
        val warmup = calendarDays >= K.WARMUP_DAYS && trainingDays >= K.WARMUP_TRAINING_DAYS
        return AcwrSeries(points, warmup, calendarDays, trainingDays)
    }
}
