package xyz.mdhv.formanalyser.app.domain

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.mdhv.formanalyser.app.awaitUntil
import xyz.mdhv.formanalyser.app.data.AppDatabase
import xyz.mdhv.formanalyser.app.data.AthleteEntity
import xyz.mdhv.formanalyser.app.data.ScoringRepository

/**
 * Exercises [ScoringViewModel] against a REAL (Robolectric-backed) Room database via a real
 * [ScoringRepository] -- no fakes for the persistence layer, because the thing most worth guarding
 * here is a house invariant that spans both classes: a machine proposal (an End Scan candidate) must
 * never move [xyz.mdhv.formanalyser.scoring.Scorecard.total] until a human explicitly confirms it. A
 * test that faked [ScoringRepository] could only assert that the ViewModel *called* the right
 * repository method, not that the invariant actually holds end to end through Room.
 *
 * Main-thread note: see `TestFlows.kt` -- the helper polls the Robolectric main Looper rather than
 * swapping the Main dispatcher, so this module needs no coroutine-test dependency.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScoringViewModelTest {

    private lateinit var app: Application
    private lateinit var viewModel: ScoringViewModel

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        // AppDatabase.get() is a process-wide singleton by design (see its KDoc); Robolectric does
        // not guarantee a fresh classloader per test method, so each test drops the cached instance
        // and the file so it starts from nothing.
        AppDatabase.resetForTests()
        app.deleteDatabase("form-analyser.db")
        runBlocking {
            AppDatabase.get(app)
                .athleteDao()
                .upsert(AthleteEntity(id = "athlete-1", displayName = "Test Athlete", bodyMassKg = 70.0))
        }
        viewModel = ScoringViewModel(app)
    }

    @After
    fun tearDown() {
        AppDatabase.resetForTests()
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
        // The detector version rides along on the proposal (schema v11 `detectorVersion`).
        assertEquals("test-1", proposed.endScanCandidates.single().detectorVersion)

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
    fun endScanCeremony_opensWithTheOutstandingArrowCountAndClosesWithoutWritingAnything() {
        viewModel.quickStart()
        viewModel.state.awaitUntil { it.snapshot != null && !it.saving }
        val card = viewModel.state.value.snapshot!!.card
        assertNull(viewModel.endScan.value)

        viewModel.beginEndScan()
        val scan = viewModel.endScan.awaitUntil { it != null }
        assertEquals(EndScanStage.CAMERA, scan!!.stage)
        // Nothing has been shot yet, so a whole end is outstanding.
        assertEquals(card.round.arrowsPerEnd, scan.arrowsExpected)

        viewModel.cancelEndScan()
        assertNull(viewModel.endScan.value)
        // Opening and abandoning a scan is not scoring.
        assertEquals(card.total, viewModel.state.value.snapshot!!.card.total)
        assertEquals(0, viewModel.state.value.snapshot!!.card.arrows.size)
    }

    @Test
    fun observerPhrase_scoresTheSpokenArrowAndUndoRetractsIt() {
        viewModel.quickStart()
        viewModel.state.awaitUntil { it.snapshot != null && !it.saving }

        viewModel.recordObserverPhrase("eight bottom left")
        val scored = viewModel.state.awaitUntil { it.snapshot!!.card.arrows.size == 1 }
        assertEquals(8, scored.snapshot!!.card.total)

        viewModel.recordObserverPhrase("undo")
        val undone = viewModel.state.awaitUntil { it.snapshot!!.card.arrows.isEmpty() }
        assertEquals(0, undone.snapshot!!.card.total)
    }

    @Test
    fun observerPhrase_withNoValidScore_reportsAnErrorAndWritesNoArrow() {
        viewModel.quickStart()
        viewModel.state.awaitUntil { it.snapshot != null && !it.saving }

        viewModel.recordObserverPhrase("hello there")
        val failed = viewModel.state.awaitUntil { it.error != null }

        assertNotNull(failed.error)
        assertEquals(0, failed.snapshot!!.card.arrows.size)
    }
}
