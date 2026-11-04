package xyz.mdhv.formanalyser.app.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.mdhv.formanalyser.app.data.AppDatabase
import xyz.mdhv.formanalyser.app.data.AthleteEntity
import xyz.mdhv.formanalyser.app.data.CaptureMediaEntity
import xyz.mdhv.formanalyser.app.data.CheckinEntity
import xyz.mdhv.formanalyser.app.data.DocumentEntity
import xyz.mdhv.formanalyser.app.data.InjuryEntity
import xyz.mdhv.formanalyser.app.data.ObserverScoreEventEntity
import xyz.mdhv.formanalyser.app.data.PainLogEntity
import xyz.mdhv.formanalyser.app.data.ScoreArrowEntity
import xyz.mdhv.formanalyser.app.data.ScoreCandidateEntity
import xyz.mdhv.formanalyser.app.data.ScoreOpponentEndEntity
import xyz.mdhv.formanalyser.app.data.ScoreSessionEntity
import xyz.mdhv.formanalyser.app.data.SessionContextEntity
import xyz.mdhv.formanalyser.app.data.SessionEntity
import xyz.mdhv.formanalyser.app.data.ShotEntity
import xyz.mdhv.formanalyser.app.data.SorenessEntity
import xyz.mdhv.formanalyser.wellness.RetractionRegistry

