package xyz.mdhv.formanalyser.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local-first persistence model. Column names are camelCase (incumbent Room convention). All
 * Phase-1 additions are additive with defaults — existing construction sites keep compiling.
 */

@Entity(tableName = "athletes")
data class AthleteEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val bodyMassKg: Double,
    // --- Phase 1 additions ---
    val handedness: String = "RH",       // "RH" | "LH"
    val drawLengthMm: Int? = null,       // body measurement referenced by every rig
    val avatarSeed: Long = 0L,
    val club: String? = null,
    val pubkey: String? = null,          // Phase 5 fills
)

@Entity(tableName = "sessions", indices = [Index("athleteId"), Index("rigId")])
data class SessionEntity(
    @PrimaryKey val id: String,
    val athleteId: String,
    val startedAtEpochMs: Long,
    /** @Deprecated legacy: kept + written from the rig's effective poundage for compatibility. */
    val drawWeightLbs: Double,
    val distanceMeters: Int,
    // --- Phase 1 additions ---
    val rigId: String? = null,
    val handednessOverride: String? = null,   // "RH" | "LH" | null
)

/** A shootable configuration; sessions reference one. Tuning is versioned JSON (TuningV0). */
@Entity(tableName = "rig", indices = [Index("athleteId")])
data class RigEntity(
    @PrimaryKey val id: String,
    val athleteId: String,
    val name: String,
    val bowType: String,           // RECURVE | COMPOUND | BAREBOW
    val tuningJson: String?,       // TuningV0 JSON
    val active: Boolean,
    val createdAt: Long,
)

@Entity(tableName = "shots", indices = [Index("sessionId"), Index("athleteId")])
data class ShotEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val athleteId: String,
    val indexInSession: Int,
    /** FeatureVector serialised as a flat JSON object of name -> double. */
    val featuresJson: String,
    /** Arrow score on the 10-ring scale, or null until logged. */
    val score: Double?,
    /** Whether this shot is part of the athlete's "good" baseline set. */
    val isBaseline: Boolean,
)
