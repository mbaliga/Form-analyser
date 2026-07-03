package xyz.mdhv.formanalyser.app.domain

import org.json.JSONObject
import xyz.mdhv.baseline.engine.baseline.BaselineBuilder
import xyz.mdhv.baseline.engine.baseline.BaselineModel
import xyz.mdhv.baseline.engine.deviation.DeviationResult
import xyz.mdhv.baseline.engine.deviation.DeviationScorer
import xyz.mdhv.baseline.engine.fatigue.FatigueTracker
import xyz.mdhv.baseline.engine.fatigue.FatigueTrajectory
import xyz.mdhv.baseline.engine.model.FeatureVector
import xyz.mdhv.baseline.engine.model.Rep
import xyz.mdhv.baseline.engine.sport.FeatureScoreRelation
import xyz.mdhv.baseline.engine.sport.SignalScoreCorrelation
import xyz.mdhv.formanalyser.archery.ArcheryModule
import xyz.mdhv.formanalyser.archery.FormFeatureExtractor
import xyz.mdhv.formanalyser.archery.pose.PoseSequence

/**
 * Bridges the app to the (free, vision) engine + archery module: segment/extract a captured
 * pose sequence into per-shot form features, build & score against a baseline, and surface
 * fatigue + signal->score correlation. The math lives in the modules; this is glue + JSON.
 */
object ArcheryAnalyzer {

    /** Segment a pose capture into shots and extract each shot's form feature vector. */
    fun analyze(sequence: PoseSequence): List<FeatureVector> =
        ArcheryModule.segmenter.segment(sequence).map { ArcheryModule.extractor.extract(it) }

    /** Features plus per-shot timing spans (for the idle-trimmed session duration, Phase 2 §A4). */
    data class ShotAnalysis(
        val features: List<FeatureVector>,
        val spans: List<xyz.mdhv.formanalyser.wellness.ShotSpan>,
        val recordingSeconds: Double,
    )

    fun analyzeWithSpans(sequence: PoseSequence): ShotAnalysis {
        val shots = ArcheryModule.segmenter.segment(sequence)
        val features = shots.map { ArcheryModule.extractor.extract(it) }
        val spans = shots.map {
            xyz.mdhv.formanalyser.wellness.ShotSpan(
                drawStartS = it.drawStart / it.fps,
                releaseS = it.releaseStart / it.fps,
            )
        }
        return ShotAnalysis(features, spans, sequence.durationSeconds)
    }

    fun buildBaseline(goodShots: List<FeatureVector>): BaselineModel {
        val builder = BaselineBuilder()
        goodShots.forEach { builder.add(it) }
        return builder.build()
    }

    fun score(baseline: BaselineModel, features: FeatureVector): DeviationResult =
        DeviationScorer(baseline, ArcheryModule.deviationWeights).score(features)

    /** Form degradation across the session — fatigue via bow-arm-angle decay (handoff §3.3). */
    fun fatigue(shotsInOrder: List<FeatureVector>): FatigueTrajectory? {
        val series = shotsInOrder.map { it[FormFeatureExtractor.BOW_ARM_ANGLE] ?: Double.NaN }
        return FatigueTracker.analyze(series, higherIsBetter = true)
    }

    fun correlations(reps: List<Rep>): List<FeatureScoreRelation> =
        SignalScoreCorrelation.correlate(reps)

    // ---- feature <-> JSON for storage -------------------------------------------------

    fun featuresToJson(features: FeatureVector): String {
        val obj = JSONObject()
        for ((k, v) in features) obj.put(k, v)
        return obj.toString()
    }

    fun featuresFromJson(json: String): FeatureVector {
        val obj = JSONObject(json)
        val out = LinkedHashMap<String, Double>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out[k] = obj.getDouble(k)
        }
        return out
    }
}
