package xyz.mdhv.formanalyser.app.domain

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mdhv.formanalyser.app.data.*
import xyz.mdhv.formanalyser.athlete.*

class ProgressViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    private val scoring = ScoringRepository(app)
    private val features = AthleteFeatureRepository(app)

    data class ScorePoint(
        val atMs: Long,
        val total: Int,
        val max: Int,
        val roundId: String,
        val roundName: String,
        val rigId: String?,
    )

    data class VolumePoint(val atMs: Long, val arrows: Int)

    data class FormPoint(val atMs: Long, val stability: Double, val sessionId: String)

    data class GoalCard(val entity: GoalEntity, val progress: GoalProgress)

    data class RoundPb(val roundName: String, val score: Int, val max: Int)

    data class RigScoreContext(
        val rigId: String,
        val rigName: String,
        val rounds: Int,
        val averagePercent: Double,
        val bestPercent: Double,
    )

    data class UiState(
        val loading: Boolean = true,
        val scorePoints: List<ScorePoint> = emptyList(),
        val volumePoints: List<VolumePoint> = emptyList(),
        val formPoints: List<FormPoint> = emptyList(),
        val scoreTrend: TrendSummary? = null,
        val formTrend: TrendSummary? = null,
        val arrows28d: Int = 0,
        val sessions28d: Int = 0,
        val pbs: List<RoundPb> = emptyList(),
        val equipmentContext: List<RigScoreContext> = emptyList(),
        val goals: List<GoalCard> = emptyList(),
        val interventions: List<InterventionEntity> = emptyList(),
        val plans: List<TrainingPlanEntity> = emptyList(),
        val missingEvidence: List<String> = emptyList(),
        /** Surfaced when a write was rejected — e.g. a goal target that is not a real number. */
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    fun load() {
        viewModelScope.launch {
            // Progress reads every goal, plan and scorecard the athlete has. One malformed row
            // must not brick the whole surface with no way back: without this, a goal that fails
            // GoalDefinition's require() throws on every load and Progress can never be opened
            // again to delete it.
            _state.value =
                runCatching { withContext(Dispatchers.IO) { buildState() } }
                    .getOrElse { t ->
                        UiState(
                            loading = false,
                            missingEvidence =
                                listOf("Could not load Progress: ${t.message ?: "unknown error"}"),
                        )
                    }
        }
    }

    private suspend fun buildState(): UiState {
        val athlete =
            repo.currentAthlete()
                ?: return UiState(loading = false, missingEvidence = listOf("No athlete profile"))
        val formSessions = repo.allSessions(athlete.id)
        val scoreSessions = scoring.recent(250).filter { it.status == "FINISHED" }
        val cumulative = scoreSessions.filter { it.scoringKind != "SET_MATCH" && it.roundComplete }
        val scores =
            cumulative
                .sortedBy { it.completedAt ?: it.updatedAt }
                .map { s ->
                    ScorePoint(
                        s.completedAt ?: s.updatedAt,
                        s.total,
                        s.arrowsPerEnd * s.endCount * 10,
                        s.roundId,
                        s.roundName,
                        s.rigId,
                    )
                }
        val form =
            formSessions
                .mapNotNull { s ->
                    val shots = repo.shotsOnce(s.id)
                    val v =
                        formStability(
                            shots.map { ArcheryAnalyzer.featuresFromJson(it.featuresJson) }
                        ) ?: return@mapNotNull null
                    FormPoint(s.startedAtEpochMs, v, s.id)
                }
                .sortedBy { it.atMs }
        // Arrows scored on cards the athlete attached to a capture session, keyed by that session.
        // A linked pair is one afternoon on the shooting line, so it contributes one volume point —
        // but the two halves can disagree (the capture stopped early, or only half the ends were
        // scored), and taking the larger keeps a link from *removing* arrows the athlete shot.
        val formIds = formSessions.mapTo(mutableSetOf()) { it.id }
        // A link whose capture session is gone is not a link — the card stands on its own rather
        // than folding its arrows into a session that will never be plotted.
        fun ScoreSessionEntity.absorbedBy() = linkedFormSessionId?.takeIf { it in formIds }
        val linkedScored =
            scoreSessions
                .mapNotNull { s -> s.absorbedBy()?.let { it to s } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, cards) -> cards.sumOf { scoringArrowCount(it) } }
        val volume = mutableListOf<VolumePoint>()
        formSessions.forEach { s ->
            val captured = s.arrowsActual ?: repo.shotCount(s.id)
            volume += VolumePoint(s.startedAtEpochMs, maxOf(captured, linkedScored[s.id] ?: 0))
        }
        scoreSessions
            .filter { it.absorbedBy() == null }
            .forEach { s ->
                volume += VolumePoint(s.completedAt ?: s.updatedAt, scoringArrowCount(s))
            }
        val from28 = System.currentTimeMillis() - 28L * 86_400_000L
        val goals =
            features.goals().map { e ->
                GoalCard(
                    e,
                    GoalEngine.evaluate(
                        e.toDefinition(),
                        observationsFor(e, scoreSessions, scores, formSessions, form, volume),
                    ),
                )
            }
        goals
            .filter { it.entity.state == GoalState.ACTIVE.name && it.progress.achieved }
            .forEach { features.setGoalState(it.entity.id, GoalState.ACHIEVED) }
        // From the whole history, not the recent-250 display window, so this agrees with the
        // "New PB" the scorer announces via ScoringDao.previousBest.
        val pbs =
            scoring
                .bestPerRound()
                .map { RoundPb(it.roundName, it.best, it.arrowsPerEnd * it.endCount * 10) }
                .sortedByDescending { it.score.toDouble() / it.max.coerceAtLeast(1) }
        val names = repo.rigsOnce(athlete.id).associate { it.id to it.name }
        val equipment =
            scores
                .filter { it.rigId != null }
                .groupBy { it.rigId!! }
                .map { (rid, p) ->
                    val pct = p.map { 100.0 * it.total / it.max.coerceAtLeast(1) }
                    RigScoreContext(
                        rid,
                        names[rid] ?: "Saved rig",
                        p.size,
                        pct.average(),
                        pct.maxOrNull() ?: 0.0,
                    )
                }
                .sortedByDescending { it.rounds }
        val missing = buildList {
            if (scores.isEmpty()) add("Score a complete round to unlock score/PB trends")
            if (form.size < 2) add("Record more form sessions to establish a stability trend")
            if (goals.isEmpty()) add("Add a goal to track a target against your own history")
        }
        return UiState(
            false,
            scores,
            volume.sortedBy { it.atMs },
            form,
            // Percent of each round's own maximum. Summarising RAW totals fitted one slope across
            // mixed roundIds, so a stretch of 300-point indoor rounds next to 720-point outdoor
            // ones moved the headline "pts/day" for reasons that had nothing to do with shooting.
            // Normalising makes the number comparable, and makes it agree with the chart, which
            // was already plotting percent.
            TrendEngine.summarize(
                scores.map {
                    MetricObservation(
                        it.atMs,
                        100.0 * it.total / it.max.coerceAtLeast(1),
                        it.roundId + "@" + it.atMs,
                    )
                }
            ),
            TrendEngine.summarize(
                form.map { MetricObservation(it.atMs, it.stability, it.sessionId) }
            ),
            volume.filter { it.atMs >= from28 }.sumOf { it.arrows },
            (formSessions.map { it.id to it.startedAtEpochMs } +
                    scoreSessions
                        .filter { it.absorbedBy() == null }
                        .map { it.id to (it.completedAt ?: it.updatedAt) })
                .count { it.second >= from28 },
            pbs,
            equipment,
            goals,
            features.interventions(),
            features.plans(),
            missing,
        )
    }

    private suspend fun observationsFor(
        g: GoalEntity,
        ss: List<ScoreSessionEntity>,
        sp: List<ScorePoint>,
        fs: List<SessionEntity>,
        fp: List<FormPoint>,
        v: List<VolumePoint>,
    ): List<MetricObservation> =
        when (GoalMetric.valueOf(g.metric)) {
            GoalMetric.ROUND_TOTAL ->
                sp.filter { g.scopeKey == null || it.roundId == g.scopeKey }
                    .map {
                        MetricObservation(it.atMs, it.total.toDouble(), it.roundId + "@" + it.atMs)
                    }
            GoalMetric.ARROW_AVERAGE ->
                ss.filter { g.scopeKey == null || it.roundId == g.scopeKey }
                    .mapNotNull { s ->
                        val c = scoringArrowCount(s)
                        if (c <= 0) null
                        else
                            MetricObservation(
                                s.completedAt ?: s.updatedAt,
                                s.total.toDouble() / c,
                                s.id,
                            )
                    }
            GoalMetric.VOLUME_ARROWS ->
                v.map { MetricObservation(it.atMs, it.arrows.toDouble(), "volume@${it.atMs}") }
            GoalMetric.FORM_STABILITY ->
                fp.map { MetricObservation(it.atMs, it.stability, it.sessionId) }
            GoalMetric.TRAINING_SESSIONS -> {
                // Same absorption rule the volume/session count uses, so a "sessions per week" goal
                // and the session count on the same screen can never disagree.
                val formIds = fs.mapTo(mutableSetOf()) { it.id }
                fs.map { MetricObservation(it.startedAtEpochMs, 1.0, it.id) } +
                    ss.filter { it.linkedFormSessionId?.takeIf { id -> id in formIds } == null }
                        .map { MetricObservation(it.completedAt ?: it.updatedAt, 1.0, it.id) }
            }
            GoalMetric.PHYSIO_ADHERENCE -> physioAdherenceObservation()
        }

    private suspend fun physioAdherenceObservation(): List<MetricObservation> {
        val a = repo.currentAthlete() ?: return emptyList()
        val today = LocalDate.now()
        val from = today.minusDays(27)
        val window =
            repo.body.activePlans(a.id, today.toString()).fold(PhysioAdherence.Window.EMPTY) {
                acc,
                p ->
                acc +
                    PhysioAdherence.forPlan(
                        LocalDate.parse(p.startDate),
                        p.endDate?.let(LocalDate::parse),
                        from,
                        today,
                        JsonLists.decode(p.scheduleJson),
                        repo.body.physioSessionsFor(p.id).map {
                            java.time.Instant.ofEpochMilli(it.ts)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate()
                        },
                    )
            }
        val percent = window.percent ?: return emptyList()
        return listOf(MetricObservation(System.currentTimeMillis(), percent, "physio-28d"))
    }

    private suspend fun scoringArrowCount(s: ScoreSessionEntity) =
        xyz.mdhv.formanalyser.app.data.AppDatabase.get(getApplication<Application>())
            .scoringDao()
            .activeArrowCount(s.id)

    fun saveGoal(
        metric: GoalMetric,
        title: String,
        target: Double,
        unit: String,
        direction: GoalDirection,
        aggregation: GoalAggregation,
        targetAtMs: Long? = null,
        scopeKey: String? = null,
    ) = mutate {
        features.saveGoal(
            metric = metric,
            title = title,
            targetValue = target,
            unit = unit,
            direction = direction,
            aggregation = aggregation,
            targetAtMs = targetAtMs,
            scopeKey = scopeKey,
        )
    }

    fun setGoalState(id: String, s: GoalState) = mutate { features.setGoalState(id, s) }

    fun addIntervention(k: String, t: String, n: String?) = mutate {
        features.addIntervention(k, t, n)
    }

    fun savePlan(t: String, p: String, f: String, w: Int?, i: String, r: String?) = mutate {
        features.upsertPlan(null, t, p, f, LocalDate.now().toString(), null, w, i, r)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /**
     * Run a write off the main thread, then reload.
     *
     * The writes validate their input now (see AthleteFeatureRepository.saveGoal), so they can
     * reject. Reporting that back as state rather than letting it escape the coroutine is the
     * difference between "that target isn't a number" and the app disappearing.
     */
    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { block() } }
                .onSuccess { load() }
                .onFailure { t ->
                    _state.value =
                        _state.value.copy(error = t.message ?: "Could not save that change")
                }
        }
    }

    companion object {
        /**
         * Shot-to-shot consistency across a session, 0–100, averaged over the pose features.
         *
         * Measured as spread against a per-feature tolerance rather than as a coefficient of
         * variation. CV (`sd / |mean|`) is wrong for this data: four of the nine features
         * (`spineLeanDeg`, `shoulderTiltDeg`, `headLeanDeg`, `drawArmTiltDeg`) are deviations from
         * level or vertical, so a well-aligned archer drives the mean toward zero, the ratio
         * explodes, and the score clamps to 0 — the better the alignment, the worse it read.
         * Dispersion is what "stability" means here, and it is meaningful on its own scale.
         *
         * Tolerances are first-pass, in the same spirit as the deviation weights in ArcheryModule:
         * the band of shot-to-shot spread beyond which a feature stops looking repeatable. Worth
         * tuning against real footage and scores.
         */
        fun formStability(shots: List<Map<String, Double>>): Double? {
            if (shots.size < 2) return null
            val vals =
                shots
                    .flatMap { it.keys }
                    .toSet()
                    .mapNotNull { k ->
                        val v = shots.mapNotNull { it[k] }.filter { it.isFinite() }
                        if (v.size < 2) return@mapNotNull null
                        val m = v.average()
                        val sd = sqrt(v.sumOf { (it - m) * (it - m) } / v.size)
                        (1.0 - sd / toleranceFor(k)).coerceIn(0.0, 1.0) * 100
                    }
            return vals.takeIf { it.isNotEmpty() }?.average()
        }

        /** Spread at which a feature stops reading as repeatable, keyed off the unit suffix. */
        private fun toleranceFor(key: String): Double =
            when {
                key.endsWith("Deg") -> 5.0 // degrees of shot-to-shot wobble
                key.endsWith("Ratio") -> 0.10 // stance width, relative to shoulder width
                key.endsWith("S") -> 1.0 // seconds of draw/hold timing
                else -> 1.0
            }
    }
}
