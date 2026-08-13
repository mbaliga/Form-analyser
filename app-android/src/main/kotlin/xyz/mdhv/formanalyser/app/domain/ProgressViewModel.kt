package xyz.mdhv.formanalyser.app.domain

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlin.math.abs
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
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    fun load() {
        viewModelScope.launch { _state.value = withContext(Dispatchers.IO) { buildState() } }
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
        val volume = mutableListOf<VolumePoint>()
        formSessions.forEach { s ->
            volume += VolumePoint(s.startedAtEpochMs, s.arrowsActual ?: repo.shotCount(s.id))
        }
        scoreSessions
            .filter { it.linkedFormSessionId == null }
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
        val pbs =
            scores
                .groupBy { it.roundId }
                .values
                .mapNotNull { p ->
                    p.maxByOrNull { it.total }?.let { RoundPb(it.roundName, it.total, it.max) }
                }
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
            TrendEngine.summarize(
                scores.map {
                    MetricObservation(it.atMs, it.total.toDouble(), it.roundId + "@" + it.atMs)
                }
            ),
            TrendEngine.summarize(
                form.map { MetricObservation(it.atMs, it.stability, it.sessionId) }
            ),
            volume.filter { it.atMs >= from28 }.sumOf { it.arrows },
            (formSessions.map { it.id to it.startedAtEpochMs } +
                    scoreSessions
                        .filter { it.linkedFormSessionId == null }
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
            GoalMetric.TRAINING_SESSIONS ->
                fs.map { MetricObservation(it.startedAtEpochMs, 1.0, it.id) } +
                    ss.filter { it.linkedFormSessionId == null }
                        .map { MetricObservation(it.completedAt ?: it.updatedAt, 1.0, it.id) }
            GoalMetric.PHYSIO_ADHERENCE -> physioAdherenceObservation()
        }

    private suspend fun physioAdherenceObservation(): List<MetricObservation> {
        val a = repo.currentAthlete() ?: return emptyList()
        val today = LocalDate.now()
        val from = today.minusDays(27)
        val plans = repo.body.activePlans(a.id, today.toString())
        var expected = 0
        var completed = 0
        fun day(c: String) =
            when (c.uppercase()) {
                "MO",
                "MON" -> java.time.DayOfWeek.MONDAY
                "TU",
                "TUE" -> java.time.DayOfWeek.TUESDAY
                "WE",
                "WED" -> java.time.DayOfWeek.WEDNESDAY
                "TH",
                "THU" -> java.time.DayOfWeek.THURSDAY
                "FR",
                "FRI" -> java.time.DayOfWeek.FRIDAY
                "SA",
                "SAT" -> java.time.DayOfWeek.SATURDAY
                "SU",
                "SUN" -> java.time.DayOfWeek.SUNDAY
                else -> null
            }
        plans.forEach { p ->
            val start = maxOf(LocalDate.parse(p.startDate), from)
            val end = minOf(p.endDate?.let(LocalDate::parse) ?: today, today)
            val sched = JsonLists.decode(p.scheduleJson).mapNotNull(::day).toSet()
            var d = start
            while (!d.isAfter(end)) {
                if (d.dayOfWeek in sched) expected++
                d = d.plusDays(1)
            }
            completed +=
                repo.body.physioSessionsFor(p.id).count { r ->
                    val date =
                        java.time.Instant.ofEpochMilli(r.ts)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                    !date.isBefore(from) && !date.isAfter(today)
                }
        }
        if (expected <= 0) return emptyList()
        return listOf(
            MetricObservation(
                System.currentTimeMillis(),
                (100.0 * completed / expected).coerceIn(0.0, 100.0),
                "physio-28d",
            )
        )
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
    ) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
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
            load()
        }
    }

    fun setGoalState(id: String, s: GoalState) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { features.setGoalState(id, s) }
            load()
        }
    }

    fun addIntervention(k: String, t: String, n: String?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { features.addIntervention(k, t, n) }
            load()
        }
    }

    fun savePlan(t: String, p: String, f: String, w: Int?, i: String, r: String?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                features.upsertPlan(null, t, p, f, LocalDate.now().toString(), null, w, i, r)
            }
            load()
        }
    }

    companion object {
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
                        (1.0 - sd / abs(m).coerceAtLeast(1e-6)).coerceIn(0.0, 1.0) * 100
                    }
            return vals.takeIf { it.isNotEmpty() }?.average()
        }
    }
}
