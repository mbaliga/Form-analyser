package xyz.mdhv.formanalyser.athlete

import kotlin.math.abs
import kotlin.math.sqrt

/** Goal metrics are stable persistence keys. New metrics are additive. */
enum class GoalMetric {
    ROUND_TOTAL,
    ARROW_AVERAGE,
    VOLUME_ARROWS,
    FORM_STABILITY,
    TRAINING_SESSIONS,
    PHYSIO_ADHERENCE,
}

enum class GoalDirection {
    AT_LEAST,
    AT_MOST,
}

enum class GoalAggregation {
    LATEST,
    MAX,
    MIN,
    SUM,
    AVERAGE,
}

enum class GoalState {
    ACTIVE,
    ACHIEVED,
    PAUSED,
    ARCHIVED,
}

data class GoalDefinition(
    val id: String,
    val metric: GoalMetric,
    val title: String,
    val targetValue: Double,
    val unit: String,
    val direction: GoalDirection,
    val aggregation: GoalAggregation,
    val startAtMs: Long,
    val targetAtMs: Long? = null,
    val baselineValue: Double? = null,
    /**
     * Optional round, rig, feature or other dimension. Domain-specific filtering happens upstream.
     */
    val scopeKey: String? = null,
) {
    init {
        require(id.isNotBlank())
        require(title.isNotBlank())
        require(targetValue.isFinite())
        require(unit.isNotBlank())
        require(startAtMs >= 0)
        require(targetAtMs == null || targetAtMs >= startAtMs)
    }
}

data class MetricObservation(val atMs: Long, val value: Double, val sourceId: String) {
    init {
        require(atMs >= 0)
        require(value.isFinite())
        require(sourceId.isNotBlank())
    }
}

data class GoalProgress(
    val currentValue: Double?,
    val progressFraction: Double?,
    val achieved: Boolean,
    val sampleCount: Int,
    val remaining: Double?,
)

object GoalEngine {
    fun evaluate(goal: GoalDefinition, observations: List<MetricObservation>): GoalProgress {
        val values = observations.filter { it.atMs >= goal.startAtMs }.sortedBy { it.atMs }
        if (values.isEmpty()) return GoalProgress(null, null, false, 0, null)
        val current =
            when (goal.aggregation) {
                GoalAggregation.LATEST -> values.last().value
                GoalAggregation.MAX -> values.maxOf { it.value }
                GoalAggregation.MIN -> values.minOf { it.value }
                GoalAggregation.SUM -> values.sumOf { it.value }
                GoalAggregation.AVERAGE -> values.map { it.value }.average()
            }
        val achieved =
            when (goal.direction) {
                GoalDirection.AT_LEAST -> current >= goal.targetValue
                GoalDirection.AT_MOST -> current <= goal.targetValue
            }
        val baseline = goal.baselineValue
        val fraction =
            if (baseline != null && baseline != goal.targetValue) {
                when (goal.direction) {
                    GoalDirection.AT_LEAST ->
                        ((current - baseline) / (goal.targetValue - baseline)).coerceIn(0.0, 1.0)
                    GoalDirection.AT_MOST ->
                        ((baseline - current) / (baseline - goal.targetValue)).coerceIn(0.0, 1.0)
                }
            } else if (goal.targetValue != 0.0 && goal.direction == GoalDirection.AT_LEAST) {
                (current / goal.targetValue).coerceIn(0.0, 1.0)
            } else if (achieved) 1.0 else null
        val remaining =
            when (goal.direction) {
                GoalDirection.AT_LEAST -> (goal.targetValue - current).coerceAtLeast(0.0)
                GoalDirection.AT_MOST -> (current - goal.targetValue).coerceAtLeast(0.0)
            }
        return GoalProgress(current, fraction, achieved, values.size, remaining)
    }
}

data class TrendSummary(
    val sampleCount: Int,
    val firstValue: Double,
    val lastValue: Double,
    val mean: Double,
    /** Units of value per day. */
    val slopePerDay: Double,
    val standardDeviation: Double,
    /** 0..1. Higher means repeated values are tighter around their mean. */
    val stability: Double,
    val delta: Double,
)

/** A value paired with the instant it was recorded, for plotting against real elapsed time. */
data class TimedValue(val atMs: Long, val value: Double)

/**
 * Horizontal placement for a time series, as fractions of the plot width.
 *
 * Charts used to space points by array index, so a three-month gap and a next-day session rendered
 * the same distance apart. That is not a cosmetic difference: an index-spaced line makes a long
 * layoff invisible and makes a burst of sessions look like steady progress, which is exactly the
 * misreading the product's evidence rules exist to prevent.
 *
 * Degenerate cases are placed at the centre rather than at an edge: a single point, or several
 * recorded at the same instant, genuinely have no spread to show and pinning them left would imply
 * one.
 */
