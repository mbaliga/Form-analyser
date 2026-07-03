package xyz.mdhv.formanalyser.app.vault

import android.content.Context
import android.net.Uri
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import java.io.File
import java.security.MessageDigest

/**
 * Encrypted document vault (Phase 3 §C). Tink streaming AEAD (AES256_GCM_HKDF_4KB) with the
 * keyset wrapped by Android Keystore — androidx.security-crypto is deprecated and deliberately
 * not used. Ciphertext is bound to its Room row id via associated data. Decrypted views live
 * only in cacheDir/vault-view and are wiped on app start and viewer close.
 *
 * Note on deletion: ciphertext removal is a plain file delete — flash overwrite is theater and
 * we don't pretend otherwise.
 */
class Vault(private val context: Context) {

    private val streamingAead: StreamingAead by lazy {
        StreamingAeadConfig.register()
        val handle = AndroidKeysetManager.Builder()
            .withSharedPref(context, "crocodyl_vault_keyset", "crocodyl_vault_prefs")
            .withKeyTemplate(KeyTemplates.get("AES256_GCM_HKDF_4KB"))
            .withMasterKeyUri("android-keystore://crocodyl_vault_master")
            .build()
            .keysetHandle
        handle.getPrimitive(StreamingAead::class.java)
    }

    private fun vaultDir(): File = File(context.filesDir, "vault").apply { mkdirs() }
    private fun viewDir(): File = File(context.cacheDir, "vault-view").apply { mkdirs() }

    data class Encrypted(val encPath: String, val sha256: String, val sizeBytes: Long)

    /** Stream-encrypt [source] into the vault. AD = the document row id. Max 25 MB. */
    fun encryptFrom(source: Uri, documentId: String): Encrypted {
        val outFile = File(vaultDir(), documentId)
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        context.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "cannot open source" }
            streamingAead.newEncryptingStream(outFile.outputStream(), documentId.toByteArray()).use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    total += n
                    require(total <= MAX_BYTES) {
                        outFile.delete()
                        "Document larger than 25 MB"
                    }
                    digest.update(buf, 0, n)
                    out.write(buf, 0, n)
                }
            }
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        return Encrypted(outFile.absolutePath, sha, total)
    }

    /** Decrypt to the view cache for display; caller shows it, [wipeViewCache] cleans up. */
    fun decryptToView(encPath: String, documentId: String, ext: String): File {
        val outFile = File(viewDir(), "$documentId.$ext")
        File(encPath).inputStream().use { fileIn ->
            streamingAead.newDecryptingStream(fileIn, documentId.toByteArray()).use { input ->
                outFile.outputStream().use { input.copyTo(it) }
            }
        }
        return outFile
    }

    fun delete(encPath: String) {
        runCatching { File(encPath).delete() }
    }

    fun wipeViewCache() {
        runCatching { viewDir().listFiles()?.forEach { it.delete() } }
    }

    companion object {
        const val MAX_BYTES = 25L * 1024 * 1024
    }
}
