package xyz.mdhv.formanalyser.app.domain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mdhv.formanalyser.app.ai.AiSettings
import xyz.mdhv.formanalyser.app.ai.CoachLlmRouter
import xyz.mdhv.formanalyser.app.data.AppPrefs
import xyz.mdhv.formanalyser.app.data.Repository
import xyz.mdhv.formanalyser.coach.CoachFacts
import xyz.mdhv.formanalyser.coach.CoachInsight
import xyz.mdhv.formanalyser.coach.CoachIntent
import xyz.mdhv.formanalyser.coach.CoachModel
import xyz.mdhv.formanalyser.coach.CompletionRequest
import xyz.mdhv.formanalyser.coach.CompletionResult
import xyz.mdhv.formanalyser.coach.LlmClient
import xyz.mdhv.formanalyser.coach.LlmErrorKind
import xyz.mdhv.formanalyser.coach.ModelRegistry
import xyz.mdhv.formanalyser.coach.PromptBuilder
import xyz.mdhv.formanalyser.coach.Redaction
import xyz.mdhv.formanalyser.coach.RigSummary
import xyz.mdhv.formanalyser.coach.RuleCoach
import xyz.mdhv.formanalyser.coach.ShotLoadSummary
import xyz.mdhv.formanalyser.coach.StreamDirective
import xyz.mdhv.formanalyser.coach.StreamEvent
import xyz.mdhv.formanalyser.coach.StreamSink
import xyz.mdhv.formanalyser.coach.UsageSnapshot
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
 * The LLM plumbing is a seam: [CoachLlmRouter.resolve] maps a model to a client (a BYOK cloud adapter
 * or the on-device runtime) and [CoachLlmRouter.keyPresent] reports whether a required BYOK key is
 * configured. [router] is a Hilt-injected singleton built from the provider factories +
 * [xyz.mdhv.formanalyser.app.ai.KeyVault] — see [xyz.mdhv.formanalyser.app.di.CoachModule] for the one
 * piece of that graph ([xyz.mdhv.formanalyser.app.ai.OnDeviceLlmClient]) that still needs a `@Provides`
 * method, and [CoachLlmRouter]'s own KDoc for why this class's old hand-rolled
 * `ViewModelProvider.Factory` is gone even though the object graph it built is not.
 * This VM never touches an API key and never duplicates wellness math — it reuses [WellnessAssembler].
 */
