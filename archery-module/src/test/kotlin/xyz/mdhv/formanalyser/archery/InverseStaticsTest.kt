package xyz.mdhv.formanalyser.archery

import xyz.mdhv.formanalyser.archery.statics.DrawArmPose
import xyz.mdhv.formanalyser.archery.statics.InverseStatics
import xyz.mdhv.formanalyser.archery.statics.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InverseStaticsTest {
    // Draw arm laid out along +x with elbow at the origin.
    private val pose = DrawArmPose(
        shoulder = Vec2(-0.30, 0.0),
        elbow = Vec2(0.0, 0.0),
        wrist = Vec2(0.25, 0.0),
        fingers = Vec2(0.30, 0.0),
    )

    @Test fun verticalDrawForceGivesForceTimesLeverArm() {
        // 100 N straight down at the fingers, weightless limbs -> moment = distance * force.
        val m = InverseStatics.analyze(
            pose = pose,
            drawForceNewtons = 100.0,
            stringDirection = Vec2(0.0, -1.0),
            bodyMassKg = 0.0,
        )
        assertEquals(0.60 * 100.0, m.shoulder, 1e-6) // 0.30-(-0.30) = 0.60 m lever from shoulder
        assertEquals(0.30 * 100.0, m.elbow, 1e-6)    // 0.30 m lever from elbow
        assertEquals(0.05 * 100.0, m.wrist, 1e-6)    // 0.05 m lever from wrist
    }

    @Test fun shoulderLeverIsLongerThanElbow() {
        val m = InverseStatics.analyze(pose, 200.0, Vec2(0.0, -1.0), 0.0)
        assertTrue(m.shoulder > m.elbow && m.elbow > m.wrist)
    }

    @Test fun collinearForceProducesNoMoment() {
        // Force along +x, arm along +x -> lever arm is zero.
        val m = InverseStatics.analyze(pose, 300.0, Vec2(1.0, 0.0), 0.0)
        assertEquals(0.0, m.elbow, 1e-9)
        assertEquals(0.0, m.shoulder, 1e-9)
    }

    @Test fun limbWeightAddsToTheHoldingMoment() {
        val noWeight = InverseStatics.analyze(pose, 100.0, Vec2(0.0, -1.0), bodyMassKg = 0.0)
        val withWeight = InverseStatics.analyze(pose, 100.0, Vec2(0.0, -1.0), bodyMassKg = 80.0)
        // limb weights act downward like the draw force here, so moments grow.
        assertTrue(withWeight.shoulder > noWeight.shoulder)
        assertTrue(withWeight.elbow > noWeight.elbow)
    }
}
