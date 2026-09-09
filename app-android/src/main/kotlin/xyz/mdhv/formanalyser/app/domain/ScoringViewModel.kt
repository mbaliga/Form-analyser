package xyz.mdhv.formanalyser.app.domain

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mdhv.formanalyser.app.capture.EndScanCapture
import xyz.mdhv.formanalyser.app.capture.ObserverVoice
import xyz.mdhv.formanalyser.app.data.ObserverScoreEventEntity
import xyz.mdhv.formanalyser.app.data.ScoreCandidateEntity
import xyz.mdhv.formanalyser.app.data.ScoreSessionEntity
import xyz.mdhv.formanalyser.app.data.ScoringRepository
import xyz.mdhv.formanalyser.app.data.SessionEntity
import xyz.mdhv.formanalyser.scoring.EndScan
import xyz.mdhv.formanalyser.scoring.FaceCalibration
import xyz.mdhv.formanalyser.scoring.FaceLayout
import xyz.mdhv.formanalyser.scoring.ImagePoint
import xyz.mdhv.formanalyser.scoring.ObserverSpeech
import xyz.mdhv.formanalyser.scoring.PlotPoint
import xyz.mdhv.formanalyser.scoring.RoundPack
import xyz.mdhv.formanalyser.scoring.ScoreInput
import xyz.mdhv.formanalyser.scoring.Scorecard
import xyz.mdhv.formanalyser.scoring.ScoringKind
import xyz.mdhv.formanalyser.scoring.SetMatchSummary

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

/**
 * Live Observer's microphone: one listen at a time, and its own feedback separate from the
 * scorecard's [ScoringUiState.error] because a rejected utterance is not a failure — it is the
 * grammar working as designed (reject over guess), and it must never pop the same error dialog a
 * genuine write failure does.
 */
data class VoiceUiState(
    /** What the mic button should offer right now; refreshed on entering the Observer tab. */
    val availability: ObserverVoice.Availability = ObserverVoice.Availability.NO_ON_DEVICE_RECOGNIZER,
    val listening: Boolean = false,
    /** The winning transcript from the last listen, accepted or rejected — shown verbatim. */
    val heard: String? = null,
    /** Non-null when the last listen was refused. Null (not this) is what "accepted" looks like. */
    val rejection: ObserverSpeech.RejectReason? = null,
    /** The last accepted arrow named only half a sector ("eight left") and it was dropped, not guessed. */
    val sectorDropped: Boolean = false,
)

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
    val voice: VoiceUiState = VoiceUiState(),
    /** Spoken arrows awaiting the human confirm the house invariant requires; never a tap's. */
    val pendingObserverEvents: List<ObserverScoreEventEntity> = emptyList(),
)

