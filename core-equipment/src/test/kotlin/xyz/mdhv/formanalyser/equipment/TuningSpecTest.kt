package xyz.mdhv.formanalyser.equipment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ArrowMathTest {
    @Test fun focMatchesHandComputed() {
        // 762 mm (30") arrow, balance point 457.2 mm from nock throat.
        // center = 381 mm; FOC = (457.2 − 381)/762 = 76.2/762 = 10.0%.
        assertEquals(10.0, ArrowMath.focPercent(762.0, 457.2), 1e-9)
        // 700 mm arrow, balance 406 mm: center 350; (406−350)/700 = 56/700 = 8.0%.
        assertEquals(8.0, ArrowMath.focPercent(700.0, 406.0), 1e-9)
    }

    @Test fun focRejectsNonPositiveLength() {
        assertFailsWith<IllegalArgumentException> { ArrowMath.focPercent(0.0, 100.0) }
    }

    @Test fun gppMatchesHandComputed() {
        assertEquals(8.75, ArrowMath.gpp(350.0, 40.0), 1e-9)   // 350 / 40
        assertEquals(10.0, ArrowMath.gpp(300.0, 30.0), 1e-9)   // 300 / 30
    }

    @Test fun gppRejectsNonPositivePoundage() {
        assertFailsWith<IllegalArgumentException> { ArrowMath.gpp(350.0, 0.0) }
    }

    @Test fun gppUsesEffectivePoundageFromEstimator() {
        // resolve → estimated OTF, then GPP against it (reuse of PoundageEstimator).
        val eff = PoundageEstimator.resolve(
            xyz.mdhv.formanalyser.model.BowType.RECURVE,
            otfLbs = null, markedLbs = 40.0, riserLengthIn = 25.0, drawLengthMm = (28.0 * 25.4).toInt(),
        )!!
        // 28" on 25" riser ⇒ ≈ marked 40 lbs; 350 gr / 40 lb ≈ 8.75.
        assertEquals(8.75, ArrowMath.gpp(350.0, eff.lbs), 0.05)
    }

    @Test fun kineticEnergyMatchesHandComputed() {
        // 350 gr @ 200 fps: 350·200²/450240 = 14,000,000/450240 = 31.0945…
        assertEquals(31.0945, ArrowMath.kineticEnergyFtLb(350.0, 200.0), 1e-3)
    }

    @Test fun kineticEnergyProxyMatchesHandComputed() {
        // eff 40 lb, draw 711.2 mm (28"), brace 228.6 mm (9"): powerStroke 482.6 mm = 1.58333 ft.
        // stored = 0.5·40·1.58333 = 31.6667 ft·lb; ×0.70 efficiency = 22.1667 ft·lb.
        assertEquals(22.1667, ArrowMath.kineticEnergyProxyFtLb(40.0, 711.2, 228.6), 1e-3)
    }

    @Test fun kineticEnergyProxyRejectsNonPositivePowerStroke() {
        assertFailsWith<IllegalArgumentException> { ArrowMath.kineticEnergyProxyFtLb(40.0, 200.0, 200.0) }
    }
}

class TuningSpecRoundTripTest {
    private fun fullSpec() = TuningSpec(
        braceHeightMm = 225.0,
        tillerTopMm = 3.0,
        tillerBottomMm = 5.0,
        nockingPointHeightMm = 6.0,
        plungerTensionSteps = 4,
        clickerPositionMm = 720.0,
        stringStrands = 14,
        stringMaterial = "8125G",
        stabilizer = StabilizerSetup(30.0, 12.0, 4.0, 8.0, 2.0),
        arrow = ArrowSpec(spine = 620, lengthMm = 720.0, pointGrains = 100.0, totalMassGrains = 350.0, balancePointMm = 432.0, vaneCount = 3),
    )

    @Test fun fullRoundTrip() {
        val spec = fullSpec()
        assertEquals(spec, TuningSpec.fromMap(spec.toMap()))
    }

    @Test fun missingFieldsDefaultToNullAndCurrentVersion() {
        // Old / partial payload: only a brace height recorded.
        val partial = mapOf<String, Any?>("braceHeightMm" to 228.6)
        val spec = TuningSpec.fromMap(partial)
        assertEquals(228.6, spec.braceHeightMm)
        assertEquals(TuningSpec.CURRENT_SCHEMA_VERSION, spec.schemaVersion)
        assertEquals(null, spec.tillerTopMm)
        assertEquals(null, spec.arrow)
        assertEquals(null, spec.stabilizer)
        assertEquals(null, spec.stringMaterial)
    }

    @Test fun emptyMapUpgradesToAllDefaults() {
        assertEquals(TuningSpec(), TuningSpec.fromMap(emptyMap()))
    }

    @Test fun unknownFutureKeysAreIgnored() {
        val fromFuture = mapOf<String, Any?>(
            "schemaVersion" to 99,
            "braceHeightMm" to 225.0,
            "someFutureField" to "ignore-me",
        )
        val spec = TuningSpec.fromMap(fromFuture)
        assertEquals(99, spec.schemaVersion)
        assertEquals(225.0, spec.braceHeightMm)
    }

