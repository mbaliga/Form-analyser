package xyz.mdhv.formanalyser.exchange

import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Direct tests of the [Verifier] seam. These are the checks that must hold for a recipient with
 * nothing but a JDK — no Android, no third-party crypto — because the same verifier has to run in
 * the app, here, and later in the static web viewer.
 */
class SigningTest {

    private fun keyPair(curve: String = "secp256r1") =
        KeyPairGenerator.getInstance("EC")
            .apply { initialize(ECGenParameterSpec(curve)) }
            .generateKeyPair()

    private fun sign(key: java.security.PrivateKey, bytes: ByteArray, alg: String) =
        Signature.getInstance(alg).run {
            initSign(key)
            update(bytes)
            sign()
        }

    @Test
    fun `a P-256 signature over the exact payload verifies`() {
        val pair = keyPair()
        val payload = "the exact bytes that were signed".toByteArray()
        val sig = sign(pair.private, payload, SignatureAlgorithms.ECDSA_P256_SHA256)

        assertEquals(
            SignatureOutcome.VALID,
            EcdsaP256Verifier.verify(
                payload,
                sig,
                pair.public.encoded,
                SignatureAlgorithms.ECDSA_P256_SHA256,
            ),
        )
    }

    @Test
    fun `a different payload does not verify`() {
        val pair = keyPair()
        val sig = sign(pair.private, "a".toByteArray(), SignatureAlgorithms.ECDSA_P256_SHA256)
        assertEquals(
            SignatureOutcome.INVALID,
            EcdsaP256Verifier.verify(
                "b".toByteArray(),
                sig,
                pair.public.encoded,
                SignatureAlgorithms.ECDSA_P256_SHA256,
            ),
        )
    }

    @Test
    fun `a different key does not verify`() {
        val signer = keyPair()
        val other = keyPair()
        val payload = "x".toByteArray()
        val sig = sign(signer.private, payload, SignatureAlgorithms.ECDSA_P256_SHA256)
        assertEquals(
            SignatureOutcome.INVALID,
            EcdsaP256Verifier.verify(
                payload,
                sig,
                other.public.encoded,
                SignatureAlgorithms.ECDSA_P256_SHA256,
            ),
        )
    }

    @Test
    fun `garbage key and signature bytes come back as outcomes, never exceptions`() {
        val pair = keyPair()
        val payload = "x".toByteArray()
        val sig = sign(pair.private, payload, SignatureAlgorithms.ECDSA_P256_SHA256)

        assertEquals(
            SignatureOutcome.MALFORMED_PUBLIC_KEY,
            EcdsaP256Verifier.verify(payload, sig, ByteArray(0), SignatureAlgorithms.ECDSA_P256_SHA256),
        )
        assertEquals(
            SignatureOutcome.MALFORMED_PUBLIC_KEY,
            EcdsaP256Verifier.verify(
                payload,
                sig,
                byteArrayOf(1, 2, 3, 4, 5),
                SignatureAlgorithms.ECDSA_P256_SHA256,
            ),
        )
        assertEquals(
            SignatureOutcome.MALFORMED_SIGNATURE,
            EcdsaP256Verifier.verify(
                payload,
                ByteArray(0),
                pair.public.encoded,
                SignatureAlgorithms.ECDSA_P256_SHA256,
            ),
        )
        // A non-DER signature body throws inside the JCA; the seam turns that into INVALID.
        assertEquals(
            SignatureOutcome.INVALID,
            EcdsaP256Verifier.verify(
                payload,
                byteArrayOf(9, 9, 9, 9),
                pair.public.encoded,
                SignatureAlgorithms.ECDSA_P256_SHA256,
            ),
        )
    }

    @Test
    fun `an RSA key is not mistaken for an EC identity`() {
        val rsa = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        assertEquals(
            SignatureOutcome.MALFORMED_PUBLIC_KEY,
            EcdsaP256Verifier.verify(
                "x".toByteArray(),
                byteArrayOf(1, 2, 3),
                rsa.public.encoded,
                SignatureAlgorithms.ECDSA_P256_SHA256,
            ),
        )
    }

    @Test
    fun `a valid P-384 signature is refused because the suite is P-256`() {
        // SHA256withECDSA verifies P-384 happily. Accepting it would silently widen the suite past
        // what SignatureAlgorithms declares and past what a fingerprint's length implies to a human.
        val pair = keyPair("secp384r1")
        val payload = "x".toByteArray()
        val sig = sign(pair.private, payload, SignatureAlgorithms.ECDSA_P256_SHA256)
        assertEquals(
            SignatureOutcome.MALFORMED_PUBLIC_KEY,
            EcdsaP256Verifier.verify(
                payload,
                sig,
                pair.public.encoded,
                SignatureAlgorithms.ECDSA_P256_SHA256,
            ),
        )
    }

    @Test
    fun `an unknown suite name is refused without touching the key`() {
        assertEquals(
            SignatureOutcome.UNSUPPORTED_ALGORITHM,
            EcdsaP256Verifier.verify(
                "x".toByteArray(),
                byteArrayOf(1),
                byteArrayOf(1),
                "SHA1withECDSA",
            ),
        )
    }

    @Test
    fun `TOFU distinguishes first contact, match and mismatch`() {
        val a = keyPair().public.encoded
        val b = keyPair().public.encoded
        assertEquals(SenderTrust.FIRST_CONTACT, TofuTrust.evaluate(null, a))
        assertEquals(SenderTrust.PINNED_MATCH, TofuTrust.evaluate(a.copyOf(), a))
        assertEquals(SenderTrust.PINNED_MISMATCH, TofuTrust.evaluate(b, a))
    }

    @Test
    fun `payload checksum binds names and is order-independent`() {
        val a = CrocPayloadEntry("session", "[1]".toByteArray())
        val b = CrocPayloadEntry("shot", "[2]".toByteArray())
        assertEquals(PayloadChecksum.of(listOf(a, b)), PayloadChecksum.of(listOf(b, a)))

        // Swapping the names while keeping the bytes must change the checksum — the failure a
        // bytes-only digest cannot see.
        val swapped =
            listOf(
                CrocPayloadEntry("shot", "[1]".toByteArray()),
                CrocPayloadEntry("session", "[2]".toByteArray()),
            )
        assert(PayloadChecksum.of(listOf(a, b)) != PayloadChecksum.of(swapped))
        assert(PayloadChecksum.of(listOf(a, b)).startsWith(PayloadChecksum.PREFIX))
    }
}
