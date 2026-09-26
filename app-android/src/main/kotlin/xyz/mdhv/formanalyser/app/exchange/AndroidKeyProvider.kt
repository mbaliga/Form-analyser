package xyz.mdhv.formanalyser.app.exchange

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import xyz.mdhv.formanalyser.exchange.KeyProvider
import xyz.mdhv.formanalyser.exchange.PubkeyIdentity
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.PrivateKey
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
 * The private key never leaves secure storage. It is used in-place by the Keystore to sign a
 * person-to-person `.croc` checksum; raw private key material never exists in the app process.
 * Generation is idempotent — an existing key is
 * reused, so the identity survives app restarts and only changes if the user wipes app data (which
 * also wipes every table an export could describe).
 */
class AndroidKeyProvider : KeyProvider {

    override fun identity(): PubkeyIdentity = PubkeyIdentity.of(getOrCreatePublicKey().encoded)

    /** Sign the supplied checksum with the non-exportable Keystore key. */
    fun signChecksum(checksum: String): ByteArray {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val privateKey = ks.getKey(ALIAS, null) as? PrivateKey ?: run {
            getOrCreatePublicKey()
            (KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }).getKey(ALIAS, null) as? PrivateKey
        } ?: error("Crocodyl export identity private key is unavailable")
        return Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initSign(privateKey)
            update(checksum.toByteArray(Charsets.UTF_8))
            sign()
        }
    }

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
        const val SIGNATURE_ALGORITHM: String = "SHA256withECDSA"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}
