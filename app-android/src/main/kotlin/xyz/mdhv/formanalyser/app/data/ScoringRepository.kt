package xyz.mdhv.formanalyser.app.data

import android.content.Context
import androidx.room.withTransaction
import java.util.UUID
import xyz.mdhv.formanalyser.scoring.*

class ScoringRepository(context: Context) {
    private val db = AppDatabase.get(context.applicationContext)
    private val scoring = db.scoringDao()
    private val features = db.athleteFeatureDao()
    private val athletes = db.athleteDao()
    private val rigs = db.rigDao()
    private val sessions = db.sessionDao()

    data class Snapshot(val session: ScoreSessionEntity, val card: Scorecard)

    /**
     * Every path that can change a scorecard's arrows, opponent totals or outcome must pass through
     * here first.
     *
     * A finished session has already had `roundComplete` and `completedAt` written, and
     * [ScoringDao.previousBest] and the Progress trend both read those columns to decide what
     * counts as a complete round. [ScoringDao.updateSummary] rewrites only `total`/`xCount`/set
     * points, so mutating a finished card leaves those columns describing a round that no longer
     * exists — e.g. undoing two arrows off a finished 72-arrow card leaves a 70-arrow total still
     * flagged `roundComplete = 1`, which then becomes a personal best and a point on the score
     * trend. The UI also disables these controls, but the repository is the boundary that has to
     * hold: it is the only thing standing between a future caller and corrupted PB history.
     */
    private fun requireActive(session: ScoreSessionEntity) {
        // A retracted card is addressable by id (restore needs that), so the boundary has to say so
        // rather than relying on it having left the lists.
        check(session.deletedAt == null) {
            "This scorecard was deleted; restore it from Settings → Data to keep scoring."
        }
        check(session.status == "ACTIVE") {
            "This scorecard is already finished; reopen or start a new one to keep scoring."
        }
    }

    /**
     * Triple-face rounds have no 1–5 rings, so anything scoring below 6 is a miss, not a low ring.
     * Enforced centrally because every input path — numeric, plot, Live Observer tap and a
     * confirmed End Scan candidate — has to agree; when this lived inline in the manual path only,
     * observer taps and End Scan confirmations could write impossible scores onto a triple face.
     */
    private fun validateForLayout(score: ArrowScore, layout: FaceLayout) {
        if (layout != FaceLayout.SINGLE && score.points in 1..5)
            error("Triple-face scoring accepts 6-10 or miss")
    }

    suspend fun currentAthlete(): AthleteEntity? = athletes.firstOrNull()

    suspend fun resumeActive(): Snapshot? {
        val athlete = athletes.firstOrNull() ?: return null
        val active = scoring.activeForAthlete(athlete.id) ?: return null
        return snapshot(active.id)
    }

    suspend fun quickStart(): Snapshot {
        val athlete = athletes.firstOrNull() ?: error("No athlete profile")
        scoring.activeForAthlete(athlete.id)?.let {
            return snapshot(it.id)
        }
        val template = scoring.latestPinned(athlete.id) ?: scoring.latest(athlete.id)
        return start(
            template?.toRoundDefinition() ?: RoundPack.WA_RECURVE_70M_72,
            template?.pinned == true,
        )
    }

    suspend fun start(round: RoundDefinition, pinned: Boolean = false): Snapshot {
        val athlete = athletes.firstOrNull() ?: error("No athlete profile")
        val rig = rigs.activeForAthlete(athlete.id)
        val now = System.currentTimeMillis()
        val e =
            ScoreSessionEntity(
                UUID.randomUUID().toString(),
                athlete.id,
                rig?.id,
                roundId = round.id,
                roundName = round.name,
                distanceMeters = round.distanceMeters,
                targetFaceCm = round.targetFaceCm,
                arrowsPerEnd = round.arrowsPerEnd,
                endCount = round.endCount,
                scoringKind = round.scoringKind.name,
                faceLayout = round.faceLayout.name,
                startedAt = now,
                updatedAt = now,
                pinned = pinned,
            )
        scoring.upsertSession(e)
        return Snapshot(e, Scorecard(round))
    }

