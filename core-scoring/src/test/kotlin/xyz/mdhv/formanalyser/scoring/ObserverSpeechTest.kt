package xyz.mdhv.formanalyser.scoring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The voice grammar's contract, exercised without a microphone.
 *
 * The rejection cases matter more than the acceptance ones: a wrongly accepted phrase writes a
 * false arrow onto an authoritative scorecard, while a wrongly rejected one costs one repetition.
 */
class ObserverSpeechTest {

    private fun accepted(transcript: String): ObserverSpeech.Result.Accepted {
        val r = ObserverSpeech.parse(transcript)
        assertTrue(r is ObserverSpeech.Result.Accepted, "expected accept for '$transcript', got $r")
        return r
    }

    private fun rejected(transcript: String): ObserverSpeech.Result.Rejected {
        val r = ObserverSpeech.parse(transcript)
        assertTrue(r is ObserverSpeech.Result.Rejected, "expected reject for '$transcript', got $r")
        return r
    }

    @Test
    fun `numerals words X and miss all reach the keypad vocabulary`() {
        assertEquals("9", accepted("nine").token)
        assertEquals("9", accepted("9").token)
        assertEquals("10", accepted("Ten.").token)
        assertEquals("X", accepted("x").token)
        assertEquals("M", accepted("miss").token)
        assertEquals("M", accepted("zero").token)
        // Everything the parser emits must be a token the existing keypad path already accepts.
        ObserverSpeech.examples.forEach { phrase ->
            ScoreInput.parse(accepted(phrase).token)
        }
    }

    @Test
    fun `tokens round-trip through ScoreInput to the same arrow the keypad would record`() {
        assertEquals(ArrowScore.X, ScoreInput.parse(accepted("X").token))
        assertEquals(ArrowScore.MISS, ScoreInput.parse(accepted("missed").token))
        assertEquals(ArrowScore.points(7), ScoreInput.parse(accepted("seven").token))
    }

    @Test
    fun `ten and X in one phrase are one arrow, other repetitions are refused`() {
        assertEquals("X", accepted("ten x").token)
        assertEquals(
            ObserverSpeech.RejectReason.TWO_SCORE_WORDS,
            rejected("nine nine").reason,
        )
        assertEquals(ObserverSpeech.RejectReason.TWO_SCORE_WORDS, rejected("nine or ten").reason)
    }

    @Test
    fun `the nine sector boxes parse in either word order`() {
        assertEquals(ObserverSector.BOTTOM_LEFT, accepted("eight bottom left").sector)
        assertEquals(ObserverSector.BOTTOM_LEFT, accepted("left bottom 8").sector)
        assertEquals(ObserverSector.TOP_CENTER, accepted("9 top middle").sector)
        assertEquals(ObserverSector.MID_RIGHT, accepted("7 middle right").sector)
        assertEquals(ObserverSector.CENTER, accepted("10 centre").sector)
        assertEquals(ObserverSector.CENTER, accepted("10 center").sector)
        assertEquals(ObserverSector.TOP_RIGHT, accepted("six top-right").sector)
    }

    @Test
    fun `half a sector keeps the score and reports the loss instead of inventing a row`() {
        val r = accepted("eight left")
        assertEquals("8", r.token)
        assertNull(r.sector)
        assertTrue(r.sectorDropped)
    }

    @Test
    fun `two words on one axis are not a box`() {
        assertEquals(ObserverSpeech.RejectReason.BAD_SECTOR, rejected("nine top bottom").reason)
        assertEquals(ObserverSpeech.RejectReason.BAD_SECTOR, rejected("nine left right").reason)
        assertEquals(
            ObserverSpeech.RejectReason.BAD_SECTOR,
            rejected("nine top left bottom").reason,
        )
    }

    @Test
    fun `nothing outside the vocabulary is skipped as filler`() {
        assertEquals(ObserverSpeech.RejectReason.UNKNOWN_WORD, rejected("nine in the gold").reason)
        assertEquals(ObserverSpeech.RejectReason.NO_SCORE_WORD, rejected("good shot").reason)
        assertEquals(ObserverSpeech.RejectReason.NOTHING_HEARD, rejected("   ").reason)
    }

