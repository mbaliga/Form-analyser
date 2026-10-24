package xyz.mdhv.formanalyser.scoring

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TargetCalibrationTest {

    private fun usable(c: FaceCalibration): FaceProjection =
        assertIs<FaceCalibration.Result.Usable>(c.project()).projection

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 1e-9) {
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected, got $actual")
    }

    @Test
    fun squareOnFaceMapsLinearly() {
        val calibration = FaceCalibration.ellipse(ImagePoint(0.5, 0.5), 0.375, 0.375)
        assertNull(calibration.problem())
        val p = usable(calibration)
        // Face centre is image centre; +x is right; +y is *up* the image, which is -y in image space.
        val centre = assertNotNull(p.toImage(0.0, 0.0))
        assertClose(0.5, centre.x, 1e-9)
        assertClose(0.5, centre.y, 1e-9)
        val right = assertNotNull(p.toImage(0.5, 0.0))
        assertClose(0.5 + 0.5 * 0.375, right.x, 1e-9)
        assertClose(0.5, right.y, 1e-9)
        val up = assertNotNull(p.toImage(0.0, 0.5))
        assertClose(0.5, up.x, 1e-9)
        assertClose(0.5 - 0.5 * 0.375, up.y, 1e-9)
    }

    @Test
    fun boundaryScoreScalesTheFace() {
        // Handles placed on the 6-ring's outer edge — the printed edge of a triple face, which has
        // no rings below 6. That edge is at face radius 0.5, so the full face is twice as wide as
        // the handles are apart.
        val calibration =
            FaceCalibration.ellipse(ImagePoint(0.5, 0.5), 0.2, 0.2, boundaryScore = 6)
        assertEquals(0.5, calibration.boundaryRadius)
        val p = usable(calibration)
        val sixRing = assertNotNull(p.toImage(0.5, 0.0))
        assertClose(0.7, sixRing.x, 1e-9)
        val faceEdge = assertNotNull(p.toImage(1.0, 0.0))
        assertClose(0.9, faceEdge.x, 1e-9)
    }

    @Test
    fun perspectiveQuadRoundTripsBothWays() {
        // A trapezoid: the face photographed from below and slightly to the left, so the top edge is
        // further away and the quad is not symmetric about either axis.
        val calibration =
            FaceCalibration(
                top = ImagePoint(0.48, 0.22),
                right = ImagePoint(0.79, 0.49),
                bottom = ImagePoint(0.52, 0.87),
                left = ImagePoint(0.16, 0.46),
            )
        assertNull(calibration.problem())
        val p = usable(calibration)
        // The four handles must land exactly on their face positions — four correspondences
        // determine a homography exactly, so this is not an approximation.
        val handles =
            listOf(
                Triple(0.0, 1.0, calibration.top),
                Triple(1.0, 0.0, calibration.right),
                Triple(0.0, -1.0, calibration.bottom),
                Triple(-1.0, 0.0, calibration.left),
            )
        for ((fx, fy, expected) in handles) {
            val image = assertNotNull(p.toImage(fx, fy))
            assertClose(expected.x, image.x, 1e-9)
            assertClose(expected.y, image.y, 1e-9)
            val back = assertNotNull(p.toFace(image))
            assertClose(fx, back.x, 1e-9)
            assertClose(fy, back.y, 1e-9)
        }
        // And an interior point survives the round trip, which the handles alone would not prove.
        val interior = assertNotNull(p.toImage(0.31, -0.42))
        val back = assertNotNull(p.toFace(interior))
        assertClose(0.31, back.x, 1e-9)
        assertClose(-0.42, back.y, 1e-9)
    }

    @Test
    fun faceIndexIsCarriedOntoEveryPlotPoint() {
        val calibration =
            FaceCalibration.ellipse(ImagePoint(0.5, 0.3), 0.2, 0.2, faceIndex = 2, boundaryScore = 6)
        val p = usable(calibration)
        assertEquals(2, p.faceIndex)
        assertEquals(2, assertNotNull(p.toFace(ImagePoint(0.5, 0.3))).faceIndex)
    }

    @Test
    fun coincidentHandlesAreDegenerate() {
        val calibration =
            FaceCalibration(
                top = ImagePoint(0.5, 0.3),
                right = ImagePoint(0.5, 0.3005),
                bottom = ImagePoint(0.5, 0.7),
                left = ImagePoint(0.3, 0.5),
            )
        assertEquals(CalibrationProblem.DEGENERATE, calibration.problem())
        assertIs<FaceCalibration.Result.Unusable>(calibration.project())
    }

    @Test
    fun anticlockwiseHandlesReadAsMirrored() {
        // Left and right swapped: still a perfectly good convex quad, just wound the other way,
        // which would silently mirror every arrow across the vertical axis.
        val calibration =
            FaceCalibration(
                top = ImagePoint(0.5, 0.2),
                right = ImagePoint(0.2, 0.5),
                bottom = ImagePoint(0.5, 0.8),
                left = ImagePoint(0.8, 0.5),
            )
        assertEquals(CalibrationProblem.MIS_ORDERED, calibration.problem())
    }

    @Test
    fun crossedHandlesAreNotConvex() {
        val calibration =
            FaceCalibration(
                top = ImagePoint(0.5, 0.2),
                right = ImagePoint(0.8, 0.5),
                bottom = ImagePoint(0.2, 0.5),
                left = ImagePoint(0.5, 0.8),
            )
        assertEquals(CalibrationProblem.NOT_CONVEX, calibration.problem())
    }

    @Test
    fun steepAngleIsRefusedRatherThanScored() {
        // Photographed from far off to the side: the face is three times wider than it is tall.
        val calibration = FaceCalibration.ellipse(ImagePoint(0.5, 0.5), 0.36, 0.12)
        assertEquals(CalibrationProblem.EXTREME_PERSPECTIVE, calibration.problem())
        val result = assertIs<FaceCalibration.Result.Unusable>(calibration.project())
        assertEquals(CalibrationProblem.EXTREME_PERSPECTIVE, result.problem)
    }

    @Test
    fun everyProblemCarriesSomethingToShowTheAthlete() {
        CalibrationProblem.entries.forEach {
            assertTrue(it.message.isNotBlank(), "${it.name} has no message")
        }
    }

    @Test
    fun pointsBeyondThePlottableDomainAreRefusedNotClamped() {
        val p = usable(FaceCalibration.ellipse(ImagePoint(0.5, 0.5), 0.1, 0.1))
        // Two face radii out to the left of the face is x = -2, outside what PlotPoint accepts.
        assertNull(p.toFace(ImagePoint(0.3, 0.5)))
    }
}
