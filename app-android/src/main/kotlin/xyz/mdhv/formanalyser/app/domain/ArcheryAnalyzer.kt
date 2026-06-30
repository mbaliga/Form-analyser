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
import xyz.mdhv.baseline.engine.model.TimeSeries
import xyz.mdhv.baseline.engine.sport.FeatureScoreRelation
import xyz.mdhv.baseline.engine.sport.SignalScoreCorrelation
import xyz.mdhv.formanalyser.archery.ArcheryFeatureExtractor
import xyz.mdhv.formanalyser.archery.ArcheryModule

/**
 * Bridges the app to the (free) engine + archery module: segment/extract a captured window,
 * build & score against a baseline, and surface fatigue + signal->score correlation. All the
 * actual math lives in the modules; this is glue + JSON (de)serialisation for storage.
 */
object ArcheryAnalyzer {

    /** Segment a captured IMU window into shots and extract each shot's feature vector. */
    fun analyze(window: TimeSeries): List<FeatureVector> =
        ArcheryModule.segmenter.segment(window).map { ArcheryModule.extractor.extract(it) }

    fun buildBaseline(goodShots: List<FeatureVector>): BaselineModel {
        val builder = BaselineBuilder()
        goodShots.forEach { builder.add(it) }
        return builder.build()
    }

    fun score(baseline: BaselineModel, features: FeatureVector): DeviationResult =
        DeviationScorer(baseline, ArcheryModule.deviationWeights).score(features)

    /** Steadiness decay across the session — the fatigue story (handoff §3.3). */
    fun fatigue(shotsInOrder: List<FeatureVector>): FatigueTrajectory? {
        val steadiness = shotsInOrder.map { it[ArcheryFeatureExtractor.STEADINESS] ?: Double.NaN }
        return FatigueTracker.analyze(steadiness, higherIsBetter = true)
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
