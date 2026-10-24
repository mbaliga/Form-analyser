package xyz.mdhv.formanalyser.app.domain

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mdhv.formanalyser.app.capture.EndScanCapture
import xyz.mdhv.formanalyser.app.data.ScoreCandidateEntity
import xyz.mdhv.formanalyser.app.data.ScoreSessionEntity
import xyz.mdhv.formanalyser.app.data.ScoringRepository
import xyz.mdhv.formanalyser.app.data.SessionEntity
import xyz.mdhv.formanalyser.scoring.EndScan
import xyz.mdhv.formanalyser.scoring.FaceCalibration
import xyz.mdhv.formanalyser.scoring.FaceLayout
import xyz.mdhv.formanalyser.scoring.ImagePoint
import xyz.mdhv.formanalyser.scoring.PlotPoint
import xyz.mdhv.formanalyser.scoring.ObserverCommand
import xyz.mdhv.formanalyser.scoring.RoundPack
import xyz.mdhv.formanalyser.scoring.ScoreInput
import xyz.mdhv.formanalyser.scoring.Scorecard
import xyz.mdhv.formanalyser.scoring.ScoringKind
import xyz.mdhv.formanalyser.scoring.SetMatchSummary
import xyz.mdhv.formanalyser.scoring.SpokenScore

enum class ScoringInputMode {
    NUMBERS,
    PLOT,
    OBSERVER,
    END_SCAN,
}

/** Where an End Scan has got to. Strictly forward except for an explicit retake. */
enum class EndScanStage {
    /** Framing the target through the camera. */
    CAMERA,
    /** Dragging the four handles onto the face's edge. */
    CALIBRATE,
    /** Looking at what the detector found, before any of it is filed as a proposal. */
    REVIEW,
}

/**
 * One End Scan ceremony in progress, held only while it is happening.
 *
 * [photo] lives here and nowhere else: not in a file, not in a table, not in the export. It is
 * dropped the moment the athlete leaves the flow, which is why this whole state is nullable rather
 * than a set of fields on [ScoringUiState] — "not scanning" has to be a state in which the bitmap is
 * unreachable, not one in which it happens to be unused. It is kept on the ViewModel rather than in
 * the composition so that turning the phone over mid-calibration does not throw the photograph away
 * and send the athlete back to the shooting line for another one.
 */
data class EndScanUiState(
    val stage: EndScanStage = EndScanStage.CAMERA,
    val photo: Bitmap? = null,
    val calibration: FaceCalibration? = null,
    val result: EndScan.Result? = null,
    /** Arrows still to be recorded in this end — the cap on how much may be proposed. */
    val arrowsExpected: Int = 0,
    val busy: Boolean = false,
    val error: String? = null,
) {
    /** Why the current handles are unusable, or null. Derived, so it cannot fall out of date. */
    val calibrationProblem
        get() = calibration?.problem()
}

