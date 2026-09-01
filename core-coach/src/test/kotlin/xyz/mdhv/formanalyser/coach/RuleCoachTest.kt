package xyz.mdhv.formanalyser.coach

import xyz.mdhv.formanalyser.wellness.InjurySummary
import xyz.mdhv.formanalyser.wellness.StreakState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuleCoachTest {

    private fun codes(facts: CoachFacts) = RuleCoach.insights(facts).map { it.code }

    @Test
    fun `high acwr fires deload warning`() {
        val c = codes(CoachFacts(acwr = 1.8))
        assertTrue("acwr_spike" in c)
        assertEquals(InsightSeverity.WARNING, RuleCoach.insights(CoachFacts(acwr = 1.8)).first { it.code == "acwr_spike" }.severity)
    }

    @Test
    fun `climbing acwr fires advice not warning`() {
        val c = codes(CoachFacts(acwr = 1.4))
        assertTrue("acwr_climbing" in c)
        assertTrue("acwr_spike" !in c)
    }

    @Test
    fun `sweet-spot acwr fires nothing`() {
        assertTrue(codes(CoachFacts(acwr = 1.1)).isEmpty())
    }

    @Test
    fun `severe injury fires protect warning`() {
        val insights = RuleCoach.insights(CoachFacts(activeInjuries = listOf(InjurySummary(listOf("rotator_cuff_r"), 3))))
        val i = insights.first { it.code == "injury_protect_severe" }
        assertEquals(InsightSeverity.WARNING, i.severity)
        assertTrue(i.detail.contains("rotator_cuff_r"))
    }

    @Test
    fun `moderate injury fires advice`() {
        val c = codes(CoachFacts(activeInjuries = listOf(InjurySummary(listOf("lat_l"), 2))))
        assertTrue("injury_protect" in c)
        assertTrue("injury_protect_severe" !in c)
    }

    @Test
    fun `stale checkin prompts`() {
        assertTrue("checkin_stale" in codes(CoachFacts(checkinAgeHours = 48.0)))
        assertTrue("checkin_stale" !in codes(CoachFacts(checkinAgeHours = 10.0)))
    }

    @Test
    fun `provisional streak prompts a lock-in`() {
        val prov = StreakState(length = 5, patchedCount = 0, brokenAt = null, provisionalToday = true)
        assertTrue("streak_provisional" in codes(CoachFacts(streak = prov)))
        val notProv = prov.copy(provisionalToday = false)
        assertTrue("streak_provisional" !in codes(CoachFacts(streak = notProv)))
    }

    @Test
    fun `empty facts yield no insights`() {
        assertTrue(RuleCoach.insights(CoachFacts()).isEmpty())
    }

    @Test
    fun `insights are ordered by severity`() {
        val facts = CoachFacts(
            acwr = 1.9, // WARNING
            activeInjuries = listOf(InjurySummary(listOf("lat_l"), 2)), // ADVICE
            streak = StreakState(3, 0, null, provisionalToday = true), // INFO
        )
        val sev = RuleCoach.insights(facts).map { it.severity.ordinal }
        assertEquals(sev.sortedDescending(), sev, "insights must be severity-descending")
    }

    @Test
    fun `deterministic across repeated calls`() {
        val facts = CoachFacts(acwr = 1.9, checkinAgeHours = 50.0)
        assertEquals(RuleCoach.insights(facts), RuleCoach.insights(facts))
    }
}