object TrendPlot {
    fun xFractions(atMs: List<Long>): List<Double> {
        if (atMs.isEmpty()) return emptyList()
        if (atMs.size == 1) return listOf(0.5)
        val min = atMs.min()
        val span = (atMs.max() - min).toDouble()
        if (span <= 0.0) return List(atMs.size) { 0.5 }
        return atMs.map { (it - min) / span }
    }
}

object TrendEngine {
    private const val DAY_MS = 86_400_000.0

    fun summarize(observations: List<MetricObservation>): TrendSummary? {
        val sorted = observations.sortedBy { it.atMs }
        if (sorted.isEmpty()) return null
        val values = sorted.map { it.value }
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        val sd = sqrt(variance)
        val stability =
            if (abs(mean) < 1e-9) {
                if (sd < 1e-9) 1.0 else 0.0
            } else {
                (1.0 - (sd / abs(mean))).coerceIn(0.0, 1.0)
            }

        val t0 = sorted.first().atMs
        val xs = sorted.map { (it.atMs - t0) / DAY_MS }
        val xMean = xs.average()
        val denom = xs.sumOf { (it - xMean) * (it - xMean) }
        val slope =
            if (denom < 1e-12) 0.0
            else {
                sorted.indices.sumOf { i -> (xs[i] - xMean) * (values[i] - mean) } / denom
            }
        return TrendSummary(
            sampleCount = values.size,
            firstValue = values.first(),
            lastValue = values.last(),
            mean = mean,
            slopePerDay = slope,
            standardDeviation = sd,
            stability = stability,
            delta = values.last() - values.first(),
        )
    }
}

/** Values available to prefill a new athlete session. Null means "we have no evidence". */
data class SessionDefaults(
    val disciplineId: String? = null,
    val rigId: String? = null,
    val venue: String? = null,
    val distanceMeters: Int? = null,
    val targetFaceCm: Int? = null,
    val arrowCount: Int? = null,
    val roundId: String? = null,
    val trainingIntent: String? = null,
)

data class SessionContextObservation(val atMs: Long, val defaults: SessionDefaults)

/**
 * "Intelligent" here deliberately means explainable local evidence, not opaque prediction: choose
 * the most recent known value independently for each field, then let an explicit pin win.
 */
object SmartDefaultsEngine {
    fun resolve(
        history: List<SessionContextObservation>,
        pinned: SessionDefaults? = null,
        activeRigId: String? = null,
    ): SessionDefaults {
        val recent = history.sortedByDescending { it.atMs }
        fun <T> latest(get: (SessionDefaults) -> T?): T? =
            recent.firstNotNullOfOrNull { get(it.defaults) }
        val inferred =
            SessionDefaults(
                disciplineId = latest { it.disciplineId },
                rigId = activeRigId ?: latest { it.rigId },
                venue = latest { it.venue },
                distanceMeters = latest { it.distanceMeters },
                targetFaceCm = latest { it.targetFaceCm },
                arrowCount = latest { it.arrowCount },
                roundId = latest { it.roundId },
                trainingIntent = latest { it.trainingIntent },
            )
        val p = pinned ?: return inferred
        return SessionDefaults(
            disciplineId = p.disciplineId ?: inferred.disciplineId,
            rigId = p.rigId ?: inferred.rigId,
            venue = p.venue ?: inferred.venue,
            distanceMeters = p.distanceMeters ?: inferred.distanceMeters,
            targetFaceCm = p.targetFaceCm ?: inferred.targetFaceCm,
            arrowCount = p.arrowCount ?: inferred.arrowCount,
            roundId = p.roundId ?: inferred.roundId,
            trainingIntent = p.trainingIntent ?: inferred.trainingIntent,
        )
    }
}

enum class BodySignal {
    PAIN,
    SORENESS,
    INJURY,
    PHYSIO_TARGET,
}

data class RegionSignal(
    val regionId: String,
    val pain: Int = 0,
    val soreness: Boolean = false,
    val injurySeverity: Int = 0,
    val physioTarget: Boolean = false,
) {
    init {
        require(regionId.isNotBlank())
        require(pain in 0..10)
        require(injurySeverity in 0..10)
    }

    /** Display intensity only; it is not a diagnosis or injury-risk probability. */
    val contextIntensity: Int
        get() = maxOf(pain, injurySeverity, if (soreness) 3 else 0)

    val signals: Set<BodySignal>
        get() = buildSet {
            if (pain > 0) add(BodySignal.PAIN)
            if (soreness) add(BodySignal.SORENESS)
            if (injurySeverity > 0) add(BodySignal.INJURY)
            if (physioTarget) add(BodySignal.PHYSIO_TARGET)
        }
}
