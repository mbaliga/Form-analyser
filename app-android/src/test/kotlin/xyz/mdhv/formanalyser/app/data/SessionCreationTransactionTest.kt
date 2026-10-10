package xyz.mdhv.formanalyser.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SessionCreationTransactionTest {
    @Test
    fun requiredSessionAndCheckinCommitTogether() = runBlocking {
        withDatabase { database ->
            database.athleteDao().upsert(AthleteEntity("athlete", "Test Athlete", 70.0))
            val repository = Repository(database)
            val checkin = CheckinEntity("pre", "athlete", 100L, "PRE", skipped = true)
            val session =
                SessionEntity(
                    "session",
                    "athlete",
                    100L,
                    drawWeightLbs = 0.0,
                    distanceMeters = 18,
                    preCheckinId = checkin.id,
                )

            repository.createSessionWithPreCheckin(session, checkin, emptyList())

            assertEquals(session, database.sessionDao().byId(session.id))
            assertNotNull(database.wellnessDao().checkinById(checkin.id))
        }
    }

    @Test
    fun failedPrecheckinWriteRollsBackAndDoesNotCreateSession() = runBlocking {
        withDatabase { database ->
            database.athleteDao().upsert(AthleteEntity("athlete", "Test Athlete", 70.0))
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER reject_soreness BEFORE INSERT ON soreness " +
                    "BEGIN SELECT RAISE(ABORT, 'injected write failure'); END"
            )
            val repository = Repository(database)
            val checkin = CheckinEntity("pre", "athlete", 100L, "PRE", skipped = false)
            val session =
                SessionEntity(
                    "session",
                    "athlete",
                    100L,
                    drawWeightLbs = 0.0,
                    distanceMeters = 18,
                    preCheckinId = checkin.id,
                )
            val failingSoreness = listOf(SorenessEntity(checkin.id, "shoulder"))

            assertThrows(RuntimeException::class.java) {
                runBlocking {
                    repository.createSessionWithPreCheckin(session, checkin, failingSoreness)
                }
            }

            assertNull(database.sessionDao().byId(session.id))
            assertNull(database.wellnessDao().checkinById(checkin.id))
        }
    }

    private suspend fun withDatabase(block: suspend (AppDatabase) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "session-transaction-${System.nanoTime()}.db"
        context.deleteDatabase(name)
        val database =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .allowMainThreadQueries()
                .build()
        try {
            block(database)
        } finally {
            runCatching { database.close() }
            context.deleteDatabase(name)
        }
    }
}
