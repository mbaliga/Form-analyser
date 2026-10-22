package xyz.mdhv.crocodyl.muaythai

import xyz.mdhv.crocodyl.engine.model.FeatureVector
import xyz.mdhv.crocodyl.engine.sport.FeatureExtractor
import xyz.mdhv.crocodyl.engine.sport.RepSegmenter
import xyz.mdhv.crocodyl.engine.sport.ScoringRubric
import xyz.mdhv.crocodyl.engine.sport.SportModule

/** Athlete-selected focus for a logged round; this is context, not a technique judgment. */
enum class MuayThaiRoundFocus(val id: String, val label: String) {
    SHADOWBOXING("shadowboxing", "Shadowboxing"),
    HEAVY_BAG("heavy_bag", "Heavy bag"),
    PADS("pads", "Pads"),
    TECHNIQUE("technique", "Technique"),
    SPARRING("sparring", "Sparring"),
}

/** One manually logged round. Values describe the session; none are inferred from video. */
data class MuayThaiRound(
    val index: Int,
    val focus: MuayThaiRoundFocus,
    val workSeconds: Int,
    val restSeconds: Int,
    val perceivedEffortRpe: Int? = null,
    /** Optional athlete self-rating, not an instructor or model score. */
    val selfRating: Int? = null,
) {
    init {
        require(index > 0) { "round index must be positive" }
        require(workSeconds in 1..900) { "work duration must be 1..900 seconds" }
        require(restSeconds in 0..600) { "rest duration must be 0..600 seconds" }
        require(perceivedEffortRpe == null || perceivedEffortRpe in 1..10) { "RPE must be 1..10" }
        require(selfRating == null || selfRating in 1..10) { "self-rating must be 1..10" }
    }
}

object MuayThaiModule : SportModule<List<MuayThaiRound>, MuayThaiRound> {
    override val sportId: String = "muay_thai"
    override val segmenter: RepSegmenter<List<MuayThaiRound>, MuayThaiRound> =
        RepSegmenter { rounds -> rounds.toList() }

    override val extractor: FeatureExtractor<MuayThaiRound> = object : FeatureExtractor<MuayThaiRound> {
        override val featureNames: List<String> = listOf(
            "work_duration_seconds",
            "rest_duration_seconds",
            "perceived_effort_rpe",
        )

        override fun extract(rep: MuayThaiRound): FeatureVector = buildMap {
            put("work_duration_seconds", rep.workSeconds.toDouble())
            put("rest_duration_seconds", rep.restSeconds.toDouble())
            rep.perceivedEffortRpe?.let { put("perceived_effort_rpe", it.toDouble()) }
        }
    }

    /** Outcome is an optional athlete self-rating on a 1–10 scale, never an automatic grade. */
    override val scoring: ScoringRubric = object : ScoringRubric {
        override val maxScore: Double = 10.0
        override fun isValid(score: Double): Boolean = score in 1.0..10.0 && score == kotlin.math.floor(score)
    }

    override val fatigueMetrics: Map<String, Boolean> = mapOf("perceived_effort_rpe" to false)
}
