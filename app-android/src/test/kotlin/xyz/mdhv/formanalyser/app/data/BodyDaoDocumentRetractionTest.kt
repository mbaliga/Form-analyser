package xyz.mdhv.formanalyser.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A document is retracted, never hard-deleted by the single delete tap. These tests pin the DAO
 * contract the UI relies on: retracted rows leave every list and count, come back on restore, and
 * the only DELETE in the DAO refuses a live row at SQL level.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BodyDaoDocumentRetractionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "body-dao-document-${System.nanoTime()}.db"
    private lateinit var database: AppDatabase
    private lateinit var dao: BodyDao

    @Before
    fun open() {
        context.deleteDatabase(name)
        database =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .allowMainThreadQueries()
                .build()
        dao = database.bodyDao()
    }

    @After
    fun close() {
        runCatching { database.close() }
        context.deleteDatabase(name)
    }

    private fun doc(id: String, ts: Long, size: Long, injuryId: String? = null) =
        DocumentEntity(
            id = id,
            athleteId = "a1",
            ts = ts,
            title = "Doc $id",
            mime = "application/pdf",
            encPath = "/vault/$id",
            sha256 = "sha-$id",
            sizeBytes = size,
            injuryId = injuryId,
        )

    @Test
    fun retractedDocumentLeavesEveryListAndCountAndRestoreBringsItBack() = runBlocking {
        dao.insertDocument(doc("a", ts = 2, size = 1024, injuryId = "i1"))
        dao.insertDocument(doc("b", ts = 1, size = 2048))
        assertEquals(2, dao.documentCount("a1"))
        assertEquals(3072L, dao.documentBytes("a1"))

        dao.retractDocument("a", at = 500L)

        assertEquals(listOf("b"), dao.documents("a1").map { it.id })
        assertEquals(emptyList<String>(), dao.documentsForInjury("i1").map { it.id })
        assertEquals(1, dao.documentCount("a1"))
        assertEquals("the retracted document's bytes are not counted", 2048L, dao.documentBytes("a1"))
        assertEquals(listOf("a"), dao.retractedDocuments("a1").map { it.id })
        // documentById is the one lookup that still sees it, so Restore and Erase can find it.
        assertEquals(500L, dao.documentById("a")?.deletedAt)

        dao.restoreDocument("a")

        assertEquals(2, dao.documentCount("a1"))
        assertEquals(listOf("a", "b"), dao.documents("a1").map { it.id })
        assertEquals(listOf("a"), dao.documentsForInjury("i1").map { it.id })
        assertEquals(emptyList<String>(), dao.retractedDocuments("a1").map { it.id })
        assertNull(dao.documentById("a")?.deletedAt)
    }

    @Test
    fun eraseRefusesALiveRowAtSqlLevelAndRemovesARetractedOne() = runBlocking {
        dao.insertDocument(doc("live", ts = 1, size = 10))
        dao.insertDocument(doc("gone", ts = 2, size = 10))
        dao.retractDocument("gone", at = 9L)

        assertEquals("a live row can never be erased through the DAO", 0, dao.eraseRetractedDocument("live"))
        assertNotNull(dao.documentById("live"))

        assertEquals(1, dao.eraseRetractedDocument("gone"))
        assertNull(dao.documentById("gone"))
        assertEquals(0, dao.eraseRetractedDocument("gone"))
    }

    @Test
    fun retractingAnAlreadyRetractedRowKeepsTheOriginalTimestamp() = runBlocking {
        dao.insertDocument(doc("a", ts = 1, size = 10))
        dao.retractDocument("a", at = 111L)
        dao.retractDocument("a", at = 999L)

        assertEquals(111L, dao.documentById("a")?.deletedAt)
    }
}
