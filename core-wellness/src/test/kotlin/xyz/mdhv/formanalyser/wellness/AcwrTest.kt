package xyz.mdhv.formanalyser.wellness

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AcwrTest {
    private val d0 = LocalDate.of(2026, 1, 1)

    @Test fun ewmaAgainstHandComputedFixture() {
        val loads = listOf(
            DailyLoad(d0, shotLoad = 100.0, srpeLoad = 0.0, complete = true),
            DailyLoad(d0.plusDays(1), shotLoad = 200.0, srpeLoad = 0.0, complete = true),
        )
        val s = Acwr.compute(loads, today = d0.plusDays(1))
        assertEquals(2, s.points.size)
        val last = s.latest!!
        // seed day0: acute=chronic=100. day1: acute=0.25*200+0.75*100=125
        assertEquals(125.0, last.acuteEwma, 1e-9)
        // chronic = (2/29)*200 + (27/29)*100 = 106.8966
        assertEquals(106.89655, last.chronicEwma, 1e-4)
        assertEquals(125.0 / 106.89655, last.acwr!!, 1e-4)
        assertEquals(AcwrZone.SWEET, last.zone) // ~1.169 ∈ (0.8, 1.3]
    }

    @Test fun warmupGateAndChronicGuard() {
        // young history → warm-up not complete
        val young = listOf(DailyLoad(d0, 100.0, 0.0, true), DailyLoad(d0.plusDays(1), 120.0, 0.0, true))
        assertFalse(Acwr.compute(young, d0.plusDays(1)).warmupComplete)

        // all-zero load → chronic below guard → acwr null
        val zero = listOf(DailyLoad(d0, 0.0, 0.0, true))
        assertNull(Acwr.compute(zero, d0).latest!!.acwr)
    }

    @Test fun warmupCompletesWithEnoughHistory() {
        // 25 consecutive training days → 25 calendar, 25 training ≥ thresholds
        val loads = (0 until 25).map { DailyLoad(d0.plusDays(it.toLong()), 100.0, 0.0, true) }
        val s = Acwr.compute(loads, d0.plusDays(24))
        assertTrue(s.warmupComplete)
        assertEquals(25, s.calendarDays)
        assertEquals(25, s.trainingDays)
    }

    @Test fun zoneBoundaries() {
        assertEquals(AcwrZone.DETRAINING, Acwr.zoneOf(0.5))
        assertEquals(AcwrZone.SWEET, Acwr.zoneOf(1.0))
        assertEquals(AcwrZone.CAUTION, Acwr.zoneOf(1.4))
        assertEquals(AcwrZone.SPIKE, Acwr.zoneOf(1.8))
    }
}
