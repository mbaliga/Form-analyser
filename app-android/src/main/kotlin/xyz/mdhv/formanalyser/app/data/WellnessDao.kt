package xyz.mdhv.formanalyser.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WellnessDao {
    // --- check-ins + soreness ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheckin(c: CheckinEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSoreness(rows: List<SorenessEntity>)

    @Query("SELECT * FROM checkin WHERE athleteId = :athleteId ORDER BY ts DESC LIMIT 1")
    suspend fun latestCheckin(athleteId: String): CheckinEntity?

    @Query("SELECT * FROM checkin WHERE athleteId = :athleteId AND ts >= :fromTs ORDER BY ts ASC")
    suspend fun checkinsSince(athleteId: String, fromTs: Long): List<CheckinEntity>

    @Query("SELECT regionId FROM soreness WHERE checkinId = :checkinId")
    suspend fun sorenessFor(checkinId: String): List<String>

    @Query("SELECT * FROM checkin WHERE id = :id")
    suspend fun checkinById(id: String): CheckinEntity?

    // --- rest days ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRestDay(r: RestDayEntity)

    @Query("SELECT * FROM rest_day")
    suspend fun allRestDays(): List<RestDayEntity>

    // --- hiatus ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHiatus(h: HiatusEntity)

    @Query("SELECT * FROM hiatus WHERE endDate IS NULL LIMIT 1")
    suspend fun openHiatus(): HiatusEntity?

    @Query("SELECT * FROM hiatus")
    suspend fun allHiatuses(): List<HiatusEntity>

    @Query("UPDATE hiatus SET endDate = :endDate WHERE id = :id")
    suspend fun endHiatus(id: String, endDate: String)

    // --- private-class life layer ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMood(m: MoodEntity)

    @Query("SELECT * FROM mood_entry ORDER BY ts DESC LIMIT :limit")
    suspend fun recentMoods(limit: Int): List<MoodEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLifeEvent(e: LifeEventEntity)

    @Query("SELECT * FROM life_event ORDER BY startDate DESC")
    suspend fun allLifeEvents(): List<LifeEventEntity>

    @Query("SELECT * FROM life_event WHERE endDate IS NULL OR endDate >= :today")
    suspend fun activeLifeEvents(today: String): List<LifeEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCycle(c: CycleEntity)

    @Query("SELECT * FROM cycle_entry ORDER BY startDate ASC")
    suspend fun allCycles(): List<CycleEntity>

    // --- medical class ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedication(m: MedicationEntity)

    @Query("SELECT * FROM medication_entry ORDER BY ts DESC")
    suspend fun allMedications(): List<MedicationEntity>

    @Query("SELECT DISTINCT name FROM medication_entry ORDER BY name")
    suspend fun medicationNames(): List<String>

    // --- events ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(e: EventEntity)

    @Query("SELECT * FROM event ORDER BY ts DESC LIMIT :limit")
    suspend fun recentEvents(limit: Int): List<EventEntity>
}
