package xyz.mdhv.formanalyser.app.domain

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import xyz.mdhv.formanalyser.app.data.AppPrefs
import xyz.mdhv.formanalyser.app.data.AthleteEntity
import xyz.mdhv.formanalyser.app.data.Repository
import xyz.mdhv.formanalyser.app.data.RigEntity
import xyz.mdhv.formanalyser.app.data.SessionEntity
import xyz.mdhv.formanalyser.equipment.PoundageSource
import xyz.mdhv.formanalyser.wellness.ReadinessResult
import xyz.mdhv.formanalyser.wellness.StreakState

/** Home landing state (Phases 1–2). Manual refresh, matching the app's incumbent VM style. */
class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    private val prefs = AppPrefs(app)
    private val assembler = WellnessAssembler(repo)

    private val _athlete = MutableStateFlow<AthleteEntity?>(null)
    val athlete: StateFlow<AthleteEntity?> = _athlete

    private val _activeRig = MutableStateFlow<RigEntity?>(null)
    val activeRig: StateFlow<RigEntity?> = _activeRig

    private val _recent = MutableStateFlow<List<SessionEntity>>(emptyList())
    val recent: StateFlow<List<SessionEntity>> = _recent

    private val _readiness = MutableStateFlow<ReadinessResult?>(null)
    val readiness: StateFlow<ReadinessResult?> = _readiness

    private val _streak = MutableStateFlow<StreakState?>(null)
    val streak: StateFlow<StreakState?> = _streak

    private val _activeInjuryCount = MutableStateFlow(0)
    val activeInjuryCount: StateFlow<Int> = _activeInjuryCount

    fun load() {
        viewModelScope.launch {
            val a = withContext(Dispatchers.IO) { repo.currentAthlete() }
            _athlete.value = a
            if (a != null) {
                _activeRig.value = withContext(Dispatchers.IO) { repo.activeRig(a.id) }
                _recent.value = withContext(Dispatchers.IO) { repo.recentSessions(a.id, 5) }
                _readiness.value = withContext(Dispatchers.IO) { assembler.readiness(a.id) }
                val planned = prefs.plannedRestDays.first()
                _streak.value = withContext(Dispatchers.IO) { assembler.streak(a.id, planned) }
                _activeInjuryCount.value = withContext(Dispatchers.IO) { repo.body.activeInjuries(a.id).size }
            }
        }
    }

    /** "≈ 36 lbs" / "40 lbs" label for the active rig, or null. */
    fun rigLabel(): String? {
        val rig = _activeRig.value ?: return null
        val ep = Tuning.effectivePoundage(rig, _athlete.value?.drawLengthMm) ?: return rig.name
        val prefix = if (ep.source == PoundageSource.ESTIMATED) "≈ " else ""
        return "${rig.name} · $prefix${"%.0f".format(ep.lbs)} lbs"
    }
}
