package xyz.mdhv.formanalyser.scoring

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Synthetic photographs of a target face, drawn directly in face coordinates.
 *
 * There are no real range photographs in this repository and none can be produced in a headless
 * build, so these fixtures test what is actually testable: that the geometry is right, that a shape
 * the detector is supposed to accept is accepted at the position it is supposed to land on, and —
 * far more importantly — that the shapes it is supposed to refuse are refused for the stated reason.
 * They say nothing about how any of this behaves on a boss in the rain.
 */
private class TargetPhoto(val background: Int = 200) {
    val luma = ByteArray(WIDTH * HEIGHT) { background.toByte() }

    /** A filled disc centred on a face coordinate, with its radius given in face units. */
    fun mark(faceX: Double, faceY: Double, radiusFace: Double, value: Int) {
        val px = CENTRE_X + faceX * FACE_RADIUS_PX
        val py = CENTRE_Y - faceY * FACE_RADIUS_PX
        val rp = radiusFace * FACE_RADIUS_PX
        val x0 = floor(px - rp).toInt().coerceAtLeast(0)
        val x1 = floor(px + rp).toInt().coerceAtMost(WIDTH - 1)
        val y0 = floor(py - rp).toInt().coerceAtLeast(0)
        val y1 = floor(py + rp).toInt().coerceAtMost(HEIGHT - 1)
        for (y in y0..y1) for (x in x0..x1) {
            if (hypot(x - px, y - py) <= rp) luma[y * WIDTH + x] = value.toByte()
        }
    }

    /** A nocked arrow: a shaft standing out of the face, so a streak running radially outward. */
    fun arrow(
        impactRadius: Double,
        angleDeg: Double,
        length: Double,
        halfWidth: Double = 0.008,
        value: Int = 30,
    ) {
        val angle = angleDeg * PI / 180.0
        val steps = 600
        for (i in 0..steps) {
            val r = impactRadius + length * i / steps
            mark(r * cos(angle), r * sin(angle), halfWidth, value)
        }
    }

    /** A printed ring boundary: elongated, but tangential — the false positive that matters most. */
    fun ringLine(radius: Double, fromDeg: Double, toDeg: Double, halfWidth: Double = 0.008) {
        val steps = 600
        for (i in 0..steps) {
            val angle = (fromDeg + (toDeg - fromDeg) * i / steps) * PI / 180.0
            mark(radius * cos(angle), radius * sin(angle), halfWidth, 30)
        }
    }

    fun image() = GrayImage(WIDTH, HEIGHT, luma)

    companion object {
        const val WIDTH = 800
        const val HEIGHT = 800
        const val FACE_RADIUS_PX = 300.0
        const val CENTRE_X = 399.5
        const val CENTRE_Y = 399.5

        /**
         * The calibration an athlete would produce for a face drawn dead-centre and square-on. The
         * handles are on the outer edge of the scoring area, so `boundaryScore` stays at its default.
         */
        val calibration =
            FaceCalibration.ellipse(
                ImagePoint(0.5, 0.5),
                FACE_RADIUS_PX / WIDTH,
                FACE_RADIUS_PX / HEIGHT,
            )

        val projection: FaceProjection
            get() = assertIs<FaceCalibration.Result.Usable>(calibration.project()).projection
    }
}

class EndScanDetectorTest {

    private fun options(
        arrows: Int,
        faceCm: Int = 122,
        layout: FaceLayout = FaceLayout.SINGLE,
    ) = EndScan.Options(targetFaceCm = faceCm, faceLayout = layout, arrowsExpected = arrows)

    @Test
    fun rectifyPutsTheFaceCentreAtTheOriginAndCoversTheWholeDisc() {
        val photo = TargetPhoto()
        photo.mark(0.0, 0.0, 0.03, 20)
        val raster = EndScan.rectify(photo.image(), TargetPhoto.projection)
        assertTrue(raster.faceCoverage > 0.999, "coverage was ${raster.faceCoverage}")
        val mid = raster.size / 2
        assertEquals(20, raster.luma[mid * raster.size + mid].toInt() and 0xFF)
        // Well outside the mark but still on the face: background.
        assertEquals(200, raster.luma[mid * raster.size + (mid + raster.size / 4)].toInt() and 0xFF)
    }

