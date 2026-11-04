package xyz.mdhv.formanalyser.app.domain

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.mdhv.formanalyser.app.ai.AiSettings
import xyz.mdhv.formanalyser.app.awaitUntil
import xyz.mdhv.formanalyser.app.data.AppDatabase
import xyz.mdhv.formanalyser.app.data.AthleteEntity
import xyz.mdhv.formanalyser.coach.CoachIntent
import xyz.mdhv.formanalyser.coach.CoachModel
import xyz.mdhv.formanalyser.coach.CompletionRequest
import xyz.mdhv.formanalyser.coach.CompletionResponse
import xyz.mdhv.formanalyser.coach.CompletionResult
import xyz.mdhv.formanalyser.coach.LlmClient
import xyz.mdhv.formanalyser.coach.ModelRegistry

/**
 * Exercises [CoachViewModel] with a REAL [xyz.mdhv.formanalyser.app.data.Repository]
 * (Robolectric-backed Room, same reasoning as [ScoringViewModelTest]) but a hand-written
 * `clientResolver` / `keyPresent` seam: the real ones resolve to
 * [xyz.mdhv.formanalyser.app.ai.KeyVault] (Tink over the Android Keystore) and
 * [xyz.mdhv.formanalyser.app.ai.OnDeviceLlmClient] (a MediaPipe runtime file on disk), and
 * Robolectric supplies neither a real Keystore provider nor MediaPipe. [LlmClient] is already a pure
 * JVM interface designed for exactly this, so a plain lambda pair is far cheaper and more honest
 * than standing up either -- and needs no mocking library (the module's strict dependency lockfile
 * carries none).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CoachViewModelTest {

    private lateinit var app: Application
    private lateinit var viewModel: CoachViewModel

    // The two seam lambdas read these on every call, so each test configures the scenario it needs.
    private var keyPresent: (CoachModel) -> Boolean = { false }
    private var client: LlmClient? = null
    private val resolvedModels = mutableListOf<CoachModel>()

    private val byokModel: CoachModel = ModelRegistry.models.first { it.requiresByok }
    private val onDeviceModel: CoachModel = ModelRegistry.models.first { !it.requiresByok }

    /** A minimal, pure-JVM [LlmClient] test double -- no network, no Android. */
    private class FakeLlmClient(private val text: String) : LlmClient {
        var completeCalls = 0
            private set

        override fun supports(model: CoachModel) = true

        override fun complete(request: CompletionRequest): CompletionResult {
            completeCalls++
            return CompletionResult.Success(
                CompletionResponse(text = text, modelId = request.model.id, inputTokens = 42, outputTokens = 7)
            )
        }
    }

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        // Same isolation rationale as ScoringViewModelTest: AppDatabase.get() is a process-wide
        // singleton, so each test starts from a database no other test method has touched.
        AppDatabase.resetForTests()
        app.deleteDatabase("form-analyser.db")
        runBlocking {
            AppDatabase.get(app)
                .athleteDao()
                .upsert(AthleteEntity(id = "athlete-1", displayName = "Test Athlete", bodyMassKg = 70.0))
        }
        keyPresent = { false }
        client = null
        resolvedModels.clear()
        viewModel =
            CoachViewModel(
                app,
                clientResolver = { model ->
                    resolvedModels += model
                    client
                },
                keyPresent = { model -> keyPresent(model) },
                aiSettings = AiSettings(app),
            )
    }

    @After
    fun tearDown() {
        AppDatabase.resetForTests()
    }

    @Test
    fun needsKey_isTrueOnlyForABYOKModelWithNoKeyConfigured() {
        keyPresent = { false }
        assertTrue(viewModel.needsKey(byokModel))

        keyPresent = { true }
        assertFalse(viewModel.needsKey(byokModel))

        // An on-device model never needs a key, regardless of what the seam reports -- requiresByok
        // is false for it, and needsKey must short-circuit on that alone (see CoachModel's own law:
        // there is no such thing as a free hosted CLOUD tier, so "no key needed" is only ever true
        // for ON_DEVICE).
        keyPresent = { false }
        assertFalse(viewModel.needsKey(onDeviceModel))
    }

    @Test
    fun ask_onAModelMissingItsKey_surfacesNeedsKeyWithoutEverResolvingAClient() {
        keyPresent = { false }

        viewModel.ask(CoachIntent.SESSION_DEBRIEF, byokModel, medicalGrant = false)

        val state = viewModel.ask.value
        assertTrue(state is CoachAskState.NeedsKey)
        assertEquals(byokModel, (state as CoachAskState.NeedsKey).model)
        // The guard must fire before any client is resolved -- this is a synchronous check, not a
        // failed call, so BYOK-less use never even reaches the network seam.
        assertTrue(resolvedModels.isEmpty())
    }

    @Test
    fun ask_withAKeyPresent_groundsOverRealFactsAndReturnsTheClientsAnswer() {
        keyPresent = { true }
        val fakeClient = FakeLlmClient("Solid follow-through this week.")
        client = fakeClient

        viewModel.ask(CoachIntent.SESSION_DEBRIEF, byokModel, medicalGrant = false)

        val state = viewModel.ask.awaitUntil { it is CoachAskState.Ready || it is CoachAskState.Error }
        val ready = state as? CoachAskState.Ready ?: error("expected Ready, was $state")
        assertEquals("Solid follow-through this week.", ready.text)
        assertEquals(1, fakeClient.completeCalls)
        // Token counts the provider reported ride along to the UI (never rendered as zero when absent).
        assertNotNull(ready.usage)
        assertEquals(42, ready.usage!!.inputTokens)
        assertEquals(7, ready.usage!!.outputTokens)
        assertFalse(ready.truncated)
    }

    @Test
    fun ask_whenNoClientResolves_reportsAnErrorRatherThanCrashing() {
        keyPresent = { true }
        client = null

        viewModel.ask(CoachIntent.SESSION_DEBRIEF, byokModel, medicalGrant = false)

        val state = viewModel.ask.awaitUntil { it is CoachAskState.Ready || it is CoachAskState.Error }
        assertTrue(state is CoachAskState.Error)
    }
}
