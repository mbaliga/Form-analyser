package xyz.mdhv.formanalyser.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "goal", indices = [Index("athleteId"), Index("state"), Index("targetAtMs")])
data class GoalEntity(@PrimaryKey val id: String, val athleteId: String, val metric: String, val title: String, val targetValue: Double, val unit: String, val direction: String, val aggregation: String, val startAtMs: Long, val targetAtMs: Long? = null, val baselineValue: Double? = null, val scopeKey: String? = null, val state: String = "ACTIVE", val createdAtMs: Long, val updatedAtMs: Long)

@Entity(tableName = "intervention", indices = [Index("athleteId"), Index("atMs"), Index("kind")])
data class InterventionEntity(@PrimaryKey val id: String, val athleteId: String, val atMs: Long, val kind: String, val title: String, val note: String? = null, val rigId: String? = null)

@Entity(tableName = "session_default", indices = [Index("athleteId", unique = true)])
data class SessionDefaultEntity(@PrimaryKey val id: String, val athleteId: String, val disciplineId: String? = null, val rigId: String? = null, val venue: String? = null, val distanceMeters: Int? = null, val targetFaceCm: Int? = null, val arrowCount: Int? = null, val roundId: String? = null, val trainingIntent: String? = null, val pinnedFieldsCsv: String = "", val updatedAtMs: Long)

@Entity(tableName = "training_plan", indices = [Index("athleteId"), Index("state"), Index("startDate")])
data class TrainingPlanEntity(@PrimaryKey val id: String, val athleteId: String, val title: String, val phase: String, val focus: String, val startDate: String, val endDate: String? = null, val weeklyArrowTarget: Int? = null, val intensity: String = "MIXED", val recoveryNotes: String? = null, val state: String = "ACTIVE", val createdAtMs: Long, val updatedAtMs: Long)

@Entity(tableName = "score_candidate", indices = [Index("scoreSessionId"), Index("endIndex"), Index("status")])
data class ScoreCandidateEntity(@PrimaryKey val id: String, val scoreSessionId: String, val endIndex: Int, val candidateIndex: Int, val points: Int, val isX: Boolean, val plotX: Double? = null, val plotY: Double? = null, val plotFaceIndex: Int? = null, val confidence: Double? = null, val source: String = "END_SCAN", val status: String = "PROPOSED", val resolution: String = "END_ONLY", val createdAtMs: Long, val resolvedAtMs: Long? = null)

@Entity(tableName = "observer_score_event", indices = [Index("scoreSessionId"), Index("atMs"), Index("status")])
data class ObserverScoreEventEntity(@PrimaryKey val id: String, val scoreSessionId: String, val atMs: Long, val ring: Int, val isX: Boolean = false, val sector: String? = null, val inputMode: String, val status: String = "CONFIRMED", val declaredText: String? = null, val resolution: String = "SHOT_INFERRED", val correctionOfId: String? = null)

@Entity(tableName = "session_context", indices = [Index("athleteId"), Index("startedAtMs"), Index("rigId")])
data class SessionContextEntity(@PrimaryKey val sessionId: String, val athleteId: String, val disciplineId: String = "olympic_recurve", val rigId: String? = null, val venue: String? = null, val distanceMeters: Int? = null, val targetFaceCm: Int? = null, val arrowCountPlanned: Int? = null, val roundId: String? = null, val trainingIntent: String? = null, val startedAtMs: Long)
