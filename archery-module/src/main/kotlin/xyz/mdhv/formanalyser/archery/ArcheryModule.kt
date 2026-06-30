package xyz.mdhv.formanalyser.archery

import xyz.mdhv.baseline.engine.sport.ScoringRubric
import xyz.mdhv.baseline.engine.sport.SportModule
import xyz.mdhv.formanalyser.archery.pose.PoseSequence
import xyz.mdhv.formanalyser.archery.pose.PoseShot

/**
 * Standard target-archery 10-ring scoring. A miss is 0; X (inner 10) is scored as 10 for
 * correlation purposes (X-count is tracked separately by the app).
 */
object ArcheryScoringRubric : ScoringRubric {
    override val maxScore: Double = 10.0
    override fun isValid(score: Double): Boolean = score in 0.0..10.0 && score == kotlin.math.floor(score)
}

/**
 * The (free, vision-based) archery plug-in for the Baseline engine. Input is a pose capture
 * (`PoseSequence`); the module segments shots and extracts postural form + sequence-timing
 * features. The shot-to-shot sensor signals (drift / release / cant) are the paid Baseline
 * add-on's job, not here.
 */
object ArcheryModule : SportModule<PoseSequence, PoseShot> {
    override val sportId: String = "archery"

    override val segmenter = PoseShotSegmenter()
    override val extractor = FormFeatureExtractor()
    override val scoring = ArcheryScoringRubric

    // Alignment features that most define form are weighted above the looser ones. These are
    // first-pass weights to tune with the coach once there's real footage + scores.
    override val deviationWeights: Map<String, Double> = mapOf(
        FormFeatureExtractor.BOW_ARM_ANGLE to 2.0,
        FormFeatureExtractor.DRAW_ELBOW_ANGLE to 1.5,
        FormFeatureExtractor.DRAW_ARM_TILT to 1.5,
        FormFeatureExtractor.SPINE_LEAN to 1.5,
        FormFeatureExtractor.SHOULDER_TILT to 1.0,
        FormFeatureExtractor.HEAD_LEAN to 1.0,
        FormFeatureExtractor.STANCE_WIDTH to 0.5,
        FormFeatureExtractor.HOLD_DURATION_S to 0.5,
        FormFeatureExtractor.DRAW_DURATION_S to 0.5,
    )

    // Fatigue (form degradation across a session): the bow arm tends to drop and posture to
    // lean as the archer tires. Polarity/choice to validate with logged sessions.
    override val fatigueMetrics: Map<String, Boolean> = mapOf(
        FormFeatureExtractor.BOW_ARM_ANGLE to true,  // straighter (higher) = fresher
        FormFeatureExtractor.SPINE_LEAN to false,    // more lean = more fatigued
    )
}
