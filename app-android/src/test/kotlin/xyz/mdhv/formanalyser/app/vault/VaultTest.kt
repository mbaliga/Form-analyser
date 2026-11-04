package xyz.mdhv.formanalyser.app.vault

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VaultTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun encryptAndDecryptRoundTripKeepsPlaintextOutOfVault() {
        val source = File(context.cacheDir, "vault-source-${UUID.randomUUID()}").apply {
            writeText("private training document")
        }
        val documentId = "doc-${UUID.randomUUID()}"
        try {
            val encrypted = Vault(context).encryptFrom(Uri.fromFile(source), documentId)
            val ciphertext = File(encrypted.encPath).readBytes()
            assertFalse(String(ciphertext).contains("private training document"))
            assertEquals(source.length(), encrypted.sizeBytes)
            assertEquals(sha256(source.readBytes()), encrypted.sha256)

            val view = Vault(context).decryptToView(encrypted.encPath, documentId, "txt")
            assertEquals(source.readText(), view.readText())
            assertTrue(view.isFile)
        } finally {
            source.delete()
            Vault.wipeViewCache(context)
            File(context.filesDir, "vault").listFiles()?.forEach { it.delete() }
        }
    }

    @Test
    fun wrongAssociatedDataFailsAndDoesNotPublishAView() {
        val source = File(context.cacheDir, "vault-source-${UUID.randomUUID()}").apply {
            writeText("private training document")
        }
        val documentId = "doc-${UUID.randomUUID()}"
        try {
            val encrypted = Vault(context).encryptFrom(Uri.fromFile(source), documentId)
            var failed = false
            try {
                Vault(context).decryptToView(encrypted.encPath, "wrong-$documentId", "txt")
            } catch (_: Throwable) {
                failed = true
            }
            assertTrue(failed)
            assertFalse(File(context.cacheDir, "vault-view/wrong-$documentId.txt").exists())
        } finally {
            source.delete()
            Vault.wipeViewCache(context)
            File(context.filesDir, "vault").listFiles()?.forEach { it.delete() }
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
