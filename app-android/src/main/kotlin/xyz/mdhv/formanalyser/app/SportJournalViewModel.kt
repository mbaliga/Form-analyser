package xyz.mdhv.formanalyser.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import xyz.mdhv.formanalyser.app.data.AppDatabase
import xyz.mdhv.formanalyser.app.data.SportSessionEntity

data class SportJournalState(
    val sessions: List<SportSessionEntity> = emptyList(),
    val saving: Boolean = false,
    val message: String? = null,
)

/** Local journal for manually recorded sport sessions; it does not infer technique from video. */
class SportJournalViewModel(app: Application) : AndroidViewModel(app) {
    private val application = app
    private val stateHolder = MutableStateFlow(SportJournalState())
    val state: StateFlow<SportJournalState> = stateHolder
    private var observeJob: Job? = null

    fun observe(sportId: String) {
        require(sportId in SUPPORTED_SPORTS)
        if (observeJob?.isActive == true && observedSportId == sportId) return
        observeJob?.cancel()
        observedSportId = sportId
        observeJob = viewModelScope.launch {
            val (database, athlete) = withContext(Dispatchers.IO) {
                val db = AppDatabase.get(application)
                db to db.athleteDao().firstOrNull()
            }
            if (athlete == null) {
                stateHolder.value = stateHolder.value.copy(message = "Finish athlete setup to save sessions.")
                return@launch
            }
            database.sportSessionDao().recentForSport(athlete.id, sportId).collectLatest { rows ->
                stateHolder.value = stateHolder.value.copy(sessions = rows)
            }
        }
    }

    fun save(
        sportId: String,
        durationSeconds: Int,
        perceivedEffortRpe: Int?,
        summary: String,
        detailsJson: String,
        features: Map<String, Double>,
    ) {
        require(sportId in SUPPORTED_SPORTS)
        require(durationSeconds in 0..86_400)
        require(perceivedEffortRpe == null || perceivedEffortRpe in 1..10)
        val cleanSummary = summary.trim().take(MAX_SUMMARY_LENGTH)
        require(cleanSummary.isNotBlank())
        val details = Json.parseToJsonElement(detailsJson)
        require(details is JsonObject) { "session details must be a JSON object" }
        require(detailsJson.length <= MAX_JSON_LENGTH)
        require(features.size <= MAX_FEATURES && features.values.all { it.isFinite() })
        stateHolder.value = stateHolder.value.copy(saving = true, message = null)
        viewModelScope.launch {
            val outcome = runCatching {
                val featureJson = JsonObject(features.mapValues { kotlinx.serialization.json.JsonPrimitive(it.value) }).toString()
                withContext(Dispatchers.IO) {
                    val database = AppDatabase.get(application)
                    val athlete = database.athleteDao().firstOrNull()
                        ?: error("Finish athlete setup before recording a session.")
                    database.sportSessionDao().insert(
                        SportSessionEntity(
                            id = UUID.randomUUID().toString(),
                            athleteId = athlete.id,
                            sportId = sportId,
                            startedAtEpochMs = System.currentTimeMillis(),
                            durationSeconds = durationSeconds,
                            perceivedEffortRpe = perceivedEffortRpe,
                            summary = cleanSummary,
                            detailsJson = details.toString(),
                            featuresJson = featureJson,
                        )
                    )
                }
            }
            stateHolder.value = stateHolder.value.copy(
                saving = false,
                message = outcome.fold({ "Session saved on this device." }, { it.message ?: "Session could not be saved." }),
            )
        }
    }

    private var observedSportId: String? = null

    private companion object {
        val SUPPORTED_SPORTS = setOf("muay_thai", "bouldering")
        const val MAX_SUMMARY_LENGTH = 120
        const val MAX_JSON_LENGTH = 16_384
        const val MAX_FEATURES = 64
    }
}
