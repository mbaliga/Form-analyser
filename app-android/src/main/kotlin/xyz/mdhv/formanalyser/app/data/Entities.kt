package xyz.mdhv.formanalyser.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local-first persistence model. The engine works on extracted feature vectors, so a shot is
 * stored as its features (JSON) + outcome score + a baseline flag. Raw IMU windows are large;
 * persisting them as files is a later concern (handoff §5: Room/SQLite on device) and tracked
 * as a TODO in the capture flow.
 */

@Entity(tableName = "athletes")
data class AthleteEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val bodyMassKg: Double,
)

@Entity(tableName = "sessions", indices = [Index("athleteId")])
data class SessionEntity(
    @PrimaryKey val id: String,
    val athleteId: String,
    val startedAtEpochMs: Long,
    val drawWeightLbs: Double,
    val distanceMeters: Int,
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
