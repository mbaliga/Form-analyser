package xyz.mdhv.formanalyser.app.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import xyz.mdhv.formanalyser.app.ai.AiSettings
import xyz.mdhv.formanalyser.app.ai.CoachLlmRouter
import xyz.mdhv.formanalyser.app.awaitUntil
import xyz.mdhv.formanalyser.app.data.AppDatabase
import xyz.mdhv.formanalyser.app.data.AppPrefs
import xyz.mdhv.formanalyser.app.data.AthleteEntity
import xyz.mdhv.formanalyser.app.data.Repository
import xyz.mdhv.formanalyser.coach.CoachIntent
import xyz.mdhv.formanalyser.coach.CoachModel
import xyz.mdhv.formanalyser.coach.CompletionRequest
import xyz.mdhv.formanalyser.coach.CompletionResponse
import xyz.mdhv.formanalyser.coach.CompletionResult
import xyz.mdhv.formanalyser.coach.LlmClient
import xyz.mdhv.formanalyser.coach.ModelRegistry

/**
 * Exercises [CoachViewModel] with a REAL [Repository] (Robolectric-backed Room, same reasoning as
 * [ScoringViewModelTest]) but a mocked [CoachLlmRouter]: unlike the repository, the router's real
 * implementation resolves to [xyz.mdhv.formanalyser.app.ai.KeyVault] (Tink over the Android
 * Keystore) and [xyz.mdhv.formanalyser.app.ai.OnDeviceLlmClient] (a MediaPipe LLM runtime file on
 * disk) — Robolectric supplies neither a real Keystore provider nor MediaPipe, so faking the one
 * seam ([xyz.mdhv.formanalyser.coach.LlmClient], already designed as pure JVM interface for exactly
 * this) is far cheaper and more honest than standing up either. [CoachLlmRouter] is a concrete,
 * final Kotlin class — mocked via Mockito's inline mock maker rather than made `open` for testing's
 * sake.
 *
 * Not device-verified — nothing in this environment can run an Android test; CI's Robolectric run
 * (`android.yml`) is the judge, same caveat as every other Android-layer test here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CoachViewModelTest {

    private lateinit var context: Context
    private lateinit var router: CoachLlmRouter
    private lateinit var viewModel: CoachViewModel

    private val byokModel: CoachModel = ModelRegistry.models.first { it.requiresByok }
    private val onDeviceModel: CoachModel = ModelRegistry.models.first { !it.requiresByok }

    /** A minimal, pure-JVM [LlmClient] test double — no network, no Android. */
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
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        // Same isolation rationale as ScoringViewModelTest: AppDatabase.get() is a process-wide
        // singleton, so each test starts from a database no other test method has touched.
        context.deleteDatabase("form-analyser.db")
        runBlocking {
            AppDatabase.get(context)
                .athleteDao()
                .upsert(AthleteEntity(id = "athlete-1", displayName = "Test Athlete", bodyMassKg = 70.0))
        }
        router = mock()
        viewModel = CoachViewModel(Repository(context), AppPrefs(context), router, AiSettings(context))
    }

    @After
    fun tearDown() {
        AppDatabase.resetForTests()
        Dispatchers.resetMain()
    }

    @Test
    fun needsKey_isTrueOnlyForABYOKModelWithNoKeyConfigured() {
        whenever(router.keyPresent(byokModel)).thenReturn(false)
        assertTrue(viewModel.needsKey(byokModel))

        whenever(router.keyPresent(byokModel)).thenReturn(true)
        assertFalse(viewModel.needsKey(byokModel))

        // An on-device model never needs a key, regardless of what the router reports — requiresByok
        // is false for it, and needsKey must short-circuit on that alone (see CoachModel's own law:
        // there is no such thing as a free hosted CLOUD tier, so "no key needed" is only ever true
        // for ON_DEVICE).
        assertFalse(viewModel.needsKey(onDeviceModel))
    }

    @Test
    fun ask_onAModelMissingItsKey_surfacesNeedsKeyWithoutEverResolvingAClient() {
        whenever(router.keyPresent(byokModel)).thenReturn(false)

        viewModel.ask(CoachIntent.SESSION_DEBRIEF, byokModel, medicalGrant = false)

        val state = viewModel.ask.value
        assertTrue(state is CoachAskState.NeedsKey)
        assertEquals(byokModel, (state as CoachAskState.NeedsKey).model)
        // The guard must fire before any client is resolved — this is a synchronous check, not a
        // failed call, so BYOK-less use never even reaches the network seam.
        verify(router, never()).resolve(byokModel)
    }

    @Test
    fun ask_withAKeyPresent_groundsOverRealFactsAndReturnsTheClientsAnswer() {
        whenever(router.keyPresent(byokModel)).thenReturn(true)
        val fakeClient = FakeLlmClient("Solid follow-through this week.")
        whenever(router.resolve(byokModel)).thenReturn(fakeClient)

        viewModel.ask(CoachIntent.SESSION_DEBRIEF, byokModel, medicalGrant = false)

        val state = viewModel.ask.awaitUntil { it !is CoachAskState.Loading && it !is CoachAskState.Idle }
        val ready = state as? CoachAskState.Ready ?: error("expected Ready, was $state")
        assertEquals("Solid follow-through this week.", ready.text)
        assertEquals(1, fakeClient.completeCalls)
    }

    @Test
    fun ask_whenNoClientResolves_reportsAnErrorRatherThanCrashing() {
        whenever(router.keyPresent(byokModel)).thenReturn(true)
        whenever(router.resolve(byokModel)).thenReturn(null)

        viewModel.ask(CoachIntent.SESSION_DEBRIEF, byokModel, medicalGrant = false)

        val state = viewModel.ask.awaitUntil { it !is CoachAskState.Loading && it !is CoachAskState.Idle }
        assertTrue(state is CoachAskState.Error)
    }
}
