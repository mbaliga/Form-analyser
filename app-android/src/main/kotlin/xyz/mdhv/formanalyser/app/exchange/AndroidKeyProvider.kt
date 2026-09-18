package xyz.mdhv.formanalyser.app.exchange

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import xyz.mdhv.formanalyser.exchange.KeyProvider
import xyz.mdhv.formanalyser.exchange.PubkeyIdentity
import xyz.mdhv.formanalyser.exchange.SignatureAlgorithms
import xyz.mdhv.formanalyser.exchange.SigningKeyProvider
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import javax.inject.Inject

/**
 * Android Keystore-backed [KeyProvider] for the Phase 5 export ceremony, and the [SigningKeyProvider]
 * that seals `.croc` exchange envelopes.
 *
 * Design choice (documented per the task): a single, non-exportable **EC P-256 keypair** generated
 * once under [ALIAS] inside the hardware-backed `AndroidKeyStore` is this device/athlete's stable
 * identity. Only the PUBLIC key ever leaves the Keystore — its X.509 `SubjectPublicKeyInfo` (DER)
 * encoding is deterministic and constant for the life of the key, so wrapping it in a
 * [PubkeyIdentity] yields a stable fingerprint that identifies every `.crocbak` this device produces.
 *
 * The private key never leaves secure storage: signing happens *inside* the Keystore, with this
 * class holding only an opaque handle. We deliberately avoid storing any secret in app-owned
 * storage — unlike a Tink-wrapped random seed, the raw private key material for this identity never
 * exists in the app process at all. Generation is idempotent: an existing key is reused, so the
 * identity survives app restarts and only changes if the user wipes app data (which also wipes every
 * table an export could describe).
 *
 * The key has been generated with `PURPOSE_SIGN or PURPOSE_VERIFY` and `DIGEST_SHA256` since the day
 * it was introduced, before anything signed anything — so keys already on athletes' devices can sign
 * `.croc` envelopes with no migration and no change of fingerprint. That matters: rotating the key to
 * gain a capability it already had would have invalidated every fingerprint a coach had pinned.
 *
 * No `setUserAuthenticationRequired`: signing an export the athlete just confirmed on-screen must not
 * raise a second biometric prompt, and the ceremony itself — not the key — is the consent gate.
 */
class AndroidKeyProvider @Inject constructor() : SigningKeyProvider {

    override val algorithm: String = SignatureAlgorithms.ECDSA_P256_SHA256

    override fun identity(): PubkeyIdentity = PubkeyIdentity.of(getOrCreatePublicKey().encoded)

    /**
     * Sign [bytes] with the Keystore-resident private key.
     *
     * [bytes] must be the EXACT bytes that get written to the envelope (`CrocSigning.seal` is what
     * guarantees that on the caller's behalf). `Signature.getInstance` resolves to the AndroidKeyStore
     * provider because the key handle came from it, so the SHA-256 digest is computed in the app and
     * only the ECDSA operation crosses into secure hardware.
     *
     * Throws if the key is missing or unusable. A device that cannot sign cannot produce a `.croc`,
     * and silently emitting an unsigned or empty-signature envelope would be far worse than failing:
     * the recipient's whole trust decision rests on that signature being real.
     */
    override fun sign(bytes: ByteArray): ByteArray =
        Signature.getInstance(algorithm).run {
            initSign(signingKey())
            update(bytes)
            sign()
        }

    /** The Keystore private key handle, generating the identity keypair first if it is absent. */
    private fun signingKey(): PrivateKey {
        (keystore().getKey(ALIAS, null) as? PrivateKey)?.let { return it }
        // No entry yet (first run, or the athlete has only ever looked at their fingerprint on a
        // build that never generated it). Generate, then re-open: an AndroidKeyStore instance is a
        // snapshot, so the freshly created entry is read through a newly loaded one.
        getOrCreatePublicKey()
        return keystore().getKey(ALIAS, null) as? PrivateKey
            ?: error("export identity key is missing from the Android Keystore")
    }

    private fun keystore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun getOrCreatePublicKey(): PublicKey {
        val ks = keystore()
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
