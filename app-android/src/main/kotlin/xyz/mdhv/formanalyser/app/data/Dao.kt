package xyz.mdhv.formanalyser.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AthleteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(athlete: AthleteEntity)

    @Query("SELECT * FROM athletes LIMIT 1")
    suspend fun firstOrNull(): AthleteEntity?
}

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE athleteId = :athleteId ORDER BY startedAtEpochMs DESC")
    fun forAthlete(athleteId: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE athleteId = :athleteId ORDER BY startedAtEpochMs DESC LIMIT :limit")
    suspend fun recent(athleteId: String, limit: Int): List<SessionEntity>
}

@Dao
interface RigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rig: RigEntity)

    @Query("SELECT * FROM rig WHERE athleteId = :athleteId ORDER BY createdAt ASC")
    fun observeForAthlete(athleteId: String): Flow<List<RigEntity>>

    @Query("SELECT * FROM rig WHERE athleteId = :athleteId ORDER BY createdAt ASC")
    suspend fun forAthleteOnce(athleteId: String): List<RigEntity>

    @Query("SELECT * FROM rig WHERE athleteId = :athleteId AND active = 1 LIMIT 1")
    suspend fun activeForAthlete(athleteId: String): RigEntity?

    @Query("SELECT COUNT(*) FROM rig WHERE athleteId = :athleteId")
    suspend fun countForAthlete(athleteId: String): Int

    @Query("UPDATE rig SET active = 0 WHERE athleteId = :athleteId")
    suspend fun clearActive(athleteId: String)

    @Query("UPDATE rig SET active = 1 WHERE id = :rigId")
    suspend fun markActive(rigId: String)

    @androidx.room.Transaction
    suspend fun setActive(athleteId: String, rigId: String) {
        // Primary single-active guarantee (transactional): clear siblings, set the target.
        clearActive(athleteId)
        markActive(rigId)
    }

    @Query("DELETE FROM rig WHERE id = :rigId")
    suspend fun delete(rigId: String)
}

@Dao
interface ShotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(shots: List<ShotEntity>)

    @Upsert
    suspend fun upsert(shot: ShotEntity)

    @Query("SELECT * FROM shots WHERE sessionId = :sessionId ORDER BY indexInSession ASC")
    fun forSession(sessionId: String): Flow<List<ShotEntity>>

    @Query("SELECT * FROM shots WHERE sessionId = :sessionId ORDER BY indexInSession ASC")
    suspend fun forSessionOnce(sessionId: String): List<ShotEntity>

    /** The athlete's "good" shots — the material a baseline is built from. */
    @Query("SELECT * FROM shots WHERE athleteId = :athleteId AND isBaseline = 1")
    suspend fun baselineShots(athleteId: String): List<ShotEntity>

    /** All scored shots for the athlete — the basis for signal->score correlation. */
    @Query("SELECT * FROM shots WHERE athleteId = :athleteId AND score IS NOT NULL")
    suspend fun scoredShots(athleteId: String): List<ShotEntity>

    @Query("UPDATE shots SET score = :score WHERE id = :shotId")
    suspend fun setScore(shotId: String, score: Double?)

    @Query("UPDATE shots SET isBaseline = :isBaseline WHERE id = :shotId")
    suspend fun setBaseline(shotId: String, isBaseline: Boolean)
}
