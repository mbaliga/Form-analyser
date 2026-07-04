package xyz.mdhv.formanalyser.app.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import xyz.mdhv.crocodyl.engine.model.Rep
import xyz.mdhv.formanalyser.app.domain.ArcheryAnalyzer

/** Thin persistence facade over the Room DAOs, with mapping to engine [Rep]s. */
class Repository(context: Context) {
    private val db = AppDatabase.get(context)
    private val athletes = db.athleteDao()
    private val sessions = db.sessionDao()
    private val shots = db.shotDao()

    suspend fun ensureAthlete(id: String, name: String, bodyMassKg: Double): AthleteEntity {
        val existing = athletes.firstOrNull()
        if (existing != null) return existing
        val a = AthleteEntity(id, name, bodyMassKg)
        athletes.upsert(a)
        return a
    }

    suspend fun currentAthlete(): AthleteEntity? = athletes.firstOrNull()

    suspend fun createSession(session: SessionEntity) = sessions.insert(session)

    fun sessionsFor(athleteId: String): Flow<List<SessionEntity>> = sessions.forAthlete(athleteId)

    suspend fun saveShots(shots: List<ShotEntity>) = this.shots.insertAll(shots)

    fun shotsFor(sessionId: String): Flow<List<ShotEntity>> = shots.forSession(sessionId)

    suspend fun shotsOnce(sessionId: String): List<ShotEntity> = shots.forSessionOnce(sessionId)

    suspend fun setScore(shotId: String, score: Double?) = shots.setScore(shotId, score)

    suspend fun setBaseline(shotId: String, isBaseline: Boolean) = shots.setBaseline(shotId, isBaseline)

    suspend fun baselineShots(athleteId: String): List<ShotEntity> = shots.baselineShots(athleteId)

    suspend fun scoredReps(athleteId: String): List<Rep> =
        shots.scoredShots(athleteId).map { it.toRep() }

    fun ShotEntity.toRep(): Rep = Rep(
        id = id,
        sessionId = sessionId,
        indexInSession = indexInSession,
        features = ArcheryAnalyzer.featuresFromJson(featuresJson),
        score = score,
    )
}
