package xyz.mdhv.formanalyser.app.domain

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mdhv.crocodyl.engine.fatigue.FatigueTrajectory
import xyz.mdhv.crocodyl.engine.model.FeatureVector
import xyz.mdhv.crocodyl.engine.sport.FeatureScoreRelation
import xyz.mdhv.formanalyser.app.capture.PoseRecorder
import xyz.mdhv.formanalyser.app.data.*
import xyz.mdhv.formanalyser.archery.EffectiveHandedness
import xyz.mdhv.formanalyser.archery.HandednessNormalizer
import xyz.mdhv.formanalyser.athlete.SessionDefaults
import xyz.mdhv.formanalyser.model.Handedness
import xyz.mdhv.formanalyser.wellness.DurationModel

data class PreCheckinData(
    val skipped: Boolean,
    val energy: Int? = null,
    val sleep: Int? = null,
    val motivation: Int? = null,
    val sorenessRegionIds: List<String> = emptyList(),
    val note: String? = null,
)

data class PostPending(val durationAutoS: Int, val detectedArrows: Int)

data class ShotView(
    val id: String,
    val index: Int,
    val features: FeatureVector,
    val score: Double?,
    val stability: Double?,
    val topDeviationFeature: String?,
    val isBaseline: Boolean,
)

data class BaselineInfo(val ready: Boolean, val repCount: Long) {
    val needed: Long
        get() = (8L - repCount).coerceAtLeast(0)
}

class SessionViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    private val athleteFeatures = AthleteFeatureRepository(app)
    val recorder = PoseRecorder(app)
    val liveTracking: StateFlow<Boolean>
        get() = recorder.liveTracking

    val liveBowArmAngle: StateFlow<Double?>
        get() = recorder.liveBowArmAngle

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
            val a =
                withContext(Dispatchers.IO) {
                    repo.ensureAthlete(UUID.randomUUID().toString(), "Athlete", 70.0)
                }
            _athleteName.value = a.displayName
            _athleteHandedness.value = Handedness.fromStorage(a.handedness)
            _activeRig.value = withContext(Dispatchers.IO) { repo.activeRig(a.id) }
        }
    }

    fun refreshActiveRig() {
        viewModelScope.launch {
            val a = withContext(Dispatchers.IO) { repo.currentAthlete() } ?: return@launch
            _activeRig.value = withContext(Dispatchers.IO) { repo.activeRig(a.id) }
        }
    }

    fun startSession(
        distanceMeters: Int,
        handednessOverride: Handedness? = null,
        pre: PreCheckinData? = null,
        context: SessionDefaults? = null,
    ) {
        viewModelScope.launch {
            val athlete = withContext(Dispatchers.IO) { repo.currentAthlete() } ?: return@launch
            val rig = withContext(Dispatchers.IO) { repo.activeRig(athlete.id) }
            _activeRig.value = rig
            val poundage =
                rig?.let { Tuning.effectivePoundage(it, athlete.drawLengthMm)?.lbs } ?: 0.0
            currentHandednessOverride = handednessOverride
            val sid = UUID.randomUUID().toString()
            val startedAtMs = System.currentTimeMillis()
            withContext(Dispatchers.IO) {
                val preId =
                    pre?.let { p ->
                        val cid = UUID.randomUUID().toString()
                        repo.wellness.insertCheckin(
                            CheckinEntity(
                                cid,
                                athlete.id,
                                System.currentTimeMillis(),
                                "PRE",
                                p.skipped,
                                p.energy,
                                p.sleep,
                                p.motivation,
                                note = p.note,
                            )
                        )
                        if (p.sorenessRegionIds.isNotEmpty())
                            repo.wellness.insertSoreness(
                                p.sorenessRegionIds.distinct().map { SorenessEntity(cid, it) }
                            )
                        cid
                    }
                repo.createSession(
                    SessionEntity(
                        sid,
                        athlete.id,
                        startedAtMs,
                        poundage,
                        distanceMeters,
                        rigId = rig?.id,
                        handednessOverride = handednessOverride?.name,
                        preCheckinId = preId,
                    )
                )
                context?.let {
                    athleteFeatures.saveSessionContext(
                        sid,
                        it.copy(rigId = rig?.id ?: it.rigId, distanceMeters = distanceMeters),
                        startedAtMs,
                    )
                }
            }
            currentSessionId = sid
            _sessionActive.value = true
            _shots.value = emptyList()
            _postPending.value = null
            refresh()
        }
    }

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

    fun stopRecordingAndAnalyze() {
        if (!_isRecording.value) return
        _isRecording.value = false
        val window = recorder.stop()
        val sid = currentSessionId ?: return
        if (window == null) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val athlete = repo.currentAthlete() ?: return@withContext
                val handed =
                    EffectiveHandedness.resolve(
                        Handedness.fromStorage(athlete.handedness),
                        currentHandednessOverride,
                    )
                val normalized = HandednessNormalizer.normalize(window, handed)
                val analysis = ArcheryAnalyzer.analyzeWithSpans(normalized)
                val offset = repo.shotsOnce(sid).size
                repo.saveShots(
                    analysis.features.mapIndexed { i, f ->
                        ShotEntity(
                            UUID.randomUUID().toString(),
                            sid,
                            athlete.id,
                            offset + i,
                            ArcheryAnalyzer.featuresToJson(f),
                            null,
                            false,
                        )
                    }
                )
                val duration = DurationModel.auto(analysis.spans, analysis.recordingSeconds)
                _postPending.value = PostPending(duration.seconds.toInt(), repo.shotsOnce(sid).size)
            }
            refresh()
        }
    }

    fun savePostCheckin(rpe: Double?, feel: Int?, durationOverrideS: Int?, arrowsActual: Int?) {
        val sid = currentSessionId ?: return
        val pending = _postPending.value
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val a = repo.currentAthlete() ?: return@withContext
                val cid = UUID.randomUUID().toString()
                repo.wellness.insertCheckin(
                    CheckinEntity(
                        cid,
                        a.id,
                        System.currentTimeMillis(),
                        "POST",
                        false,
                        rpe = rpe,
                        feel = feel,
                    )
                )
                val auto = pending?.durationAutoS
                repo.finishSession(
                    sid,
                    cid,
                    auto,
                    durationOverrideS ?: auto,
                    arrowsActual ?: pending?.detectedArrows,
                )
            }
            _postPending.value = null
        }
    }

    fun skipPostCheckin() {
        val sid = currentSessionId ?: return
        val p =
            _postPending.value
                ?: run {
                    _postPending.value = null
                    return
                }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.finishSession(sid, null, p.durationAutoS, p.durationAutoS, p.detectedArrows)
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

    private suspend fun refresh() {
        val sid = currentSessionId ?: return
        val c =
            withContext(Dispatchers.IO) {
                val a = repo.currentAthlete() ?: return@withContext null
                val entities = repo.shotsOnce(sid)
                val baselineFeatures =
                    repo.baselineShots(a.id).map {
                        ArcheryAnalyzer.featuresFromJson(it.featuresJson)
                    }
                val model = ArcheryAnalyzer.buildBaseline(baselineFeatures)
                val views =
                    entities.map { e ->
                        val f = ArcheryAnalyzer.featuresFromJson(e.featuresJson)
                        val dev = if (model.isReady()) ArcheryAnalyzer.score(model, f) else null
                        ShotView(
                            e.id,
                            e.indexInSession,
                            f,
                            e.score,
                            dev?.stability,
                            dev?.topDeviation?.key,
                            e.isBaseline,
                        )
                    }
                Computed(
                    views,
                    BaselineInfo(model.isReady(), model.repCount),
                    ArcheryAnalyzer.fatigue(views.sortedBy { it.index }.map { it.features }),
                    ArcheryAnalyzer.correlations(repo.scoredReps(a.id)),
                )
            } ?: return
        _shots.value = c.shots
        _baseline.value = c.baseline
        _fatigue.value = c.fatigue
        _correlations.value = c.correlations
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
