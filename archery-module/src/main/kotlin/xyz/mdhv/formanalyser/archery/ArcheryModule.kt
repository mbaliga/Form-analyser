package xyz.mdhv.formanalyser.archery

import xyz.mdhv.baseline.engine.sport.ScoringRubric
import xyz.mdhv.baseline.engine.sport.SportModule

/**
 * Standard target-archery 10-ring scoring. A miss is 0; X (inner 10) is scored as 10 for
 * correlation purposes (X-count is tracked separately by the app).
 */
object ArcheryScoringRubric : ScoringRubric {
    override val maxScore: Double = 10.0
    override fun isValid(score: Double): Boolean = score in 0.0..10.0 && score == kotlin.math.floor(score)
}

/**
 * The archery plug-in for the Baseline engine. This is the single object the engine talks to;
 * everything archery-specific is reachable from here, and nothing here leaks into the engine.
 *
 * The deviation weights encode the governing principle (handoff §3.3): the sensor-owned,
 * shot-to-shot signals (release, drift, cant) decide *this* arrow and are weighted above the
 * slower postural/contextual features.
 */
object ArcheryModule : SportModule<ArcheryShot> {
    override val sportId: String = "archery"

    override val segmenter = ArcheryShotSegmenter()
    override val extractor = ArcheryFeatureExtractor()
    override val scoring = ArcheryScoringRubric

    override val deviationWeights: Map<String, Double> = mapOf(
        ArcheryFeatureExtractor.RELEASE_PEAK to 2.0,
        ArcheryFeatureExtractor.PIN_DRIFT_DEG to 2.0,
        ArcheryFeatureExtractor.CANT_DEG to 1.5,
        ArcheryFeatureExtractor.STEADINESS to 1.5,
        // hold duration & tremor frequency are weaker per-shot predictors.
        ArcheryFeatureExtractor.HOLD_DURATION_S to 0.5,
        ArcheryFeatureExtractor.TREMOR_HZ to 0.5,
    )

    override val fatigueMetrics: Map<String, Boolean> = mapOf(
        // higher steadiness = fresher; steadiness decay across the session = fatigue.
        ArcheryFeatureExtractor.STEADINESS to true,
        // drift and tremor grow with fatigue.
        ArcheryFeatureExtractor.PIN_DRIFT_DEG to false,
    )
}
