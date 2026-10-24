package xyz.mdhv.formanalyser.app.vault

import android.content.Context
import android.net.Uri
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import java.io.File
import java.io.IOException
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
        require(documentId.matches(SAFE_COMPONENT)) { "Invalid document id" }
        val outFile = File(vaultDir(), documentId)
        check(!outFile.exists()) { "Document already exists" }
        val stagingFile = File(vaultDir(), ".${documentId}.partial")
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        try {
            context.contentResolver.openInputStream(source).use { input ->
                requireNotNull(input) { "cannot open source" }
                streamingAead.newEncryptingStream(
                    stagingFile.outputStream(),
                    documentId.toByteArray(),
                ).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        total += n
                        require(total <= MAX_BYTES) { "Document larger than 25 MB" }
                        digest.update(buf, 0, n)
                        out.write(buf, 0, n)
                    }
                }
            }
            if (!stagingFile.renameTo(outFile)) throw IOException("Could not publish encrypted document")
        } catch (t: Throwable) {
            runCatching { stagingFile.delete() }
            runCatching { outFile.delete() }
            throw t
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        return Encrypted(outFile.absolutePath, sha, total)
    }

    /** Decrypt to the view cache for display; caller shows it, [wipeViewCache] cleans up. */
    fun decryptToView(encPath: String, documentId: String, ext: String): File {
        require(documentId.matches(SAFE_COMPONENT)) { "Invalid document id" }
        require(ext.matches(SAFE_EXTENSION)) { "Invalid document extension" }
        val dir = viewDir()
        val outFile = File(dir, "$documentId.$ext")
        // Never expose a partially decrypted document to the viewer. Decrypt into a unique
        // sibling first, then publish it only after authentication and the full copy succeed.
        val stagingFile = File.createTempFile(".$documentId-", ".$ext.partial", dir)
        try {
            File(encPath).inputStream().use { fileIn ->
                streamingAead.newDecryptingStream(fileIn, documentId.toByteArray()).use { input ->
                    stagingFile.outputStream().use { input.copyTo(it) }
                }
            }
            if (outFile.exists() && !outFile.delete()) {
                throw IOException("Could not replace decrypted document view")
            }
            if (!stagingFile.renameTo(outFile)) {
                throw IOException("Could not publish decrypted document view")
            }
        } catch (t: Throwable) {
            runCatching { stagingFile.delete() }
            throw t
        }
        return outFile
    }

    fun delete(encPath: String) {
        runCatching {
            val vault = vaultDir().canonicalFile
            val candidate = File(encPath).canonicalFile
            if (candidate.parentFile == vault && candidate.name.matches(SAFE_COMPONENT)) {
                candidate.delete()
            }
        }
    }

    fun wipeViewCache() {
        runCatching { viewDir().listFiles()?.forEach { it.delete() } }
    }

    companion object {
        const val MAX_BYTES = 25L * 1024 * 1024
        private val SAFE_COMPONENT = Regex("[A-Za-z0-9_-]{1,128}")
        private val SAFE_EXTENSION = Regex("[A-Za-z0-9]{1,16}")

        /** Remove plaintext viewer output without constructing the Keystore-backed vault. */
        fun wipeViewCache(context: Context) {
            runCatching { File(context.cacheDir, "vault-view").deleteRecursively() }
        }
    }
}
