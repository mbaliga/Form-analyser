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
import xyz.mdhv.formanalyser.model.BowType
import xyz.mdhv.formanalyser.model.Handedness
import java.util.UUID

/** Rigs + athlete-profile editing (Settings). Manual refresh style. */
class RigsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)

    private val _athlete = MutableStateFlow<AthleteEntity?>(null)
    val athlete: StateFlow<AthleteEntity?> = _athlete

    private val _rigs = MutableStateFlow<List<RigEntity>>(emptyList())
    val rigs: StateFlow<List<RigEntity>> = _rigs

    fun load() {
        viewModelScope.launch {
            val a = withContext(Dispatchers.IO) { repo.currentAthlete() }
            _athlete.value = a
            if (a != null) _rigs.value = withContext(Dispatchers.IO) { repo.rigsOnce(a.id) }
        }
    }

    fun updateProfile(name: String, club: String?, handedness: Handedness, drawLengthMm: Int?) {
        val a = _athlete.value ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.updateAthlete(
                    a.copy(
                        displayName = name.trim().ifBlank { a.displayName },
                        club = club?.trim()?.ifBlank { null },
                        handedness = handedness.name,
                        drawLengthMm = drawLengthMm,
                    ),
                )
            }
            load()
        }
    }

    fun saveRig(existingId: String?, name: String, bowType: BowType, tuningJson: String) {
        val a = _athlete.value ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val isFirst = repo.rigCount(a.id) == 0
                repo.upsertRig(
                    RigEntity(
                        id = existingId ?: UUID.randomUUID().toString(),
                        athleteId = a.id,
                        name = name.ifBlank { "My bow" },
                        bowType = bowType.name,
                        tuningJson = tuningJson,
                        active = isFirst || (existingId != null && _rigs.value.firstOrNull { it.id == existingId }?.active == true),
                        createdAt = _rigs.value.firstOrNull { it.id == existingId }?.createdAt ?: System.currentTimeMillis(),
                    ),
                )
            }
            load()
        }
    }

    fun activate(rigId: String) {
        val a = _athlete.value ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.setActiveRig(a.id, rigId) }
            load()
        }
    }

    /** Returns false (and does nothing) if the rig is active or the last one. */
    fun delete(rig: RigEntity, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { repo.deleteRig(rig) }
            if (ok) load()
            onResult(ok)
        }
    }
}
