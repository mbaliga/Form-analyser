package xyz.mdhv.formanalyser.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SportSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SportSessionEntity)

    @Query(
        "SELECT * FROM sport_session WHERE athleteId = :athleteId AND sportId = :sportId " +
            "AND deletedAt IS NULL ORDER BY startedAtEpochMs DESC LIMIT :limit"
    )
    fun recentForSport(athleteId: String, sportId: String, limit: Int = 20): Flow<List<SportSessionEntity>>

    @Query("UPDATE sport_session SET deletedAt = :at WHERE id = :id AND deletedAt IS NULL")
    suspend fun retract(id: String, at: Long)

    @Query("UPDATE sport_session SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: String)
}
