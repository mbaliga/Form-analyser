package xyz.mdhv.formanalyser.app.data

import android.content.Context
import java.util.UUID
import xyz.mdhv.formanalyser.athlete.GoalAggregation
import xyz.mdhv.formanalyser.athlete.GoalDefinition
import xyz.mdhv.formanalyser.athlete.GoalDirection
import xyz.mdhv.formanalyser.athlete.GoalMetric
import xyz.mdhv.formanalyser.athlete.GoalState
import xyz.mdhv.formanalyser.athlete.SessionContextObservation
import xyz.mdhv.formanalyser.athlete.SessionDefaults
import xyz.mdhv.formanalyser.athlete.SmartDefaultsEngine

class AthleteFeatureRepository(context: Context) {
    private val db = AppDatabase.get(context.applicationContext)
    private val athlete = db.athleteDao()
    private val sessions = db.sessionDao()
    private val rigs = db.rigDao()
    val dao: AthleteFeatureDao = db.athleteFeatureDao()

    suspend fun athleteId(): String? = athlete.firstOrNull()?.id

    suspend fun goals(): List<GoalEntity> = athleteId()?.let { dao.goals(it) }.orEmpty()

    suspend fun interventions(): List<InterventionEntity> =
        athleteId()?.let { dao.interventions(it) }.orEmpty()

    suspend fun plans(): List<TrainingPlanEntity> =
        athleteId()?.let { dao.trainingPlans(it) }.orEmpty()