/**
 * Real schema, real DAOs, real dump queries: retracting a parent must take the rows that belong to it
 * out of the export, and nothing else.
 *
 * Every table the registry names is queried with [ExportViewModel.tableDumpQuery] against a database
 * holding one live and one retracted parent of each kind, each with its own children. The owned
 * children of a retracted parent must be absent; a link child (a nullable reference) must still be
 * exported even when it points at a retracted parent; and a child whose parent row is simply missing
 * (an orphan an import without foreign keys can create) is exported as before.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExportRetractionFunctionalTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun close() {
        db.close()
    }

    private fun session(id: String, deletedAt: Long? = null, preCheckinId: String? = null) =
        SessionEntity(
            id = id,
            athleteId = ATHLETE,
            startedAtEpochMs = 1_000,
            drawWeightLbs = 38.0,
            distanceMeters = 18,
            preCheckinId = preCheckinId,
            deletedAt = deletedAt,
        )

    private fun shot(id: String, sessionId: String) =
        ShotEntity(id, sessionId, ATHLETE, 1, "{}", 9.0, false)

    private fun scoreSession(id: String, deletedAt: Long? = null, linkedFormSessionId: String? = null) =
        ScoreSessionEntity(
            id = id,
            athleteId = ATHLETE,
            rigId = null,
            linkedFormSessionId = linkedFormSessionId,
            roundId = "r",
            roundName = "Round",
            distanceMeters = 18,
            targetFaceCm = 40,
            arrowsPerEnd = 3,
            endCount = 10,
            scoringKind = "TOTAL",
            faceLayout = "SINGLE",
            startedAt = 1_000,
            updatedAt = 1_000,
            deletedAt = deletedAt,
        )

    private fun checkin(id: String, deletedAt: Long? = null) =
        CheckinEntity(id = id, athleteId = ATHLETE, ts = 1_000, kind = "STANDALONE", deletedAt = deletedAt)

    private fun injury(id: String, deletedAt: Long? = null) =
        InjuryEntity(
            id = id,
            athleteId = ATHLETE,
            onset = "2026-01-01",
            regionsJson = "[]",
            severity = 1,
            mechanism = "UNKNOWN",
            status = "ACTIVE",
            deletedAt = deletedAt,
        )

    private fun document(id: String, injuryId: String?, deletedAt: Long? = null) =
        DocumentEntity(id, ATHLETE, 1_000, "Doc $id", "application/pdf", "/vault/$id", "sha", 10, injuryId, deletedAt)

    private fun pain(id: String, injuryId: String?, deletedAt: Long? = null) =
        PainLogEntity(id, ATHLETE, 1_000, "shoulder", 3, "[]", injuryId, deletedAt)

    private fun seed() = runBlocking {
        db.athleteDao().upsert(AthleteEntity(ATHLETE, "Archer", 65.0))

        // --- form sessions: S_live, S_ret, each with the full set of owned children -------------
        db.sessionDao().insert(session("S_live"))
        db.sessionDao().insert(session("S_ret", deletedAt = 1))
        for (s in listOf("S_live", "S_ret")) {
            db.shotDao().insertAll(listOf(shot("shot_$s", s)))
            db.athleteFeatureDao().upsertSessionContext(SessionContextEntity(s, ATHLETE, startedAtMs = 1_000))
            db.captureMediaDao().insert(CaptureMediaEntity("media_$s", s, "/cache/$s.mp4", 1, 1, 1, 1, 1))
        }
        // An orphan: its parent row does not exist at all. Not a retraction leak; still exported.
        db.shotDao().insertAll(listOf(shot("shot_orphan", "missing")))

        // --- scorecards -------------------------------------------------------------------------
        db.scoringDao().upsertSession(scoreSession("SS_live"))
        db.scoringDao().upsertSession(scoreSession("SS_ret", deletedAt = 1))
        for (s in listOf("SS_live", "SS_ret")) {
            db.scoringDao().upsertArrow(
                ScoreArrowEntity("arrow_$s", s, 0, 0, 9, false, source = "MANUAL", authority = "ATHLETE",
                    resolution = "RING", createdAt = 1_000)
            )
            db.scoringDao().upsertOpponentEnd(ScoreOpponentEndEntity(s, 0, 20, 1_000))
            db.athleteFeatureDao().upsertCandidate(
                ScoreCandidateEntity("cand_$s", s, 0, 0, 9, false, createdAtMs = 1_000)
            )
            db.athleteFeatureDao().upsertObserverEvent(
                ObserverScoreEventEntity("obs_$s", s, 1_000, 9, inputMode = "TAP")
            )
        }
        // A link: a live scorecard that points at a retracted form session survives, and still exports.
        db.scoringDao().upsertSession(scoreSession("SS_linked_to_retracted_session", linkedFormSessionId = "S_ret"))

        // --- check-ins --------------------------------------------------------------------------
        db.wellnessDao().insertCheckin(checkin("C_live"))
        db.wellnessDao().insertCheckin(checkin("C_ret", deletedAt = 1))
        for (c in listOf("C_live", "C_ret")) db.wellnessDao().insertSoreness(listOf(SorenessEntity(c, "shoulder")))
        // A link: a live session whose pre-check-in was retracted survives and still exports.
        db.sessionDao().insert(session("S_linked_to_retracted_checkin", preCheckinId = "C_ret"))

        // --- injuries, with their documents and pain entries ------------------------------------
        db.bodyDao().upsertInjury(injury("I_live"))
        db.bodyDao().upsertInjury(injury("I_ret", deletedAt = 1))
        for (i in listOf("I_live", "I_ret")) {
            db.bodyDao().insertDocument(document("doc_$i", i))
            db.bodyDao().insertPain(pain("pain_$i", i))
        }
        db.bodyDao().insertDocument(document("doc_no_injury", null))
        db.bodyDao().insertPain(pain("pain_no_injury", null))
        // Retracted in their own right: gone from the export whatever their injury says.
        db.bodyDao().insertDocument(document("doc_self_retracted", null, deletedAt = 5))
        db.bodyDao().insertPain(pain("pain_self_retracted", null, deletedAt = 5))
    }

    /** Primary-key column that identifies a row of [table] in the dump (composite PKs use their parent id). */
    private fun keyColumn(table: String): String =
        when (table) {
            "soreness" -> "checkinId"
            "score_opponent_end" -> "scoreSessionId"
            "session_context" -> "sessionId"
            else -> "id"
        }

    private fun exported(table: String): Set<String> =
        db.openHelper.readableDatabase.query(ExportViewModel.tableDumpQuery(table)).use { c ->
            val col = c.getColumnIndexOrThrow(keyColumn(table))
            buildSet { while (c.moveToNext()) add(c.getString(col)) }
        }

    @Test
    fun ownedChildrenOfARetractedParentAreNotExportedAndLiveOnesAre() {
        seed()

        assertEquals(setOf("shot_S_live", "shot_orphan"), exported("shots"))
        assertEquals(setOf("S_live"), exported("session_context"))
        assertEquals(setOf("arrow_SS_live"), exported("score_arrow"))
        assertEquals(setOf("SS_live"), exported("score_opponent_end"))
        assertEquals(setOf("cand_SS_live"), exported("score_candidate"))
        assertEquals(setOf("obs_SS_live"), exported("observer_score_event"))
        assertEquals(setOf("C_live"), exported("soreness"))
    }

    @Test
    fun linksToARetractedParentStillExportBecauseTheyAreNotOwned() {
        seed()

        // A document or pain entry attached to a retracted injury is still the athlete's own record.
        assertEquals(setOf("doc_I_live", "doc_I_ret", "doc_no_injury"), exported("document"))
        assertEquals(setOf("pain_I_live", "pain_I_ret", "pain_no_injury"), exported("pain_log"))
        // A live session/scorecard that merely points at a retracted check-in / form session stays.
        assertEquals(setOf("S_live", "S_linked_to_retracted_checkin"), exported("sessions"))
        assertEquals(setOf("SS_live", "SS_linked_to_retracted_session"), exported("score_session"))
    }

    @Test
    fun everyRegisteredTableIsCoveredAndTheRawVideoTableIsNeverDumped() {
        seed()

        val covered =
            setOf(
                "shots", "session_context", "score_arrow", "score_opponent_end", "score_candidate",
                "observer_score_event", "soreness", "document", "pain_log", "sessions", "score_session",
            )
        val registered = RetractionRegistry.refs.map { it.table }.toSet() - "capture_media"
        assertEquals("add any new registered table to this test's seed", registered, covered)
        covered.forEach { exported(it) } // every query compiles and runs against the real schema

        assertThrows(IllegalArgumentException::class.java) { ExportViewModel.tableDumpQuery("capture_media") }
    }

    private companion object {
        const val ATHLETE = "a1"
    }
}
