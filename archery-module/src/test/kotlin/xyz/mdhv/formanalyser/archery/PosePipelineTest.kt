package xyz.mdhv.formanalyser.archery

import xyz.mdhv.baseline.engine.baseline.BaselineBuilder
import xyz.mdhv.baseline.engine.deviation.DeviationScorer
import xyz.mdhv.formanalyser.archery.pose.Handedness
import xyz.mdhv.formanalyser.archery.pose.Landmark
import xyz.mdhv.formanalyser.archery.pose.PoseFrame
import xyz.mdhv.formanalyser.archery.pose.PoseLandmarks
import xyz.mdhv.formanalyser.archery.pose.PoseSequence
import xyz.mdhv.formanalyser.archery.pose.PoseShot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Builds synthetic right-handed-archer pose frames (normalised coords, y-down). */
internal object PoseSynth {
    const val FPS = 30.0

    // Static body landmarks for a clean "good form" hold.
    private val goodBody = mapOf(
        PoseLandmarks.NOSE to (0.50 to 0.20),
        PoseLandmarks.LEFT_SHOULDER to (0.45 to 0.30),   // bow shoulder (right-handed)
        PoseLandmarks.RIGHT_SHOULDER to (0.55 to 0.30),  // draw shoulder
        PoseLandmarks.LEFT_ELBOW to (0.30 to 0.30),      // bow elbow (straight arm)
        PoseLandmarks.LEFT_WRIST to (0.16 to 0.30),      // bow wrist
        PoseLandmarks.RIGHT_ELBOW to (0.62 to 0.27),     // draw elbow, near shoulder level
        PoseLandmarks.LEFT_HIP to (0.46 to 0.55),
        PoseLandmarks.RIGHT_HIP to (0.54 to 0.55),
        PoseLandmarks.LEFT_ANKLE to (0.44 to 0.85),
        PoseLandmarks.RIGHT_ANKLE to (0.56 to 0.85),
    )

    fun frame(t: Long, drawWrist: Pair<Double, Double>, body: Map<Int, Pair<Double, Double>> = goodBody): PoseFrame {
        val lms = (0 until PoseLandmarks.COUNT).map { i ->
            when (i) {
                PoseLandmarks.RIGHT_WRIST -> Landmark(drawWrist.first, drawWrist.second)
                else -> body[i]?.let { Landmark(it.first, it.second) } ?: Landmark(0.0, 0.0)
            }
        }
        return PoseFrame(t, lms)
    }

    /** A full draw → anchor/hold → release cycle for one shot. */
    fun shotSequence(body: Map<Int, Pair<Double, Double>> = goodBody): PoseSequence {
        val frames = ArrayList<PoseFrame>()
        var t = 0L
        val dtNanos = (1e9 / FPS).toLong()
        val anchor = 0.52 to 0.22
        // draw: 30 frames from out/raised toward anchor (speed ~0.28/s, between thresholds)
        val start = 0.72 to 0.42
        val drawN = 30
        for (i in 0 until drawN) {
            val f = i.toDouble() / drawN
            frames.add(frame(t, lerp(start, anchor, f), body)); t += dtNanos
        }
        // hold: 20 frames at anchor with tiny jitter (~0.667 s)
        for (i in 0 until 20) {
            val jx = if (i % 2 == 0) 0.0005 else -0.0005
            frames.add(frame(t, anchor.first + jx to anchor.second, body)); t += dtNanos
        }
        // release: 6 frames snapping away fast (speed >> threshold)
        val relEnd = 0.70 to 0.40
        for (i in 0 until 6) {
            val f = (i + 1).toDouble() / 6
            frames.add(frame(t, lerp(anchor, relEnd, f), body)); t += dtNanos
        }
        return PoseSequence(frames, FPS)
    }

