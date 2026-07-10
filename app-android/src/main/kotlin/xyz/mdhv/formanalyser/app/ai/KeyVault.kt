package xyz.mdhv.formanalyser.app.ai

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import xyz.mdhv.formanalyser.coach.Provider

/**
 * BYOK API-key store. Per-provider cloud API keys, encrypted with Tink [Aead] (AES256_GCM) under a
 * keyset wrapped by the Android Keystore — the same pattern as [xyz.mdhv.formanalyser.app.vault.Vault],
 * but AEAD (not StreamingAead) since keys are tiny secrets, and a distinct keyset/pref/master-key so
 * document ciphertext and BYOK keys never share a wrapping key.
 *
 * Ciphertext is base64-encoded and held in a dedicated SharedPreferences file keyed by [Provider.name].
 * The associated data binds each ciphertext to its provider so a key blob cannot be replayed under a
 * different provider. Keys are never logged, never exported, never written to Room or DataStore.
 */
class KeyVault(private val context: Context) {

    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(Aead::class.java)
    }

    private val store by lazy {
        context.getSharedPreferences(CIPHERTEXT_PREFS, Context.MODE_PRIVATE)
    }

    private fun prefKey(provider: Provider): String = provider.name

    /** Encrypt and store [key] for [provider]. A blank key clears the entry instead. */
    fun setKey(provider: Provider, key: String) {
        if (key.isBlank()) {
            clearKey(provider)
            return
        }
        val ciphertext = aead.encrypt(key.toByteArray(Charsets.UTF_8), provider.name.toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        store.edit().putString(prefKey(provider), encoded).apply()
    }

    /** Decrypt and return the stored key for [provider], or null if none / undecryptable. */
    fun getKey(provider: Provider): String? {
        val encoded = store.getString(prefKey(provider), null) ?: return null
        return runCatching {
            val ciphertext = Base64.decode(encoded, Base64.NO_WRAP)
            val plaintext = aead.decrypt(ciphertext, provider.name.toByteArray(Charsets.UTF_8))
            String(plaintext, Charsets.UTF_8)
        }.getOrNull()
    }

    fun clearKey(provider: Provider) {
        store.edit().remove(prefKey(provider)).apply()
    }

    fun hasKey(provider: Provider): Boolean = store.contains(prefKey(provider))

    companion object {
        private const val KEYSET_NAME = "crocodyl_byok_keyset"
        private const val KEYSET_PREFS = "crocodyl_byok_prefs"
        private const val MASTER_KEY_URI = "android-keystore://crocodyl_byok_master"
        private const val CIPHERTEXT_PREFS = "crocodyl_byok_keys"
    }
}