data class ScoringUiState(
    val snapshot: ScoringRepository.Snapshot? = null,
    val recent: List<ScoreSessionEntity> = emptyList(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val inputMode: ScoringInputMode = ScoringInputMode.NUMBERS,
    val error: String? = null,
    val completionMessage: String? = null,
    val endScanCandidates: List<ScoreCandidateEntity> = emptyList(),
    /** Capture sessions offered by the link picker; loaded only when the athlete opens it. */
    val linkableFormSessions: List<SessionEntity> = emptyList(),
    /** Non-null while the delete confirmation is open, holding what it will say. */
    val deletionPreview: ScoringRepository.DeletionPreview? = null,
)

class ScoringViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ScoringRepository(app)
    private val _state = MutableStateFlow(ScoringUiState())
    val state: StateFlow<ScoringUiState> = _state.asStateFlow()

    /**
     * The End Scan ceremony, or null when none is open. A separate flow from [state] on purpose: it
     * holds a bitmap, it is short-lived, and none of it belongs to the scorecard.
     */
    private val _endScan = MutableStateFlow<EndScanUiState?>(null)
    val endScan: StateFlow<EndScanUiState?> = _endScan.asStateFlow()

    fun load() {
        if (!_state.value.loading && _state.value.snapshot != null) return
        viewModelScope.launch {
            try {
                val (a, r) = withContext(Dispatchers.IO) { repo.resumeActive() to repo.recent(10) }
                _state.update {
                    it.copy(snapshot = a, recent = r, loading = false, error = null)
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _state.update {
                    it.copy(loading = false, error = t.message ?: "Could not load scoring")
                }
            }
        }
    }

    fun quickStart() = action {
        val s = repo.quickStart()
        return@action { copy(snapshot = s, completionMessage = null) }
    }

    fun openScorecard(id: String) = action {
        val s = repo.snapshot(id)
        return@action { copy(snapshot = s, completionMessage = null) }
    }

    fun startBuiltIn(id: String) = action {
        val s = repo.start(RoundPack.byId(id) ?: error("Unknown round: $id"))
        return@action { copy(snapshot = s, completionMessage = null) }
    }

    fun startCustom(
        name: String,
        distanceMeters: Int,
        targetFaceCm: Int,
        arrowsPerEnd: Int,
        endCount: Int,
    ) = action {
        val s =
            repo.start(
                RoundPack.customPractice(
                    // Derive the round id from the round's *shape*, not a fresh UUID. previousBest
                    // matches on roundId, so a random id per session meant no custom practice ever
                    // had a predecessor and every one of them reported "New PB".
                    customRoundId(distanceMeters, targetFaceCm, arrowsPerEnd, endCount),
                    name.ifBlank { "Custom practice" },
                    distanceMeters,
                    targetFaceCm,
                    arrowsPerEnd,
                    endCount,
                )
            )
        return@action { copy(snapshot = s, completionMessage = null) }
    }

    fun setInputMode(mode: ScoringInputMode) {
        _state.update { it.copy(inputMode = mode) }
        if (mode == ScoringInputMode.END_SCAN) loadEndScanCandidates()
    }

    fun recordToken(token: String) {
        val id = _state.value.snapshot?.session?.id ?: return
        val score =
            runCatching { ScoreInput.parse(token) }
                .getOrElse { t ->
                    _state.update { it.copy(error = t.message) }
                    return
                }
        action {
            val s = repo.recordNumeric(id, score)
            return@action { copy(snapshot = s) }
        }
    }

    fun recordPlot(p: PlotPoint) {
        val id = _state.value.snapshot?.session?.id ?: return
        action {
            val s = repo.recordPlot(id, p)
            return@action { copy(snapshot = s) }
        }
    }

    fun recordObserverToken(token: String, sector: String? = null) {
        val id = _state.value.snapshot?.session?.id ?: return
        val s =
            runCatching { ScoreInput.parse(token) }
                .getOrElse { t ->
                    _state.update { it.copy(error = t.message) }
                    return
                }
        action {
            val n = repo.recordObserverTap(id, s.points, s.isX, sector)
            return@action { copy(snapshot = n) }
        }
    }

    fun recordObserverPhrase(utterance: String) {
        val id = _state.value.snapshot?.session?.id ?: return
        val command =
            runCatching { ScoreInput.parseObserverCommand(utterance) }
                .getOrElse { t ->
                    _state.update { it.copy(error = t.message ?: "No valid score heard") }
                    return
                }
        if (command == ObserverCommand.Undo) {
            undo()
            return
        }
        if (command == ObserverCommand.Skip) return
        if (command == ObserverCommand.FinishEnd) {
            action {
                val next = repo.finishObserverEnd(id, utterance.trim())
                return@action { copy(snapshot = next) }
            }
            return
        }
        if (command is ObserverCommand.Correct) {
            action {
                val next = repo.correctLastObserver(id, command.value)
                return@action { copy(snapshot = next) }
            }
            return
        }
        val spoken =
            when (command) {
                is ObserverCommand.Score -> command.value
                ObserverCommand.Repeat -> {
                    val last = _state.value.snapshot?.card?.arrows?.lastOrNull()
                    if (last == null) {
                        _state.update { it.copy(error = "There is no previous arrow to repeat") }
                        return
                    }
                    SpokenScore(last.score, "unspecified", utterance.trim())
                }
                ObserverCommand.Undo -> return
                ObserverCommand.Skip, ObserverCommand.FinishEnd -> return
                is ObserverCommand.Correct -> return
            }
        action {
            val next = repo.recordObserverTap(
                id,
                spoken.score.points,
                spoken.score.isX,
                spoken.sector,
                inputKind = "VOICE",
                declaredText = spoken.declaredText,
            )
            return@action { copy(snapshot = next) }
        }
    }

    fun loadEndScanCandidates() {
        val s = _state.value.snapshot ?: return
        viewModelScope.launch {
            try {
                val c =
                    withContext(Dispatchers.IO) {
                        repo.endScanCandidates(s.session.id, s.card.currentEndIndex)
                    }
                _state.update { it.copy(endScanCandidates = c) }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _state.update {
                    it.copy(error = t.message ?: "Could not load End Scan candidates")
                }
            }
        }
    }

    fun acceptDetectedCandidates(c: List<ScoringRepository.EndScanCandidate>) {
        val s = _state.value.snapshot ?: return
        action {
            val proposed = repo.proposeEndScanCandidates(s.session.id, s.card.currentEndIndex, c)
            return@action { copy(endScanCandidates = proposed) }
        }
    }

    fun confirmEndScanCandidate(cid: String) {
        val s = _state.value.snapshot ?: return
        action {
            val n = repo.confirmEndScanCandidate(cid, s.session.id, s.card.currentEndIndex)
            val c = repo.endScanCandidates(s.session.id, n.card.currentEndIndex)
            return@action { copy(snapshot = n, endScanCandidates = c) }
        }
    }

    fun rejectEndScanCandidate(cid: String) {
        val s = _state.value.snapshot ?: return
        action {
            repo.rejectEndScanCandidate(cid, s.session.id, s.card.currentEndIndex)
            val c = repo.endScanCandidates(s.session.id, s.card.currentEndIndex)
            return@action { copy(endScanCandidates = c) }
        }
    }

    // --- End Scan: photograph → calibrate → detect → propose -----------------------------------

    /**
     * Open the scan ceremony for the end currently being shot.
     *
     * Refuses up front rather than letting the athlete walk to the target, take a photograph and
     * only then be told the end was already full — the two conditions below are exactly the ones
     * [ScoringRepository.confirmEndScanCandidate] would fail on at the very end of the flow.
     */
    fun beginEndScan() {
        val s = _state.value.snapshot ?: return
        if (s.session.status != "ACTIVE") {
            _state.update {
                it.copy(error = "This scorecard is finished, so there is nothing left to scan.")
            }
            return
        }
        val outstanding = arrowsOutstanding(s.card)
        if (outstanding <= 0) {
            _state.update {
                it.copy(
                    error =
                        "Every arrow in this end is already recorded. Start the next end before scanning."
                )
            }
            return
        }
        _endScan.value = EndScanUiState(arrowsExpected = outstanding)
    }

    /**
     * Leave the ceremony. Nothing was written, and dropping the state drops the photograph with it —
     * the only copy that ever existed.
     */
    fun cancelEndScan() {
        _endScan.value = null
    }

    /**
     * A photograph arrived from the camera; decode it, seed the handles, hand it to the athlete.
     *
     * Takes the compressed frame rather than a bitmap so the caller can hand CameraX its buffer back
     * immediately, and so the decode — a megapixel JPEG plus a rotation — happens on a worker rather
     * than on the frame the shutter animation is running on.
     */
    fun endScanPhotoCaptured(frame: EndScanCapture.CapturedFrame) {
        val layout = _state.value.snapshot?.card?.round?.faceLayout ?: return
        _endScan.update { it?.copy(busy = true, error = null) }
        viewModelScope.launch {
            val photo =
                runCatching { withContext(Dispatchers.Default) { EndScanCapture.decode(frame) } }
                    .getOrNull()
            if (photo == null) {
                endScanCaptureFailed("That photo could not be read. Try again.")
                return@launch
            }
            _endScan.update { cur ->
                cur?.copy(
                    stage = EndScanStage.CALIBRATE,
                    photo = photo,
                    calibration = seedCalibration(photo, layout),
                    result = null,
                    busy = false,
                    error = null,
                )
            }
        }
    }

    /** The camera could not produce a frame. Said plainly rather than left as a dead shutter. */
    fun endScanCaptureFailed(message: String) {
        _endScan.update { it?.copy(busy = false, error = message) }
    }

    fun retakeEndScanPhoto() {
        _endScan.update {
            it?.copy(
                stage = EndScanStage.CAMERA,
                photo = null,
                calibration = null,
                result = null,
                busy = false,
                error = null,
            )
        }
    }

    /** One handle moved. Called continuously while a handle is being dragged. */
    fun updateEndScanCalibration(calibration: FaceCalibration) {
        _endScan.update { it?.copy(calibration = calibration, error = null) }
    }

    /**
     * Run the detector over the calibrated photograph.
     *
     * Off the main thread because it resamples the face into a 768-pixel square and sweeps it twice;
     * on a mid-range phone that is well short of a second, but it is not free and the shutter button
     * is right next to it.
     */
    fun runEndScanDetection() {
        val snapshot = _state.value.snapshot ?: return
        val scan = _endScan.value ?: return
        val photo = scan.photo ?: return
        val calibration = scan.calibration ?: return
        val projected = calibration.project()
        if (projected is FaceCalibration.Result.Unusable) {
            _endScan.update { it?.copy(error = projected.problem.message) }
            return
        }
        val projection = (projected as FaceCalibration.Result.Usable).projection
        val round = snapshot.card.round
        _endScan.update { it?.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching {
                    withContext(Dispatchers.Default) {
                        EndScan.detect(
                            EndScanCapture.toGray(photo),
                            projection,
                            EndScan.Options(
                                targetFaceCm = round.targetFaceCm,
                                faceLayout = round.faceLayout,
                                arrowsExpected = scan.arrowsExpected,
                            ),
                        )
                    }
                }
                .onSuccess { found ->
                    _endScan.update {
                        it?.copy(stage = EndScanStage.REVIEW, result = found, busy = false)
                    }
                }
                .onFailure { t ->
                    _endScan.update {
                        it?.copy(busy = false, error = t.message ?: "Could not read that photo")
                    }
                }
        }
    }

    /**
     * File what the detector found as proposals and close the ceremony.
     *
     * This is not scoring. Every impact becomes a `PROPOSED` row that the End Scan review panel then
     * makes the athlete confirm or reject one at a time; totals, X counts and personal bests are
     * untouched until they do.
     */
    fun proposeEndScanFindings() {
        val snapshot = _state.value.snapshot ?: return
        val scan = _endScan.value ?: return
        val found = scan.result ?: return
        if (found.impacts.isEmpty()) {
            cancelEndScan()
            return
        }
        val endIndex = snapshot.card.currentEndIndex
        val candidates =
            found.impacts.map {
                ScoringRepository.EndScanCandidate(
                    points = it.score.points,
                    isX = it.score.isX,
                    plot = it.plot,
                    confidence = it.confidence,
                    detectorVersion = found.detectorVersion,
                )
            }
        _endScan.update { it?.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching {
                    withContext(Dispatchers.IO) {
                        repo.proposeEndScanCandidates(snapshot.session.id, endIndex, candidates)
                        repo.endScanCandidates(snapshot.session.id, endIndex)
                    }
                }
                .onSuccess { proposed ->
                    _endScan.value = null
                    _state.update {
                        it.copy(
                            endScanCandidates = proposed,
                            inputMode = ScoringInputMode.END_SCAN,
                        )
                    }
                }
                .onFailure { t ->
                    _endScan.update {
                        it?.copy(busy = false, error = t.message ?: "Could not save the proposals")
                    }
                }
        }
    }

    /**
     * How many arrows of the end being shot are still unrecorded.
     *
     * The cap on everything a scan may propose. Counting what is already on the card rather than
     * assuming a fresh end matters because End Scan is mixed with the keypad in practice — an athlete
     * who has already tapped in two of three arrows must not be offered three more.
     */
    private fun arrowsOutstanding(card: Scorecard): Int =
        if (card.isFull) 0 else (card.round.arrowsPerEnd - card.currentArrowIndex).coerceAtLeast(0)

    /**
     * Handles for a face assumed square-on and centred — a starting point for the athlete to drag.
     *
     * A circle in the photograph is an ellipse in normalized coordinates whenever the frame is not
     * square, so the two radii are derived separately from the bitmap's own dimensions. The size is
     * a guess at a target that fills the frame; it is meant to be wrong by a nudge, not right.
     */
    private fun seedCalibration(
        photo: Bitmap,
        layout: FaceLayout,
    ): FaceCalibration {
        val radiusPx = 0.36 * min(photo.width, photo.height)
        return FaceCalibration.ellipse(
            centre = ImagePoint(0.5, 0.5),
            radiusX = radiusPx / photo.width,
            radiusY = radiusPx / photo.height,
            boundaryScore = FaceCalibration.defaultBoundaryScore(layout),
        )
    }

    fun undo() {
        val id = _state.value.snapshot?.session?.id ?: return
        action {
            val s = repo.undo(id)
            return@action { copy(snapshot = s) }
        }
    }

    fun setOpponentEndTotal(e: Int, t: Int) {
        val id = _state.value.snapshot?.session?.id ?: return
        action {
            val s = repo.setOpponentEndTotal(id, e, t)
            return@action { copy(snapshot = s) }
        }
    }

    fun setShootOffWinner(w: SetMatchSummary.Winner) {
        val id = _state.value.snapshot?.session?.id ?: return
        action {
            val s = repo.setShootOffWinner(id, w)
            return@action { copy(snapshot = s) }
        }
    }

    /**
     * Load the account of what deleting this scorecard would remove, so the confirmation can state
     * it. Never a step towards deleting on its own — [deleteScorecard] is a separate, later call.
     */
    fun previewDeletion() {
        val id = _state.value.snapshot?.session?.id ?: return
        viewModelScope.launch {
            try {
                val p = withContext(Dispatchers.IO) { repo.previewScorecardDeletion(id) }
                _state.update { it.copy(deletionPreview = p) }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _state.update { it.copy(error = t.message ?: "Could not prepare scorecard deletion") }
            }
        }
    }

    fun dismissDeletion() {
        _state.update { it.copy(deletionPreview = null) }
    }

    /**
     * Erases the open scorecard and returns the screen to the list. Only ever called from the
     * confirmation the athlete accepted.
     */
    fun deleteScorecard() {
        val id = _state.value.snapshot?.session?.id ?: return
        action {
            repo.deleteScorecard(id)
            val recent = repo.recent(10)
            return@action {
                copy(
                    snapshot = null,
                    recent = recent,
                    deletionPreview = null,
                    completionMessage = null,
                )
            }
        }
    }

    /** Populate the link picker. Cheap, and only ever called when the athlete opens the dialog. */
    fun loadLinkableFormSessions() {
        viewModelScope.launch {
            try {
                val s = withContext(Dispatchers.IO) { repo.linkableFormSessions() }
                _state.update { it.copy(linkableFormSessions = s) }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _state.update { it.copy(error = t.message ?: "Could not load training sessions") }
            }
        }
    }

    /** [formSessionId] null detaches — the athlete can always take a link back off. */
    fun setLinkedFormSession(formSessionId: String?) {
        val id = _state.value.snapshot?.session?.id ?: return
        action {
            val s = repo.setLinkedFormSession(id, formSessionId)
            return@action { copy(snapshot = s) }
        }
    }

    fun togglePinned() {
        val id = _state.value.snapshot?.session?.id ?: return
        action {
            val s = repo.togglePinned(id)
            return@action { copy(snapshot = s) }
        }
    }

    fun updateContext(sight: String?, venue: String?, conditions: String?, intent: String?) {
        val id = _state.value.snapshot?.session?.id ?: return
        action {
            val s = repo.updateContext(id, sight, venue, conditions, intent)
            return@action { copy(snapshot = s) }
        }
    }

    fun finish() {
        val cur = _state.value.snapshot ?: return
        action {
            val (f, pb) = repo.finish(cur.session.id)
            val c = f.card
            val msg =
                when {
                    c.round.scoringKind == ScoringKind.SET_MATCH -> {
                        val s = c.setMatchSummary()
                        if (s?.winner != null)
                            "Match finished · ${s.athleteSetPoints}–${s.opponentSetPoints} set points"
                        else
                            "Match saved · ${s?.athleteSetPoints?:0}–${s?.opponentSetPoints?:0} set points"
                    }
                    c.isComplete() && (pb == null || c.total > pb) -> "New PB · ${c.total}"
                    c.isComplete() -> "Round finished · ${c.total}"
                    else -> "Practice saved · ${c.total}"
                }
            return@action { copy(snapshot = f, completionMessage = msg) }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Run one scoring mutation off the main thread and fold its result into whatever state is
     * current when it lands.
     *
     * [block] returns a *patch* rather than a finished state on purpose. The previous version built
     * `_state.value.copy(...)` inside the block, which captured the state as it was before the IO
     * and then wrote it back wholesale — so anything the athlete changed while the write was in
     * flight (switching input mode, dismissing an error) was silently reverted. Applying the patch
     * through [MutableStateFlow.update] keeps those concurrent edits.
     *
     * The [ScoringUiState.saving] flag still serialises actions: viewModelScope dispatches on
     * Main.immediate, so the flag is set synchronously before the first suspension point.
     */
    private fun action(block: suspend () -> (ScoringUiState.() -> ScoringUiState)) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val patch = withContext(Dispatchers.IO) { block() }
                _state.update { it.patch().copy(saving = false, loading = false, error = null) }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _state.update {
                    it.copy(
                        saving = false,
                        loading = false,
                        error = t.message ?: "Scoring action failed",
                    )
                }
            }
        }
    }

    private companion object {
        /** Stable id for a custom practice round, so repeats of the same shape share PB history. */
        fun customRoundId(distanceM: Int, faceCm: Int, arrowsPerEnd: Int, endCount: Int) =
            "custom.${distanceM}m.${faceCm}cm.${arrowsPerEnd}x$endCount"
    }
}
