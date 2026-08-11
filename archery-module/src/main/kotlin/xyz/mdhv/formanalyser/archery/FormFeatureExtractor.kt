package xyz.mdhv.formanalyser.archery

import xyz.mdhv.crocodyl.engine.model.FeatureVector
import xyz.mdhv.crocodyl.engine.sport.FeatureExtractor
import xyz.mdhv.formanalyser.archery.pose.ArmLandmarks
import xyz.mdhv.formanalyser.archery.pose.Geometry
import xyz.mdhv.formanalyser.archery.pose.PoseFrame
import xyz.mdhv.formanalyser.archery.pose.PoseLandmarks
import xyz.mdhv.formanalyser.archery.pose.PoseShot

/**
 * Extracts archery *form* features from a segmented [PoseShot] — vision's well-posed sub-problem
 * (handoff §3.2: "postural form / alignment is vision's strength"). Measured over the anchor/HOLD
 * window and averaged for stability. Every feature is an angle or a ratio, so it's scale-invariant
 * — independent of the unknown metric scale of a monocular view.
 *
 * Camera placement assumption (handoff §3.6): lateral / sagittal to the archer. The specific
 * feature definitions and their "good" ranges need validation with the coach before they're
 * trusted — same caveat the sensor channel carries for its thresholds.
 */
class FormFeatureExtractor : FeatureExtractor<PoseShot> {

    override val featureNames = listOf(
        BOW_ARM_ANGLE, DRAW_ELBOW_ANGLE, DRAW_ARM_TILT, SHOULDER_TILT,
        HEAD_LEAN, SPINE_LEAN, STANCE_WIDTH, HOLD_DURATION_S, DRAW_DURATION_S,
    )

    override fun extract(rep: PoseShot): FeatureVector {
        val arms = ArmLandmarks(rep.handedness)
        val hold = rep.holdSlice().frames.ifEmpty { listOf(rep.anchorFrame()) }

        fun avg(metric: (PoseFrame) -> Double): Double = hold.sumOf(metric) / hold.size

        return linkedMapOf(
            // Bow arm straightness: shoulder-elbow-wrist; ~180° is a straight, stable bow arm.
            BOW_ARM_ANGLE to avg { Geometry.angleDeg(it[arms.bowShoulder], it[arms.bowElbow], it[arms.bowWrist]) },
            // Draw elbow angle: shoulder-elbow-wrist of the drawing arm.
            DRAW_ELBOW_ANGLE to avg { Geometry.angleDeg(it[arms.drawShoulder], it[arms.drawElbow], it[arms.drawWrist]) },
            // Draw upper-arm tilt from horizontal: the draw elbow should sit near shoulder level
            // (in line with the arrow); large tilt = dropped/high elbow.
            DRAW_ARM_TILT to avg { Geometry.tiltFromHorizontalDeg(it[arms.drawShoulder], it[arms.drawElbow]) },
            // Shoulder line level (T-form): tilt of the line between the shoulders from horizontal.
            SHOULDER_TILT to avg { Geometry.tiltFromHorizontalDeg(it[arms.bowShoulder], it[arms.drawShoulder]) },
            // Head lean: nose vs shoulder midpoint, from vertical (steady upright head).
            HEAD_LEAN to avg { Geometry.leanFromVerticalDeg(Geometry.midpoint(it[arms.bowShoulder], it[arms.drawShoulder]), it[PoseLandmarks.NOSE]) },
            // Spine lean: hip midpoint -> shoulder midpoint, from vertical (no leaning back).
            SPINE_LEAN to avg {
                val hipMid = Geometry.midpoint(it[PoseLandmarks.LEFT_HIP], it[PoseLandmarks.RIGHT_HIP])
                val shMid = Geometry.midpoint(it[arms.bowShoulder], it[arms.drawShoulder])
                Geometry.leanFromVerticalDeg(hipMid, shMid)
            },
            // Stance width: ankle separation relative to shoulder width.
            STANCE_WIDTH to avg {
                val shoulders = Geometry.dist(it[arms.bowShoulder], it[arms.drawShoulder])
                if (shoulders < 1e-9) 0.0 else Geometry.dist(it[PoseLandmarks.LEFT_ANKLE], it[PoseLandmarks.RIGHT_ANKLE]) / shoulders
            },
            HOLD_DURATION_S to rep.holdDurationSeconds,
            DRAW_DURATION_S to rep.drawDurationSeconds,
        )
    }

    companion object {
        const val BOW_ARM_ANGLE = "bowArmAngleDeg"
        const val DRAW_ELBOW_ANGLE = "drawElbowAngleDeg"
        const val DRAW_ARM_TILT = "drawArmTiltDeg"
        const val SHOULDER_TILT = "shoulderTiltDeg"
        const val HEAD_LEAN = "headLeanDeg"
        const val SPINE_LEAN = "spineLeanDeg"
        const val STANCE_WIDTH = "stanceWidthRatio"
        const val HOLD_DURATION_S = "holdDurationS"
        const val DRAW_DURATION_S = "drawDurationS"
    }
}
