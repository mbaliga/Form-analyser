package xyz.mdhv.formanalyser.app.domain

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mdhv.formanalyser.app.data.ScoreCandidateEntity
import xyz.mdhv.formanalyser.app.data.ScoreSessionEntity
import xyz.mdhv.formanalyser.app.data.ScoringRepository
import xyz.mdhv.formanalyser.scoring.PlotPoint
import xyz.mdhv.formanalyser.scoring.RoundPack
import xyz.mdhv.formanalyser.scoring.ScoreInput
import xyz.mdhv.formanalyser.scoring.ScoringKind
import xyz.mdhv.formanalyser.scoring.SetMatchSummary

enum class ScoringInputMode {
    NUMBERS,
    PLOT,
    OBSERVER,
    END_SCAN,
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
)

class ScoringViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ScoringRepository(app)
    private val _state = MutableStateFlow(ScoringUiState())
    val state: StateFlow<ScoringUiState> = _state.asStateFlow()

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
        val s = repo.quickStart() { copy(snapshot = s, completionMessage = null) }
    }

    fun openScorecard(id: String) = action {
        val s = repo.snapshot(id) { copy(snapshot = s, completionMessage = null) }
    }

    fun startBuiltIn(id: String) = action {
        val s =
            repo.start(RoundPack.byId(id) ?: error("Unknown round: $id")) {
                copy(snapshot = s, completionMessage = null)
            }
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
            ) {
                copy(snapshot = s, completionMessage = null)
            }
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
            val s = repo.recordNumeric(id, score) { copy(snapshot = s) }
        }
    }

    fun recordPlot(p: PlotPoint) {
        val id = _state.value.snapshot?.session?.id ?: return
        action {
            val s = repo.recordPlot(id, p) { copy(snapshot = s) }
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
            val n = repo.recordObserverTap(id, s.points, s.isX, sector) { copy(snapshot = n) }
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
            val c =
                repo.endScanCandidates(s.session.id, n.card.currentEndIndex) {
                    copy(snapshot = n, endScanCandidates = c)
                }
        }
    }

    fun rejectEndScanCandidate(cid: String) {
        val s = _state.value.snapshot ?: return
        action {
            repo.rejectEndScanCandidate(cid)
            val c =
                repo.endScanCandidates(s.session.id, s.card.currentEndIndex) {
                    copy(endScanCandidates = c)
                }
        }
    }

    fun undo() {
        val id = _state.value.snapshot?.session?.id ?: return
        action {
            val s = repo.undo(id) { copy(snapshot = s) }
        }
    }

    fun setOpponentEndTotal(e: Int, t: Int) {
        val id = _state.value.snapshot?.session?.id ?: return
        action {
            val s = repo.setOpponentEndTotal(id, e, t) { copy(snapshot = s) }
        }
    }

    fun setShootOffWinner(w: SetMatchSummary.Winner) {
        val id = _state.value.snapshot?.session?.id ?: return
        action {
            val s = repo.setShootOffWinner(id, w) { copy(snapshot = s) }
        }
    }

    fun togglePinned() {
        val id = _state.value.snapshot?.session?.id ?: return
        action {
            val s = repo.togglePinned(id) { copy(snapshot = s) }
        }
    }

    fun updateContext(sight: String?, venue: String?, conditions: String?, intent: String?) {
        val id = _state.value.snapshot?.session?.id ?: return
        action {
            val s = repo.updateContext(id, sight, venue, conditions, intent) { copy(snapshot = s) }
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
            { copy(snapshot = f, completionMessage = msg) }
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
