package xyz.mdhv.formanalyser.wellness

import xyz.mdhv.formanalyser.wellness.WellnessConstants as K

enum class ReadinessLevel { QUIET, REST_ADVISED, CAUTION, READY }

/** An ACTIVE injury as the readiness cascade sees it (v3, Phase 3). */
data class InjurySummary(val regions: List<String>, val severity: Int)

/** Inputs to the readiness cascade. All optional — absent inputs simply don't fire clauses. */
data class ReadinessInput(
    val hiatusActive: Boolean = false,
    val acwr: Double? = null,
    val energy: Int? = null,
    val sleep: Int? = null,
    val sorenessRegionCount: Int = 0,
    val activeLifeEventMaxImpact: Int? = null,
    val latestCheckinAgeHours: Double? = null,
    /** v3 additive — ACTIVE injuries only. Reasons, never lockouts. */
    val activeInjuries: List<InjurySummary> = emptyList(),
)

data class ReadinessResult(val level: ReadinessLevel, val reasons: List<String>)

/**
 * Readiness v2 (Phase 2 §A5) — a deterministic precedence cascade. The first section with a firing
 * clause sets the level; every firing clause contributes a reason. Life-event impact emits a
 * *context* reason ("high-stress window"), never event content. v3 (Phase 3) adds injuries as an
 * additive input. Missing data never crashes the cascade.
 */
object Readiness {
    private data class Fired(val level: ReadinessLevel, val reason: String)

    fun assess(input: ReadinessInput): ReadinessResult {
        val fired = mutableListOf<Fired>()

        if (input.hiatusActive) fired += Fired(ReadinessLevel.QUIET, "On pause")

        input.acwr?.let { if (it > K.ACWR_CAUTION_HI) fired += Fired(ReadinessLevel.REST_ADVISED, "High acute load (ACWR ${fmt(it)})") }
        input.energy?.let { if (it <= K.ENERGY_REST) fired += Fired(ReadinessLevel.REST_ADVISED, "Very low energy") }
        input.activeInjuries.filter { it.severity >= 3 }.forEach {
            fired += Fired(ReadinessLevel.REST_ADVISED, "Active injury (severity 3): ${it.regions.joinToString()}")
        }
        input.activeInjuries.filter { it.severity == 2 }.forEach {
            fired += Fired(ReadinessLevel.CAUTION, "Active injury: ${it.regions.joinToString()}")
        }

        input.acwr?.let {
            if (it in K.ACWR_SWEET_HI..K.ACWR_CAUTION_HI) fired += Fired(ReadinessLevel.CAUTION, "Load climbing (ACWR ${fmt(it)})")
        }
        input.sleep?.let { if (it <= K.SLEEP_CAUTION) fired += Fired(ReadinessLevel.CAUTION, "Poor sleep") }
        input.energy?.let { if (it == K.ENERGY_CAUTION) fired += Fired(ReadinessLevel.CAUTION, "Low energy") }
        if (input.sorenessRegionCount >= K.SORENESS_CAUTION) fired += Fired(ReadinessLevel.CAUTION, "Soreness in ${input.sorenessRegionCount} regions")
        input.activeLifeEventMaxImpact?.let { if (it >= K.LIFE_IMPACT_CAUTION) fired += Fired(ReadinessLevel.CAUTION, "high-stress window") }
        input.latestCheckinAgeHours?.let { if (it > K.STALENESS_H) fired += Fired(ReadinessLevel.CAUTION, "No recent check-in — log one for a truer read") }

        if (fired.isEmpty()) return ReadinessResult(ReadinessLevel.READY, listOf("Ready to train"))

        val level = fired.minBy { it.level.ordinal }.level
        return ReadinessResult(level, fired.map { it.reason })
    }

    private fun fmt(x: Double): String = String.format("%.2f", x)
}
