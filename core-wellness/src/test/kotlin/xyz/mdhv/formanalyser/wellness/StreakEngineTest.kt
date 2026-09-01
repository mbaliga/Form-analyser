package xyz.mdhv.formanalyser.wellness

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreakEngineTest {
    private val d0 = LocalDate.of(2026, 2, 1)
    private fun day(offset: Int, s: Boolean = false, r: Boolean = false, p: Boolean = false, c: Boolean = false, h: Boolean = false) =
        DayFacts(d0.plusDays(offset.toLong()), session = s, restLogged = r, plannedRest = p, anyCheckin = c, hiatus = h)

    @Test fun truthTableAllClauses() {
        assertTrue(StreakEngine.qualifies(day(0, s = true)))
        assertTrue(StreakEngine.qualifies(day(0, r = true)))
        assertTrue(StreakEngine.qualifies(day(0, p = true, c = true)))   // planned rest + check-in
        assertTrue(StreakEngine.qualifies(day(0, h = true)))
        // negatives
        assertEquals(false, StreakEngine.qualifies(day(0)))
        assertEquals(false, StreakEngine.qualifies(day(0, p = true)))    // planned rest, no check-in
        assertEquals(false, StreakEngine.qualifies(day(0, c = true)))    // check-in alone
    }

    @Test fun singleGracePatches_doubleBreaks() {
        val patched = StreakEngine.evaluate(listOf(day(0, s = true), day(1), day(2, s = true)))
        assertEquals(2, patched.length)
        assertEquals(1, patched.patchedCount)
        assertNull(patched.brokenAt)

        val broken = StreakEngine.evaluate(listOf(day(0, s = true), day(1), day(2), day(3, s = true)))
        assertEquals(1, broken.length)              // reset then one fresh qualifying day
        assertEquals(0, broken.patchedCount)
        assertEquals(d0.plusDays(2), broken.brokenAt)
    }

    @Test fun hiatusFreezesWithoutPatching() {
        val s = StreakEngine.evaluate(listOf(day(0, h = true), day(1, h = true), day(2, h = true)))
        assertEquals(3, s.length)
        assertEquals(0, s.patchedCount)
    }

    @Test fun plannedRestWithoutCheckinIsUnlogged() {
        val s = StreakEngine.evaluate(listOf(day(0, s = true), day(1, p = true)))
        assertEquals(1, s.length)          // planned rest alone doesn't qualify
        assertEquals(1, s.patchedCount)    // it's a grace day
    }

    @Test fun todayIsProvisional() {
        val extends = StreakEngine.evaluate(listOf(day(0, s = true)), today = day(1, s = true))
        assertEquals(2, extends.length)
        assertTrue(extends.provisionalToday)

        // today unlogged never breaks, even after a grace day
        val safe = StreakEngine.evaluate(listOf(day(0, s = true), day(1)), today = day(2))
        assertEquals(1, safe.length)
        assertNull(safe.brokenAt)
        assertEquals(false, safe.provisionalToday)
    }

    @Test fun dstBoundaryDays() {
        // US spring-forward weekend — LocalDate math is DST-agnostic; count must be exact.
        val start = LocalDate.of(2026, 3, 7)
        val days = (0..3).map { DayFacts(start.plusDays(it.toLong()), session = true) }
        assertEquals(4, StreakEngine.evaluate(days).length)
    }
}
