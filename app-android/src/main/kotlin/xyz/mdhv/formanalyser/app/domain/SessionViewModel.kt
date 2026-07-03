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
import xyz.mdhv.formanalyser.archery.EffectiveHandedness
import xyz.mdhv.formanalyser.archery.HandednessNormalizer
import xyz.mdhv.formanalyser.app.capture.PoseRecorder
import xyz.mdhv.formanalyser.app.data.CheckinEntity
import xyz.mdhv.formanalyser.app.data.Repository
import xyz.mdhv.formanalyser.app.data.RigEntity
import xyz.mdhv.formanalyser.app.data.SessionEntity
import xyz.mdhv.formanalyser.app.data.ShotEntity
import xyz.mdhv.formanalyser.app.data.SorenessEntity
import xyz.mdhv.formanalyser.model.Handedness
import xyz.mdhv.formanalyser.wellness.DurationModel
import java.util.UUID

/** Pre-check-in data from the gate sheet (≤15 s by construction). Skips are recorded. */
data class PreCheckinData(
    val skipped: Boolean,
    val energy: Int? = null,
    val sleep: Int? = null,
    val motivation: Int? = null,
    val sorenessRegionIds: List<String> = emptyList(),
    val note: String? = null,
)

/** Pending post-check-in state after a capture stops (drives the post sheet). */
data class PostPending(val durationAutoS: Int, val detectedArrows: Int)

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
    val recorder = PoseRecorder(app)

    val liveTracking: StateFlow<Boolean> get() = recorder.liveTracking
    val liveBowArmAngle: StateFlow<Double?> get() = recorder.liveBowArmAngle

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

    private val _activeRig = MutableStateFlow<RigEntity?>(null)
    val activeRig: StateFlow<RigEntity?> = _activeRig

    private val _athleteHandedness = MutableStateFlow(Handedness.RH)
    val athleteHandedness: StateFlow<Handedness> = _athleteHandedness

    private val _postPending = MutableStateFlow<PostPending?>(null)
    val postPending: StateFlow<PostPending?> = _postPending

    private var currentSessionId: String? = null
    private var currentHandednessOverride: Handedness? = null

    init {
        viewModelScope.launch {
            val athlete = withContext(Dispatchers.IO) {
                repo.ensureAthlete(UUID.randomUUID().toString(), "Athlete", bodyMassKg = 70.0)
            }
            _athleteName.value = athlete.displayName
            _athleteHandedness.value = Handedness.fromStorage(athlete.handedness)
            _activeRig.value = withContext(Dispatchers.IO) { repo.activeRig(athlete.id) }
        }
    }

    fun refreshActiveRig() {
        viewModelScope.launch {
            val athlete = withContext(Dispatchers.IO) { repo.currentAthlete() } ?: return@launch
            _activeRig.value = withContext(Dispatchers.IO) { repo.activeRig(athlete.id) }
        }
    }

    /** Start a session from the athlete's active rig; legacy draw-weight is written from its
     *  effective poundage (measured>estimated>marked) for compatibility. The pre-check-in (or its
     *  recorded skip) is written first and linked (Phase 2 §D). */
    fun startSession(distanceMeters: Int, handednessOverride: Handedness? = null, pre: PreCheckinData? = null) {
        viewModelScope.launch {
            val athlete = withContext(Dispatchers.IO) { repo.currentAthlete() } ?: return@launch
            val rig = withContext(Dispatchers.IO) { repo.activeRig(athlete.id) }
            _activeRig.value = rig
            val poundage = rig?.let { Tuning.effectivePoundage(it, athlete.drawLengthMm)?.lbs } ?: 0.0
            currentHandednessOverride = handednessOverride
            val sid = UUID.randomUUID().toString()
            withContext(Dispatchers.IO) {
                val preId = pre?.let { p ->
                    val cid = UUID.randomUUID().toString()
                    repo.wellness.insertCheckin(
                        CheckinEntity(
                            id = cid, athleteId = athlete.id, ts = System.currentTimeMillis(),
                            kind = "PRE", skipped = p.skipped,
                            energy = p.energy, sleep = p.sleep, motivation = p.motivation, note = p.note,
                        ),
                    )
                    if (p.sorenessRegionIds.isNotEmpty()) {
                        repo.wellness.insertSoreness(p.sorenessRegionIds.distinct().map { SorenessEntity(cid, it) })
                    }
                    cid
                }
                repo.createSession(
                    SessionEntity(
                        id = sid,
                        athleteId = athlete.id,
                        startedAtEpochMs = System.currentTimeMillis(),
                        drawWeightLbs = poundage,
                        distanceMeters = distanceMeters,
                        rigId = rig?.id,
                        handednessOverride = handednessOverride?.name,
                        preCheckinId = preId,
                    )
                )
            }
            currentSessionId = sid
            _sessionActive.value = true
            _shots.value = emptyList()
            _postPending.value = null
            refresh()
        }
    }

    /** Reopen an existing session (e.g. from Home's recent list) into Review. */
    fun openSession(sessionId: String) {
        currentSessionId = sessionId
        currentHandednessOverride = null
        _sessionActive.value = true
        viewModelScope.launch { refresh() }
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
                // Single handedness normalization point: mirror LH captures into the canonical RH
                // frame before the segmenter/extractor touch them (Phase 1 §B).
                val handedness = EffectiveHandedness.resolve(
                    Handedness.fromStorage(athlete.handedness),
                    sessionOverride = currentHandednessOverride,
                )
                val normalized = HandednessNormalizer.normalize(window, handedness)
                val analysis = ArcheryAnalyzer.analyzeWithSpans(normalized)
                val offset = repo.shotsOnce(sid).size
                val entities = analysis.features.mapIndexed { i, f ->
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
                // Idle-trimmed auto duration (Phase 2 §A4) → drives the post-check-in sheet.
                val duration = DurationModel.auto(analysis.spans, analysis.recordingSeconds)
                val detected = repo.shotsOnce(sid).size
                _postPending.value = PostPending(
                    durationAutoS = duration.seconds.toInt(),
                    detectedArrows = detected,
                )
            }
            refresh()
        }
    }

    /** Save the post-check-in and close out the session row (duration + arrow reconciliation). */
    fun savePostCheckin(rpe: Double?, feel: Int?, durationOverrideS: Int?, arrowsActual: Int?) {
        val sid = currentSessionId ?: return
        val pending = _postPending.value
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val athlete = repo.currentAthlete() ?: return@withContext
                val cid = UUID.randomUUID().toString()
                repo.wellness.insertCheckin(
                    CheckinEntity(
                        id = cid, athleteId = athlete.id, ts = System.currentTimeMillis(),
                        kind = "POST", skipped = false, rpe = rpe, feel = feel,
                    ),
                )
                val auto = pending?.durationAutoS
                repo.finishSession(
                    sessionId = sid,
                    postCheckinId = cid,
                    durationAutoS = auto,
                    durationS = durationOverrideS ?: auto,
                    arrowsActual = arrowsActual ?: pending?.detectedArrows,
                )
            }
            _postPending.value = null
        }
    }

    /** Skip the post sheet: still persist auto duration + detected arrows, no checkin row. */
    fun skipPostCheckin() {
        val sid = currentSessionId ?: return
        val pending = _postPending.value ?: run { _postPending.value = null; return }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.finishSession(sid, null, pending.durationAutoS, pending.durationAutoS, pending.detectedArrows)
            }
            _postPending.value = null
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
        recorder.close()
        super.onCleared()
    }
}