    suspend fun snapshot(sessionId: String): Snapshot =
        db.withTransaction {
            val s = scoring.session(sessionId) ?: error("Score session not found: $sessionId")
            Snapshot(
                s,
                buildCard(s, scoring.activeArrows(sessionId), scoring.opponentEnds(sessionId)),
            )
        }

    suspend fun recordNumeric(sessionId: String, score: ArrowScore) =
        record(sessionId, score, null, ScoreSource.MANUAL_NUMERIC)

    suspend fun recordPlot(sessionId: String, point: PlotPoint) =
        record(sessionId, null, point, ScoreSource.MANUAL_PLOT)

    private suspend fun record(
        sessionId: String,
        numericScore: ArrowScore?,
        plot: PlotPoint?,
        source: ScoreSource,
        authority: AuthorityState = AuthorityState.HUMAN_CONFIRMED,
        resolution: ObservationResolution = ObservationResolution.SHOT_CONFIRMED,
    ): Snapshot =
        db.withTransaction {
            val cur = snapshotUnlocked(sessionId)
            requireActive(cur.session)
            val score =
                numericScore
                    ?: scoreFromPlot(
                        plot ?: error("Plot point required"),
                        cur.card.round.faceLayout,
                    )
            validateForLayout(score, cur.card.round.faceLayout)
            val next =
                cur.card.record(
                    UUID.randomUUID().toString(),
                    score,
                    plot,
                    source,
                    authority,
                    resolution,
                )
            scoring.upsertArrow(next.arrows.last().toEntity(sessionId, System.currentTimeMillis()))
            scoring.updateFrom(next, sessionId)
            Snapshot(scoring.session(sessionId)!!, next)
        }

    suspend fun undo(sessionId: String): Snapshot =
        db.withTransaction {
            val cur = snapshotUnlocked(sessionId)
            requireActive(cur.session)
            val last = cur.card.arrows.lastOrNull() ?: return@withTransaction cur
            scoring.retractArrow(last.id, System.currentTimeMillis())
            val next = cur.card.undoLast()
            scoring.updateFrom(next, sessionId)
            Snapshot(scoring.session(sessionId)!!, next)
        }

    suspend fun setOpponentEndTotal(sessionId: String, endIndex: Int, total: Int): Snapshot =
        db.withTransaction {
            val cur = snapshotUnlocked(sessionId)
            requireActive(cur.session)
            // Validate against the card before writing the row: withOpponentEndTotal rejects a set
            // that has not been shot, and inside withTransaction that throw rolls the write back.
            val next = cur.card.withOpponentEndTotal(endIndex, total)
            scoring.upsertOpponentEnd(
                ScoreOpponentEndEntity(sessionId, endIndex, total, System.currentTimeMillis())
            )
            scoring.updateFrom(next, sessionId)
            Snapshot(scoring.session(sessionId)!!, next)
        }

    suspend fun setShootOffWinner(sessionId: String, winner: SetMatchSummary.Winner): Snapshot =
        db.withTransaction {
            val cur = snapshotUnlocked(sessionId)
            requireActive(cur.session)
            val next = cur.card.withShootOffWinner(winner)
            scoring.setShootOffWinner(sessionId, winner.name, System.currentTimeMillis())
            scoring.updateFrom(next, sessionId)
            Snapshot(scoring.session(sessionId)!!, next)
        }

    suspend fun togglePinned(sessionId: String): Snapshot =
        db.withTransaction {
            val s = scoring.session(sessionId) ?: error("Score session not found")
            scoring.setPinned(sessionId, !s.pinned, System.currentTimeMillis())
            snapshotUnlocked(sessionId)
        }

    /**
     * The capture sessions a scorecard may be attached to — most recent first.
     *
     * Bounded, because this is a picker and not a browser: the athlete is identifying the session
     * they just shot, and a card scored weeks after the fact is a case for editing the capture
     * session, not for scrolling a year of history in a dialog.
     */
    suspend fun linkableFormSessions(limit: Int = 12): List<SessionEntity> {
        val a = athletes.firstOrNull() ?: return emptyList()
        return sessions.recent(a.id, limit)
    }

