package xyz.mdhv.crocodyl.bouldering

import xyz.mdhv.crocodyl.engine.model.FeatureVector
import xyz.mdhv.crocodyl.engine.sport.FeatureExtractor
import xyz.mdhv.crocodyl.engine.sport.RepSegmenter
import xyz.mdhv.crocodyl.engine.sport.ScoringRubric
import xyz.mdhv.crocodyl.engine.sport.SportModule

/** Grade systems stay separate: labels are never converted to a supposedly universal number. */
enum class ClimbingGradeSystem(val id: String, val label: String) {
    V_SCALE("v_scale", "V scale"),
    FONT("font", "Font"),
    UK("uk", "UK"),
    CUSTOM("custom", "Custom"),
}

enum class BoulderStyle(val id: String, val label: String) {
    SLAB("slab", "Slab"),
    VERTICAL("vertical", "Vertical"),
    OVERHANG("overhang", "Overhang"),
    ROOF("roof", "Roof"),
    UNKNOWN("unknown", "Unspecified"),
}

enum class BoulderOutcome(val id: String, val label: String) {
    TOP("top", "Topped"),
    ZONE("zone", "Zone reached"),
    FALL("fall", "Fell before zone"),
    WALKED_AWAY("walked_away", "Stopped attempt"),
}

/** A manually recorded problem visit. Grade, style and outcome are athlete-entered observations. */
data class BoulderProblemAttempt(
    val id: String,
    val gradeSystem: ClimbingGradeSystem,
    val gradeLabel: String,
    val style: BoulderStyle,
    val attempts: Int,
    val outcome: BoulderOutcome,
    val perceivedEffortRpe: Int? = null,
) {
    init {
        require(id.isNotBlank()) { "problem id is required" }
        require(gradeLabel.isNotBlank() && gradeLabel.length <= 24) { "grade label must be 1..24 characters" }
        require(attempts in 1..100) { "attempt count must be 1..100" }
        require(perceivedEffortRpe == null || perceivedEffortRpe in 1..10) { "RPE must be 1..10" }
    }

    val isSent: Boolean get() = outcome == BoulderOutcome.TOP
    val reachedZone: Boolean get() = outcome == BoulderOutcome.TOP || outcome == BoulderOutcome.ZONE
}

object BoulderingModule : SportModule<List<BoulderProblemAttempt>, BoulderProblemAttempt> {
    override val sportId: String = "bouldering"
    override val segmenter: RepSegmenter<List<BoulderProblemAttempt>, BoulderProblemAttempt> =
        RepSegmenter { problems -> problems.toList() }

    override val extractor: FeatureExtractor<BoulderProblemAttempt> = object : FeatureExtractor<BoulderProblemAttempt> {
        override val featureNames: List<String> = listOf(
            "attempt_count",
            "zone_reached",
            "problem_topped",
            "perceived_effort_rpe",
        )

        override fun extract(rep: BoulderProblemAttempt): FeatureVector = buildMap {
            put("attempt_count", rep.attempts.toDouble())
            put("zone_reached", if (rep.reachedZone) 1.0 else 0.0)
            put("problem_topped", if (rep.isSent) 1.0 else 0.0)
            rep.perceivedEffortRpe?.let { put("perceived_effort_rpe", it.toDouble()) }
        }
    }

    override val scoring: ScoringRubric = object : ScoringRubric {
        override val maxScore: Double = 1.0
        override fun isValid(score: Double): Boolean = score == 0.0 || score == 1.0
    }

    override val deviationWeights: Map<String, Double> = mapOf(
        "attempt_count" to 1.0,
        "zone_reached" to 0.6,
        "problem_topped" to 1.0,
        "perceived_effort_rpe" to 0.5,
    )
    override val fatigueMetrics: Map<String, Boolean> = mapOf("perceived_effort_rpe" to false)
}
