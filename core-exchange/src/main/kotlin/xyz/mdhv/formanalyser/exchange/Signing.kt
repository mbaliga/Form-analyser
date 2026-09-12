package xyz.mdhv.formanalyser.exchange

import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.X509EncodedKeySpec

/**
 * The signature suites this module understands.
 *
 * Exactly one is defined, deliberately. A `.croc` recipient must be able to verify an envelope with
 * nothing but the JDK — no BouncyCastle, no Android — because the same verifier has to run in the
 * app, in pure-JVM tests, and (compiled forward) in the static web viewer. ECDSA P-256 + SHA-256 is
 * the intersection of "hardware-backed and non-exportable in the Android Keystore" and "verifiable
 * from `java.security` alone", so it is the whole list until something forces a second entry.
 *
 * The suite name is recorded INSIDE the signed manifest (see `CrocManifest.signatureAlgorithm`) as
 * well as in the unsigned sidecar, so an attacker cannot quietly downgrade a reader to a weaker
 * suite: the two copies disagreeing is itself a verification failure.
 */
object SignatureAlgorithms {
    /** JCA name for ECDSA over P-256 with SHA-256, DER-encoded `SEQUENCE { r, s }` output. */
    const val ECDSA_P256_SHA256: String = "SHA256withECDSA"

    /** Bit length of the P-256 prime field; the only curve accepted by [EcdsaP256Verifier]. */
    const val P256_FIELD_SIZE_BITS: Int = 256

    /** JCA [KeyFactory] algorithm used to rebuild a public key from X.509 SPKI bytes. */
    const val EC_KEY_FACTORY: String = "EC"
}

/**
 * The signing half of the exchange seam — the capability [KeyProvider] deliberately does not have.
 *
 * Only the platform layer implements this. On Android the private key lives in the hardware-backed
 * Keystore and never enters the app process, so "signing" is necessarily a call OUT of the core;
 * modelling it as an interface is what keeps `core-exchange` free of private-key material and free
 * of platform APIs. Tests supply a plain in-memory EC keypair through the same seam.
 *
 * Implementations must be pure functions of [sign]'s argument: the same bytes must always produce a
 * signature that verifies. (ECDSA itself is randomised — the signature bytes differ per call — which
 * is why nothing in this module ever compares two signatures for equality.)
 */
interface Signer {
    /** The [SignatureAlgorithms] suite this signer produces. Stamped into the manifest it signs. */
    val algorithm: String

    /**
     * Sign [bytes] — always the *exact* bytes that will be written to the envelope, never a
     * re-serialization of them. Throws (rather than returning empty) if the key is unavailable: a
     * missing signing key is a broken device state, not a routine outcome.
     */
    fun sign(bytes: ByteArray): ByteArray
}

/**
 * A [KeyProvider] that can also sign — the shape the Android Keystore provider actually has.
 *
 * Kept as a separate interface rather than widening [KeyProvider] so that the many read-only
 * identity call sites (the `.crocbak` export ceremony, any fake in a test) are unaffected, and so
 * that "this code can sign as the athlete" is a visible, deliberate type in a signature rather than
 * an ambient capability every holder of a [KeyProvider] silently gains.
 */
interface SigningKeyProvider : KeyProvider, Signer

/** Why a signature check did not come back valid. Never merged into a single boolean. */
enum class SignatureOutcome {
    /** The signature verifies against the supplied public key over the supplied payload. */
    VALID,

    /** Well-formed inputs, but the signature does not match this payload/key pair. */
    INVALID,

    /** The public key bytes are not a P-256 X.509 SubjectPublicKeyInfo. */
    MALFORMED_PUBLIC_KEY,

    /** The signature bytes are absent or not a parseable DER ECDSA signature. */
    MALFORMED_SIGNATURE,

    /** The named suite is not one this build knows how to verify. */
    UNSUPPORTED_ALGORITHM,
}

/**
 * The verifying half of the exchange seam.
 *
 * Takes RAW public-key bytes rather than a `java.security.PublicKey` on purpose: the recipient only
 * ever has what the envelope carried (and what it previously pinned), which is a byte string. Key
 * reconstruction is part of what has to be checked, so it belongs behind this seam, not in front of
 * it.
 *
 * Implementations must never throw for hostile input — a corrupt or attacker-supplied envelope is
 * an ordinary input to this function, and it must come back as an outcome the importer can show.
 */
interface Verifier {
    fun verify(
        payload: ByteArray,
        signature: ByteArray,
        publicKeyBytes: ByteArray,
        algorithm: String,
    ): SignatureOutcome
}

