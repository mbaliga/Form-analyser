package xyz.mdhv.formanalyser.app.exchange

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import xyz.mdhv.formanalyser.exchange.KeyProvider
import xyz.mdhv.formanalyser.exchange.PubkeyIdentity
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PublicKey

/**
 * Android Keystore-backed [KeyProvider] for the Phase 5 export ceremony.
 *
 * Design choice (documented per the task): a single, non-exportable **EC P-256 keypair** generated
 * once under [ALIAS] inside the hardware-backed `AndroidKeyStore` is this device/athlete's stable
 * identity. Only the PUBLIC key ever leaves the Keystore — its X.509 `SubjectPublicKeyInfo` (DER)
 * encoding is deterministic and constant for the life of the key, so wrapping it in a
 * [PubkeyIdentity] yields a stable fingerprint that identifies every `.crocbak` this device produces.
 *
 * The private key never leaves secure storage and is never exercised here (Phase 5 does not sign the
 * archive — the manifest merely records *who* produced it). We deliberately avoid storing any secret
 * in app-owned storage: unlike a Tink-wrapped random seed, the raw private key material for this
 * identity never exists in the app process at all. Generation is idempotent — an existing key is
 * reused, so the identity survives app restarts and only changes if the user wipes app data (which
 * also wipes every table an export could describe).
 */
class AndroidKeyProvider : KeyProvider {

    override fun identity(): PubkeyIdentity = PubkeyIdentity.of(getOrCreatePublicKey().encoded)

    private fun getOrCreatePublicKey(): PublicKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        ks.getCertificate(ALIAS)?.publicKey?.let { return it }

        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        generator.initialize(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            )
                .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build(),
        )
        return generator.generateKeyPair().public
    }

    companion object {
        /** Keystore alias for the durable export identity key. */
        const val ALIAS: String = "crocodyl_export_identity"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}
