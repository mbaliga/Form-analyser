package xyz.mdhv.formanalyser.archery

import xyz.mdhv.formanalyser.archery.pose.Geometry
import xyz.mdhv.formanalyser.archery.pose.Landmark
import kotlin.test.Test
import kotlin.test.assertEquals

class GeometryTest {
    private fun lm(x: Double, y: Double) = Landmark(x, y)

    @Test fun rightAngleAndStraightLine() {
        assertEquals(90.0, Geometry.angleDeg(lm(1.0, 0.0), lm(0.0, 0.0), lm(0.0, 1.0)), 1e-6)
        assertEquals(180.0, Geometry.angleDeg(lm(1.0, 0.0), lm(0.0, 0.0), lm(-1.0, 0.0)), 1e-6)
        assertEquals(45.0, Geometry.angleDeg(lm(1.0, 0.0), lm(0.0, 0.0), lm(1.0, 1.0)), 1e-6)
    }

    @Test fun tiltFromHorizontal() {
        assertEquals(0.0, Geometry.tiltFromHorizontalDeg(lm(0.0, 0.0), lm(1.0, 0.0)), 1e-6)
        assertEquals(90.0, Geometry.tiltFromHorizontalDeg(lm(0.0, 0.0), lm(0.0, 1.0)), 1e-6)
        assertEquals(45.0, Geometry.tiltFromHorizontalDeg(lm(0.0, 0.0), lm(1.0, 1.0)), 1e-6)
    }

    @Test fun leanFromVertical() {
        assertEquals(0.0, Geometry.leanFromVerticalDeg(lm(0.0, 0.0), lm(0.0, 1.0)), 1e-6)
        assertEquals(90.0, Geometry.leanFromVerticalDeg(lm(0.0, 0.0), lm(1.0, 0.0)), 1e-6)
    }
}