    @Test
    fun `common English homophones of score words are not accepted as scores`() {
        // These are the words most likely to arrive from a neighbouring lane's conversation. A
        // repetition is cheap; a false four is not.
        listOf("for", "to", "too", "won", "ate", "fine", "sex", "free").forEach {
            assertEquals(
                ObserverSpeech.RejectReason.NO_SCORE_WORD,
                rejected(it).reason,
                "'$it' must not be read as a score",
            )
        }
    }

    @Test
    fun `agreeing hypotheses accept and the top one is what the athlete is shown`() {
        val r =
            ObserverSpeech.resolve(
                listOf(
                    ObserverSpeech.Hypothesis("nine", 0.9f),
                    ObserverSpeech.Hypothesis("9", 0.7f),
                    ObserverSpeech.Hypothesis("9.", 0.6f),
                )
            )
        assertTrue(r is ObserverSpeech.Result.Accepted)
        assertEquals("9", r.token)
        assertEquals("nine", r.heard)
    }

    @Test
    fun `hypotheses that disagree on the score are refused rather than ranked`() {
        val r =
            ObserverSpeech.resolve(
                listOf(
                    ObserverSpeech.Hypothesis("nine", 0.8f),
                    ObserverSpeech.Hypothesis("five", 0.75f),
                )
            )
        assertTrue(r is ObserverSpeech.Result.Rejected)
        assertEquals(ObserverSpeech.RejectReason.DISAGREEMENT, r.reason)
    }

    @Test
    fun `hypotheses that agree on the score but not the sector keep the arrow and drop the sector`() {
        val r =
            ObserverSpeech.resolve(
                listOf(
                    ObserverSpeech.Hypothesis("nine bottom left", 0.8f),
                    ObserverSpeech.Hypothesis("nine bottom right", 0.7f),
                )
            )
        assertTrue(r is ObserverSpeech.Result.Accepted)
        assertEquals("9", r.token)
        assertNull(r.sector)
        assertTrue(r.sectorDropped)
    }

    @Test
    fun `an unparseable list reports the top hypothesis's own reason`() {
        val r =
            ObserverSpeech.resolve(
                listOf(
                    ObserverSpeech.Hypothesis("good shot", 0.9f),
                    ObserverSpeech.Hypothesis("nine in the gold", 0.4f),
                )
            )
        assertTrue(r is ObserverSpeech.Result.Rejected)
        assertEquals(ObserverSpeech.RejectReason.NO_SCORE_WORD, r.reason)
        assertEquals("good shot", r.heard)
    }

    @Test
    fun `a recogniser that doubts itself is refused, one that reports nothing is not`() {
        val doubted =
            ObserverSpeech.resolve(listOf(ObserverSpeech.Hypothesis("nine", 0.1f)))
        assertTrue(doubted is ObserverSpeech.Result.Rejected)
        assertEquals(ObserverSpeech.RejectReason.LOW_CONFIDENCE, doubted.reason)
        // Engines that report 0f for everything must not silently disable voice entirely.
        assertTrue(
            ObserverSpeech.resolve(listOf(ObserverSpeech.Hypothesis("nine", 0f))) is
                ObserverSpeech.Result.Accepted
        )
        assertTrue(
            ObserverSpeech.resolve(listOf(ObserverSpeech.Hypothesis("nine"))) is
                ObserverSpeech.Result.Accepted
        )
    }

    @Test
    fun `an empty result list is nothing heard, not an error`() {
        val r = ObserverSpeech.resolve(emptyList())
        assertTrue(r is ObserverSpeech.Result.Rejected)
        assertEquals(ObserverSpeech.RejectReason.NOTHING_HEARD, r.reason)
    }

    @Test
    fun `every sector label is itself a phrase the parser reads back to the same box`() {
        // The 3×3 tap picker renders these labels; if a label did not parse, the spoken and tapped
        // vocabularies would have quietly diverged in the one column that stores both.
        ObserverSector.entries.forEach { s ->
            assertEquals(s, accepted("nine ${s.label}").sector, "label '${s.label}'")
        }
    }
}
