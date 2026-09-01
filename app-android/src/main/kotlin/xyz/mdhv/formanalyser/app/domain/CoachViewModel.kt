package xyz.mdhv.formanalyser.app.domain

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import xyz.mdhv.formanalyser.app.ai.AiSettings
import xyz.mdhv.formanalyser.app.ai.KeyVault
import xyz.mdhv.formanalyser.app.ai.OnDeviceLlmClient
import xyz.mdhv.formanalyser.app.ai.providers.CloudLlmClients
import xyz.mdhv.formanalyser.app.data.AppPrefs
import xyz.mdhv.formanalyser.app.data.Repository
import xyz.mdhv.formanalyser.coach.CoachFacts
import xyz.mdhv.formanalyser.coach.CoachInsight
import xyz.mdhv.formanalyser.coach.CoachIntent
import xyz.mdhv.formanalyser.coach.CoachModel
import xyz.mdhv.formanalyser.coach.CompletionRequest
import xyz.mdhv.formanalyser.coach.CompletionResult
import xyz.mdhv.formanalyser.coach.LlmClient
import xyz.mdhv.formanalyser.coach.ModelKind
import xyz.mdhv.formanalyser.coach.ModelRegistry
import xyz.mdhv.formanalyser.coach.PromptBuilder
import xyz.mdhv.formanalyser.coach.Redaction
import xyz.mdhv.formanalyser.coach.RigSummary
import xyz.mdhv.formanalyser.coach.RuleCoach
import xyz.mdhv.formanalyser.coach.ShotLoadSummary
import xyz.mdhv.formanalyser.coach.WithheldFact
import xyz.mdhv.formanalyser.wellness.InjurySummary

/**
 * The one place where the app's own local data becomes coaching. It has two faces:
 *
 *  - [insights] — the deterministic, offline [RuleCoach] read over the athlete's assembled
 *    [CoachFacts]. Always available, no key, no network.
 *  - [ask] — an optional LLM turn. It grounds a [CoachIntent] over the SAME facts, runs them through
 *    [Redaction] for the chosen model's destination, builds a prompt from the *redacted* factsheet,
 *    and completes it on an [LlmClient] off the main thread — surfacing the response, a typed
 *    error, and the exact "what wasn't sent" list.
 *
 * The LLM plumbing is a seam: [clientResolver] maps a model to a client (a BYOK cloud adapter or the
 * on-device runtime) and [keyPresent] reports whether a required BYOK key is configured. The Integrate
 * layer supplies the real seam from the provider factories + [KeyVault]; [factory] wires a default one.
 * This VM never touches an API key and never duplicates wellness math — it reuses [WellnessAssembler].
 */
