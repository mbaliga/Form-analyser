package xyz.mdhv.formanalyser.app.domain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mdhv.formanalyser.app.data.AthleteFeatureRepository
import xyz.mdhv.formanalyser.athlete.SessionDefaults

@HiltViewModel
class TrainingContextViewModel @Inject constructor(
    private val repo: AthleteFeatureRepository,
) : ViewModel() {
    private val _defaults = MutableStateFlow<SessionDefaults?>(null)
    val defaults: StateFlow<SessionDefaults?> = _defaults
    fun load() { viewModelScope.launch { _defaults.value = withContext(Dispatchers.IO) { repo.smartDefaults() } } }
    fun remember(defaults: SessionDefaults, pinSetup: Boolean) { viewModelScope.launch { withContext(Dispatchers.IO) { val pins = if (pinSetup) setOf("discipline", "rig", "venue", "distance", "target", "arrows", "round", "intent") else emptySet(); repo.saveDefaults(defaults, pins) }; _defaults.value = defaults } }
}
