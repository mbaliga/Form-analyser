package xyz.mdhv.formanalyser.app.domain

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
                    _state.value =
                        _state.value.copy(snapshot = a, recent = r, loading = false, error = null)
                }
                .onFailure {
                    _state.value =
                        _state.value.copy(
                            loading = false,
                            error = it.message ?: "Could not load scoring",
                        )
                }
        }
    }

    fun quickStart() = action {
        _state.value.copy(snapshot = repo.quickStart(), completionMessage = null)
    }

    fun openScorecard(id: String) = action {
        _state.value.copy(snapshot = repo.snapshot(id), completionMessage = null)
    }

    fun startBuiltIn(id: String) = action {
        _state.value.copy(
            snapshot = repo.start(RoundPack.byId(id) ?: error("Unknown round: $id")),
            completionMessage = null,
        )
    }

    fun startCustom(
        name: String,
        distanceMeters: Int,
        targetFaceCm: Int,
        arrowsPerEnd: Int,
        endCount: Int,
    ) = action {
        _state.value.copy(
            snapshot =
                repo.start(
                    RoundPack.customPractice(
                        "custom.${java.util.UUID.randomUUID()}",
                        name.ifBlank { "Custom practice" },
                        distanceMeters,
                        targetFaceCm,
                        arrowsPerEnd,
                        endCount,
                    )
                ),
            completionMessage = null,
        )
    }

    fun setInputMode(mode: ScoringInputMode) {
        _state.value = _state.value.copy(inputMode = mode)
        if (mode == ScoringInputMode.END_SCAN) loadEndScanCandidates()
    }

    fun recordToken(token: String) {
        val id = _state.value.snapshot?.session?.id ?: return
        val score =
            runCatching { ScoreInput.parse(token) }
                .getOrElse {
                    _state.value = _state.value.copy(error = it.message)
                    return
                }
        action { _state.value.copy(snapshot = repo.recordNumeric(id, score)) }
    }

    fun recordPlot(p: PlotPoint) {
        val id = _state.value.snapshot?.session?.id ?: return
        action { _state.value.copy(snapshot = repo.recordPlot(id, p)) }
    }

    fun recordObserverToken(token: String, sector: String? = null) {
        val id = _state.value.snapshot?.session?.id ?: return
        val s =
            runCatching { ScoreInput.parse(token) }
                .getOrElse {
                    _state.value = _state.value.copy(error = it.message)
                    return
                }
        action { _state.value.copy(snapshot = repo.recordObserverTap(id, s.points, s.isX, sector)) }
    }

    fun loadEndScanCandidates() {
        val s = _state.value.snapshot ?: return
        viewModelScope.launch {
            _state.value =
                _state.value.copy(
                    endScanCandidates =
                        withContext(Dispatchers.IO) {
                            repo.endScanCandidates(s.session.id, s.card.currentEndIndex)
                        }
                )
        }
    }

    fun acceptDetectedCandidates(c: List<ScoringRepository.EndScanCandidate>) {
        val s = _state.value.snapshot ?: return
        viewModelScope.launch {
            _state.value =
                _state.value.copy(
                    endScanCandidates =
                        withContext(Dispatchers.IO) {
                            repo.proposeEndScanCandidates(s.session.id, s.card.currentEndIndex, c)
                        }
                )
        }
    }

    fun confirmEndScanCandidate(cid: String) {
        val s = _state.value.snapshot ?: return
        action {
            val n = repo.confirmEndScanCandidate(cid, s.session.id, s.card.currentEndIndex)
            _state.value.copy(
                snapshot = n,
                endScanCandidates = repo.endScanCandidates(s.session.id, n.card.currentEndIndex),
            )
        }
    }

    fun rejectEndScanCandidate(cid: String) {
        val s = _state.value.snapshot ?: return
        action {
            repo.rejectEndScanCandidate(cid)
            _state.value.copy(
                endScanCandidates = repo.endScanCandidates(s.session.id, s.card.currentEndIndex)
            )
        }
    }

    fun undo() {
        val id = _state.value.snapshot?.session?.id ?: return
        action { _state.value.copy(snapshot = repo.undo(id)) }
    }

    fun setOpponentEndTotal(e: Int, t: Int) {
        val id = _state.value.snapshot?.session?.id ?: return
        action { _state.value.copy(snapshot = repo.setOpponentEndTotal(id, e, t)) }
    }

    fun setShootOffWinner(w: SetMatchSummary.Winner) {
        val id = _state.value.snapshot?.session?.id ?: return
        action { _state.value.copy(snapshot = repo.setShootOffWinner(id, w)) }
    }

    fun togglePinned() {
        val id = _state.value.snapshot?.session?.id ?: return
        action { _state.value.copy(snapshot = repo.togglePinned(id)) }
    }

    fun updateContext(sight: String?, venue: String?, conditions: String?, intent: String?) {
        val id = _state.value.snapshot?.session?.id ?: return
        action {
            _state.value.copy(snapshot = repo.updateContext(id, sight, venue, conditions, intent))
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
            _state.value.copy(snapshot = f, completionMessage = msg)
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun action(block: suspend () -> ScoringUiState) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null)
            try {
                val n = withContext(Dispatchers.IO) { block() }
                _state.value = n.copy(saving = false, loading = false, error = null)
            } catch (t: Throwable) {
                _state.value =
                    _state.value.copy(
                        saving = false,
                        loading = false,
                        error = t.message ?: "Scoring action failed",
                    )
            }
        }
    }
}
