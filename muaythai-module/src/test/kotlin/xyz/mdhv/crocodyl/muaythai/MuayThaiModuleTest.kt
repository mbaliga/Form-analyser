package xyz.mdhv.crocodyl.muaythai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MuayThaiModuleTest {
    @Test
    fun `round focus and duration remain explicit in extracted training signals`() {
        val round = MuayThaiRound(1, MuayThaiRoundFocus.PADS, 180, 60, perceivedEffortRpe = 7)
        assertEquals("muay_thai", MuayThaiModule.sportId)
        assertEquals(listOf(round), MuayThaiModule.segmenter.segment(listOf(round)))
        assertEquals(180.0, MuayThaiModule.extractor.extract(round)["work_duration_seconds"])
        assertEquals(7.0, MuayThaiModule.extractor.extract(round)["perceived_effort_rpe"])
        assertNull(round.selfRating) // Self-rating remains optional and athlete-controlled.
    }

    @Test
    fun `invalid round durations and self reports are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            MuayThaiRound(1, MuayThaiRoundFocus.TECHNIQUE, 0, 30)
        }
        assertFailsWith<IllegalArgumentException> {
            MuayThaiRound(1, MuayThaiRoundFocus.SPARRING, 180, 60, perceivedEffortRpe = 11)
        }
    }

    @Test
    fun `self rating rubric accepts only whole number values from one to ten`() {
        assertEquals(true, MuayThaiModule.scoring.isValid(8.0))
        assertEquals(false, MuayThaiModule.scoring.isValid(0.0))
        assertEquals(false, MuayThaiModule.scoring.isValid(8.5))
    }
}
