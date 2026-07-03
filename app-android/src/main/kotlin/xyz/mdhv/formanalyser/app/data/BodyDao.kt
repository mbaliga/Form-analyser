package xyz.mdhv.formanalyser.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BodyDao {
    // --- pain ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPain(p: PainLogEntity)

    @Query("SELECT * FROM pain_log WHERE athleteId = :athleteId AND ts >= :fromTs ORDER BY ts ASC")
    suspend fun painSince(athleteId: String, fromTs: Long): List<PainLogEntity>

    @Query("SELECT * FROM pain_log WHERE athleteId = :athleteId AND regionId = :regionId ORDER BY ts DESC LIMIT :limit")
    suspend fun painForRegion(athleteId: String, regionId: String, limit: Int): List<PainLogEntity>

    // --- injuries ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInjury(i: InjuryEntity)

    @Query("SELECT * FROM injury WHERE athleteId = :athleteId ORDER BY onset DESC")
    suspend fun injuries(athleteId: String): List<InjuryEntity>

    @Query("SELECT * FROM injury WHERE athleteId = :athleteId AND status = 'ACTIVE'")
    suspend fun activeInjuries(athleteId: String): List<InjuryEntity>

    @Query("SELECT * FROM injury WHERE id = :id")
    suspend fun injuryById(id: String): InjuryEntity?

    // --- physio ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlan(p: PhysioPlanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExercise(e: PhysioExerciseEntity)

    @Query("DELETE FROM physio_exercise WHERE planId = :planId")
    suspend fun clearExercises(planId: String)

    @Query("SELECT * FROM physio_plan WHERE athleteId = :athleteId AND (endDate IS NULL OR endDate >= :today)")
    suspend fun activePlans(athleteId: String, today: String): List<PhysioPlanEntity>

    @Query("SELECT * FROM physio_plan WHERE athleteId = :athleteId ORDER BY startDate DESC")
    suspend fun allPlans(athleteId: String): List<PhysioPlanEntity>

    @Query("SELECT * FROM physio_exercise WHERE planId = :planId")
    suspend fun exercisesFor(planId: String): List<PhysioExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhysioSession(s: PhysioSessionEntity)

    @Query("SELECT * FROM physio_session WHERE planId = :planId ORDER BY ts DESC")
    suspend fun physioSessionsFor(planId: String): List<PhysioSessionEntity>

    // --- documents (MEDICAL) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(d: DocumentEntity)

    @Query("SELECT * FROM document WHERE athleteId = :athleteId ORDER BY ts DESC")
    suspend fun documents(athleteId: String): List<DocumentEntity>

    @Query("SELECT * FROM document WHERE injuryId = :injuryId ORDER BY ts DESC")
    suspend fun documentsForInjury(injuryId: String): List<DocumentEntity>

    @Query("DELETE FROM document WHERE id = :id")
    suspend fun deleteDocument(id: String)

    @Query("SELECT COUNT(*) FROM document WHERE athleteId = :athleteId")
    suspend fun documentCount(athleteId: String): Int

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM document WHERE athleteId = :athleteId")
    suspend fun documentBytes(athleteId: String): Long
}