@HiltViewModel
class CoachViewModel @Inject constructor(
    private val repo: Repository,
    private val prefs: AppPrefs,
    private val router: CoachLlmRouter,
    private val aiSettings: AiSettings,
) : ViewModel() {

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
    fun needsKey(m: CoachModel = _model.value): Boolean = m.requiresByok && !router.keyPresent(m)

    /**
     * True while the athlete has asked to stop the in-flight streamed ask. Read by [streamToOutcome]'s
     * [StreamSink] on every event; a real streaming [LlmClient] override honours a returned
     * [StreamDirective.CANCEL] by stopping its read and returning a truncated `Success` with whatever
     * text already arrived (see [LlmClient.stream]'s KDoc) — a cancel is not an error.
     *
     * Deliberately a plain flag, NOT `viewModelScope`/coroutine `Job` cancellation: cancelling the Job
     * running [ask] would also kill the code *after* the client call that publishes the partial result
     * to [_ask] — the classic cancellation trap. [onCleared] sets this too, so a blocking read loop
     * left running past the ViewModel's own lifetime still unwinds instead of lingering on IO.
     */
    @Volatile private var cancelRequested = false

    /** Ask the in-flight streamed completion to stop. See [cancelRequested]. No-op if nothing is running. */
    fun cancelAsk() {
        cancelRequested = true
    }

    override fun onCleared() {
        cancelRequested = true
        super.onCleared()
    }

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
     * streams it on [Dispatchers.IO] via [streamToOutcome]. The withheld list is captured pre-call so
     * it is shown on every terminal state (streaming, ready, or error) — the redaction audit must
     * never appear only at the end.
     */
    fun ask(intent: CoachIntent, model: CoachModel, medicalGrant: Boolean) {
        if (model.requiresByok && !router.keyPresent(model)) {
            _ask.value = CoachAskState.NeedsKey(model)
            return
        }
        cancelRequested = false
        _ask.value = CoachAskState.Loading
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val f = facts ?: assembleFacts()
                if (f == null) return@withContext Outcome.NoData
                val keepPrivate = aiSettings.keepPrivate.first()
                val redacted = Redaction.redactFor(f, model, medicalGrant = medicalGrant, keepPrivate = keepPrivate)
                val client = router.resolve(model)
                    ?: return@withContext Outcome.Result(
                        text = null,
                        withheld = redacted.withheld,
                        error = "No client available for ${model.displayName}",
                        attempted = false,
                    )
                val messages = PromptBuilder.build(intent, redacted)
                streamToOutcome(model, CompletionRequest(model = model, messages = messages), redacted.withheld, client)
            }
            _ask.value = when (outcome) {
                Outcome.NoData -> CoachAskState.Error("No data to coach on yet.", emptyList())
                is Outcome.Result ->
                    if (outcome.text != null) {
                        CoachAskState.Ready(model, outcome.text, outcome.withheld, outcome.usage, outcome.truncated)
                    } else {
                        CoachAskState.Error(outcome.error ?: "Coaching failed.", outcome.withheld, outcome.partialText)
                    }
            }
            // Recorded only for a call that actually reached a client — the "no client available" and
            // "no data yet" paths never spent a token and must not inflate the ask counter.
            if (outcome is Outcome.Result && outcome.attempted) {
                aiSettings.recordUsage(model.id, outcome.usage ?: UsageSnapshot())
            }
        }
    }

    fun clearAsk() { _ask.value = CoachAskState.Idle }

    /**
     * Drive one completion through [LlmClient.stream], publishing [CoachAskState.Streaming] as text
     * arrives (throttled to roughly [STREAM_PUBLISH_THROTTLE_MS] so a fast provider doesn't recompose
     * Compose on every token) when [LlmClient.supportsStreaming] says the model really streams.
     * A model that doesn't (or the on-device runtime, whose streaming override is not yet built — see
     * [xyz.mdhv.formanalyser.app.ai.OnDeviceLlmClient]'s KDoc) still goes through [LlmClient.stream]'s
     * default one-shot bridge, but this never publishes an intermediate [CoachAskState.Streaming] for
     * it: the bridge's single Delta
     * arrives after the WHOLE answer already exists, and animating that as if it were live would
     * misrepresent latency (a synthetic typewriter over a complete answer).
     *
     * A provider gated out of streaming (some OpenAI orgs are, and answer 400 naming `"stream"`) gets
     * exactly one silent non-streaming retry via [LlmClient.complete] — but only when zero deltas were
     * ever shown, so nothing already on screen is duplicated by trying again.
     */
    private fun streamToOutcome(
        model: CoachModel,
        request: CompletionRequest,
        withheld: List<WithheldFact>,
        client: LlmClient,
    ): Outcome {
        val canRenderIncrementally = client.supportsStreaming(model)
        val buffer = StringBuilder()
        var usage: UsageSnapshot? = null
        var deltasDelivered = 0
        var lastPublishAtMs = 0L

        fun publish(force: Boolean) {
            if (!canRenderIncrementally) return
            val now = System.currentTimeMillis()
            if (force || now - lastPublishAtMs >= STREAM_PUBLISH_THROTTLE_MS) {
                _ask.value = CoachAskState.Streaming(model, buffer.toString(), withheld, usage)
                lastPublishAtMs = now
            }
        }

        val sink = StreamSink { event ->
            when (event) {
                is StreamEvent.Started ->
                    if (event.inputTokens != null) usage = UsageSnapshot(event.inputTokens, usage?.outputTokens)
                is StreamEvent.Delta -> {
                    buffer.append(event.text)
                    deltasDelivered++
                    publish(force = false)
                }
                // StreamAssembler already folds "last non-null value wins" per field before dispatching
                // this, so the event's own fields are the current running totals, not raw deltas.
                is StreamEvent.UsageUpdate -> usage = UsageSnapshot(event.inputTokens, event.outputTokens)
            }
            if (cancelRequested) StreamDirective.CANCEL else StreamDirective.CONTINUE
        }

        var result = client.stream(request, sink)
        if (result is CompletionResult.Failure &&
            result.error.kind == LlmErrorKind.INVALID_REQUEST &&
            deltasDelivered == 0
        ) {
            result = client.complete(request)
        }
        publish(force = true) // flush the final buffered text before the terminal state replaces it

        return when (result) {
            is CompletionResult.Success -> {
                val r = result.response
                Outcome.Result(
                    text = r.text,
                    withheld = withheld,
                    error = null,
                    usage = UsageSnapshot(r.inputTokens, r.outputTokens),
                    truncated = r.truncated,
                )
            }
            is CompletionResult.Failure ->
                Outcome.Result(
                    text = null,
                    withheld = withheld,
                    error = result.error.message,
                    usage = usage,
                    partialText = buffer.toString(),
                )
        }
    }

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

        data class Result(
            val text: String?,
            val withheld: List<WithheldFact>,
            val error: String?,
            val usage: UsageSnapshot? = null,
            val truncated: Boolean = false,
            /** What had already streamed in when [error] happened — shown under the error banner. */
            val partialText: String = "",
            /** False only for a guard that never actually reached a client (no client resolved for
             *  the model) — [ask] must not record a token-less "ask" for a call that never happened. */
            val attempted: Boolean = true,
        ) : Outcome
    }

    companion object {
        /**
         * How often a streamed ask is allowed to publish to [_ask] while text is still arriving. A
         * per-token publish would recompose Compose dozens of times a second for no visible benefit;
         * this is a judgement call that cannot be tuned by feel in an environment with no device.
         */
        private const val STREAM_PUBLISH_THROTTLE_MS = 50L
    }
}

