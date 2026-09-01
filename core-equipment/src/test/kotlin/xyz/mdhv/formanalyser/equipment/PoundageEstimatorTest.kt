package xyz.mdhv.formanalyser.equipment

import xyz.mdhv.formanalyser.model.BowType
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PoundageEstimatorTest {
    @Test fun ilfConventionDefaults() {
        // 32# limbs, 23" riser, ~29.2" draw ⇒ ≈36.4 lbs (spec worked example).
        // (inches don't land on integer mm, so allow a tenth of a pound of rounding slack)
        val drawMm = (29.2 * 25.4).roundToInt()
        val est = PoundageEstimator.estimateOtfLbs(markedLbs = 32.0, riserLengthIn = 23.0, drawLengthMm = drawMm)
        assertEquals(36.4, est, 0.1)
    }

    @Test fun referenceRigEqualsMarked() {
        // 28" draw on a 25" riser is the rating point → estimate ≈ marked (mm rounding aside).
        val est = PoundageEstimator.estimateOtfLbs(40.0, 25.0, (28.0 * 25.4).roundToInt())
        assertEquals(40.0, est, 0.05)
    }

    @Test fun precedenceMeasuredOverEstimatedOverMarked() {
        // measured wins
        assertEquals(
            PoundageSource.MEASURED,
            PoundageEstimator.resolve(BowType.RECURVE, otfLbs = 38.0, markedLbs = 34.0, riserLengthIn = 25.0, drawLengthMm = 720)!!.source,
        )
        // no measured → estimated (recurve with full inputs)
        val est = PoundageEstimator.resolve(BowType.RECURVE, otfLbs = null, markedLbs = 34.0, riserLengthIn = 25.0, drawLengthMm = 720)!!
        assertEquals(PoundageSource.ESTIMATED, est.source)
        // no measured, missing riser → falls back to marked
        assertEquals(
            PoundageSource.MARKED,
            PoundageEstimator.resolve(BowType.RECURVE, otfLbs = null, markedLbs = 34.0, riserLengthIn = null, drawLengthMm = 720)!!.source,
        )
    }

    @Test fun compoundNeverEstimates() {
        val r = PoundageEstimator.resolve(BowType.COMPOUND, otfLbs = null, markedLbs = 50.0, riserLengthIn = 25.0, drawLengthMm = 720)!!
        assertEquals(PoundageSource.MARKED, r.source)
        assertEquals(50.0, r.lbs, 1e-9)
    }

    @Test fun nothingKnownIsNull() {
        assertNull(PoundageEstimator.resolve(BowType.RECURVE, null, null, null, null))
    }

    @Test fun kgConversion() {
        assertEquals(18.14, PoundageEstimator.lbsToKg(40.0), 0.01)
    }
}
