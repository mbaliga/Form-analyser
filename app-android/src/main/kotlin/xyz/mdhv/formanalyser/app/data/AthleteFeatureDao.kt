package xyz.mdhv.formanalyser.app.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface AthleteFeatureDao {
    @Upsert suspend fun upsertGoal(goal: GoalEntity)

    @Query(
        "SELECT * FROM goal WHERE athleteId = :athleteId AND state != 'ARCHIVED' ORDER BY state ASC, targetAtMs ASC, createdAtMs DESC"
    )
    suspend fun goals(athleteId: String): List<GoalEntity>

    @Query("SELECT * FROM goal WHERE id = :id LIMIT 1") suspend fun goal(id: String): GoalEntity?

    @Query("UPDATE goal SET state = :state, updatedAtMs = :atMs WHERE id = :id")
    suspend fun setGoalState(id: String, state: String, atMs: Long)

    @Upsert suspend fun upsertIntervention(intervention: InterventionEntity)

    @Query("SELECT * FROM intervention WHERE athleteId = :athleteId ORDER BY atMs ASC")
    suspend fun interventions(athleteId: String): List<InterventionEntity>

    @Query("DELETE FROM intervention WHERE id = :id") suspend fun deleteIntervention(id: String)

    @Upsert suspend fun upsertDefaults(defaults: SessionDefaultEntity)

    @Query("SELECT * FROM session_default WHERE athleteId = :athleteId LIMIT 1")
    suspend fun defaults(athleteId: String): SessionDefaultEntity?

    @Upsert suspend fun upsertSessionContext(context: SessionContextEntity)

    @Query(
        "SELECT * FROM session_context WHERE athleteId = :athleteId ORDER BY startedAtMs DESC LIMIT :limit"
    )
    suspend fun recentSessionContexts(athleteId: String, limit: Int): List<SessionContextEntity>

    @Query("SELECT * FROM session_context WHERE sessionId = :sessionId LIMIT 1")
    suspend fun sessionContext(sessionId: String): SessionContextEntity?

    @Upsert suspend fun upsertTrainingPlan(plan: TrainingPlanEntity)

    @Query(
        "SELECT * FROM training_plan WHERE athleteId = :athleteId AND state != 'ARCHIVED' ORDER BY startDate DESC"
    )
    suspend fun trainingPlans(athleteId: String): List<TrainingPlanEntity>

    @Query("UPDATE training_plan SET state = :state, updatedAtMs = :atMs WHERE id = :id")
    suspend fun setPlanState(id: String, state: String, atMs: Long)

    @Upsert suspend fun upsertCandidate(candidate: ScoreCandidateEntity)

    @Query(
        "SELECT * FROM score_candidate WHERE scoreSessionId = :sessionId AND endIndex = :endIndex ORDER BY candidateIndex ASC"
    )
    suspend fun candidatesForEnd(sessionId: String, endIndex: Int): List<ScoreCandidateEntity>

    @Query("UPDATE score_candidate SET status = :status, resolvedAtMs = :atMs WHERE id = :id")
    suspend fun resolveCandidate(id: String, status: String, atMs: Long)

    @Upsert suspend fun upsertObserverEvent(event: ObserverScoreEventEntity)

    @Query("SELECT * FROM observer_score_event WHERE scoreSessionId = :sessionId ORDER BY atMs ASC")
    suspend fun observerEvents(sessionId: String): List<ObserverScoreEventEntity>

    @Query("UPDATE observer_score_event SET status = 'RETRACTED' WHERE id = :id")
    suspend fun retractObserverEvent(id: String)

    // --- Athlete-initiated deletion (no cascade on these tables; see the repositories) ---
    @Query("DELETE FROM score_candidate WHERE scoreSessionId = :sessionId")
    suspend fun deleteCandidates(sessionId: String)

    @Query("DELETE FROM observer_score_event WHERE scoreSessionId = :sessionId")
    suspend fun deleteObserverEvents(sessionId: String)

    @Query("DELETE FROM session_context WHERE sessionId = :sessionId")
    suspend fun deleteSessionContext(sessionId: String)
}