/**
 * The reference [Verifier]: ECDSA P-256 / SHA-256 using only `java.security`.
 *
 * `publicKeyBytes` is an X.509 `SubjectPublicKeyInfo` DER encoding — exactly what
 * `java.security.PublicKey.encoded` returns for the Android Keystore key, and what
 * `AndroidKeyProvider` already wraps into a [PubkeyIdentity]. So the bytes an athlete's device
 * publishes as its identity ARE the bytes a recipient rebuilds a verification key from; there is no
 * second encoding to keep in step.
 *
 * The curve is checked explicitly. `SHA256withECDSA` will happily verify a P-384 or P-521 signature,
 * so accepting any EC key would silently widen the suite beyond what [SignatureAlgorithms] declares
 * and beyond what the fingerprint's length implies to a human comparing two cards.
 */
object EcdsaP256Verifier : Verifier {

    override fun verify(
        payload: ByteArray,
        signature: ByteArray,
        publicKeyBytes: ByteArray,
        algorithm: String,
    ): SignatureOutcome {
        if (algorithm != SignatureAlgorithms.ECDSA_P256_SHA256) {
            return SignatureOutcome.UNSUPPORTED_ALGORITHM
        }
        if (signature.isEmpty()) return SignatureOutcome.MALFORMED_SIGNATURE
        if (publicKeyBytes.isEmpty()) return SignatureOutcome.MALFORMED_PUBLIC_KEY

        val key =
            try {
                KeyFactory.getInstance(SignatureAlgorithms.EC_KEY_FACTORY)
                    .generatePublic(X509EncodedKeySpec(publicKeyBytes))
            } catch (_: GeneralSecurityException) {
                return SignatureOutcome.MALFORMED_PUBLIC_KEY
            } catch (_: IllegalArgumentException) {
                return SignatureOutcome.MALFORMED_PUBLIC_KEY
            }

        val fieldSize = (key as? ECPublicKey)?.params?.curve?.field?.fieldSize
        if (fieldSize != SignatureAlgorithms.P256_FIELD_SIZE_BITS) {
            return SignatureOutcome.MALFORMED_PUBLIC_KEY
        }

        return try {
            val sig = Signature.getInstance(SignatureAlgorithms.ECDSA_P256_SHA256)
            sig.initVerify(key)
            sig.update(payload)
            // A malformed DER body usually throws SignatureException here rather than returning
            // false; both mean "this is not a signature over this payload by this key", and the
            // importer treats them identically, so they are not split into separate outcomes.
            if (sig.verify(signature)) SignatureOutcome.VALID else SignatureOutcome.INVALID
        } catch (_: GeneralSecurityException) {
            SignatureOutcome.INVALID
        }
    }
}

/**
 * How much a recipient already knew about the key that signed an envelope.
 *
 * This is an axis SEPARATE from cryptographic validity, and conflating the two is the classic
 * mistake: a perfectly valid signature by a key you have never seen proves only that whoever holds
 * that key wrote this file — not that they are the coach you think they are.
 */
enum class SenderTrust {
    /** No key was pinned for this sender yet. Trust-on-first-use: show it, let the human pin it. */
    FIRST_CONTACT,

    /** The presented key is byte-identical to the pinned one. The ordinary, boring case. */
    PINNED_MATCH,

    /**
     * A key was pinned for this sender and a DIFFERENT one signed this envelope. Quarantine: the
     * envelope is never auto-imported and the mismatch is shown, because the innocent explanation
     * (they reinstalled) and the hostile one (someone is impersonating them) look identical from
     * here and only the human can tell them apart.
     */
    PINNED_MISMATCH,
}

/**
 * Trust-on-first-use evaluation. Pure byte comparison, so it is fully unit-testable.
 *
 * Note WHY the pin is looked up by the sender's human label and not by their fingerprint:
 * [Fingerprint.format] is plain hex of the whole key, so fingerprint and key bytes determine each
 * other. A pin keyed by fingerprint could therefore never report a mismatch — a new key is simply a
 * new fingerprint, i.e. a stranger. Detecting "the person I call Coach Anna is now presenting a
 * different key" requires the durable handle to be the relationship, which is why `CrocManifest`
 * carries a self-asserted `senderLabel` and the recipient's store maps label -> pinned key bytes.
 */
object TofuTrust {
    /**
     * @param pinnedKeyBytes the key previously pinned for this sender, or null on first contact.
     * @param presentedKeyBytes the key that actually signed the envelope in hand.
     */
    fun evaluate(pinnedKeyBytes: ByteArray?, presentedKeyBytes: ByteArray): SenderTrust =
        when {
            pinnedKeyBytes == null -> SenderTrust.FIRST_CONTACT
            pinnedKeyBytes.contentEquals(presentedKeyBytes) -> SenderTrust.PINNED_MATCH
            else -> SenderTrust.PINNED_MISMATCH
        }
}
