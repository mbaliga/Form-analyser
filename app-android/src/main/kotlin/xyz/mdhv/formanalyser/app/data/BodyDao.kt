package xyz.mdhv.formanalyser.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BodyDao {
    // --- pain ---
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPain(p: PainLogEntity)

    @Query(
        "SELECT * FROM pain_log WHERE athleteId = :athleteId AND ts >= :fromTs AND deletedAt IS NULL ORDER BY ts ASC"
    )
    suspend fun painSince(athleteId: String, fromTs: Long): List<PainLogEntity>

    @Query(
        "SELECT * FROM pain_log WHERE athleteId = :athleteId AND regionId = :regionId AND deletedAt IS NULL ORDER BY ts DESC LIMIT :limit"
    )
    suspend fun painForRegion(athleteId: String, regionId: String, limit: Int): List<PainLogEntity>

    // --- injuries ---
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertInjury(i: InjuryEntity)

    @Query(
        "SELECT * FROM injury WHERE athleteId = :athleteId AND deletedAt IS NULL ORDER BY onset DESC"
    )
    suspend fun injuries(athleteId: String): List<InjuryEntity>

    @Query(
        "SELECT * FROM injury WHERE athleteId = :athleteId AND status = 'ACTIVE' AND deletedAt IS NULL"
    )
    suspend fun activeInjuries(athleteId: String): List<InjuryEntity>

    @Query("SELECT * FROM injury WHERE id = :id") suspend fun injuryById(id: String): InjuryEntity?

    // Retraction, not deletion. An injury feeds the atlas overlay, the Body tab badge, readiness,
    // the coach factsheet and the auto-link stamped on later pain entries, and both pain_log and
    // document hold its id — dropping the row would leave those pointing at nothing.
    @Query("UPDATE injury SET deletedAt = :at WHERE id = :id")
    suspend fun retractInjury(id: String, at: Long)

    @Query("UPDATE injury SET deletedAt = NULL WHERE id = :id")
    suspend fun restoreInjury(id: String)

    @Query(
        "SELECT * FROM injury WHERE athleteId = :athleteId AND deletedAt IS NOT NULL ORDER BY deletedAt DESC"
    )
    suspend fun retractedInjuries(athleteId: String): List<InjuryEntity>

    @Query("UPDATE pain_log SET deletedAt = :at WHERE id = :id")
    suspend fun retractPainLog(id: String, at: Long)

    @Query("UPDATE pain_log SET deletedAt = NULL WHERE id = :id")
    suspend fun restorePainLog(id: String)

    @Query(
        "SELECT * FROM pain_log WHERE athleteId = :athleteId AND deletedAt IS NOT NULL ORDER BY deletedAt DESC"
    )
    suspend fun retractedPainLogs(athleteId: String): List<PainLogEntity>

    // --- physio ---
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertPlan(p: PhysioPlanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExercise(e: PhysioExerciseEntity)

    @Query("DELETE FROM physio_exercise WHERE planId = :planId")
    suspend fun clearExercises(planId: String)

    @Query(
        "SELECT * FROM physio_plan WHERE athleteId = :athleteId AND (endDate IS NULL OR endDate >= :today)"
    )
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
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertDocument(d: DocumentEntity)

    @Query("SELECT * FROM document WHERE athleteId = :athleteId ORDER BY ts DESC")
    suspend fun documents(athleteId: String): List<DocumentEntity>

    @Query("SELECT * FROM document WHERE injuryId = :injuryId ORDER BY ts DESC")
    suspend fun documentsForInjury(injuryId: String): List<DocumentEntity>

    @Query("DELETE FROM document WHERE id = :id") suspend fun deleteDocument(id: String)

    @Query("SELECT COUNT(*) FROM document WHERE athleteId = :athleteId")
    suspend fun documentCount(athleteId: String): Int

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM document WHERE athleteId = :athleteId")
    suspend fun documentBytes(athleteId: String): Long
}
