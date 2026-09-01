package xyz.mdhv.formanalyser.app.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ScoringDao {
    @Upsert suspend fun upsertSession(session: ScoreSessionEntity)

    @Upsert suspend fun upsertArrow(arrow: ScoreArrowEntity)

    @Upsert suspend fun upsertOpponentEnd(end: ScoreOpponentEndEntity)

    @Query("SELECT * FROM score_session WHERE id = :id LIMIT 1")
    suspend fun session(id: String): ScoreSessionEntity?

    @Query("SELECT * FROM score_session WHERE id = :id LIMIT 1")
    fun observeSession(id: String): Flow<ScoreSessionEntity?>

    @Query(
        "SELECT * FROM score_arrow WHERE scoreSessionId = :sessionId AND active = 1 ORDER BY endIndex ASC, arrowIndex ASC, createdAt ASC"
    )
    suspend fun activeArrows(sessionId: String): List<ScoreArrowEntity>

    @Query(
        "SELECT * FROM score_arrow WHERE scoreSessionId = :sessionId AND active = 1 ORDER BY endIndex ASC, arrowIndex ASC, createdAt ASC"
    )
    fun observeActiveArrows(sessionId: String): Flow<List<ScoreArrowEntity>>

    @Query(
        "SELECT * FROM score_opponent_end WHERE scoreSessionId = :sessionId ORDER BY endIndex ASC"
    )
    suspend fun opponentEnds(sessionId: String): List<ScoreOpponentEndEntity>

    @Query(
        "SELECT * FROM score_opponent_end WHERE scoreSessionId = :sessionId ORDER BY endIndex ASC"
    )
    fun observeOpponentEnds(sessionId: String): Flow<List<ScoreOpponentEndEntity>>

    @Query(
        "SELECT * FROM score_session WHERE athleteId = :athleteId AND status = 'ACTIVE' ORDER BY updatedAt DESC LIMIT 1"
    )
    suspend fun activeForAthlete(athleteId: String): ScoreSessionEntity?

    @Query(
        "SELECT * FROM score_session WHERE athleteId = :athleteId AND pinned = 1 ORDER BY updatedAt DESC LIMIT 1"
    )
    suspend fun latestPinned(athleteId: String): ScoreSessionEntity?

    @Query(
        "SELECT * FROM score_session WHERE athleteId = :athleteId ORDER BY updatedAt DESC LIMIT 1"
    )
    suspend fun latest(athleteId: String): ScoreSessionEntity?

    @Query(
        "SELECT * FROM score_session WHERE athleteId = :athleteId ORDER BY updatedAt DESC LIMIT :limit"
    )
    suspend fun recent(athleteId: String, limit: Int): List<ScoreSessionEntity>

    @Query(
        "SELECT MAX(total) FROM score_session WHERE athleteId = :athleteId AND roundId = :roundId AND status = 'FINISHED' AND roundComplete = 1 AND id != :excludeSessionId"
    )
    suspend fun previousBest(athleteId: String, roundId: String, excludeSessionId: String): Int?

    /**
     * Best complete round per roundId, over the athlete's whole history.
     *
     * Deliberately unbounded, and filtered identically to [previousBest]. Progress used to derive
     * its PB list from the most recent 250 scorecards while the scorer announced "New PB" from
     * [previousBest] over all of them, so a long-standing best could scroll out of Progress's
     * window and the two screens would disagree about the athlete's own record.
     */
    @Query(
        "SELECT roundId, roundName, MAX(total) AS best, arrowsPerEnd, endCount " +
            "FROM score_session WHERE athleteId = :athleteId AND status = 'FINISHED' " +
            "AND roundComplete = 1 AND scoringKind != 'SET_MATCH' GROUP BY roundId"
    )
    suspend fun bestPerRound(athleteId: String): List<RoundBest>

    @Query(
        "UPDATE score_arrow SET active = 0, retractedAt = :at WHERE id = :arrowId AND active = 1"
    )
    suspend fun retractArrow(arrowId: String, at: Long)

    @Query(
        "UPDATE score_session SET total = :total, xCount = :xCount, athleteSetPoints = :athleteSetPoints, opponentSetPoints = :opponentSetPoints, updatedAt = :updatedAt WHERE id = :sessionId"
    )
    suspend fun updateSummary(
        sessionId: String,
        total: Int,
        xCount: Int,
        athleteSetPoints: Int?,
        opponentSetPoints: Int?,
        updatedAt: Long,
    )

    @Query(
        "UPDATE score_session SET pinned = :pinned, updatedAt = :updatedAt WHERE id = :sessionId"
    )
    suspend fun setPinned(sessionId: String, pinned: Boolean, updatedAt: Long)

    @Query(
        "UPDATE score_session SET shootOffWinner = :winner, updatedAt = :updatedAt WHERE id = :sessionId"
    )
    suspend fun setShootOffWinner(sessionId: String, winner: String, updatedAt: Long)

    /**
     * Attach (or detach) the capture session this scorecard was shot alongside.
     *
     * Deliberately the only write in this DAO that does *not* touch `updatedAt`: [latest] and
     * [recent] order by that column, so stamping it here would shuffle an old scorecard to the top
     * of "Recent" — and make it the template [ScoringRepository.quickStart] copies — purely because
     * the athlete corrected a link on it. The link is metadata about the card, not scoring
     * activity.
     */
    @Query("UPDATE score_session SET linkedFormSessionId = :formSessionId WHERE id = :sessionId")
    suspend fun setLinkedFormSession(sessionId: String, formSessionId: String?)

    @Query(
        "UPDATE score_session SET sightMark = :sightMark, venue = :venue, conditions = :conditions, trainingIntent = :trainingIntent, updatedAt = :updatedAt WHERE id = :sessionId"
    )
    suspend fun updateContext(
        sessionId: String,
        sightMark: String?,
        venue: String?,
        conditions: String?,
        trainingIntent: String?,
        updatedAt: Long,
    )

    @Query(
        "UPDATE score_session SET status = 'FINISHED', roundComplete = :roundComplete, completedAt = :completedAt, updatedAt = :completedAt WHERE id = :sessionId"
    )
    suspend fun finish(sessionId: String, roundComplete: Boolean, completedAt: Long)

    @Query(
        "SELECT * FROM score_session WHERE athleteId = :athleteId AND status = 'FINISHED' ORDER BY completedAt ASC"
    )
    suspend fun completedForAthlete(athleteId: String): List<ScoreSessionEntity>

    @Query("SELECT COUNT(*) FROM score_arrow WHERE scoreSessionId = :sessionId AND active = 1")
    suspend fun activeArrowCount(sessionId: String): Int

    // --- Athlete-initiated deletion -------------------------------------------------------------
    // There is no cascade on these tables, so every row a scorecard owns is removed by name.
    // Nothing in the app calls these except an explicit, counted confirmation the athlete accepts;
    // see ScoringRepository.deleteScorecard.

    @Query("DELETE FROM score_arrow WHERE scoreSessionId = :sessionId")
    suspend fun deleteArrows(sessionId: String)

    @Query("DELETE FROM score_opponent_end WHERE scoreSessionId = :sessionId")
    suspend fun deleteOpponentEnds(sessionId: String)

    @Query("DELETE FROM score_session WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("SELECT COUNT(*) FROM score_session WHERE linkedFormSessionId = :formSessionId")
    suspend fun linkedCardCount(formSessionId: String): Int

    /**
     * Detach every scorecard pointing at a capture session that is being deleted.
     *
     * Deleting a training session must not take its scores with it: the arrows and the totals are
     * separate evidence the athlete recorded by hand, and they survive on their own. As with
     * [setLinkedFormSession], `updatedAt` is left alone so this does not reorder "Recent".
     */
    @Query(
        "UPDATE score_session SET linkedFormSessionId = NULL WHERE linkedFormSessionId = :formSessionId"
    )
    suspend fun clearLinksTo(formSessionId: String)
}
