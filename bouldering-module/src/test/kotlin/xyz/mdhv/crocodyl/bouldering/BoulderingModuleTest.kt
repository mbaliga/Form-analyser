package xyz.mdhv.crocodyl.bouldering

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BoulderingModuleTest {
    @Test
    fun `attempts and outcomes become interpretable features`() {
        val problem = BoulderProblemAttempt(
            id = "p-1",
            gradeSystem = ClimbingGradeSystem.FONT,
            gradeLabel = "6A+",
            style = BoulderStyle.OVERHANG,
            attempts = 3,
            outcome = BoulderOutcome.ZONE,
            perceivedEffortRpe = 8,
        )
        val features = BoulderingModule.extractor.extract(problem)
        assertEquals("bouldering", BoulderingModule.sportId)
        assertEquals(3.0, features["attempt_count"])
        assertEquals(1.0, features["zone_reached"])
        assertEquals(0.0, features["problem_topped"])
        assertEquals("6A+", problem.gradeLabel)
    }

    @Test
    fun `invalid labels attempts and effort are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            BoulderProblemAttempt("p", ClimbingGradeSystem.V_SCALE, "", BoulderStyle.SLAB, 1, BoulderOutcome.FALL)
        }
        assertFailsWith<IllegalArgumentException> {
            BoulderProblemAttempt("p", ClimbingGradeSystem.V_SCALE, "V2", BoulderStyle.SLAB, 0, BoulderOutcome.FALL)
        }
        assertFailsWith<IllegalArgumentException> {
            BoulderProblemAttempt("p", ClimbingGradeSystem.CUSTOM, "x", BoulderStyle.UNKNOWN, 1, BoulderOutcome.TOP, 0)
        }
    }

    @Test
    fun `sent result uses a binary outcome score`() {
        assertEquals(true, BoulderingModule.scoring.isValid(1.0))
        assertEquals(false, BoulderingModule.scoring.isValid(0.5))
    }
}