    /** A bare anchor/hold window (for testing the extractor directly). */
    fun holdShot(body: Map<Int, Pair<Double, Double>>): PoseShot {
        val anchor = 0.52 to 0.22
        val frames = (0 until 20).map { frame(it.toLong(), anchor, body) }
        val seq = PoseSequence(frames, FPS)
        return PoseShot(seq, Handedness.RIGHT, drawStart = 0, holdStart = 0, releaseStart = 20, releaseEnd = 20)
    }

    fun goodBodyPublic(): Map<Int, Pair<Double, Double>> = goodBody
    fun bentBowArm(): Map<Int, Pair<Double, Double>> = goodBody + mapOf(PoseLandmarks.LEFT_WRIST to (0.20 to 0.44))
    fun leaning(): Map<Int, Pair<Double, Double>> = goodBody + mapOf(
        PoseLandmarks.LEFT_SHOULDER to (0.40 to 0.30), PoseLandmarks.RIGHT_SHOULDER to (0.50 to 0.30),
    )

    private fun lerp(a: Pair<Double, Double>, b: Pair<Double, Double>, t: Double) =
        (a.first + (b.first - a.first) * t) to (a.second + (b.second - a.second) * t)
}

class PoseSegmenterTest {
    @Test fun findsOneShotWithOrderedPhases() {
        val shots = PoseShotSegmenter().segment(PoseSynth.shotSequence())
        assertEquals(1, shots.size)
        val s = shots[0]
        assertTrue(s.drawStart <= s.holdStart)
        assertTrue(s.holdStart < s.releaseStart)
        assertTrue(s.releaseStart < s.releaseEnd)
        assertEquals(0.667, s.holdDurationSeconds, 0.15)
    }
}

class FormFeatureTest {
    private val extractor = FormFeatureExtractor()

    @Test fun straightBowArmScoresNear180() {
        val f = extractor.extract(PoseSynth.holdShot(PoseSynth.goodBodyPublic()))
        // good body has a straight, horizontal bow arm
        assertEquals(180.0, f[FormFeatureExtractor.BOW_ARM_ANGLE]!!, 2.0)
        assertTrue(f[FormFeatureExtractor.SPINE_LEAN]!! < 2.0)
    }

    @Test fun bentBowArmAndLeanAreDetected() {
        val good = extractor.extract(PoseSynth.holdShot(PoseSynth.goodBodyPublic()))
        val bent = extractor.extract(PoseSynth.holdShot(PoseSynth.bentBowArm()))
        val lean = extractor.extract(PoseSynth.holdShot(PoseSynth.leaning()))
        assertTrue(bent[FormFeatureExtractor.BOW_ARM_ANGLE]!! < good[FormFeatureExtractor.BOW_ARM_ANGLE]!! - 10)
        assertTrue(lean[FormFeatureExtractor.SPINE_LEAN]!! > good[FormFeatureExtractor.SPINE_LEAN]!! + 5)
    }
}

class PoseEngineIntegrationTest {
    @Test fun baselineFromGoodFormFlagsPoorForm() {
        val extractor = FormFeatureExtractor()
        val builder = BaselineBuilder()
        // 10 good-form holds with small variation so the baseline has spread.
        repeat(10) { i ->
            val body = PoseSynth.goodBodyPublic().toMutableMap()
            val jitter = (i % 3 - 1) * 0.004
            body[PoseLandmarks.LEFT_WRIST] = (0.16 + jitter) to (0.30 + jitter)
            builder.add(extractor.extract(PoseSynth.holdShot(body)))
        }
        val baseline = builder.build()
        assertTrue(baseline.isReady())

        val scorer = DeviationScorer(baseline, weights = ArcheryModule.deviationWeights)
        val good = scorer.score(extractor.extract(PoseSynth.holdShot(PoseSynth.goodBodyPublic()))).stability
        val poor = scorer.score(extractor.extract(PoseSynth.holdShot(PoseSynth.bentBowArm()))).stability
        assertTrue(good > poor, "good form ($good) should beat bent bow arm ($poor)")
        assertTrue(poor < 80.0)
    }
}
