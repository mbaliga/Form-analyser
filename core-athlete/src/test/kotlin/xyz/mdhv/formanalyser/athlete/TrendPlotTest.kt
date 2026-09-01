package xyz.mdhv.formanalyser.athlete

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrendPlotTest {
    private val day = 86_400_000L

    @Test
    fun `empty and single series are handled without dividing by zero`() {
        assertEquals(emptyList(), TrendPlot.xFractions(emptyList()))
        assertEquals(listOf(0.5), TrendPlot.xFractions(listOf(1_700_000_000_000L)))
    }

    @Test
    fun `points recorded at the same instant stack at the centre`() {
        val t = 1_700_000_000_000L
        assertEquals(listOf(0.5, 0.5, 0.5), TrendPlot.xFractions(listOf(t, t, t)))
    }

    @Test
    fun `series spans the full width from first to last`() {
        val x = TrendPlot.xFractions(listOf(0L, day, 2 * day))
        assertEquals(0.0, x.first())
        assertEquals(1.0, x.last())
    }

    /**
     * Regression: charts spaced points by array index, so these three sessions — two consecutive
     * days then a three-month layoff — rendered as evenly spaced thirds, hiding the gap entirely.
     * The middle point must sit near the start, not at 0.5.
     */
    @Test
    fun `a long layoff is visible instead of being flattened to even spacing`() {
        val x = TrendPlot.xFractions(listOf(0L, day, 90 * day))
        assertTrue(abs(x[1] - 1.0 / 90.0) < 1e-9, "day 1 of 90 should sit at ~1/90, got ${x[1]}")
        assertTrue(x[1] < 0.05, "index spacing would have put this at 0.5")
    }

    @Test
    fun `unsorted input is placed by time, not by position`() {
        val x = TrendPlot.xFractions(listOf(2 * day, 0L, day))
        assertEquals(listOf(1.0, 0.0, 0.5), x)
    }
}
