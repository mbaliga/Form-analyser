package xyz.mdhv.formanalyser.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertThrows
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.mdhv.formanalyser.scoring.ArrowScore
import xyz.mdhv.formanalyser.scoring.RoundPack

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScoringRepositoryTest {
    @Test
    fun activeCardAndArrowPersistAcrossDatabaseReopenAndResume() = runBlocking {
        withDatabase { database, name ->
            seedAthlete(database)
            val firstRepository = ScoringRepository(database)
            val started = firstRepository.start(RoundPack.WA_RECURVE_70M_72)
            firstRepository.recordNumeric(started.session.id, ArrowScore(10, isX = true))
            database.close()

            val reopened = openDatabase(name)
            try {
                val resumed = ScoringRepository(reopened).resumeActive()

                assertNotNull(resumed)
                assertEquals(started.session.id, resumed?.session?.id)
                assertEquals(10, resumed?.card?.total)
                assertEquals(1, resumed?.card?.arrows?.size)
                assertTrue(resumed?.card?.arrows?.single()?.score?.isX == true)
            } finally {
                reopened.close()
            }
        }
    }

    @Test
    fun tripleFaceRejectsImpossibleLowRingsFromNumericAndObserverInputs() = runBlocking {
        withDatabase { database, _ ->
            seedAthlete(database)
            val repository = ScoringRepository(database)
            val started = repository.start(RoundPack.WA_RECURVE_18M_60)

            assertThrows(IllegalStateException::class.java) {
                runBlocking { repository.recordNumeric(started.session.id, ArrowScore(5)) }
            }
            assertThrows(IllegalStateException::class.java) {
                runBlocking { repository.recordObserverTap(started.session.id, ring = 5) }
            }

            val unchanged = repository.snapshot(started.session.id)
            assertEquals(0, unchanged.card.arrows.size)
            assertEquals(0, unchanged.card.total)
        }
    }

    @Test
    fun finishedCardCannotBeChangedByLaterScoringOrUndo() = runBlocking {
        withDatabase { database, _ ->
            seedAthlete(database)
            val repository = ScoringRepository(database)
            val round = RoundPack.customPractice("single-arrow-test", "Single arrow", 18, 40, 1, 1)
            val started = repository.start(round)
            repository.recordNumeric(started.session.id, ArrowScore(9))
            val (finished, personalBest) = repository.finish(started.session.id)

            assertEquals("FINISHED", finished.session.status)
            assertNull(personalBest)
            assertThrows(IllegalStateException::class.java) {
                runBlocking { repository.recordNumeric(started.session.id, ArrowScore(1)) }
            }
            assertThrows(IllegalStateException::class.java) {
                runBlocking { repository.undo(started.session.id) }
            }

            val unchanged = repository.snapshot(started.session.id)
            assertEquals(1, unchanged.card.arrows.size)
            assertEquals(9, unchanged.card.total)
        }
    }

    @Test
    fun retractedCardLeavesHistoryViewsAndRestoresItsArrowsAndBest() = runBlocking {
        withDatabase { database, _ ->
            seedAthlete(database)
            val repository = ScoringRepository(database)
            val round = RoundPack.customPractice("retract-restore-test", "Practice", 18, 40, 1, 1)
            val started = repository.start(round)
            repository.recordNumeric(started.session.id, ArrowScore(9))
            repository.finish(started.session.id)

            val preview = repository.previewScorecardDeletion(started.session.id)
            assertFalse(preview.permanent)
            assertTrue(preview.detail.contains("1 arrow(s)"))

            repository.deleteScorecard(started.session.id)
            assertTrue(repository.recent().isEmpty())
            assertTrue(repository.bestPerRound().isEmpty())
            assertEquals(started.session.id, repository.retractedScorecards().single().id)

            repository.restoreScorecard(started.session.id)
            val restored = repository.snapshot(started.session.id)
            assertNull(restored.session.deletedAt)
            assertEquals(1, restored.card.arrows.size)
            assertEquals(9, restored.card.total)
            assertEquals(9, repository.bestPerRound().single().best)
            assertEquals(started.session.id, repository.recent().single().id)
        }
    }

    @Test
    fun onlyAnEmptyUntouchedCardUsesThePermanentDiscardPath() = runBlocking {
        withDatabase { database, _ ->
            seedAthlete(database)
            val repository = ScoringRepository(database)
            val started = repository.start(RoundPack.WA_RECURVE_70M_72)

            val preview = repository.previewScorecardDeletion(started.session.id)
            assertTrue(preview.permanent)
            assertTrue(preview.detail.contains("cannot be restored"))

            repository.deleteScorecard(started.session.id)
            assertTrue(repository.retractedScorecards().isEmpty())
            assertTrue(repository.recent().isEmpty())
            assertThrows(IllegalStateException::class.java) {
                runBlocking { repository.snapshot(started.session.id) }
            }
        }
    }

    private suspend fun withDatabase(block: suspend (AppDatabase, String) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "scoring-repository-${System.nanoTime()}.db"
        context.deleteDatabase(name)
        val database = openDatabase(name)
        try {
            block(database, name)
        } finally {
            runCatching { database.close() }
            context.deleteDatabase(name)
        }
    }

    private fun openDatabase(name: String): AppDatabase =
        Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            name,
        ).allowMainThreadQueries().build()

    private suspend fun seedAthlete(database: AppDatabase) {
        database.athleteDao().upsert(AthleteEntity("athlete-test", "Archer", 65.0))
    }
}
