package xyz.mdhv.formanalyser.app.domain

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mdhv.baseline.engine.model.FeatureVector
import xyz.mdhv.baseline.engine.sport.FeatureScoreRelation
import xyz.mdhv.baseline.engine.fatigue.FatigueTrajectory
import xyz.mdhv.formanalyser.app.capture.ImuRecorder
import xyz.mdhv.formanalyser.app.data.Repository
import xyz.mdhv.formanalyser.app.data.SessionEntity
import xyz.mdhv.formanalyser.app.data.ShotEntity
import java.util.UUID

/** A captured shot as the UI sees it: features + outcome + how far it deviates from baseline. */
data class ShotView(
    val id: String,
    val index: Int,
    val features: FeatureVector,
    val score: Double?,
    val stability: Double?,        // null until a baseline is ready
    val topDeviationFeature: String?,
    val isBaseline: Boolean,
)

data class BaselineInfo(val ready: Boolean, val repCount: Long) {
    val needed: Long get() = (8L - repCount).coerceAtLeast(0)
}

/**
 * Drives the IMU-first MVP loop (handoff §11): capture → segment → features → baseline →
 * deviation → fatigue + signal↔score. Uses manual refresh after each mutation (simple and
 * predictable) rather than reactive Room Flows.
 */
class SessionViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    val recorder = ImuRecorder(app)

    val liveAngularSpeed: StateFlow<Double> get() = recorder.liveAngularSpeed

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _sessionActive = MutableStateFlow(false)
    val sessionActive: StateFlow<Boolean> = _sessionActive

    private val _shots = MutableStateFlow<List<ShotView>>(emptyList())
    val shots: StateFlow<List<ShotView>> = _shots

    private val _baseline = MutableStateFlow(BaselineInfo(false, 0))
    val baseline: StateFlow<BaselineInfo> = _baseline

    private val _fatigue = MutableStateFlow<FatigueTrajectory?>(null)
    val fatigue: StateFlow<FatigueTrajectory?> = _fatigue

    private val _correlations = MutableStateFlow<List<FeatureScoreRelation>>(emptyList())
    val correlations: StateFlow<List<FeatureScoreRelation>> = _correlations

    private val _athleteName = MutableStateFlow("Athlete")
    val athleteName: StateFlow<String> = _athleteName

    private var currentSessionId: String? = null

    init {
        viewModelScope.launch {
            val athlete = withContext(Dispatchers.IO) {
                repo.ensureAthlete(UUID.randomUUID().toString(), "Athlete", bodyMassKg = 70.0)
            }
            _athleteName.value = athlete.displayName
        }
    }

    fun startSession(drawWeightLbs: Double, distanceMeters: Int) {
        viewModelScope.launch {
            val athlete = withContext(Dispatchers.IO) { repo.currentAthlete() } ?: return@launch
            val sid = UUID.randomUUID().toString()
            withContext(Dispatchers.IO) {
                repo.createSession(
                    SessionEntity(
                        id = sid,
                        athleteId = athlete.id,
                        startedAtEpochMs = System.currentTimeMillis(),
                        drawWeightLbs = drawWeightLbs,
                        distanceMeters = distanceMeters,
                    )
                )
            }
            currentSessionId = sid
            _sessionActive.value = true
            _shots.value = emptyList()
            refresh()
        }
    }

    fun startRecording() {
        if (!recorder.isAvailable || _isRecording.value) return
        recorder.start()
        _isRecording.value = true
    }

    /** Stop capture, segment the window into shots, persist them, and refresh the analysis. */
    fun stopRecordingAndAnalyze() {
        if (!_isRecording.value) return
        _isRecording.value = false
        val window = recorder.stop()
        val sid = currentSessionId ?: return
        if (window == null) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val athlete = repo.currentAthlete() ?: return@withContext
                val featuresList = ArcheryAnalyzer.analyze(window)
                val offset = repo.shotsOnce(sid).size
                val entities = featuresList.mapIndexed { i, f ->
                    ShotEntity(
                        id = UUID.randomUUID().toString(),
                        sessionId = sid,
                        athleteId = athlete.id,
                        indexInSession = offset + i,
                        featuresJson = ArcheryAnalyzer.featuresToJson(f),
                        score = null,
                        isBaseline = false,
                    )
                }
                repo.saveShots(entities)
            }
            refresh()
        }
    }

    fun setScore(shotId: String, score: Double?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.setScore(shotId, score) }
            refresh()
        }
    }

    fun toggleBaseline(shotId: String, isBaseline: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.setBaseline(shotId, isBaseline) }
            refresh()
        }
    }

    /** Recompute baseline, per-shot deviation, fatigue, and signal→score correlation. */
    private suspend fun refresh() {
        val sid = currentSessionId ?: return
        val computed = withContext(Dispatchers.IO) {
            val athlete = repo.currentAthlete() ?: return@withContext null
            val entities = repo.shotsOnce(sid)
            val baselineFeatures = repo.baselineShots(athlete.id)
                .map { ArcheryAnalyzer.featuresFromJson(it.featuresJson) }
            val model = ArcheryAnalyzer.buildBaseline(baselineFeatures)

            val views = entities.map { e ->
                val f = ArcheryAnalyzer.featuresFromJson(e.featuresJson)
                val dev = if (model.isReady()) ArcheryAnalyzer.score(model, f) else null
                ShotView(
                    id = e.id,
                    index = e.indexInSession,
                    features = f,
                    score = e.score,
                    stability = dev?.stability,
                    topDeviationFeature = dev?.topDeviation?.key,
                    isBaseline = e.isBaseline,
                )
            }
            val fatigue = ArcheryAnalyzer.fatigue(views.sortedBy { it.index }.map { it.features })
            val correlations = ArcheryAnalyzer.correlations(repo.scoredReps(athlete.id))
            Computed(views, BaselineInfo(model.isReady(), model.repCount), fatigue, correlations)
        } ?: return

        _shots.value = computed.shots
        _baseline.value = computed.baseline
        _fatigue.value = computed.fatigue
        _correlations.value = computed.correlations
    }

    private data class Computed(
        val shots: List<ShotView>,
        val baseline: BaselineInfo,
        val fatigue: FatigueTrajectory?,
        val correlations: List<FeatureScoreRelation>,
    )

    override fun onCleared() {
        if (_isRecording.value) recorder.stop()
        super.onCleared()
    }
}
