package xyz.mdhv.formanalyser.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Phase 2 wellness + life layer. Table names match the PrivacyRegistry contract in core-wellness
 * exactly — the registry is the single source of privacy classes (PRIVATE/MEDICAL markers below
 * are documentation; enforcement lands with core-exchange in Phase 5).
 */

@Entity(tableName = "checkin", indices = [Index("athleteId"), Index("ts")])
data class CheckinEntity(
    @PrimaryKey val id: String,
    val athleteId: String,
    val ts: Long,
    val kind: String,               // PRE | POST | STANDALONE
    val skipped: Boolean = false,   // spec: skips are recorded
    val energy: Int? = null,        // 1..5
    val sleep: Int? = null,         // 1..5
    val motivation: Int? = null,    // 1..5
    val rpe: Double? = null,        // Borg CR10, POST only
    val feel: Int? = null,          // 1..5, POST only
    val note: String? = null,
)

@Entity(tableName = "soreness", primaryKeys = ["checkinId", "regionId"])
data class SorenessEntity(
    val checkinId: String,
    val regionId: String,
)

@Entity(tableName = "rest_day")
data class RestDayEntity(
    @PrimaryKey val date: String,   // ISO LocalDate
    val planned: Boolean,
    val note: String? = null,
)

@Entity(tableName = "hiatus")
data class HiatusEntity(
    @PrimaryKey val id: String,
    val startDate: String,          // ISO LocalDate
    val endDate: String? = null,    // null = open
    val lifeEventId: String? = null,
)

/** PRIVATE class — in no export tier, ever. */
@Entity(tableName = "mood_entry", indices = [Index("ts")])
data class MoodEntity(
    @PrimaryKey val id: String,
    val ts: Long,
    val mood: Int,                  // 1..5
    val tagsJson: String,           // ["stressed",...]
    val note: String? = null,
)

/** PRIVATE class — in no export tier, ever. */
@Entity(tableName = "life_event")
data class LifeEventEntity(
    @PrimaryKey val id: String,
    val startDate: String,
    val endDate: String? = null,    // null = ongoing
    val category: String,           // BEREAVEMENT|ILLNESS|EXAMS|WORK|TRAVEL|RELATIONSHIP|OTHER
    val impact: Int,                // 1..3
    val title: String,
)

/** PRIVATE class — in no export tier, ever. */
@Entity(tableName = "cycle_entry")
data class CycleEntity(
    @PrimaryKey val id: String,
    val startDate: String,
    val endDate: String? = null,
    val flow: Int? = null,          // 1..3
    val symptomsJson: String = "[]",
)

/** MEDICAL class — per-item explicit export ceremony only. */
@Entity(tableName = "medication_entry", indices = [Index("ts")])
data class MedicationEntity(
    @PrimaryKey val id: String,
    val ts: Long,
    val name: String,
    val dose: String? = null,
    val schedule: String? = null,
    val taken: Boolean = true,
)

@Entity(tableName = "event", indices = [Index("ts")])
data class EventEntity(
    @PrimaryKey val id: String,
    val ts: Long,
    val title: String,
    val icon: String? = null,
    val tagsJson: String = "[]",
)