    @Test
    fun aNockedArrowIsProposedAtItsInnerTipNotItsCentroid() {
        val photo = TargetPhoto()
        // Impact at face radius 0.53 — inside the 5-ring — with 0.12 of shaft projecting outward.
        // The centroid of that streak sits near radius 0.59, which is a different ring; reading the
        // centroid instead of the inner tip is the single most likely way to be quietly wrong here.
        photo.arrow(impactRadius = 0.53, angleDeg = 90.0, length = 0.12)
        val result = EndScan.detect(photo.image(), TargetPhoto.projection, options(arrows = 1))
        assertEquals(1, result.impacts.size, "rejections: ${result.rejections}")
        val impact = result.impacts.single()
        assertEquals(EndScan.Shape.STREAK, impact.shape)
        assertTrue(abs(impact.plot.x) < 0.04, "expected a vertical arrow, got x=${impact.plot.x}")
        assertTrue(
            abs(impact.plot.radius - 0.53) < 0.04,
            "expected the inner tip near 0.53, got ${impact.plot.radius}",
        )
        assertEquals(5, impact.score.points)
        assertTrue(impact.confidence > EndScan.REVIEW_CAREFULLY_BELOW)
    }

    @Test
    fun anArrowInTheMiddleIsAcceptedAsACompactMark() {
        val photo = TargetPhoto()
        // Pointing straight at the camera from the middle of the gold: no out-of-plane displacement
        // to speak of, so no streak, and the centroid is the impact.
        photo.mark(0.02, 0.015, 0.010, 30)
        val result = EndScan.detect(photo.image(), TargetPhoto.projection, options(arrows = 1))
        assertEquals(1, result.impacts.size, "rejections: ${result.rejections}")
        val impact = result.impacts.single()
        assertEquals(EndScan.Shape.COMPACT, impact.shape)
        assertTrue(impact.score.isX, "expected an X inside the 0.05 ring, got ${impact.score}")
        assertTrue(
            impact.confidence <= EndScan.COMPACT_CONFIDENCE_CEILING,
            "a compact mark must never be the detector's most confident shape",
        )
    }

    @Test
    fun printedRingLinesAreRefusedForRunningTheWrongWay() {
        val photo = TargetPhoto()
        photo.ringLine(radius = 0.7, fromDeg = 10.0, toDeg = 32.0)
        val result = EndScan.detect(photo.image(), TargetPhoto.projection, options(arrows = 3))
        assertTrue(result.impacts.isEmpty(), "proposed ${result.impacts} from a ring line")
        assertEquals(1, result.rejections[EndScan.Rejection.TANGENTIAL])
    }

    @Test
    fun anOldHoleAwayFromTheCentreIsRefused() {
        val photo = TargetPhoto()
        // Round, arrow-sized, and out in the 7-ring: a hole from an earlier end. A real arrow there
        // would be standing out of the face and would streak.
        photo.mark(0.42, -0.18, 0.009, 40)
        val result = EndScan.detect(photo.image(), TargetPhoto.projection, options(arrows = 3))
        assertTrue(result.impacts.isEmpty(), "proposed ${result.impacts} from an old hole")
        assertEquals(1, result.rejections[EndScan.Rejection.AMBIGUOUS_COMPACT])
    }

    @Test
    fun onlyTheArrowsSurviveAFaceCarryingBothKindsOfClutter() {
        val photo = TargetPhoto()
        photo.arrow(impactRadius = 0.53, angleDeg = 90.0, length = 0.12)
        photo.mark(0.02, 0.015, 0.010, 30)
        photo.ringLine(radius = 0.7, fromDeg = 200.0, toDeg = 224.0)
        photo.mark(0.42, -0.18, 0.009, 40)
        val result = EndScan.detect(photo.image(), TargetPhoto.projection, options(arrows = 3))
        assertEquals(2, result.impacts.size, "rejections: ${result.rejections}")
        // Highest score first, the order an end is written down in.
        assertTrue(result.impacts[0].score.isX)
        assertEquals(5, result.impacts[1].score.points)
        assertTrue(result.countMismatch, "two of three arrows found must read as a mismatch")
        assertEquals(1, result.rejections[EndScan.Rejection.TANGENTIAL])
        assertEquals(1, result.rejections[EndScan.Rejection.AMBIGUOUS_COMPACT])
    }

    @Test
    fun neverProposesMoreCandidatesThanTheEndHasArrowsLeft() {
        val photo = TargetPhoto()
        photo.arrow(impactRadius = 0.53, angleDeg = 90.0, length = 0.12)
        photo.arrow(impactRadius = 0.31, angleDeg = 210.0, length = 0.10)
        val result = EndScan.detect(photo.image(), TargetPhoto.projection, options(arrows = 1))
        assertEquals(1, result.impacts.size)
        assertEquals(1, result.rejections[EndScan.Rejection.EXCESS_OVER_ARROW_COUNT])
    }

    @Test
    fun nothingIsProposedWhenThereAreNoArrowsLeftToRecord() {
        val photo = TargetPhoto()
        photo.arrow(impactRadius = 0.53, angleDeg = 90.0, length = 0.12)
        val result = EndScan.detect(photo.image(), TargetPhoto.projection, options(arrows = 0))
        assertTrue(result.impacts.isEmpty())
    }

