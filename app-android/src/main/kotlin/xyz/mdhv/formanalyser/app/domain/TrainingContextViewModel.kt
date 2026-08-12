package xyz.mdhv.formanalyser.app.domain

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mdhv.formanalyser.app.data.AthleteFeatureRepository
import xyz.mdhv.formanalyser.athlete.SessionDefaults

class TrainingContextViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AthleteFeatureRepository(app)
    private val _defaults = MutableStateFlow<SessionDefaults?>(null)
    val defaults: StateFlow<SessionDefaults?> = _defaults
    fun load() { viewModelScope.launch { _defaults.value = withContext(Dispatchers.IO) { repo.smartDefaults() } } }
    fun remember(defaults: SessionDefaults, pinSetup: Boolean) { viewModelScope.launch { withContext(Dispatchers.IO) { val pins = if (pinSetup) setOf("discipline", "rig", "venue", "distance", "target", "arrows", "round", "intent") else emptySet(); repo.saveDefaults(defaults, pins) }; _defaults.value = defaults } }
}
