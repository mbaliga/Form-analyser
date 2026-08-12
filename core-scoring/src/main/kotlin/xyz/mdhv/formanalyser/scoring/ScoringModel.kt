package xyz.mdhv.formanalyser.scoring

import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

/** How a scorecard is resolved. Qualification and practice use cumulative points. */
enum class ScoringKind { QUALIFICATION, SET_MATCH, PRACTICE }

enum class FaceLayout { SINGLE, VERTICAL_TRIPLE, TRIANGULAR_TRIPLE }

enum class ScoreSource { MANUAL_NUMERIC, MANUAL_PLOT, END_SCAN, LIVE_OBSERVER, IMPORT }

enum class AuthorityState { HUMAN_CONFIRMED, MACHINE_PROPOSED, UNRESOLVED }

enum class ObservationResolution { SHOT_CONFIRMED, SHOT_INFERRED, END_ONLY, SESSION_ONLY }

/**
 * A versioned round definition. Built-in IDs are stable persistence keys; custom rounds should use
 * a caller-owned UUID-like ID and persist the full definition alongside the score session.
 */
data class RoundDefinition(
    val id: String,
    val name: String,
    val distanceMeters: Int,
    val targetFaceCm: Int,
    val arrowsPerEnd: Int,
    val endCount: Int,
    val scoringKind: ScoringKind,
    val faceLayout: FaceLayout = FaceLayout.SINGLE,
    val maxArrowScore: Int = 10,
) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank())
        require(distanceMeters > 0)
        require(targetFaceCm > 0)
        require(arrowsPerEnd > 0)
        require(endCount > 0)
        require(maxArrowScore > 0)
    }

    val maxArrows: Int get() = arrowsPerEnd * endCount
    val maximumTotal: Int get() = maxArrows * maxArrowScore
}

/** X is stored separately because it scores 10 but matters to tie-break/count summaries. */
data class ArrowScore(val points: Int, val isX: Boolean = false) {
    init {
        require(points in 0..10) { "Arrow score must be 0..10" }
        require(!isX || points == 10) { "X must score 10" }
    }

    companion object {
        val X = ArrowScore(10, isX = true)
        val TEN = ArrowScore(10)
        val MISS = ArrowScore(0)

        fun points(value: Int): ArrowScore = ArrowScore(value)
    }
}

/** Normalized target coordinate. (-1,-1) to (1,1) spans the full face bounding square. */
data class PlotPoint(val x: Double, val y: Double, val faceIndex: Int = 0) {
    init {
        require(x.isFinite() && y.isFinite())
        require(x in -1.5..1.5 && y in -1.5..1.5)
        require(faceIndex >= 0)
    }

    val radius: Double get() = hypot(x, y)
}

data class ScoredArrow(
    val id: String,
    val endIndex: Int,
    val arrowIndex: Int,
    val score: ArrowScore,
    val plot: PlotPoint? = null,
    val source: ScoreSource = ScoreSource.MANUAL_NUMERIC,
    val authority: AuthorityState = AuthorityState.HUMAN_CONFIRMED,
    val resolution: ObservationResolution = ObservationResolution.SHOT_CONFIRMED,
) {
    init {
        require(id.isNotBlank())
        require(endIndex >= 0)
        require(arrowIndex >= 0)
    }
}

data class EndSummary(
    val endIndex: Int,
    val arrows: List<ScoredArrow>,
    val total: Int,
    val xCount: Int,
    val isComplete: Boolean,
)

data class SetMatchSummary(
    val athleteSetPoints: Int,
    val opponentSetPoints: Int,
    val completedSets: Int,
    val winner: Winner?,
) {
    enum class Winner { ATHLETE, OPPONENT }
}

data class GroupMetrics(
    val plottedArrowCount: Int,
    val meanX: Double,
    val meanY: Double,
    val meanRadiusCm: Double,
    val maxSpreadCm: Double,
    val centerOffsetCm: Double,
)