    @Test
    fun neverProposesAMiss() {
        val photo = TargetPhoto()
        // A triple face prints nothing below 6, so an arrow at radius 0.53 is a miss there. A
        // machine may not put a zero on a scorecard: the athlete is the only one who can say an
        // arrow missed.
        photo.arrow(impactRadius = 0.53, angleDeg = 90.0, length = 0.12)
        val result =
            EndScan.detect(
                photo.image(),
                TargetPhoto.projection,
                options(arrows = 3, faceCm = 40, layout = FaceLayout.VERTICAL_TRIPLE),
            )
        assertTrue(result.impacts.none { it.score.points == 0 })
        assertTrue(result.impacts.isEmpty(), "proposed ${result.impacts}")
        assertEquals(1, result.rejections[EndScan.Rejection.OUTSIDE_FACE])
    }

    @Test
    fun anImpactOnARingBoundaryIsFlaggedForTheAthleteToJudge() {
        val photo = TargetPhoto()
        // Landing on the 6/5 boundary at radius 0.50. The rules give a line-cutter the higher value
        // and a photograph from the front cannot see whether the shaft touches the line.
        photo.arrow(impactRadius = 0.50, angleDeg = 270.0, length = 0.12)
        val result = EndScan.detect(photo.image(), TargetPhoto.projection, options(arrows = 1))
        val impact = result.impacts.single()
        assertTrue(impact.lineCutter, "radius ${impact.plot.radius} should read as a line-cutter")
        assertTrue(impact.needsCloseLook)
    }

    @Test
    fun aCroppedFaceProposesNothingAtAll() {
        val photo = TargetPhoto()
        photo.arrow(impactRadius = 0.53, angleDeg = 90.0, length = 0.12)
        // Handles pushed off the top of the frame: a third of the face was never photographed, and
        // the missing third is exactly where an unproposed arrow would be.
        val cropped =
            FaceCalibration.ellipse(ImagePoint(0.5, 0.18), 0.375, 0.375).project()
        val projection = assertIs<FaceCalibration.Result.Usable>(cropped).projection
        val result = EndScan.detect(photo.image(), projection, options(arrows = 3))
        assertTrue(result.impacts.isEmpty())
        assertEquals(1, result.rejections[EndScan.Rejection.NOT_IN_FRAME])
        assertTrue(result.faceCoverage < EndScan.MIN_FACE_COVERAGE)
    }

    @Test
    fun aBlankFaceProposesNothingRatherThanSomething() {
        val result =
            EndScan.detect(TargetPhoto().image(), TargetPhoto.projection, options(arrows = 6))
        assertTrue(result.impacts.isEmpty())
        assertTrue(result.countMismatch)
    }

    @Test
    fun aPaleShaftOnADarkRingIsFoundToo() {
        // The dark pass alone would miss this entirely: an aluminium shaft photographed against the
        // black 3-ring band is *brighter* than its background, and that is ordinary equipment.
        val photo = TargetPhoto(background = 40)
        photo.arrow(impactRadius = 0.53, angleDeg = 90.0, length = 0.12, value = 220)
        val result = EndScan.detect(photo.image(), TargetPhoto.projection, options(arrows = 1))
        assertEquals(1, result.impacts.size, "rejections: ${result.rejections}")
        assertEquals(5, result.impacts.single().score.points)
    }

    @Test
    fun everyRejectionCarriesSomethingSayableToTheAthlete() {
        EndScan.Rejection.entries.forEach {
            assertTrue(it.label.isNotBlank(), "${it.name} has no label")
        }
    }

    @Test
    fun theSizeBandFollowsTheFaceItIsScanning() {
        // One drawn streak, a twentieth of a face radius across, read against two different rounds.
        // On a 40 cm face that is a 9 mm shaft — a fat arrow with a shadow, and plausible. On a
        // 122 cm face the identical streak is 2.7 cm across, which is not an arrow at all, and the
        // detector has to refuse it rather than score whatever it happens to be lying on. The band
        // is physical, so it can only follow the face the round is actually shot at.
        val photo = TargetPhoto()
        photo.arrow(impactRadius = 0.53, angleDeg = 90.0, length = 0.32, halfWidth = 0.045)
        val asSmallFace =
            EndScan.detect(photo.image(), TargetPhoto.projection, options(arrows = 1, faceCm = 40))
        val asBigFace =
            EndScan.detect(photo.image(), TargetPhoto.projection, options(arrows = 1, faceCm = 122))
        assertEquals(1, asSmallFace.impacts.size, "rejections: ${asSmallFace.rejections}")
        assertEquals(5, asSmallFace.impacts.single().score.points)
        assertTrue(asBigFace.impacts.isEmpty(), "proposed ${asBigFace.impacts}")
        assertTrue(
            EndScan.Rejection.TOO_LARGE in asBigFace.rejections,
            "expected a size refusal, got ${asBigFace.rejections}",
        )
    }
}