    /**
     * Attach this scorecard to a capture session, or pass null to detach it.
     *
     * Never inferred. Progress treats a linked pair as one training session, so a wrong guess would
     * quietly *remove* arrows from the athlete's 28-day volume — a machine may not make that call
     * on their behalf. The athlete says which session this was, or it stays unlinked.
     *
     * No [requireActive] guard, unlike every other write here: this changes no score, no arrow and
     * no completion column, and the usual moment to record the link is *after* the card is
     * finished.
     */
    suspend fun setLinkedFormSession(sessionId: String, formSessionId: String?): Snapshot =
        db.withTransaction {
            scoring.session(sessionId) ?: error("Score session not found: $sessionId")
            if (formSessionId != null)
                checkNotNull(sessions.byId(formSessionId)) {
                    "That training session no longer exists."
                }
            scoring.setLinkedFormSession(sessionId, formSessionId)
            snapshotUnlocked(sessionId)
        }

    suspend fun updateContext(
        sessionId: String,
        sightMark: String?,
        venue: String?,
        conditions: String?,
        trainingIntent: String?,
    ): Snapshot =
        db.withTransaction {
            scoring.updateContext(
                sessionId,
                sightMark.clean(),
                venue.clean(),
                conditions.clean(),
                trainingIntent.clean(),
                System.currentTimeMillis(),
            )
            snapshotUnlocked(sessionId)
        }

    suspend fun finish(sessionId: String): Pair<Snapshot, Int?> =
        db.withTransaction {
            val cur = snapshotUnlocked(sessionId)
            requireActive(cur.session)
            val pb =
                if (cur.card.round.scoringKind == ScoringKind.SET_MATCH) null
                else scoring.previousBest(cur.session.athleteId, cur.session.roundId, sessionId)
            scoring.finish(sessionId, cur.card.isComplete(), System.currentTimeMillis())
            snapshotUnlocked(sessionId) to pb
        }

    data class EndScanCandidate(
        val points: Int,
        val isX: Boolean = false,
        val plot: PlotPoint? = null,
        val confidence: Double? = null,
    )

    suspend fun proposeEndScanCandidates(
        sessionId: String,
        endIndex: Int,
        candidates: List<EndScanCandidate>,
    ): List<ScoreCandidateEntity> =
        db.withTransaction {
            val now = System.currentTimeMillis()
            candidates.mapIndexed { i, c ->
                require(c.points in 0..10)
                ScoreCandidateEntity(
                        UUID.randomUUID().toString(),
                        sessionId,
                        endIndex,
                        i,
                        c.points,
                        c.isX,
                        c.plot?.x,
                        c.plot?.y,
                        c.plot?.faceIndex,
                        c.confidence?.coerceIn(0.0, 1.0),
                        createdAtMs = now,
                    )
                    .also { features.upsertCandidate(it) }
            }
        }

    suspend fun endScanCandidates(sessionId: String, endIndex: Int) =
        features.candidatesForEnd(sessionId, endIndex)

    suspend fun confirmEndScanCandidate(
        candidateId: String,
        sessionId: String,
        endIndex: Int,
    ): Snapshot =
        db.withTransaction {
            val c =
                features.candidatesForEnd(sessionId, endIndex).firstOrNull { it.id == candidateId }
                    ?: error("End Scan candidate not found")
            check(c.status == "PROPOSED")
            val cur = snapshotUnlocked(sessionId)
            requireActive(cur.session)
            validateForLayout(ArrowScore(c.points, c.isX), cur.card.round.faceLayout)
            val plot =
                if (c.plotX != null && c.plotY != null)
                    PlotPoint(c.plotX, c.plotY, c.plotFaceIndex ?: 0)
                else null
            val next =
                cur.card.record(
                    UUID.randomUUID().toString(),
                    ArrowScore(c.points, c.isX),
                    plot,
                    ScoreSource.END_SCAN,
                    AuthorityState.HUMAN_CONFIRMED,
                    ObservationResolution.END_ONLY,
                )
            scoring.upsertArrow(next.arrows.last().toEntity(sessionId, System.currentTimeMillis()))
            scoring.updateFrom(next, sessionId)
            features.resolveCandidate(candidateId, "CONFIRMED", System.currentTimeMillis())
            Snapshot(scoring.session(sessionId)!!, next)
        }

