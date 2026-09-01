package xyz.mdhv.formanalyser.coach

import xyz.mdhv.formanalyser.wellness.AcwrZone
import xyz.mdhv.formanalyser.wellness.InjurySummary
import xyz.mdhv.formanalyser.wellness.PrivacyClass
import xyz.mdhv.formanalyser.wellness.ReadinessLevel
import xyz.mdhv.formanalyser.wellness.StreakState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FactsheetTest {

    @Test
    fun `factsheet is deterministic and compact`() {
        val facts = CoachFacts(
            readinessLevel = ReadinessLevel.CAUTION,
            readinessReasons = listOf("Poor sleep"),
            acwr = 1.42,
            acwrZone = AcwrZone.CAUTION,
            load = ShotLoadSummary(3, 240, 12, 900),
            streak = StreakState(7, 1, null, provisionalToday = true),
            activeInjuries = listOf(InjurySummary(listOf("lat_l"), 2)),
            rig = RigSummary("Recurve A", "RECURVE", 38.0, 28.5),
        )
        val a = Factsheet.full(facts)
        val b = Factsheet.full(facts)
        assertEquals(a, b)
        assertTrue(a.contains("Readiness: CAUTION"))
        assertTrue(a.contains("ACWR: 1.42"))
        assertTrue(a.contains("Rig: Recurve A"))
    }

    @Test
    fun `empty facts render a placeholder`() {
        assertEquals("(no facts available)", Factsheet.full(CoachFacts()))
    }

    @Test
    fun `each private fact is classified PRIVATE`() {
        val lines = Grounding.factLines(
            CoachFacts(moodNote = "m", lifeEventNote = "l", cyclePhase = "c"),
        )
        assertEquals(3, lines.size)
        assertTrue(lines.all { it.privacy == PrivacyClass.PRIVATE })
    }

    @Test
    fun `medications classified MEDICAL and injuries SHAREABLE`() {
        val lines = Grounding.factLines(
            CoachFacts(
                medications = listOf("x"),
                activeInjuries = listOf(InjurySummary(listOf("lat_l"), 2)),
            ),
        )
        assertEquals(PrivacyClass.MEDICAL, lines.first { it.key == "medication" }.privacy)
        assertEquals(PrivacyClass.SHAREABLE, lines.first { it.key == "injury" }.privacy)
    }
}
