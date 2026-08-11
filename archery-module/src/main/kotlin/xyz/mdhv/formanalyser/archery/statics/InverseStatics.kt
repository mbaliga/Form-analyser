package xyz.mdhv.formanalyser.archery.statics

import kotlin.math.hypot

/** A 2D point/vector in the sagittal (side-on) plane, metres. x = forward, y = up. */
data class Vec2(val x: Double, val y: Double) {
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun times(s: Double) = Vec2(x * s, y * s)
    val norm: Double get() = hypot(x, y)
    fun unit(): Vec2 { val n = norm; return if (n < 1e-12) Vec2(0.0, 0.0) else Vec2(x / n, y / n) }
}

/** 2D scalar cross product (z of a×b): the moment a force at offset r produces. */
private fun cross(r: Vec2, f: Vec2): Double = r.x * f.y - r.y * f.x

/**
 * Body-segment inertial parameters (mass as a fraction of total body mass; centre of mass
 * as a fraction of segment length from the proximal joint). Values from Winter (2009),
 * "Biomechanics and Motor Control of Human Movement" — the table the handoff §3.4 cites
 * alongside de Leva (1996).
 */
data class SegmentParams(val massFraction: Double, val comFromProximal: Double) {
    companion object {
        val UPPER_ARM = SegmentParams(massFraction = 0.028, comFromProximal = 0.436)
        val FOREARM = SegmentParams(massFraction = 0.016, comFromProximal = 0.430)
        val HAND = SegmentParams(massFraction = 0.006, comFromProximal = 0.506)
    }
}

/** Sagittal-plane joint coordinates of the draw arm at full draw (metres). */
data class DrawArmPose(
    val shoulder: Vec2,
    val elbow: Vec2,
    val wrist: Vec2,
    /** The string-hook point (drawing fingers), where the string tension is applied. */
    val fingers: Vec2,
)

/** Per-joint holding moment, N·m. Positive magnitude is the load the muscles must hold. */
data class HoldingMoments(
    val wrist: Double,
    val elbow: Double,
    val shoulder: Double,
) {
    /** As engine features, ready to merge into a shot's [xyz.mdhv.crocodyl.engine.model.FeatureVector]. */
    fun asFeatures(): Map<String, Double> = linkedMapOf(
        "momentWristNm" to wrist,
        "momentElbowNm" to elbow,
        "momentShoulderNm" to shoulder,
    )
}

/**
 * Quasi-static inverse statics for the draw arm (handoff §3.4). At full draw, accelerations
 * ≈ 0, the external load (draw weight) is known, and the force vector lies along the string —
 * so the problem is determinate inverse *statics*, not noisy dynamics.
 *
 * For each joint we sum the moments of everything distal to it: the string tension applied at
 * the fingers, plus the weights of the distal limb segments at their centres of mass. The
 * result is the holding moment the joint's muscles must produce — a defensible per-joint load
 * proxy whose drift across a session is the strength-endurance signal that actually limits
 * archers.
 *
 * This computes one shot's moments; session-level decay is the engine's FatigueTracker job.
 */
object InverseStatics {

    const val G = 9.80665

    /**
     * @param pose draw-arm joint coordinates in the sagittal plane (metres).
     * @param drawForceNewtons the known draw weight at the archer's draw length, in Newtons.
     *        (lbf × 4.4482.)
     * @param stringDirection unit-ish vector of the string tension *acting on the fingers*
     *        (typically pointing forward, toward the bow). Normalised internally.
     * @param bodyMassKg used to weight the limb segments; pass 0.0 to isolate the draw-force
     *        contribution.
     */
    fun analyze(
        pose: DrawArmPose,
        drawForceNewtons: Double,
        stringDirection: Vec2,
        bodyMassKg: Double,
    ): HoldingMoments {
        val drawForce = stringDirection.unit() * drawForceNewtons

        // Segment masses and CoM points (proximal -> distal along each segment).
        val handMass = SegmentParams.HAND.massFraction * bodyMassKg
        val forearmMass = SegmentParams.FOREARM.massFraction * bodyMassKg
        val upperArmMass = SegmentParams.UPPER_ARM.massFraction * bodyMassKg

        val handCom = lerp(pose.wrist, pose.fingers, SegmentParams.HAND.comFromProximal)
        val forearmCom = lerp(pose.elbow, pose.wrist, SegmentParams.FOREARM.comFromProximal)
        val upperArmCom = lerp(pose.shoulder, pose.elbow, SegmentParams.UPPER_ARM.comFromProximal)

        val handW = Vec2(0.0, -handMass * G)
        val forearmW = Vec2(0.0, -forearmMass * G)
        val upperArmW = Vec2(0.0, -upperArmMass * G)

        // Moment about a joint = sum of cross(point - joint, force) over everything distal.
        fun momentAbout(joint: Vec2, includeForearm: Boolean, includeUpperArm: Boolean): Double {
            var m = cross(pose.fingers - joint, drawForce)
            m += cross(handCom - joint, handW)
            if (includeForearm) m += cross(forearmCom - joint, forearmW)
            if (includeUpperArm) m += cross(upperArmCom - joint, upperArmW)
            return m
        }

        val wrist = momentAbout(pose.wrist, includeForearm = false, includeUpperArm = false)
        val elbow = momentAbout(pose.elbow, includeForearm = true, includeUpperArm = false)
        val shoulder = momentAbout(pose.shoulder, includeForearm = true, includeUpperArm = true)

        return HoldingMoments(
            wrist = kotlin.math.abs(wrist),
            elbow = kotlin.math.abs(elbow),
            shoulder = kotlin.math.abs(shoulder),
        )
    }

    private fun lerp(a: Vec2, b: Vec2, t: Double) = a + (b - a) * t
}