    suspend fun rejectEndScanCandidate(candidateId: String) {
        features.resolveCandidate(candidateId, "REJECTED", System.currentTimeMillis())
    }

    suspend fun recordObserverTap(
        sessionId: String,
        ring: Int,
        isX: Boolean = false,
        sector: String? = null,
    ): Snapshot =
        db.withTransaction {
            require(ring in 0..10)
            val cur = snapshotUnlocked(sessionId)
            requireActive(cur.session)
            validateForLayout(ArrowScore(ring, isX), cur.card.round.faceLayout)
            val now = System.currentTimeMillis()
            val e =
                ObserverScoreEventEntity(
                    UUID.randomUUID().toString(),
                    sessionId,
                    now,
                    ring,
                    isX,
                    sector,
                    "TAP",
                    declaredText = if (isX) "X" else if (ring == 0) "M" else ring.toString(),
                )
            features.upsertObserverEvent(e)
            val next =
                cur.card.record(
                    UUID.randomUUID().toString(),
                    ArrowScore(ring, isX),
                    null,
                    ScoreSource.LIVE_OBSERVER,
                    AuthorityState.HUMAN_CONFIRMED,
                    ObservationResolution.SHOT_INFERRED,
                )
            scoring.upsertArrow(next.arrows.last().toEntity(sessionId, now))
            scoring.updateFrom(next, sessionId)
            Snapshot(scoring.session(sessionId)!!, next)
        }

    suspend fun observerEvents(sessionId: String) = features.observerEvents(sessionId)

    /**
     * What deleting a scorecard would destroy, so the athlete is told before it happens rather than
     * after.
     *
     * Crocodyl never removes athlete history on its own. When the athlete asks it to, the least it
     * owes them is an exact account of what goes — which is what this produces, and what the
     * confirmation renders.
     */
    data class DeletionPreview(
        val label: String,
        val detail: String,
        /** True for the discard path, where there is genuinely nothing to restore. */
        val permanent: Boolean,
    )

    suspend fun previewScorecardDeletion(sessionId: String): DeletionPreview {
        val s = scoring.session(sessionId) ?: error("Score session not found: $sessionId")
        return if (isDiscardable(s)) {
            DeletionPreview(
                s.roundName,
                "This scorecard has no arrows on it, so nothing has been derived from it. It will " +
                    "be discarded outright — this one cannot be restored.",
                permanent = true,
            )
        } else {
            val arrows = scoring.activeArrowCount(sessionId)
            val pb =
                if (s.roundComplete && s.status == "FINISHED")
                    scoring.previousBest(s.athleteId, s.roundId, sessionId)
                else null
            DeletionPreview(
                s.roundName,
                "$arrows arrow(s) and a total of ${s.total} will stop counting towards your " +
                    "trends, volume and personal bests." +
                    (if (pb != null && s.total > pb)
                        " This is your best ${s.roundName}; your record becomes $pb."
                    else "") +
                    " Nothing is erased — you can restore it from Settings → Data.",
                permanent = false,
            )
        }
    }

    /**
     * A scorecard that was opened and never scored on: still ACTIVE, never finished, no arrow ever
     * written (retracted ones included) and no opponent end filed.
     *
     * Every query that could have derived anything from a card requires `status = 'FINISHED'`, so
     * such a row has contributed to no trend, no volume figure and no personal best. It is a
     * mis-tap rather than history, and keeping a tombstone for it would only clutter the restore
     * list with rounds the athlete never shot.
     */
    private suspend fun isDiscardable(s: ScoreSessionEntity): Boolean =
        s.status == "ACTIVE" &&
            s.completedAt == null &&
            scoring.everArrowCount(s.id) == 0 &&
            scoring.opponentEndCount(s.id) == 0

