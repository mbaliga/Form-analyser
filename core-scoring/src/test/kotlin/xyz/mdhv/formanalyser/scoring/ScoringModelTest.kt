package xyz.mdhv.formanalyser.scoring

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScoringModelTest {
    @Test fun `qualification totals X and end summaries are deterministic`() {
        var card = Scorecard(RoundPack.WA_RECURVE_70M_72)
        val values = listOf(ArrowScore.X, ArrowScore.TEN, ArrowScore.points(9), ArrowScore.points(8), ArrowScore.points(7), ArrowScore.MISS)
        values.forEachIndexed { i, score -> card = card.record("a$i", score) }
        assertEquals(44, card.total); assertEquals(1, card.xCount); assertEquals(2, card.tenCount)
        assertEquals(1, card.ends().size); assertEquals(44, card.ends().single().total); assertTrue(card.ends().single().isComplete)
    }

    @Test fun `undo restores the next arrow slot`() {
        var card = Scorecard(RoundPack.WA_RECURVE_18M_60)
        card = card.record("1", ArrowScore.points(9)).record("2", ArrowScore.points(8))
        assertEquals(2, card.currentArrowIndex); card = card.undoLast(); assertEquals(1, card.currentArrowIndex); assertEquals(9, card.total)
    }

    @Test fun `plot converts rings and outside face to miss`() {
        assertEquals(ArrowScore.X, scoreFromPlot(PlotPoint(0.0, 0.0)))
        assertEquals(10, scoreFromPlot(PlotPoint(0.08, 0.0)).points)
        assertEquals(9, scoreFromPlot(PlotPoint(0.15, 0.0)).points)
        assertEquals(1, scoreFromPlot(PlotPoint(0.95, 0.0)).points)
        assertEquals(0, scoreFromPlot(PlotPoint(0.9, 0.9)).points)
    }

    @Test fun `indoor triple spot makes rings outside six a miss`() {
        assertEquals(6, scoreFromPlot(PlotPoint(0.49, 0.0, 2), FaceLayout.VERTICAL_TRIPLE).points)
        assertEquals(0, scoreFromPlot(PlotPoint(0.51, 0.0, 1), FaceLayout.VERTICAL_TRIPLE).points)
        assertEquals(7, scoreFromPlot(PlotPoint(0.39, 0.0, 0), FaceLayout.TRIANGULAR_TRIPLE).points)
    }

    @Test fun `grouping returns centimetre spread on the configured face`() {
        var card = Scorecard(RoundPack.WA_RECURVE_70M_72)
        card = card.record("1", ArrowScore.TEN, PlotPoint(-0.1, 0.0)).record("2", ArrowScore.TEN, PlotPoint(0.1, 0.0))
        val g = assertNotNull(card.grouping()); assertTrue(abs(g.meanX) < 1e-9); assertTrue(abs(g.maxSpreadCm - 12.2) < 1e-6)
    }

    @Test fun `recurve set match awards two one zero and stops at six`() {
        var card = Scorecard(RoundPack.WA_RECURVE_70M_MATCH)
        fun addSet(prefix: String, values: List<Int>, opponent: Int) {
            values.forEachIndexed { i, value -> card = card.record("$prefix$i", ArrowScore.points(value)) }
            val end = card.ends().last().endIndex; card = card.withOpponentEndTotal(end, opponent)
        }
        addSet("s1", listOf(10,10,9), 27); addSet("s2", listOf(9,9,9), 27); addSet("s3", listOf(10,9,9),27); addSet("s4", listOf(10,10,10),29)
        val summary = assertNotNull(card.setMatchSummary()); assertEquals(7, summary.athleteSetPoints); assertEquals(1, summary.opponentSetPoints)
        assertEquals(SetMatchSummary.Winner.ATHLETE, summary.winner); assertTrue(card.isComplete())
    }

    @Test fun `five set tie requires and resolves shoot off`() {
        var card = Scorecard(RoundPack.WA_RECURVE_70M_MATCH)
        fun tiedSet(prefix: String) { repeat(3) { i -> card = card.record("$prefix$i", ArrowScore.points(9)) }; val end=card.ends().last().endIndex; card=card.withOpponentEndTotal(end,27) }
        repeat(5) { tiedSet("t$it-") }
        val tied=assertNotNull(card.setMatchSummary()); assertEquals(5,tied.athleteSetPoints); assertEquals(5,tied.opponentSetPoints); assertFalse(card.isComplete())
        card=card.withShootOffWinner(SetMatchSummary.Winner.ATHLETE); val resolved=assertNotNull(card.setMatchSummary())
        assertEquals(6,resolved.athleteSetPoints); assertEquals(5,resolved.opponentSetPoints); assertEquals(SetMatchSummary.Winner.ATHLETE,resolved.winner); assertTrue(card.isComplete())
    }

    @Test fun `PB only triggers on a complete cumulative round`() {
        val tiny=RoundDefinition("test","Tiny",10,40,2,1,ScoringKind.PRACTICE)
        var card=Scorecard(tiny).record("1",ArrowScore.TEN); assertFalse(personalBest(card,18).isNewPersonalBest)
        card=card.record("2",ArrowScore.TEN); assertTrue(personalBest(card,18).isNewPersonalBest); assertFalse(personalBest(card,20).isNewPersonalBest)
    }
}