@HiltViewModel
class ScoringViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: ScoringRepository,
) : ViewModel() {
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
            runCatching { withContext(Dispatchers.IO) { repo.resumeActive() to repo.recent(10) } }
                .onSuccess { (a, r) ->
                    _state.update {
                        it.copy(snapshot = a, recent = r, loading = false, error = null)
                    }
                }
                .onFailure { t ->
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
        if (mode == ScoringInputMode.OBSERVER) {
            loadPendingObserverEvents()
            refreshVoiceAvailability()
        }
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

    // --- Live Observer: voice ------------------------------------------------------------------
    //
    // A transcription is machine output, so under the house invariant it must never become an
    // authoritative arrow on its own. Everything below only ever writes a *proposal*
    // (repo.proposeObserverSpoken) or turns pending proposals into arrows on an explicit confirm
    // (repo.confirmObserverEvents) — mirroring the End Scan propose/confirm/reject flow above
    // exactly. recordObserverToken (the tap path, above) is untouched: a tap already is the human
    // confirming it, so it keeps writing straight to score_arrow as it always has.

    /**
     * The microphone wrapper, created only when voice is first used. Most scoring sessions never
     * touch it at all, so there is no reason to hold a [SpeechRecognizer][android.speech.SpeechRecognizer]
     * open — or even instantiate the wrapper around one — for a screen that opened in NUMBERS mode
     * and stayed there.
     */
    private var observerVoice: ObserverVoice? = null

    private fun voice(): ObserverVoice =
        observerVoice ?: ObserverVoice(context).also { observerVoice = it }

    /**
     * Re-derive whether voice can be offered right now. Synchronous and cheap — a locale check, an
     * on-device-recogniser check and a permission check, none of which touch the microphone — so it
     * is safe to call on every entry to the Observer tab rather than caching whatever the answer was
     * on first screen-open: a language pack can finish downloading, or a permission can be revoked,
     * while the athlete is elsewhere in the app.
     */
    fun refreshVoiceAvailability() {
        val a = voice().availability()
        _state.update { it.copy(voice = it.voice.copy(availability = a)) }
    }

    /**
     * Start one listen. The Observer screen is expected to have already resolved
     * [android.Manifest.permission.RECORD_AUDIO] before calling this (matching [CaptureScreen]'s
     * camera pattern, not this class) — [ObserverVoice.start] still re-checks on its own, so a
     * revoke-while-backgrounded fails safely into [VoiceUiState.availability] rather than crashing.
     */
    fun startListening() {
        if (_state.value.voice.listening) return
        _state.update {
            it.copy(
                voice = it.voice.copy(listening = true, heard = null, rejection = null, sectorDropped = false)
            )
        }
        voice()
            .start(
                onState = { listening ->
                    _state.update { it.copy(voice = it.voice.copy(listening = listening)) }
                },
                onResult = ::handleVoiceResult,
                onUnavailable = { a ->
                    _state.update { it.copy(voice = it.voice.copy(listening = false, availability = a)) }
                },
            )
    }

    /** Stop the current listen without a result — e.g. the athlete leaving the tab mid-listen. */
    fun cancelListening() {
        observerVoice?.cancel()
        _state.update { it.copy(voice = it.voice.copy(listening = false)) }
    }

    /** Dismiss the "Heard: ..." feedback line so the next listen starts from a clean readout. */
    fun clearVoiceFeedback() {
        _state.update {
            it.copy(voice = it.voice.copy(heard = null, rejection = null, sectorDropped = false))
        }
    }

    /**
     * One listen finished. A [ObserverSpeech.Result.Rejected] writes nothing at all — no arrow, no
     * event row — only the feedback the athlete sees; an [ObserverSpeech.Result.Accepted] writes a
     * `PROPOSED` [ObserverScoreEventEntity] and nothing more, because voice never has the authority
     * a tap has.
     */
    private fun handleVoiceResult(result: ObserverSpeech.Result) {
        when (result) {
            is ObserverSpeech.Result.Rejected -> {
                _state.update {
                    it.copy(
                        voice =
                            it.voice.copy(
                                listening = false,
                                heard = result.heard,
                                rejection = result.reason,
                                sectorDropped = false,
                            )
                    )
                }
            }
            is ObserverSpeech.Result.Accepted -> {
                _state.update {
                    it.copy(
                        voice =
                            it.voice.copy(
                                listening = false,
                                heard = result.heard,
                                rejection = null,
                                sectorDropped = result.sectorDropped,
                            )
                    )
                }
                val sessionId = _state.value.snapshot?.session?.id ?: return
                // result.token is a ScoreInput keypad token by construction (see ObserverSpeech's
                // own KDoc), so this parse cannot fail in practice; runCatching only guards against
                // that contract ever drifting rather than a case expected to occur.
                val score = runCatching { ScoreInput.parse(result.token) }.getOrNull() ?: return
                viewModelScope.launch {
                    runCatching {
                            withContext(Dispatchers.IO) {
                                repo.proposeObserverSpoken(
                                    sessionId,
                                    score.points,
                                    score.isX,
                                    result.sector?.name,
                                    result.heard,
                                )
                            }
                        }
                        .onSuccess { pending -> _state.update { it.copy(pendingObserverEvents = pending) } }
                        .onFailure { t ->
                            _state.update { it.copy(error = t.message ?: "Could not save spoken arrow") }
                        }
                }
            }
        }
    }

    /** Populate the pending-confirm strip. Called on entering the Observer tab. */
    fun loadPendingObserverEvents() {
        val id = _state.value.snapshot?.session?.id ?: return
        viewModelScope.launch {
            val p = withContext(Dispatchers.IO) { repo.pendingObserverEvents(id) }
            _state.update { it.copy(pendingObserverEvents = p) }
        }
    }

    /** "Confirm all" (or one) spoken arrow — the only place voice becomes an authoritative score. */
    fun confirmSpokenArrows(eventIds: List<String>) {
        if (eventIds.isEmpty()) return
        val id = _state.value.snapshot?.session?.id ?: return
        action {
            val s = repo.confirmObserverEvents(id, eventIds)
            val pending = repo.pendingObserverEvents(id)
            return@action { copy(snapshot = s, pendingObserverEvents = pending) }
        }
    }

    /** Discard one spoken proposal — no arrow was ever written for it, so there is nothing to undo. */
    fun rejectSpokenArrow(eventId: String) {
        val id = _state.value.snapshot?.session?.id ?: return
        action {
            repo.rejectObserverEvent(eventId)
            val pending = repo.pendingObserverEvents(id)
            return@action { copy(pendingObserverEvents = pending) }
        }
    }

    fun loadEndScanCandidates() {
        val s = _state.value.snapshot ?: return
        viewModelScope.launch {
            val c =
                withContext(Dispatchers.IO) {
                    repo.endScanCandidates(s.session.id, s.card.currentEndIndex)
                }
            _state.update { it.copy(endScanCandidates = c) }
        }
    }

    fun acceptDetectedCandidates(c: List<ScoringRepository.EndScanCandidate>) {
        val s = _state.value.snapshot ?: return
        viewModelScope.launch {
            val proposed =
                withContext(Dispatchers.IO) {
                    repo.proposeEndScanCandidates(s.session.id, s.card.currentEndIndex, c)
                }
            _state.update { it.copy(endScanCandidates = proposed) }
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
            repo.rejectEndScanCandidate(cid)
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
    private fun arrowsOutstanding(card: Scorecard): Int {
        val end = card.currentEndIndex
        val recorded = card.arrows.count { it.endIndex == end }
        return (card.round.arrowsPerEnd - recorded).coerceAtLeast(0)
    }

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
            val p =
                runCatching { withContext(Dispatchers.IO) { repo.previewScorecardDeletion(id) } }
                    .getOrNull()
            _state.update { it.copy(deletionPreview = p) }
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
            val s = withContext(Dispatchers.IO) { repo.linkableFormSessions() }
            _state.update { it.copy(linkableFormSessions = s) }
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
     * Mirrors [SessionViewModel][xyz.mdhv.formanalyser.app.domain.SessionViewModel]'s
     * [PoseRecorder][xyz.mdhv.formanalyser.app.capture.PoseRecorder] teardown: release the
     * microphone recogniser if voice was ever used this session, otherwise this is a no-op.
     */
    override fun onCleared() {
        observerVoice?.close()
        super.onCleared()
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
