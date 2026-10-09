package xyz.mdhv.formanalyser.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Manual sport-specific training record. `detailsJson` and `featuresJson` are versioned by sport. */
@Entity(
    tableName = "sport_session",
    indices = [Index("athleteId"), Index("sportId"), Index("startedAtEpochMs")],
)
data class SportSessionEntity(
    @PrimaryKey val id: String,
    val athleteId: String,
    val sportId: String,
    val startedAtEpochMs: Long,
    val durationSeconds: Int,
    val perceivedEffortRpe: Int?,
    val summary: String,
    val detailsJson: String,
    val featuresJson: String,
    /** Retraction keeps the source row available for a future undo/recovery flow. */
    val deletedAt: Long? = null,
)
