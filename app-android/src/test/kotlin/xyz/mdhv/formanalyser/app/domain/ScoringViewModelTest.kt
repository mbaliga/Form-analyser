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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import xyz.mdhv.formanalyser.app.awaitUntil
import xyz.mdhv.formanalyser.app.data.AppDatabase
import xyz.mdhv.formanalyser.app.data.AthleteEntity
import xyz.mdhv.formanalyser.app.data.ScoringRepository

/**
 * Exercises [ScoringViewModel] against a REAL (Robolectric-backed) Room database via a real
 * [ScoringRepository] — no fakes for the persistence layer, because the thing most worth guarding
 * here is a house invariant that spans both classes: a machine proposal (an End Scan candidate, a
 * Live Observer transcription) must never move [xyz.mdhv.formanalyser.scoring.Scorecard.total]
 * until a human explicitly confirms it. A test that faked [ScoringRepository] could only assert
 * that the ViewModel *called* the right repository method, not that the invariant actually holds
 * end to end through Room.
 *
 * Not device-verified — nothing in this environment can run an Android test. This is authored to
 * compile and pass under CI's Robolectric run (`android.yml`), the same caveat as every other
 * Android-layer test and change in this project.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ScoringViewModelTest {

    private lateinit var context: Context
    private lateinit var repo: ScoringRepository
    private lateinit var viewModel: ScoringViewModel

    @Before
    fun setUp() {
        // Replaces the Main dispatcher viewModelScope.launch{} needs with one that runs eagerly on
        // the calling thread, so this test does not have to pump Robolectric's (paused by default)
        // main Looper by hand — see TestFlows.kt's KDoc for how the resulting async gap is awaited.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        // AppDatabase.get() is a process-wide singleton by design (see its KDoc); Robolectric does
        // not guarantee a fresh classloader per test method, so without this a later test method
        // could inherit an earlier one's now-orphaned database. Deleting the file name AppDatabase
        // itself uses (a private constant there, so named literally here) makes each test start
        // from nothing regardless of what Robolectric does or does not reset between methods.
        context.deleteDatabase("form-analyser.db")
        runBlocking {
            AppDatabase.get(context)
                .athleteDao()
                .upsert(AthleteEntity(id = "athlete-1", displayName = "Test Athlete", bodyMassKg = 70.0))
        }
        repo = ScoringRepository(context)
        viewModel = ScoringViewModel(context, repo)
    }

    @After
    fun tearDown() {
        AppDatabase.resetForTests()
        Dispatchers.resetMain()
    }

    @Test
    fun quickStartThenRecordToken_writesAnAuthoritativeArrowImmediately() {
        viewModel.quickStart()
        viewModel.state.awaitUntil { it.snapshot != null && !it.saving }

        viewModel.recordToken("9")
        val state = viewModel.state.awaitUntil { it.snapshot!!.card.total == 9 }

        assertEquals(1, state.snapshot!!.card.arrows.size)
        assertEquals(9, state.snapshot!!.card.arrows.single().score.points)
    }

    @Test
    fun endScanCandidate_neverMovesTheTotalUntilAHumanConfirmsIt() {
        viewModel.quickStart()
        viewModel.state.awaitUntil { it.snapshot != null && !it.saving }
        val totalBeforeScan = viewModel.state.value.snapshot!!.card.total

        viewModel.acceptDetectedCandidates(
            listOf(ScoringRepository.EndScanCandidate(points = 9, detectorVersion = "test-1"))
        )
        val proposed = viewModel.state.awaitUntil { it.endScanCandidates.isNotEmpty() }
        // The house invariant this whole flow exists to enforce: a machine proposal is advisory
        // only until a human confirms it, so the total must be exactly what it was before the scan.
        assertEquals(totalBeforeScan, proposed.snapshot!!.card.total)
        assertEquals("PROPOSED", proposed.endScanCandidates.single().status)

        viewModel.confirmEndScanCandidate(proposed.endScanCandidates.single().id)
        val confirmed = viewModel.state.awaitUntil { it.snapshot!!.card.total == totalBeforeScan + 9 }
        assertTrue(confirmed.endScanCandidates.none { it.status == "PROPOSED" })
    }

    @Test
    fun rejectedEndScanCandidate_isNeverScoredAndLeavesNoArrow() {
        viewModel.quickStart()
        viewModel.state.awaitUntil { it.snapshot != null && !it.saving }
        val totalBefore = viewModel.state.value.snapshot!!.card.total

        viewModel.acceptDetectedCandidates(listOf(ScoringRepository.EndScanCandidate(points = 7)))
        val proposed = viewModel.state.awaitUntil { it.endScanCandidates.isNotEmpty() }

        viewModel.rejectEndScanCandidate(proposed.endScanCandidates.single().id)
        val rejected =
            viewModel.state.awaitUntil { it.endScanCandidates.none { c -> c.status == "PROPOSED" } }

        assertEquals(totalBefore, rejected.snapshot!!.card.total)
        assertEquals(0, rejected.snapshot!!.card.arrows.size)
    }

    @Test
    fun aPendingSpokenArrow_onlyBecomesScoredOnAnExplicitConfirm() {
        viewModel.quickStart()
        viewModel.state.awaitUntil { it.snapshot != null && !it.saving }
        val sessionId = viewModel.state.value.snapshot!!.session.id
        val totalBefore = viewModel.state.value.snapshot!!.card.total

        // Seeded directly through the repository — the same call ScoringViewModel's own voice
        // pipeline makes once a transcription is accepted (handleVoiceResult). This exercises the
        // ViewModel's confirm/reject surface, not the on-device SpeechRecognizer plumbing, which
        // Robolectric has no real implementation of.
        runBlocking {
            repo.proposeObserverSpoken(sessionId, ring = 8, isX = false, sector = null, declaredText = "8")
        }

        viewModel.loadPendingObserverEvents()
        val pending = viewModel.state.awaitUntil { it.pendingObserverEvents.isNotEmpty() }
        assertEquals(totalBefore, pending.snapshot!!.card.total)

        viewModel.confirmSpokenArrows(listOf(pending.pendingObserverEvents.single().id))
        val confirmed = viewModel.state.awaitUntil { it.snapshot!!.card.total == totalBefore + 8 }
        assertTrue(confirmed.pendingObserverEvents.isEmpty())
    }

    @Test
    fun rejectingASpokenArrow_neverScoresItAndClearsThePendingStrip() {
        viewModel.quickStart()
        viewModel.state.awaitUntil { it.snapshot != null && !it.saving }
        val sessionId = viewModel.state.value.snapshot!!.session.id
        val totalBefore = viewModel.state.value.snapshot!!.card.total

        runBlocking {
            repo.proposeObserverSpoken(sessionId, ring = 5, isX = false, sector = null, declaredText = "5")
        }
        viewModel.loadPendingObserverEvents()
        val pending = viewModel.state.awaitUntil { it.pendingObserverEvents.isNotEmpty() }

        viewModel.rejectSpokenArrow(pending.pendingObserverEvents.single().id)
        val after = viewModel.state.awaitUntil { it.pendingObserverEvents.isEmpty() }
        assertEquals(totalBefore, after.snapshot!!.card.total)
    }
}