    /**
     * Retract a scorecard — or, for a never-scored one, discard it outright.
     *
     * Retraction rather than deletion is the whole point: this card feeds `previousBest`,
     * `bestPerRound`, the score trend and the PB list, and dropping the bytes would rewrite all of
     * that with nothing left on the device to explain why the numbers moved. The athlete asked for
     * it to stop counting, not for their own history to become unaccountable. [restoreScorecard]
     * puts it back.
     */
    suspend fun deleteScorecard(sessionId: String) =
        db.withTransaction {
            val s = scoring.session(sessionId) ?: error("Score session not found: $sessionId")
            if (isDiscardable(s)) {
                features.deleteCandidates(sessionId)
                features.deleteObserverEvents(sessionId)
                scoring.deleteOpponentEnds(sessionId)
                scoring.deleteArrows(sessionId)
                scoring.deleteSession(sessionId)
            } else {
                scoring.retractSession(sessionId, System.currentTimeMillis())
            }
        }

    suspend fun restoreScorecard(sessionId: String) = scoring.restoreSession(sessionId)

    suspend fun retractedScorecards(): List<ScoreSessionEntity> {
        val a = athletes.firstOrNull() ?: return emptyList()
        return scoring.retractedForAthlete(a.id)
    }

    /** Best complete round per roundId across all history — the same basis the scorer's PB uses. */
    suspend fun bestPerRound(): List<RoundBest> {
        val a = athletes.firstOrNull() ?: return emptyList()
        return scoring.bestPerRound(a.id)
    }

    suspend fun recent(limit: Int = 20): List<ScoreSessionEntity> {
        val a = athletes.firstOrNull() ?: return emptyList()
        return scoring.recent(a.id, limit)
    }

    private suspend fun snapshotUnlocked(sessionId: String): Snapshot {
        val s = scoring.session(sessionId) ?: error("Score session not found: $sessionId")
        return Snapshot(
            s,
            buildCard(s, scoring.activeArrows(sessionId), scoring.opponentEnds(sessionId)),
        )
    }

    private fun buildCard(
        s: ScoreSessionEntity,
        a: List<ScoreArrowEntity>,
        o: List<ScoreOpponentEndEntity>,
    ) =
        Scorecard(
            s.toRoundDefinition(),
            a.map { it.toCore() },
            o.associate { it.endIndex to it.total },
            s.shootOffWinner?.let(SetMatchSummary.Winner::valueOf),
        )

    private suspend fun ScoringDao.updateFrom(card: Scorecard, sessionId: String) {
        val m = card.setMatchSummary()
        updateSummary(
            sessionId,
            card.total,
            card.xCount,
            m?.athleteSetPoints,
            m?.opponentSetPoints,
            System.currentTimeMillis(),
        )
    }
}

fun ScoreSessionEntity.toRoundDefinition() =
    RoundDefinition(
        roundId,
        roundName,
        distanceMeters,
        targetFaceCm,
        arrowsPerEnd,
        endCount,
        ScoringKind.valueOf(scoringKind),
        FaceLayout.valueOf(faceLayout),
    )

private fun ScoredArrow.toEntity(sessionId: String, now: Long) =
    ScoreArrowEntity(
        id,
        sessionId,
        endIndex,
        arrowIndex,
        score.points,
        score.isX,
        plot?.x,
        plot?.y,
        plot?.faceIndex,
        source.name,
        authority.name,
        resolution.name,
        now,
    )

private fun ScoreArrowEntity.toCore() =
    ScoredArrow(
        id,
        endIndex,
        arrowIndex,
        ArrowScore(points, isX),
        if (plotX != null && plotY != null) PlotPoint(plotX, plotY, plotFaceIndex ?: 0) else null,
        ScoreSource.valueOf(source),
        AuthorityState.valueOf(authority),
        ObservationResolution.valueOf(resolution),
    )

private fun String?.clean() = this?.trim()?.takeIf { it.isNotEmpty() }