/** The ask lifecycle surfaced to the UI. [withheld] is the auditable "what wasn't sent" report. */
sealed interface CoachAskState {
    data object Idle : CoachAskState

    /** Covers the pre-first-token window, and the entire duration of a non-streaming ask. */
    data object Loading : CoachAskState

    /** The chosen model needs a BYOK key that isn't set — send the athlete to Settings, don't call out. */
    data class NeedsKey(val model: CoachModel) : CoachAskState

    /**
     * Text is arriving incrementally. Only published for a model where
     * [xyz.mdhv.formanalyser.coach.LlmClient.supportsStreaming] is true — see
     * [CoachViewModel.streamToOutcome]'s KDoc for why a non-streaming model never passes through this
     * state. [usage] is whatever the provider has revealed so far and may still change before [Ready].
     */
    data class Streaming(
        val model: CoachModel,
        val text: String,
        val withheld: List<WithheldFact>,
        val usage: UsageSnapshot?,
    ) : CoachAskState

    /**
     * [usage] is null when the provider never reported token counts (e.g. OpenAI without
     * `stream_options.include_usage`, or the on-device runtime before token counting is wired up) —
     * the UI must render that as "tokens unknown," never as zero. [truncated] is true only when the
     * athlete stopped the stream early (see [CoachViewModel.cancelAsk]); it must be labelled, never
     * shown as if it were the coach's complete answer.
     */
    data class Ready(
        val model: CoachModel,
        val text: String,
        val withheld: List<WithheldFact>,
        val usage: UsageSnapshot? = null,
        val truncated: Boolean = false,
    ) : CoachAskState

    /** [partialText] is whatever had already streamed in before the error — shown under the banner. */
    data class Error(
        val message: String,
        val withheld: List<WithheldFact>,
        val partialText: String = "",
    ) : CoachAskState
}
