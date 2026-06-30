package xyz.mdhv.formanalyser.archery

import xyz.mdhv.baseline.engine.sport.RepSegmenter
import xyz.mdhv.formanalyser.archery.pose.ArmLandmarks
import xyz.mdhv.formanalyser.archery.pose.Geometry
import xyz.mdhv.formanalyser.archery.pose.Handedness
import xyz.mdhv.formanalyser.archery.pose.PoseLandmarks
import xyz.mdhv.formanalyser.archery.pose.PoseSequence
import xyz.mdhv.formanalyser.archery.pose.PoseShot

/**
 * Tunable thresholds for shot segmentation, in normalised-image units (landmark coords are
 * in [0,1], so speeds are units/second). Defaults are starting points to be validated against
 * real footage with the coach — same honesty caveat as any first-pass detector.
 */
data class PoseSegmenterConfig(
    /** Draw-wrist speed below which the archer is "holding" at anchor. */
    val holdSpeedThreshold: Double = 0.08,
    /** A draw-wrist speed spike above this marks the release. */
    val releaseSpeedThreshold: Double = 0.45,
    /** The hold only counts if the draw wrist is within this distance of the nose (anchor). */
    val anchorRadius: Double = 0.28,
    /** A hold must last at least this long to count as a real anchor. */
    val minHoldSeconds: Double = 0.5,
    /** How far back before the hold to look for the draw motion. */
    val maxDrawSeconds: Double = 3.0,
    /** Follow-through window captured after the release spike. */
    val followThroughSeconds: Double = 0.4,
    val handedness: Handedness = Handedness.RIGHT,
)

/**
 * Segments shots from a pose capture by finding the anchor/HOLD plateau (draw wrist near the
 * face and nearly still) and the RELEASE spike that ends it. The vision analogue of the
 * sensor segmenter: same plateau-then-spike shape, but on draw-wrist kinematics instead of
 * bow-IMU angular speed.
 */
class PoseShotSegmenter(
    private val config: PoseSegmenterConfig = PoseSegmenterConfig(),
) : RepSegmenter<PoseSequence, PoseShot> {

    override fun segment(input: PoseSequence): List<PoseShot> {
        val n = input.size
        if (n < 6) return emptyList()
        val arms = ArmLandmarks(config.handedness)

        val speed = drawWristSpeed(input, arms)
        val nearAnchor = BooleanArray(n) { i ->
            Geometry.dist(input.frames[i][arms.drawWrist], input.frames[i][PoseLandmarks.NOSE]) <= config.anchorRadius
        }

        val minHoldFrames = (config.minHoldSeconds * input.fps).toInt().coerceAtLeast(1)
        val maxDrawFrames = (config.maxDrawSeconds * input.fps).toInt().coerceAtLeast(1)
        val followFrames = (config.followThroughSeconds * input.fps).toInt().coerceAtLeast(1)

        val shots = ArrayList<PoseShot>()
        var i = 0
        while (i < n) {
            if (!(speed[i] < config.holdSpeedThreshold && nearAnchor[i])) { i++; continue }
            val holdStart = i
            var j = i
            while (j < n && speed[j] < config.holdSpeedThreshold && nearAnchor[j]) j++
            val holdEnd = j // exclusive
            if (holdEnd - holdStart < minHoldFrames) { i = holdEnd + 1; continue }

            // Release: first speed spike at/after the hold ends, within a short reach.
            val searchLimit = minOf(n, holdEnd + followFrames * 3)
            var releaseStart = holdEnd
            var found = false
            var k = holdEnd
            while (k < searchLimit) {
                if (speed[k] >= config.releaseSpeedThreshold) { releaseStart = k; found = true; break }
                k++
            }
            if (!found) { i = holdEnd + 1; continue } // a let-down, not a shot
            val releaseEnd = minOf(n, releaseStart + followFrames)

            // Draw: walk back from the hold through the drawing motion.
            val backLimit = (holdStart - maxDrawFrames).coerceAtLeast(0)
            var b = holdStart
            while (b > backLimit && speed[b - 1] >= config.holdSpeedThreshold) b--
            val drawStart = b

            shots.add(PoseShot(input, config.handedness, drawStart, holdStart, releaseStart, releaseEnd))
            i = releaseEnd + 1
        }
        return shots
    }

    /** Draw-wrist speed per frame (normalised units/sec), lightly smoothed. */
    private fun drawWristSpeed(seq: PoseSequence, arms: ArmLandmarks): DoubleArray {
        val n = seq.size
        val raw = DoubleArray(n)
        for (i in 1 until n) {
            val d = Geometry.dist(seq.frames[i][arms.drawWrist], seq.frames[i - 1][arms.drawWrist])
            raw[i] = d * seq.fps
        }
        // 3-tap moving average so one jittery landmark frame doesn't break a hold.
        val smooth = DoubleArray(n)
        for (i in 0 until n) {
            var sum = 0.0; var cnt = 0
            for (k in (i - 1)..(i + 1)) if (k in 0 until n) { sum += raw[k]; cnt++ }
            smooth[i] = sum / cnt
        }
        return smooth
    }
}
