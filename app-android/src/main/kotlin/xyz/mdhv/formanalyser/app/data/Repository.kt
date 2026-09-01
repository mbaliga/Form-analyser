package xyz.mdhv.formanalyser.app.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import xyz.mdhv.crocodyl.engine.model.Rep
import xyz.mdhv.formanalyser.app.domain.ArcheryAnalyzer

/** Thin persistence facade over the Room DAOs, with mapping to engine [Rep]s. */
class Repository(context: Context) {
    private val db = AppDatabase.get(context)
    private val athletes = db.athleteDao()
    private val sessions = db.sessionDao()
    private val rigs = db.rigDao()
    private val shots = db.shotDao()

    /** Direct DAO access for the wellness (Phase 2) and body (Phase 3) layers — thin by design. */
    val wellness: WellnessDao = db.wellnessDao()
    val body: BodyDao = db.bodyDao()

    suspend fun allSessions(athleteId: String): List<SessionEntity> =
        sessions.allForAthlete(athleteId)

    suspend fun shotCount(sessionId: String): Int = shots.countForSession(sessionId)

    suspend fun finishSession(
        sessionId: String,
        postCheckinId: String?,
        durationAutoS: Int?,
        durationS: Int?,
        arrowsActual: Int?,
    ) = sessions.finishSession(sessionId, postCheckinId, durationAutoS, durationS, arrowsActual)

    // --- Athlete profile ---
    suspend fun updateAthlete(athlete: AthleteEntity) = athletes.upsert(athlete)

    // --- Rigs (Phase 1) ---
    fun rigsFor(athleteId: String): Flow<List<RigEntity>> = rigs.observeForAthlete(athleteId)

    suspend fun rigsOnce(athleteId: String): List<RigEntity> = rigs.forAthleteOnce(athleteId)

    suspend fun activeRig(athleteId: String): RigEntity? = rigs.activeForAthlete(athleteId)

    suspend fun rigCount(athleteId: String): Int = rigs.countForAthlete(athleteId)

    suspend fun upsertRig(rig: RigEntity) = rigs.upsert(rig)

    suspend fun setActiveRig(athleteId: String, rigId: String) = rigs.setActive(athleteId, rigId)

    /**
     * Refuses to delete the active rig or the last remaining rig — caller activates another first.
     */
    suspend fun deleteRig(rig: RigEntity): Boolean {
        if (rig.active) return false
        if (rigs.countForAthlete(rig.athleteId) <= 1) return false
        rigs.delete(rig.id)
        return true
    }

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

    suspend fun recentSessions(athleteId: String, limit: Int): List<SessionEntity> =
        sessions.recent(athleteId, limit)

    suspend fun saveShots(shots: List<ShotEntity>) = this.shots.insertAll(shots)

    fun shotsFor(sessionId: String): Flow<List<ShotEntity>> = shots.forSession(sessionId)

    suspend fun shotsOnce(sessionId: String): List<ShotEntity> = shots.forSessionOnce(sessionId)

    suspend fun setScore(shotId: String, score: Double?) = shots.setScore(shotId, score)

    suspend fun setBaseline(shotId: String, isBaseline: Boolean) =
        shots.setBaseline(shotId, isBaseline)

    suspend fun baselineShots(athleteId: String): List<ShotEntity> = shots.baselineShots(athleteId)

    /**
     * What deleting a capture session would destroy — shown to the athlete before anything goes.
     *
     * The linked-scorecard line matters: scores are separate evidence the athlete recorded by hand,
     * so they are kept and detached rather than deleted along with the capture. Saying so up front
     * is the difference between a delete they chose and one that surprised them.
     */
    suspend fun previewSessionDeletion(sessionId: String): String {
        val shotCount = shots.countForSession(sessionId)
        val cards = db.scoringDao().linkedCardCount(sessionId)
        return "$shotCount recorded shot(s) will stop counting towards your streak, your volume " +
            "and your form trends, and this session will leave the calendar. Nothing is erased — " +
            "you can restore it from Settings → Data." +
            if (cards > 0)
                " $cards linked scorecard(s) are unaffected; they simply count as their own " +
                    "session in Progress from then on."
            else ""
    }

    /**
     * Retract a capture session.
     *
     * Not a delete. This session feeds the streak, the load series, the calendar grid and
     * Progress's volume and stability trends; dropping the rows would rewrite all of that with
     * nothing left on the device to account for the change. The athlete asked for it to stop
     * counting, and that is exactly what happens — reversibly.
     *
     * Its shots ride the retraction with no column of their own: they are only ever read by parent
     * id, except the two athlete-wide baseline queries, which now exclude a retracted session's
     * shots so the retraction is not merely cosmetic.
     *
     * Linked scorecards are detached, because scores are separate evidence recorded by hand and
     * must go on counting as their own session. Pre- and post-session check-ins are left alone:
     * they are wellness entries about the athlete's body on a day, not properties of a recording.
     */
    suspend fun deleteSession(sessionId: String) =
        db.withTransaction {
            db.scoringDao().clearLinksTo(sessionId)
            sessions.retract(sessionId, System.currentTimeMillis())
        }

    suspend fun restoreSession(sessionId: String) = sessions.restore(sessionId)

    suspend fun retractedSessions(athleteId: String): List<SessionEntity> =
        sessions.retractedForAthlete(athleteId)

    suspend fun scoredReps(athleteId: String): List<Rep> =
        shots.scoredShots(athleteId).map { it.toRep() }

    fun ShotEntity.toRep(): Rep =
        Rep(
            id = id,
            sessionId = sessionId,
            indexInSession = indexInSession,
            features = ArcheryAnalyzer.featuresFromJson(featuresJson),
            score = score,
        )
}
