package xyz.mdhv.formanalyser.app.domain

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mdhv.formanalyser.app.data.AthleteEntity
import xyz.mdhv.formanalyser.app.data.Repository
import xyz.mdhv.formanalyser.app.data.RigEntity
import xyz.mdhv.formanalyser.app.data.SessionEntity
import xyz.mdhv.formanalyser.equipment.PoundageSource

/** Home landing state (Phase 1 §7). Manual refresh, matching the app's incumbent VM style. */
class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)

    private val _athlete = MutableStateFlow<AthleteEntity?>(null)
    val athlete: StateFlow<AthleteEntity?> = _athlete

    private val _activeRig = MutableStateFlow<RigEntity?>(null)
    val activeRig: StateFlow<RigEntity?> = _activeRig

    private val _recent = MutableStateFlow<List<SessionEntity>>(emptyList())
    val recent: StateFlow<List<SessionEntity>> = _recent

    fun load() {
        viewModelScope.launch {
            val a = withContext(Dispatchers.IO) { repo.currentAthlete() }
            _athlete.value = a
            if (a != null) {
                _activeRig.value = withContext(Dispatchers.IO) { repo.activeRig(a.id) }
                _recent.value = withContext(Dispatchers.IO) { repo.recentSessions(a.id, 5) }
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