    @Test fun ozToGramsConversion() {
        assertEquals(28.3495, TuningSpec.ozToGrams(1.0), 1e-3)
    }
}

class TuningValidatorTest {
    private fun goodSpec() = TuningSpec(
        braceHeightMm = 230.0,          // within 0.130–0.145 × (68"·25.4) = 224.5–250.4 mm
        tillerTopMm = 4.0,
        tillerBottomMm = 6.0,           // diff 2 mm, in band
        nockingPointHeightMm = 6.0,
        stringStrands = 14,
        arrow = ArrowSpec(lengthMm = 750.0, totalMassGrains = 350.0, balancePointMm = 442.0, vaneCount = 3),
        // FOC = (442 − 375)/750 = 67/750 = 8.933% (in 7–16 band)
    )
    private fun goodContext() = TuningContext(bowLengthIn = 68.0, drawLengthMm = 720.0, effectiveLbs = 40.0)

    @Test fun goodSetupHasNoWarnings() {
        assertTrue(TuningValidator.validate(goodSpec(), goodContext()).isEmpty())
    }

    @Test fun braceTooLowFlagged() {
        val spec = goodSpec().copy(braceHeightMm = 200.0) // below 224.5 mm band for 68"
        val codes = TuningValidator.validate(spec, goodContext()).map { it.code }
        assertTrue(TuningWarningCode.BRACE_HEIGHT_LOW in codes)
    }

    @Test fun braceTooHighFlagged() {
        val spec = goodSpec().copy(braceHeightMm = 260.0) // above 250.4 mm band for 68"
        val codes = TuningValidator.validate(spec, goodContext()).map { it.code }
        assertTrue(TuningWarningCode.BRACE_HEIGHT_HIGH in codes)
    }

    @Test fun negativeNockingPointIsError() {
        val spec = goodSpec().copy(nockingPointHeightMm = -3.0)
        val w = TuningValidator.validate(spec, goodContext()).single { it.code == TuningWarningCode.NOCKING_POINT_NEGATIVE }
        assertEquals(WarningSeverity.ERROR, w.severity)
    }

    @Test fun arrowTooShortForDrawIsError() {
        val spec = goodSpec().copy(arrow = goodSpec().arrow!!.copy(lengthMm = 700.0)) // < 720 + 12.7
        val codes = TuningValidator.validate(spec, goodContext()).map { it.code }
        assertTrue(TuningWarningCode.ARROW_TOO_SHORT in codes)
    }

    @Test fun largeTillerDifferenceFlagged() {
        val spec = goodSpec().copy(tillerTopMm = 2.0, tillerBottomMm = 15.0) // diff 13 mm
        val codes = TuningValidator.validate(spec, goodContext()).map { it.code }
        assertTrue(TuningWarningCode.TILLER_DIFFERENCE_LARGE in codes)
    }

    @Test fun tooLightArrowGppIsError() {
        // 150 gr @ 40 lb → 3.75 gpp, below 5.0 floor.
        val spec = goodSpec().copy(arrow = goodSpec().arrow!!.copy(totalMassGrains = 150.0))
        val w = TuningValidator.validate(spec, goodContext()).single { it.code == TuningWarningCode.GPP_TOO_LOW }
        assertEquals(WarningSeverity.ERROR, w.severity)
    }

    @Test fun focOutOfRangeFlagged() {
        // balance far forward: (500 − 375)/750 = 16.67% > 16.
        val spec = goodSpec().copy(arrow = goodSpec().arrow!!.copy(balancePointMm = 500.0))
        val codes = TuningValidator.validate(spec, goodContext()).map { it.code }
        assertTrue(TuningWarningCode.FOC_OUT_OF_RANGE in codes)
    }

    @Test fun strandCountOutOfRangeFlagged() {
        val spec = goodSpec().copy(stringStrands = 30)
        val codes = TuningValidator.validate(spec, goodContext()).map { it.code }
        assertTrue(TuningWarningCode.STRING_STRANDS_OUT_OF_RANGE in codes)
    }

    @Test fun unusualVaneCountFlagged() {
        val spec = goodSpec().copy(arrow = goodSpec().arrow!!.copy(vaneCount = 5))
        val codes = TuningValidator.validate(spec, goodContext()).map { it.code }
        assertTrue(TuningWarningCode.VANE_COUNT_UNUSUAL in codes)
    }

    @Test fun partialSpecWithoutContextValidatesWhatItCan() {
        // No context ⇒ brace/arrow-length/gpp checks skip; nocking-point check still runs.
        val spec = TuningSpec(nockingPointHeightMm = -1.0)
        val codes = TuningValidator.validate(spec).map { it.code }
        assertTrue(TuningWarningCode.NOCKING_POINT_NEGATIVE in codes)
        assertFalse(TuningWarningCode.BRACE_HEIGHT_LOW in codes)
    }
}