    suspend fun saveGoal(
        existing: GoalEntity? = null,
        metric: GoalMetric,
        title: String,
        targetValue: Double,
        unit: String,
        direction: GoalDirection,
        aggregation: GoalAggregation,
        targetAtMs: Long? = null,
        baselineValue: Double? = null,
        scopeKey: String? = null,
    ): GoalEntity {
        val athleteId = athleteId() ?: error("No athlete profile")
        val now = System.currentTimeMillis()
        // Reject at the door what GoalDefinition would reject on read. "Infinity" and "NaN" both
        // parse as valid Doubles, so a target typed as either used to persist happily and then
        // throw inside toDefinition() on *every* subsequent Progress load — bricking the one screen
        // from which the offending goal could have been deleted.
        require(targetValue.isFinite()) { "Goal target must be a real number" }
        require(baselineValue == null || baselineValue.isFinite()) {
            "Goal baseline must be a real number"
        }
        require(targetAtMs == null || targetAtMs >= (existing?.startAtMs ?: now)) {
            "Goal target date cannot be before the goal starts"
        }
        val entity =
            GoalEntity(
                existing?.id ?: UUID.randomUUID().toString(),
                athleteId,
                metric.name,
                title.trim().ifBlank {
                    metric.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)
                },
                targetValue,
                unit.trim().ifBlank { "value" },
                direction.name,
                aggregation.name,
                existing?.startAtMs ?: now,
                targetAtMs,
                baselineValue,
                scopeKey?.trim()?.takeIf { it.isNotEmpty() },
                existing?.state ?: GoalState.ACTIVE.name,
                existing?.createdAtMs ?: now,
                now,
            )
        dao.upsertGoal(entity)
        return entity
    }

    suspend fun setGoalState(id: String, state: GoalState) =
        dao.setGoalState(id, state.name, System.currentTimeMillis())

    suspend fun addIntervention(
        kind: String,
        title: String,
        note: String? = null,
        rigId: String? = null,
    ): InterventionEntity {
        val athleteId = athleteId() ?: error("No athlete profile")
        val entity =
            InterventionEntity(
                UUID.randomUUID().toString(),
                athleteId,
                System.currentTimeMillis(),
                kind,
                title.trim().ifBlank { "Change" },
                note?.trim()?.takeIf { it.isNotEmpty() },
                rigId,
            )
        dao.upsertIntervention(entity)
        return entity
    }

    suspend fun smartDefaults(): SessionDefaults {
        val athleteId = athleteId() ?: return SessionDefaults()
        val saved = dao.defaults(athleteId)
        val activeRig = rigs.activeForAthlete(athleteId)?.id
        val history = mutableListOf<SessionContextObservation>()
        dao.recentSessionContexts(athleteId, 20).forEach { c ->
            history +=
                SessionContextObservation(
                    c.startedAtMs,
                    SessionDefaults(
                        c.disciplineId,
                        c.rigId,
                        c.venue,
                        c.distanceMeters,
                        c.targetFaceCm,
                        c.arrowCountPlanned,
                        c.roundId,
                        c.trainingIntent,
                    ),
                )
        }
        sessions.recent(athleteId, 12).forEach { s ->
            history +=
                SessionContextObservation(
                    s.startedAtEpochMs,
                    SessionDefaults(
                        "olympic_recurve",
                        s.rigId,
                        distanceMeters = s.distanceMeters,
                        arrowCount = s.arrowsActual,
                    ),
                )
        }
        db.scoringDao().recent(athleteId, 12).forEach { s ->
            history +=
                SessionContextObservation(
                    s.updatedAt,
                    SessionDefaults(
                        "olympic_recurve",
                        s.rigId,
                        s.venue,
                        s.distanceMeters,
                        s.targetFaceCm,
                        s.arrowsPerEnd * s.endCount,
                        s.roundId,
                        s.trainingIntent,
                    ),
                )
        }
        val savedDefaults = saved?.toCore()
        val pinnedKeys =
            saved
                ?.pinnedFieldsCsv
                .orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        val pinned =
            savedDefaults?.let { d ->
                SessionDefaults(
                    d.disciplineId.takeIf { "discipline" in pinnedKeys },
                    d.rigId.takeIf { "rig" in pinnedKeys },
                    d.venue.takeIf { "venue" in pinnedKeys },
                    d.distanceMeters.takeIf { "distance" in pinnedKeys },
                    d.targetFaceCm.takeIf { "target" in pinnedKeys },
                    d.arrowCount.takeIf { "arrows" in pinnedKeys },
                    d.roundId.takeIf { "round" in pinnedKeys },
                    d.trainingIntent.takeIf { "intent" in pinnedKeys },
                )
            }
        return mergeMissing(SmartDefaultsEngine.resolve(history, pinned, activeRig), savedDefaults)
    }

    suspend fun saveSessionContext(
        sessionId: String,
        defaults: SessionDefaults,
        startedAtMs: Long = System.currentTimeMillis(),
    ) {
        val athleteId = athleteId() ?: return
        dao.upsertSessionContext(
            SessionContextEntity(
                sessionId,
                athleteId,
                defaults.disciplineId ?: "olympic_recurve",
                defaults.rigId,
                defaults.venue,
                defaults.distanceMeters,
                defaults.targetFaceCm,
                defaults.arrowCount,
                defaults.roundId,
                defaults.trainingIntent,
                startedAtMs,
            )
        )
    }

    suspend fun saveDefaults(defaults: SessionDefaults, pinnedFields: Set<String> = emptySet()) {
        val athleteId = athleteId() ?: return
        dao.upsertDefaults(
            SessionDefaultEntity(
                "defaults_$athleteId",
                athleteId,
                defaults.disciplineId,
                defaults.rigId,
                defaults.venue,
                defaults.distanceMeters,
                defaults.targetFaceCm,
                defaults.arrowCount,
                defaults.roundId,
                defaults.trainingIntent,
                pinnedFields.sorted().joinToString(","),
                System.currentTimeMillis(),
            )
        )
    }

    suspend fun upsertPlan(
        existing: TrainingPlanEntity?,
        title: String,
        phase: String,
        focus: String,
        startDate: String,
        endDate: String?,
        weeklyArrowTarget: Int?,
        intensity: String,
        recoveryNotes: String?,
    ): TrainingPlanEntity {
        val athleteId = athleteId() ?: error("No athlete profile")
        val now = System.currentTimeMillis()
        val plan =
            TrainingPlanEntity(
                existing?.id ?: UUID.randomUUID().toString(),
                athleteId,
                title.trim().ifBlank { "Training plan" },
                phase,
                focus.trim(),
                startDate,
                endDate?.takeIf { it.isNotBlank() },
                weeklyArrowTarget,
                intensity,
                recoveryNotes?.trim()?.takeIf { it.isNotEmpty() },
                existing?.state ?: "ACTIVE",
                existing?.createdAtMs ?: now,
                now,
            )
        dao.upsertTrainingPlan(plan)
        return plan
    }

    private fun SessionDefaultEntity.toCore() =
        SessionDefaults(
            disciplineId,
            rigId,
            venue,
            distanceMeters,
            targetFaceCm,
            arrowCount,
            roundId,
            trainingIntent,
        )

    private fun mergeMissing(
        primary: SessionDefaults,
        fallback: SessionDefaults?,
    ): SessionDefaults {
        val f = fallback ?: return primary
        return SessionDefaults(
            primary.disciplineId ?: f.disciplineId,
            primary.rigId ?: f.rigId,
            primary.venue ?: f.venue,
            primary.distanceMeters ?: f.distanceMeters,
            primary.targetFaceCm ?: f.targetFaceCm,
            primary.arrowCount ?: f.arrowCount,
            primary.roundId ?: f.roundId,
            primary.trainingIntent ?: f.trainingIntent,
        )
    }
}

fun GoalEntity.toDefinition(): GoalDefinition =
    GoalDefinition(
        id,
        GoalMetric.valueOf(metric),
        title,
        targetValue,
        unit,
        GoalDirection.valueOf(direction),
        GoalAggregation.valueOf(aggregation),
        startAtMs,
        targetAtMs,
        baselineValue,
        scopeKey,
    )
