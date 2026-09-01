package xyz.mdhv.formanalyser.wellness

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DurationTest {
    @Test fun idleTrimGapCap() {
        // spans 2 s + 2 s, gap 398 s capped to 180 → 184
        val r = DurationModel.auto(listOf(ShotSpan(0.0, 2.0), ShotSpan(400.0, 402.0)), recordingLengthS = 500.0)
        assertEquals(184.0, r.seconds, 1e-9)
        assertFalse(r.flaggedNoShots)
    }

    @Test fun zeroShotsFallsBackFlagged() {
        val r = DurationModel.auto(emptyList(), recordingLengthS = 300.0)
        assertEquals(300.0, r.seconds, 1e-9)
        assertTrue(r.flaggedNoShots)
    }
}

class ReadinessTest {
    @Test fun cascadePrecedenceAndReasonAccumulation() {
        val r = Readiness.assess(ReadinessInput(acwr = 1.6, sleep = 1, sorenessRegionCount = 4))
        assertEquals(ReadinessLevel.REST_ADVISED, r.level)          // acwr>1.5 wins the level
        assertTrue(r.reasons.any { it.startsWith("High acute load") })
        assertTrue(r.reasons.contains("Poor sleep"))               // lower-section clauses still add reasons
        assertTrue(r.reasons.any { it.startsWith("Soreness in 4") })
    }

    @Test fun lifeEventEmitsContextNotContent() {
        val r = Readiness.assess(ReadinessInput(activeLifeEventMaxImpact = 3))
        assertEquals(ReadinessLevel.CAUTION, r.level)
        assertTrue(r.reasons.contains("high-stress window"))
        assertFalse(r.reasons.any { it.contains("bereavement") || it.contains("exam") })
    }

    @Test fun missingInputsNeverFire() {
        val r = Readiness.assess(ReadinessInput())
        assertEquals(ReadinessLevel.READY, r.level)
        assertEquals(listOf("Ready to train"), r.reasons)
    }

    @Test fun hiatusIsQuiet() {
        assertEquals(ReadinessLevel.QUIET, Readiness.assess(ReadinessInput(hiatusActive = true, acwr = 1.9)).level)
    }

    @Test fun injuryClausesV3() {
        // severity 3 ACTIVE → REST_ADVISED
        val sev3 = Readiness.assess(ReadinessInput(activeInjuries = listOf(InjurySummary(listOf("rotator_cuff_r"), 3))))
        assertEquals(ReadinessLevel.REST_ADVISED, sev3.level)
        assertTrue(sev3.reasons.any { it.contains("rotator_cuff_r") })
        // severity 2 ACTIVE → CAUTION
        val sev2 = Readiness.assess(ReadinessInput(activeInjuries = listOf(InjurySummary(listOf("erector_l"), 2))))
        assertEquals(ReadinessLevel.CAUTION, sev2.level)
        // severity 1 fires nothing
        assertEquals(
            ReadinessLevel.READY,
            Readiness.assess(ReadinessInput(activeInjuries = listOf(InjurySummary(listOf("hand_l"), 1)))).level,
        )
        // additive default keeps old call sites compiling + behaving
        assertEquals(ReadinessLevel.READY, Readiness.assess(ReadinessInput()).level)
    }
}

class CycleEstimatorTest {
    private val start = LocalDate.of(2026, 1, 1)

    @Test fun gateBelowThreeCycles() {
        // 3 starts = 2 intervals < 3 → null
        val starts = listOf(start, start.plusDays(28), start.plusDays(56))
        assertEquals(null, CycleEstimator.estimate(starts, start.plusDays(60)))
    }

    @Test fun medianMadAndBuckets() {
        // 5 starts, all 28-day intervals (4 intervals ≥ gate)
        val starts = (0..4).map { start.plusDays(it * 28L) }
        val latest = starts.last()

        val menstrual = CycleEstimator.estimate(starts, latest.plusDays(2))!!  // day 3
        assertEquals(28.0, menstrual.cycleLengthDays, 1e-9)
        assertEquals(0.0, menstrual.uncertaintyDays, 1e-9)
        assertEquals(CyclePhase.MENSTRUAL, menstrual.phase)

        val ovulatory = CycleEstimator.estimate(starts, latest.plusDays(13))!! // day 14 = 28−14
        assertEquals(CyclePhase.OVULATORY, ovulatory.phase)

        val luteal = CycleEstimator.estimate(starts, latest.plusDays(20))!!    // day 21
        assertEquals(CyclePhase.LUTEAL, luteal.phase)
    }
}
