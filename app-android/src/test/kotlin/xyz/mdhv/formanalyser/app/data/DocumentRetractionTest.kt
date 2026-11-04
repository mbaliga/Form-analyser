package xyz.mdhv.formanalyser.app.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.mdhv.formanalyser.app.vault.Vault

/**
 * Real [Vault], real Room: the two-step rule for removing a medical document. A live document can
 * never be erased; retracting leaves the ciphertext untouched; erasing a retracted one removes the
 * file and then the row, and never the row alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DocumentRetractionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "document-retraction-${System.nanoTime()}.db"
    private lateinit var database: AppDatabase
    private lateinit var dao: BodyDao
    private val vault = Vault(context)
    private val retraction: DocumentRetraction by lazy { DocumentRetraction(dao, vault) }
    private val sources = mutableListOf<File>()

    @Before
    fun open() {
        context.deleteDatabase(dbName)
        database =
            Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .build()
        dao = database.bodyDao()
    }

    @After
    fun close() {
        runCatching { database.close() }
        context.deleteDatabase(dbName)
        sources.forEach { it.delete() }
        Vault.wipeViewCache(context)
        File(context.filesDir, "vault").listFiles()?.forEach { it.delete() }
    }

    private fun storeDocument(): DocumentEntity {
        val source =
            File(context.cacheDir, "retraction-source-${UUID.randomUUID()}").apply {
                writeText("private medical report")
                sources += this
            }
        val id = "doc-${UUID.randomUUID()}"
        val enc = vault.encryptFrom(Uri.fromFile(source), id)
        val doc =
            DocumentEntity(
                id = id,
                athleteId = "a1",
                ts = 1L,
                title = "MRI",
                mime = "application/pdf",
                encPath = enc.encPath,
                sha256 = enc.sha256,
                sizeBytes = enc.sizeBytes,
            )
        runBlocking { dao.insertDocument(doc) }
        return doc
    }

    @Test
    fun aLiveDocumentCannotBeErased() = runBlocking {
        val doc = storeDocument()

        assertFalse(retraction.erasePermanently(doc.id))

        assertTrue("ciphertext must remain", File(doc.encPath).isFile)
        assertNotNull("row must remain", dao.documentById(doc.id))
        assertEquals(1, dao.documentCount("a1"))
    }

    @Test
    fun retractingKeepsTheCiphertextAndErasingARetractedDocumentRemovesFileThenRow() = runBlocking {
        val doc = storeDocument()

        retraction.retract(doc.id, now = 42L)
        assertTrue("retracting must not touch the encrypted file", File(doc.encPath).isFile)
        assertEquals(0, dao.documentCount("a1"))

        assertTrue(retraction.erasePermanently(doc.id))

        assertFalse("ciphertext must be gone", File(doc.encPath).exists())
        assertNull("row must be gone", dao.documentById(doc.id))
        assertFalse("erasing something already erased reports false", retraction.erasePermanently(doc.id))
    }

    @Test
    fun restoreAfterRetractBringsTheDocumentBackWithItsFile() = runBlocking {
        val doc = storeDocument()

        retraction.retract(doc.id, now = 42L)
        retraction.restore(doc.id)

        assertEquals(listOf(doc.id), dao.documents("a1").map { it.id })
        assertTrue(File(doc.encPath).isFile)
        assertFalse("a restored document is live again, so it cannot be erased", retraction.erasePermanently(doc.id))
    }

    @Test
    fun theRowIsKeptWhenTheCiphertextCannotBeRemoved() = runBlocking {
        val doc = storeDocument()
        // A path outside this device's vault: Vault.delete refuses it, so the row must not be
        // dropped while the file it points at might still exist.
        val elsewhere =
            File(context.cacheDir, "not-in-vault-${UUID.randomUUID()}").apply {
                writeText("x")
                sources += this
            }
        val outside = doc.copy(id = "outside-${UUID.randomUUID()}", encPath = elsewhere.absolutePath)
        dao.insertDocument(outside)
        retraction.retract(outside.id, now = 42L)

        assertFalse(retraction.erasePermanently(outside.id))

        assertNotNull(dao.documentById(outside.id))
        assertTrue("a file outside the vault is never touched", elsewhere.isFile)
    }

    @Test
    fun aCiphertextThatIsAlreadyGoneStillLetsTheRetractedRowBeErased() = runBlocking {
        val doc = storeDocument()
        retraction.retract(doc.id, now = 42L)
        assertTrue(File(doc.encPath).delete())

        assertTrue(retraction.erasePermanently(doc.id))
        assertNull(dao.documentById(doc.id))
    }

    @Test
    fun vaultDeleteReportsWhetherTheFileIsAbsentAfterwards() {
        val doc = storeDocument()

        assertTrue(vault.delete(doc.encPath))
        assertFalse(File(doc.encPath).exists())
        assertTrue("already absent counts as success", vault.delete(doc.encPath))
        val elsewhere =
            File(context.cacheDir, "not-in-vault-${UUID.randomUUID()}").apply {
                writeText("x")
                sources += this
            }
        assertFalse("outside the vault is refused", vault.delete(elsewhere.absolutePath))
        assertTrue(elsewhere.isFile)
    }
}
