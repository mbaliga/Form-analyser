package xyz.mdhv.crocodyl.engine.sport

import xyz.mdhv.crocodyl.engine.model.FeatureVector

/**
 * THE SEAM. Everything sport-specific lives behind this interface so the engine core
 * (baseline, deviation, fatigue, correlation, sync) never imports anything archery.
 * Fencing / pistol / hangboard each ship one implementation and slot in without touching core.
 *
 * A module is three things the handoff §2 calls out — a rep-segmenter, a feature extractor,
 * and a scoring rubric.
 *
 * @param TInput the raw capture stream the sport segments (e.g. a pose sequence for the
 *               vision app, or an IMU time-series for the sensor add-on). Opaque to the engine.
 * @param TRaw   the module's own raw-rep type (e.g. one segmented archery shot). Opaque to the
 *               engine; produced by [segmenter] and consumed by [extractor].
 */
interface SportModule<TInput, TRaw> {
    /** Stable id, e.g. "archery". */
    val sportId: String

    val segmenter: RepSegmenter<TInput, TRaw>
    val extractor: FeatureExtractor<TRaw>
    val scoring: ScoringRubric

    /**
     * Per-feature weights for the deviation summary. Lets a sport encode which signals matter
     * most (e.g. the alignment features that most determine archery form).
     * Features not listed default to 1.0.
     */
    val deviationWeights: Map<String, Double> get() = emptyMap()

    /**
     * Which features the sport believes degrade with fatigue, and their polarity
     * (true == higher-is-fresher). Drives [xyz.mdhv.crocodyl.engine.fatigue.FatigueTracker].
     */
    val fatigueMetrics: Map<String, Boolean> get() = emptyMap()
}

/** Splits a continuous capture stream of type [TInput] into discrete raw reps. */
fun interface RepSegmenter<TInput, TRaw> {
    fun segment(input: TInput): List<TRaw>
}

/** Turns one raw rep into a named feature vector the engine can baseline & score. */
interface FeatureExtractor<TRaw> {
    /** The features this extractor can produce. Stable, used for UI and baseline keys. */
    val featureNames: List<String>
    fun extract(rep: TRaw): FeatureVector
}

/** Describes a sport's outcome scale (the ground truth correlation is built against). */
interface ScoringRubric {
    /** Best possible single-rep score (e.g. 10 for a 10-ring target, X counts as 10). */
    val maxScore: Double
    /** Whether [score] is a legal value on this scale. */
    fun isValid(score: Double): Boolean
}