class CoachViewModel(
    app: Application,
    private val clientResolver: (CoachModel) -> LlmClient?,
    private val keyPresent: (CoachModel) -> Boolean,
    private val aiSettings: AiSettings = AiSettings(app),
) : AndroidViewModel(app) {

    private val repo = Repository(app)
    private val prefs = AppPrefs(app)
    private val assembler = WellnessAssembler(repo)

    /** Deterministic offline coach output. Recomputed by [load]; empty until then / with no data. */
    private val _insights = MutableStateFlow<List<CoachInsight>>(emptyList())
    val insights: StateFlow<List<CoachInsight>> = _insights

    /** True once [load] has run — lets the screen tell "no data" from "not loaded yet". */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    /** The selected coaching task and model, persisted via [AiSettings]. */
    private val _intent = MutableStateFlow(CoachIntent.SESSION_DEBRIEF)
    val intent: StateFlow<CoachIntent> = _intent

    private val _model = MutableStateFlow(
        ModelRegistry.byId(AiSettings.DEFAULT_MODEL_ID) ?: ModelRegistry.models.first(),
    )
    val model: StateFlow<CoachModel> = _model

    /** The ask lifecycle. Also carries the withheld report so the UI can show what was excluded. */
    private val _ask = MutableStateFlow<CoachAskState>(CoachAskState.Idle)
    val ask: StateFlow<CoachAskState> = _ask

    /** All selectable models, straight from the registry allow-list. */
    val models: List<CoachModel> = ModelRegistry.models

    /** Last-assembled facts, reused by [ask] so a completion doesn't re-hit the DB unnecessarily. */
    @Volatile private var facts: CoachFacts? = null

    fun setIntent(i: CoachIntent) { _intent.value = i }

    fun setModel(m: CoachModel) {
        _model.value = m
        viewModelScope.launch { aiSettings.setSelectedModelId(m.id) }
    }

    /** True iff the current model is a BYOK cloud model with no key configured. */
    fun needsKey(m: CoachModel = _model.value): Boolean = m.requiresByok && !keyPresent(m)

    fun load() {
        viewModelScope.launch {
            // Restore the persisted model selection.
            val savedId = aiSettings.selectedModelId.first()
            ModelRegistry.byId(savedId)?.let { _model.value = it }

            val assembled = withContext(Dispatchers.IO) { assembleFacts() }
            facts = assembled
            _insights.value = assembled?.let { RuleCoach.insights(it) } ?: emptyList()
            _loaded.value = true
        }
    }

    /**
     * Grounded LLM turn. Guards BYOK first (surfaces [CoachAskState.NeedsKey] instead of calling out),
     * then redacts for the model's destination, builds the prompt from the redacted factsheet, and
     * completes it on [Dispatchers.IO]. The withheld list is captured pre-call so it is shown on both
     * success and failure.
     */
    fun ask(intent: CoachIntent, model: CoachModel, medicalGrant: Boolean) {
        if (model.requiresByok && !keyPresent(model)) {
            _ask.value = CoachAskState.NeedsKey(model)
            return
        }
        _ask.value = CoachAskState.Loading
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val f = facts ?: assembleFacts()
                if (f == null) return@withContext Outcome.NoData
                val keepPrivate = aiSettings.keepPrivate.first()
                val redacted = Redaction.redactFor(f, model, medicalGrant = medicalGrant, keepPrivate = keepPrivate)
                val client = clientResolver(model)
                    ?: return@withContext Outcome.Result(null, redacted.withheld, "No client available for ${model.displayName}")
                val messages = PromptBuilder.build(intent, redacted)
                when (val r = client.complete(CompletionRequest(model = model, messages = messages))) {
                    is CompletionResult.Success -> Outcome.Result(r.response.text, redacted.withheld, null)
                    is CompletionResult.Failure -> Outcome.Result(null, redacted.withheld, r.error.message)
                }
            }
            _ask.value = when (outcome) {
                Outcome.NoData -> CoachAskState.Error("No data to coach on yet.", emptyList())
                is Outcome.Result ->
                    if (outcome.text != null) CoachAskState.Ready(model, outcome.text, outcome.withheld)
                    else CoachAskState.Error(outcome.error ?: "Coaching failed.", outcome.withheld)
            }
        }
    }

    fun clearAsk() { _ask.value = CoachAskState.Idle }

    // ── fact assembly (IO + mapping only; all math lives in core-wellness / core-equipment) ──────

    private suspend fun assembleFacts(): CoachFacts? {
        val athlete = repo.currentAthlete() ?: return null
        val id = athlete.id
        val now = System.currentTimeMillis()

        val readiness = assembler.readiness(id)
        val acwrSeries = assembler.acwr(id)
        val acwrVal = if (acwrSeries.warmupComplete) acwrSeries.latest?.acwr else null
        val acwrZone = if (acwrSeries.warmupComplete) acwrSeries.latest?.zone else null
        val streak = assembler.streak(id, prefs.plannedRestDays.first())

        val sessions = repo.allSessions(id)
        val d7 = now - 7L * 24 * 3600 * 1000
        val d28 = now - 28L * 24 * 3600 * 1000
        val s7 = sessions.filter { it.startedAtEpochMs >= d7 }
        val s28 = sessions.filter { it.startedAtEpochMs >= d28 }
        suspend fun shots(list: List<xyz.mdhv.formanalyser.app.data.SessionEntity>): Int =
            list.sumOf { it.arrowsActual ?: repo.shotCount(it.id) }
        val load = ShotLoadSummary(
            sessions7d = s7.size,
            shots7d = shots(s7),
            sessions28d = s28.size,
            shots28d = shots(s28),
        )

        val injuries = repo.body.activeInjuries(id).map {
            InjurySummary(regions = JsonLists.decode(it.regionsJson), severity = it.severity)
        }

        val rig = repo.activeRig(id)?.let { r ->
            RigSummary(
                label = r.name,
                bowType = r.bowType,
                drawWeightLbs = Tuning.effectivePoundage(r, athlete.drawLengthMm)?.lbs,
                drawLengthInches = athlete.drawLengthMm?.let { it / 25.4 },
            )
        }

        val latestCheckin = repo.wellness.latestCheckin(id)
        val checkinAgeHours = latestCheckin?.let { (now - it.ts) / 3_600_000.0 }

        return CoachFacts(
            readinessLevel = readiness.level,
            readinessReasons = readiness.reasons,
            acwr = acwrVal,
            acwrZone = acwrZone,
            load = load,
            streak = streak,
            activeInjuries = injuries,
            rig = rig,
            checkinAgeHours = checkinAgeHours,
            // MEDICAL — rides behind an explicit per-request grant in Redaction.
            medications = repo.wellness.medicationNames(),
            // PRIVATE — never reaches a cloud/export; Redaction gates it.
            moodNote = latestCheckin?.note,
        )
    }

    private sealed interface Outcome {
        data object NoData : Outcome
        data class Result(val text: String?, val withheld: List<WithheldFact>, val error: String?) : Outcome
    }

    companion object {
        /**
         * A default seam built from the app's own key store + provider factories, so integration is a
         * one-liner: `viewModel(factory = CoachViewModel.factory(app))`. Cloud models resolve to a BYOK
         * client that reads the current key lazily; on-device models resolve to the MediaPipe runtime.
         */
        fun factory(app: Application): androidx.lifecycle.ViewModelProvider.Factory =
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    val keyVault = KeyVault(app)
                    val aiSettings = AiSettings(app)
                    val onDevice = OnDeviceLlmClient(app, modelPath = {
                        runBlocking { aiSettings.onDeviceModelPath.first() }
                    })
                    val resolver: (CoachModel) -> LlmClient? = { m ->
                        when (m.kind) {
                            ModelKind.CLOUD -> CloudLlmClients.forModel(m) { p -> keyVault.getKey(p) }
                            ModelKind.ON_DEVICE -> onDevice
                        }
                    }
                    val keyPresent: (CoachModel) -> Boolean = { m ->
                        when (m.kind) {
                            ModelKind.CLOUD -> keyVault.hasKey(m.provider)
                            ModelKind.ON_DEVICE -> true
                        }
                    }
                    return CoachViewModel(app, resolver, keyPresent, aiSettings) as T
                }
            }
    }
}

/** The ask lifecycle surfaced to the UI. [withheld] is the auditable "what wasn't sent" report. */
sealed interface CoachAskState {
    data object Idle : CoachAskState
    data object Loading : CoachAskState
    /** The chosen model needs a BYOK key that isn't set — send the athlete to Settings, don't call out. */
    data class NeedsKey(val model: CoachModel) : CoachAskState
    data class Ready(val model: CoachModel, val text: String, val withheld: List<WithheldFact>) : CoachAskState
    data class Error(val message: String, val withheld: List<WithheldFact>) : CoachAskState
}
