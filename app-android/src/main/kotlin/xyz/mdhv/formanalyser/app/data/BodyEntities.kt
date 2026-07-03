package xyz.mdhv.formanalyser.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Phase 3 body layer. Table names match the core-wellness PrivacyRegistry contract. */

@Entity(tableName = "pain_log", indices = [Index("athleteId"), Index("regionId"), Index("ts")])
data class PainLogEntity(
    @PrimaryKey val id: String,
    val athleteId: String,
    val ts: Long,
    val regionId: String,
    val intensity: Int,             // 0..10
    val tagsJson: String,           // ["sharp","dull","ache","tingling","stiff"]
    val injuryId: String? = null,
)

@Entity(tableName = "injury", indices = [Index("athleteId")])
data class InjuryEntity(
    @PrimaryKey val id: String,
    val athleteId: String,
    val onset: String,              // ISO LocalDate
    val regionsJson: String,        // ["rotator_cuff_r", ...]
    val severity: Int,              // 1..3
    val mechanism: String,          // ACUTE | OVERUSE | UNKNOWN
    val status: String,             // ACTIVE | RECOVERING | RESOLVED
    val resolvedDate: String? = null, // required when RESOLVED (repository invariant)
    val notes: String? = null,
)

@Entity(tableName = "physio_plan", indices = [Index("athleteId")])
data class PhysioPlanEntity(
    @PrimaryKey val id: String,
    val athleteId: String,
    val title: String,
    val targetRegionsJson: String,
    val scheduleJson: String,       // ["MO","TH"]
    val startDate: String,
    val endDate: String? = null,
    val source: String = "SELF",
    val notes: String? = null,
)

@Entity(tableName = "physio_exercise", indices = [Index("planId")])
data class PhysioExerciseEntity(
    @PrimaryKey val id: String,
    val planId: String,
    val name: String,
    val sets: Int,
    val reps: Int? = null,
    val holdS: Int? = null,
)

@Entity(tableName = "physio_session", indices = [Index("planId"), Index("ts")])
data class PhysioSessionEntity(
    @PrimaryKey val id: String,
    val planId: String,
    val ts: Long,
    val completedJson: String,      // exercise ids ticked
    val note: String? = null,
)

/** MEDICAL class — encrypted at rest (Tink streaming AEAD); per-file explicit export only. */
@Entity(tableName = "document", indices = [Index("athleteId"), Index("injuryId")])
data class DocumentEntity(
    @PrimaryKey val id: String,
    val athleteId: String,
    val ts: Long,
    val title: String,
    val mime: String,
    val encPath: String,
    val sha256: String,
    val sizeBytes: Long,
    val injuryId: String? = null,
)