data class Scorecard(
    val round: RoundDefinition,
    val arrows: List<ScoredArrow> = emptyList(),
    val opponentEndTotals: Map<Int, Int> = emptyMap(),
    val shootOffWinner: SetMatchSummary.Winner? = null,
) {
    init {
        require(arrows.size <= round.maxArrows)
        arrows.forEachIndexed { index, arrow ->
            val expectedEnd = index / round.arrowsPerEnd
            val expectedArrow = index % round.arrowsPerEnd
            require(arrow.endIndex == expectedEnd && arrow.arrowIndex == expectedArrow) {
                "Arrow ordering must be contiguous; got end=${arrow.endIndex} arrow=${arrow.arrowIndex}, expected end=$expectedEnd arrow=$expectedArrow"
            }
        }
    }

    val total: Int get() = arrows.sumOf { it.score.points }
    val xCount: Int get() = arrows.count { it.score.isX }
    val tenCount: Int get() = arrows.count { it.score.points == 10 }
    val arrowCount: Int get() = arrows.size
    val isFull: Boolean get() = arrows.size >= round.maxArrows
    val currentEndIndex: Int get() = if (isFull) max(0, round.endCount - 1) else arrows.size / round.arrowsPerEnd
    val currentArrowIndex: Int get() = if (isFull) round.arrowsPerEnd - 1 else arrows.size % round.arrowsPerEnd

    fun record(
        id: String,
        score: ArrowScore,
        plot: PlotPoint? = null,
        source: ScoreSource = if (plot == null) ScoreSource.MANUAL_NUMERIC else ScoreSource.MANUAL_PLOT,
        authority: AuthorityState = AuthorityState.HUMAN_CONFIRMED,
        resolution: ObservationResolution = ObservationResolution.SHOT_CONFIRMED,
    ): Scorecard {
        require(!isFull) { "Round is already full" }
        if (round.scoringKind == ScoringKind.SET_MATCH) {
            val set = arrows.size / round.arrowsPerEnd
            val match = setMatchSummary()
            require(match?.winner == null) { "Set match is already decided" }
            require(set < round.endCount)
            if (arrows.isNotEmpty() && arrows.size % round.arrowsPerEnd == 0) {
                val previousSet = set - 1
                require(opponentEndTotals.containsKey(previousSet)) {
                    "Record the opponent total for set ${previousSet + 1} before starting the next set"
                }
            }
        }
        val index = arrows.size
        return copy(arrows = arrows + ScoredArrow(
            id = id,
            endIndex = index / round.arrowsPerEnd,
            arrowIndex = index % round.arrowsPerEnd,
            score = score,
            plot = plot,
            source = source,
            authority = authority,
            resolution = resolution,
        ))
    }

    fun undoLast(): Scorecard = if (arrows.isEmpty()) this else copy(arrows = arrows.dropLast(1))

    fun replaceArrow(index: Int, score: ArrowScore, plot: PlotPoint? = arrows.getOrNull(index)?.plot): Scorecard {
        require(index in arrows.indices)
        val next = arrows.toMutableList()
        val old = next[index]
        next[index] = old.copy(score = score, plot = plot)
        return copy(arrows = next)
    }

    fun withOpponentEndTotal(endIndex: Int, total: Int): Scorecard {
        require(round.scoringKind == ScoringKind.SET_MATCH)
        require(endIndex in 0 until round.endCount)
        require(total in 0..(round.arrowsPerEnd * round.maxArrowScore))
        return copy(opponentEndTotals = opponentEndTotals + (endIndex to total))
    }

    fun withShootOffWinner(winner: SetMatchSummary.Winner): Scorecard {
        require(round.scoringKind == ScoringKind.SET_MATCH)
        val summary = setMatchSummary() ?: error("Not a set match")
        require(summary.completedSets == round.endCount && summary.athleteSetPoints == summary.opponentSetPoints) {
            "Shoot-off is only valid after a tied five-set match"
        }
        return copy(shootOffWinner = winner)
    }

    fun ends(): List<EndSummary> = arrows.groupBy { it.endIndex }.toSortedMap().map { (endIndex, endArrows) ->
        EndSummary(endIndex, endArrows, endArrows.sumOf { it.score.points }, endArrows.count { it.score.isX }, endArrows.size == round.arrowsPerEnd)
    }

    fun setMatchSummary(): SetMatchSummary? {
        if (round.scoringKind != ScoringKind.SET_MATCH) return null
        var athlete = 0
        var opponent = 0
        var completed = 0
        for (end in ends()) {
            if (!end.isComplete) break
            val opponentTotal = opponentEndTotals[end.endIndex] ?: break
            when {
                end.total > opponentTotal -> athlete += 2
                end.total < opponentTotal -> opponent += 2
                else -> { athlete += 1; opponent += 1 }
            }
            completed += 1
            if (athlete >= 6 || opponent >= 6) break
        }
        if (completed == round.endCount && athlete == opponent && shootOffWinner != null) {
            when (shootOffWinner) {
                SetMatchSummary.Winner.ATHLETE -> athlete += 1
                SetMatchSummary.Winner.OPPONENT -> opponent += 1
            }
        }
        val winner = when {
            athlete >= 6 -> SetMatchSummary.Winner.ATHLETE
            opponent >= 6 -> SetMatchSummary.Winner.OPPONENT
            else -> null
        }
        return SetMatchSummary(athlete, opponent, completed, winner)
    }

    fun isComplete(): Boolean = when (round.scoringKind) {
        ScoringKind.SET_MATCH -> setMatchSummary()?.winner != null
        else -> isFull
    }

    fun grouping(): GroupMetrics? {
        val points = arrows.mapNotNull { it.plot }
        if (points.size < 2) return null
        val meanX = points.map { it.x }.average()
        val meanY = points.map { it.y }.average()
        val faceRadiusCm = round.targetFaceCm / 2.0
        val meanRadiusCm = points.map { hypot(it.x - meanX, it.y - meanY) }.average() * faceRadiusCm
        var maxSpread = 0.0
        for (i in points.indices) for (j in i + 1 until points.size) {
            maxSpread = max(maxSpread, hypot(points[i].x - points[j].x, points[i].y - points[j].y))
        }
        return GroupMetrics(points.size, meanX, meanY, meanRadiusCm, maxSpread * faceRadiusCm, hypot(meanX, meanY) * faceRadiusCm)
    }
}

fun scoreFromPlot(point: PlotPoint, faceLayout: FaceLayout = FaceLayout.SINGLE): ArrowScore {
    val r = point.radius
    if (r > 1.0) return ArrowScore.MISS
    if (r <= 0.05) return ArrowScore.X
    val ring = ceil(r * 10.0).toInt().coerceIn(1, 10)
    val score = 11 - ring
    if (faceLayout != FaceLayout.SINGLE && score < 6) return ArrowScore.MISS
    return ArrowScore(points = score)
}

data class PersonalBestResult(val currentTotal: Int, val previousBest: Int?, val isNewPersonalBest: Boolean)

fun personalBest(scorecard: Scorecard, previousBest: Int?): PersonalBestResult {
    require(scorecard.round.scoringKind != ScoringKind.SET_MATCH) { "PB total is not defined for set match" }
    val eligible = scorecard.isComplete()
    return PersonalBestResult(scorecard.total, previousBest, eligible && (previousBest == null || scorecard.total > previousBest))
}
