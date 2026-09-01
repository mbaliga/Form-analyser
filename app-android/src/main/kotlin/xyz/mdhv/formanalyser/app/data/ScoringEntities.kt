package xyz.mdhv.formanalyser.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "score_session",
    indices =
        [
            Index("athleteId"),
            Index("rigId"),
            Index("startedAt"),
            Index("status"),
            Index("roundId"),
            Index("linkedFormSessionId"),
        ],
)
data class ScoreSessionEntity(
    @PrimaryKey val id: String,
    val athleteId: String,
    val rigId: String?,
    val linkedFormSessionId: String? = null,
    val roundId: String,
    val roundName: String,
    val distanceMeters: Int,
    val targetFaceCm: Int,
    val arrowsPerEnd: Int,
    val endCount: Int,
    val scoringKind: String,
    val faceLayout: String,
    val startedAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
    val status: String = "ACTIVE",
    val roundComplete: Boolean = false,
    val total: Int = 0,
    val xCount: Int = 0,
    val athleteSetPoints: Int? = null,
    val opponentSetPoints: Int? = null,
    val shootOffWinner: String? = null,
    val pinned: Boolean = false,
    val sightMark: String? = null,
    val venue: String? = null,
    val conditions: String? = null,
    val trainingIntent: String? = null,
    /**
     * Reserved; **not currently enforced anywhere.**
     *
     * Export and coach redaction classify by *table* via `PrivacyRegistry`, which lists
     * `score_session` as SHAREABLE — so every row here is treated as shareable regardless of what
     * this column says. Setting it to PRIVATE today protects nothing. It is kept because the column
     * exists in the v4→v5 DDL and dropping it needs a table rebuild, and because per-row privacy is
     * a plausible future need; wire it into the `core-exchange` consent filter before relying on
     * it.
     */
    val privacyClass: String = "SHAREABLE",
    /**
     * Set when the athlete retracts this row, null otherwise.
     *
     * Retraction, not deletion: the derived history this row feeds (see MIGRATION_6_7) cannot be
     * rewritten honestly by dropping the bytes. Every query that should ignore a retracted row
     * carries `AND deletedAt IS NULL` explicitly.
     */
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "score_arrow",
    indices =
        [
            Index("scoreSessionId"),
            Index(value = ["scoreSessionId", "endIndex", "arrowIndex", "active"], unique = false),
            Index("supersedesArrowId"),
        ],
)
data class ScoreArrowEntity(
    @PrimaryKey val id: String,
    val scoreSessionId: String,
    val endIndex: Int,
    val arrowIndex: Int,
    val points: Int,
    val isX: Boolean,
    val plotX: Double? = null,
    val plotY: Double? = null,
    val plotFaceIndex: Int? = null,
    val source: String,
    val authority: String,
    val resolution: String,
    val createdAt: Long,
    val active: Boolean = true,
    val retractedAt: Long? = null,
    val supersedesArrowId: String? = null,
)

@Entity(
    tableName = "score_opponent_end",
    primaryKeys = ["scoreSessionId", "endIndex"],
    indices = [Index("scoreSessionId")],
)
data class ScoreOpponentEndEntity(
    val scoreSessionId: String,
    val endIndex: Int,
    val total: Int,
    val updatedAt: Long,
)

/** Projection for [ScoringDao.bestPerRound]: the athlete's best complete round of each kind. */
data class RoundBest(
    val roundId: String,
    val roundName: String,
    val best: Int,
    val arrowsPerEnd: Int,
    val endCount: Int,
)
