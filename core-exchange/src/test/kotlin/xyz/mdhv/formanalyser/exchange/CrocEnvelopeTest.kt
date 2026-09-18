package xyz.mdhv.formanalyser.exchange

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Exercises the whole `.croc` seal/verify loop on plain JVM crypto.
 *
 * The point of the seam is that the verifier needs nothing but `java.security`, so these tests use a
 * software P-256 keypair in exactly the way `AndroidKeyProvider` uses a Keystore-resident one: sign
 * over the manifest bytes with `SHA256withECDSA`, publish `PublicKey.encoded` (X.509 SPKI) as the
 * identity. If this passes here, the only untested step on device is the Keystore lookup itself.
 */
class CrocEnvelopeTest {

    /** A [SigningKeyProvider] over an in-memory P-256 keypair — the test double for the Keystore. */
    private class TestSigningKeyProvider(private val pair: KeyPair) : SigningKeyProvider {
        override val algorithm: String = SignatureAlgorithms.ECDSA_P256_SHA256

        override fun identity(): PubkeyIdentity = PubkeyIdentity.of(pair.public.encoded)

        override fun sign(bytes: ByteArray): ByteArray = sign(pair.private, bytes)

        companion object {
            fun sign(key: PrivateKey, bytes: ByteArray): ByteArray =
                Signature.getInstance(SignatureAlgorithms.ECDSA_P256_SHA256).run {
                    initSign(key)
                    update(bytes)
                    sign()
                }
        }
    }

    private fun newProvider(): TestSigningKeyProvider =
        TestSigningKeyProvider(
            KeyPairGenerator.getInstance("EC")
                .apply { initialize(ECGenParameterSpec("secp256r1")) }
                .generateKeyPair()
        )

    private fun payload(vararg tables: Pair<String, String>): List<CrocPayloadEntry> =
        tables.map { (name, body) -> CrocPayloadEntry(name, body.toByteArray()) }

    private fun manifestFor(
        signer: SigningKeyProvider,
        payload: List<CrocPayloadEntry>,
        tier: ExportTier = ExportTier.SHAREABLE_ONLY,
        grants: Set<String> = emptySet(),
    ): CrocManifest {
        val decision =
            ConsentFilter.filter(payload.map { it.logicalTable }.toSet(), tier, grants)
        return CrocManifest.fromDecision(
            appVersion = "0.6.0-test",
            createdAtMs = 1_700_000_000_000L,
            kind = CrocEnvelopeKind.ATHLETE_REPORT,
            sender = signer.identity(),
            senderLabel = "Test athlete",
            tier = tier,
            decision = decision,
            payloadChecksum = PayloadChecksum.of(payload),
            rowCounts = payload.associate { it.logicalTable to 1L },
        )
    }

    private fun verdict(
        sealed: SealedCroc,
        payload: List<CrocPayloadEntry>,
        pinned: ByteArray? = null,
    ): CrocVerdict =
        CrocVerifier.verify(
            manifestBytes = sealed.manifestBytes,
            signatureBytesJson = sealed.signature.serialize(),
            payload = payload,
            pinnedSenderKey = pinned,
        )

    @Test
    fun `a sealed envelope verifies and reports first contact`() {
        val signer = newProvider()
        val payload = payload("session" to "[]", "score_session" to "[{}]")
        val sealed = CrocSigning.seal(manifestFor(signer, payload), signer)

        val v = assertIs<CrocVerdict.Verified>(verdict(sealed, payload))
        assertEquals(signer.identity(), v.sender)
        assertEquals(signer.identity().fingerprint, v.manifest.senderFingerprint)
        assertEquals(CrocEnvelopeKind.ATHLETE_REPORT, v.manifest.kindOrNull())
        assertEquals(ExportTier.SHAREABLE_ONLY, v.manifest.tierOrNull())
        assertEquals(SenderTrust.FIRST_CONTACT, v.trust)
        assertTrue(!v.requiresQuarantine)
    }

    @Test
    fun `signing is over the exact bytes written, and the manifest round-trips`() {
        val signer = newProvider()
        val payload = payload("session" to "[]")
        val sealed = CrocSigning.seal(manifestFor(signer, payload), signer)

        assertEquals(
            sealed.manifest,
            CrocManifest.deserialize(sealed.manifestBytes.toString(Charsets.UTF_8)),
        )
        // ECDSA is randomised: two seals of the same manifest differ byte-wise and both verify.
        val again = CrocSigning.seal(sealed.manifest, signer)
        assertNotEquals(sealed.signature.signatureB64, again.signature.signatureB64)
        assertIs<CrocVerdict.Verified>(verdict(again, payload))
    }

    @Test
    fun `a pinned key that matches is PINNED_MATCH, a different key quarantines`() {
        val signer = newProvider()
        val payload = payload("session" to "[]")
        val sealed = CrocSigning.seal(manifestFor(signer, payload), signer)

        val match = assertIs<CrocVerdict.Verified>(
            verdict(sealed, payload, pinned = signer.identity().keyBytes)
        )
        assertEquals(SenderTrust.PINNED_MATCH, match.trust)

        // Same label, different key: cryptographically fine, socially a red flag. Quarantine.
        val stranger = newProvider()
        val mismatch = assertIs<CrocVerdict.Verified>(
            verdict(sealed, payload, pinned = stranger.identity().keyBytes)
        )
        assertEquals(SenderTrust.PINNED_MISMATCH, mismatch.trust)
        assertTrue(mismatch.requiresQuarantine)
    }

    @Test
    fun `tampering with a single manifest byte fails the signature`() {
        val signer = newProvider()
        val payload = payload("session" to "[]")
        val sealed = CrocSigning.seal(manifestFor(signer, payload), signer)

        val doctored = sealed.manifestBytes.copyOf()
        // Flip a bit somewhere in the middle of the JSON; the parse still succeeds often enough
        // that this is a real test of the signature rather than of the parser.
        val text = doctored.toString(Charsets.UTF_8).replace("0.6.0-test", "9.9.9-test")
        val r =
            assertIs<CrocVerdict.Rejected>(
                CrocVerifier.verify(
                    manifestBytes = text.toByteArray(),
                    signatureBytesJson = sealed.signature.serialize(),
                    payload = payload,
                )
            )
        assertEquals(CrocRejection.SIGNATURE_INVALID, r.reason)
    }

    @Test
    fun `a signature from another key is rejected`() {
        val signer = newProvider()
        val impostor = newProvider()
        val payload = payload("session" to "[]")
        val sealed = CrocSigning.seal(manifestFor(signer, payload), signer)

        val forged =
            CrocSignature(
                algorithm = SignatureAlgorithms.ECDSA_P256_SHA256,
                signatureB64 =
                    Base64.getEncoder().encodeToString(impostor.sign(sealed.manifestBytes)),
            )
        val r =
            assertIs<CrocVerdict.Rejected>(
                CrocVerifier.verify(sealed.manifestBytes, forged.serialize(), payload)
            )
        assertEquals(CrocRejection.SIGNATURE_INVALID, r.reason)
    }

    @Test
    fun `a fingerprint that does not match the carried key is rejected before verifying`() {
        val signer = newProvider()
        val payload = payload("session" to "[]")
        val doctored = manifestFor(signer, payload).copy(senderFingerprint = "DEAD-BEEF")
        val sealed = CrocSigning.seal(doctored, signer)

        val r = assertIs<CrocVerdict.Rejected>(verdict(sealed, payload))
        assertEquals(CrocRejection.FINGERPRINT_MISMATCH, r.reason)
    }

    @Test
    fun `a downgraded sidecar algorithm is a visible mismatch`() {
        val signer = newProvider()
        val payload = payload("session" to "[]")
        val sealed = CrocSigning.seal(manifestFor(signer, payload), signer)

        val downgraded = sealed.signature.copy(algorithm = "SHA1withECDSA")
        val r =
            assertIs<CrocVerdict.Rejected>(
                CrocVerifier.verify(sealed.manifestBytes, downgraded.serialize(), payload)
            )
        assertEquals(CrocRejection.ALGORITHM_MISMATCH, r.reason)
    }

    @Test
    fun `an unknown schema version is refused, not guessed at`() {
        val signer = newProvider()
        val payload = payload("session" to "[]")
        val future = manifestFor(signer, payload).copy(schemaVersion = 99)
        val sealed = CrocSigning.seal(future, signer)

        val r = assertIs<CrocVerdict.Rejected>(verdict(sealed, payload))
        assertEquals(CrocRejection.UNSUPPORTED_SCHEMA_VERSION, r.reason)
    }

    @Test
    fun `payload that does not match the signed checksum is rejected`() {
        val signer = newProvider()
        val payload = payload("session" to "[]")
        val sealed = CrocSigning.seal(manifestFor(signer, payload), signer)

        val swapped = payload("session" to "[{\"id\":\"injected\"}]")
        val r = assertIs<CrocVerdict.Rejected>(verdict(sealed, swapped))
        assertEquals(CrocRejection.PAYLOAD_CHECKSUM_MISMATCH, r.reason)
    }

    @Test
    fun `an extra table in the zip that the manifest never claimed is rejected`() {
        val signer = newProvider()
        val payload = payload("session" to "[]")
        val sealed = CrocSigning.seal(manifestFor(signer, payload), signer)

        val extra = payload + CrocPayloadEntry("shot", "[]".toByteArray())
        val r = assertIs<CrocVerdict.Rejected>(verdict(sealed, extra))
        assertEquals(CrocRejection.PAYLOAD_TABLE_SET_MISMATCH, r.reason)
        assertTrue(r.detail.contains("shot"))
    }

    @Test
    fun `a validly signed envelope claiming a PRIVATE table is still refused`() {
        val signer = newProvider()
        // Hand-built, bypassing fromDecision: this is what a hostile or broken sender would emit.
        val payload = payload("session" to "[]", "mood_entry" to "[{}]")
        val manifest =
            CrocManifest(
                schemaVersion = CrocManifest.CURRENT_SCHEMA_VERSION,
                appVersion = "0.6.0-test",
                createdAtMs = 1_700_000_000_000L,
                kindName = CrocEnvelopeKind.ATHLETE_REPORT.name,
                senderPubkeyB64 =
                    Base64.getEncoder().encodeToString(signer.identity().keyBytes),
                senderFingerprint = signer.identity().fingerprint,
                tierName = ExportTier.FULL.name,
                includedTables = listOf("mood_entry", "session"),
                payloadChecksum = PayloadChecksum.of(payload),
                signatureAlgorithm = SignatureAlgorithms.ECDSA_P256_SHA256,
            )
        val sealed = CrocSigning.seal(manifest, signer)

        val r = assertIs<CrocVerdict.Rejected>(verdict(sealed, payload))
        assertEquals(CrocRejection.PRIVATE_TABLE_PRESENT, r.reason)
        assertTrue(r.detail.contains("mood_entry"))
    }

    @Test
    fun `a MEDICAL table claimed under the SHAREABLE_ONLY tier contradicts its own tier`() {
        val signer = newProvider()
        val payload = payload("session" to "[]", "medication_entry" to "[{}]")
        val manifest =
            CrocManifest(
                schemaVersion = CrocManifest.CURRENT_SCHEMA_VERSION,
                appVersion = "0.6.0-test",
                createdAtMs = 1L,
                kindName = CrocEnvelopeKind.ATHLETE_REPORT.name,
                senderPubkeyB64 =
                    Base64.getEncoder().encodeToString(signer.identity().keyBytes),
                senderFingerprint = signer.identity().fingerprint,
                tierName = ExportTier.SHAREABLE_ONLY.name,
                includedTables = listOf("medication_entry", "session"),
                payloadChecksum = PayloadChecksum.of(payload),
                signatureAlgorithm = SignatureAlgorithms.ECDSA_P256_SHA256,
            )
        val sealed = CrocSigning.seal(manifest, signer)

        val r = assertIs<CrocVerdict.Rejected>(verdict(sealed, payload))
        assertEquals(CrocRejection.TIER_CONTRADICTION, r.reason)
    }

    @Test
    fun `a granted MEDICAL table under FULL is accepted`() {
        val signer = newProvider()
        val payload = payload("session" to "[]", "medication_entry" to "[{}]")
        val manifest =
            manifestFor(signer, payload, ExportTier.FULL, grants = setOf("medication_entry"))
        assertEquals(listOf("medication_entry", "session"), manifest.includedTables)

        val sealed = CrocSigning.seal(manifest, signer)
        assertIs<CrocVerdict.Verified>(verdict(sealed, payload))
    }

    @Test
    fun `an unclassified table is refused rather than imported on trust`() {
        val signer = newProvider()
        val payload = payload("session" to "[]", "definitely_not_registered" to "[]")
        val manifest =
            CrocManifest(
                schemaVersion = CrocManifest.CURRENT_SCHEMA_VERSION,
                appVersion = "0.6.0-test",
                createdAtMs = 1L,
                kindName = CrocEnvelopeKind.ATHLETE_REPORT.name,
                senderPubkeyB64 =
                    Base64.getEncoder().encodeToString(signer.identity().keyBytes),
                senderFingerprint = signer.identity().fingerprint,
                tierName = ExportTier.FULL.name,
                includedTables = listOf("definitely_not_registered", "session"),
                payloadChecksum = PayloadChecksum.of(payload),
                signatureAlgorithm = SignatureAlgorithms.ECDSA_P256_SHA256,
            )
        val sealed = CrocSigning.seal(manifest, signer)

        val r = assertIs<CrocVerdict.Rejected>(verdict(sealed, payload))
        assertEquals(CrocRejection.UNKNOWN_TABLE_CLAIMED, r.reason)
    }

    @Test
    fun `garbage in either entry is a malformed envelope, never a crash`() {
        val signer = newProvider()
        val payload = payload("session" to "[]")
        val sealed = CrocSigning.seal(manifestFor(signer, payload), signer)

        val badManifest =
            assertIs<CrocVerdict.Rejected>(
                CrocVerifier.verify("not json".toByteArray(), sealed.signature.serialize(), payload)
            )
        assertEquals(CrocRejection.MALFORMED_ENVELOPE, badManifest.reason)

        val badSidecar =
            assertIs<CrocVerdict.Rejected>(
                CrocVerifier.verify(sealed.manifestBytes, "{", payload)
            )
        assertEquals(CrocRejection.MALFORMED_ENVELOPE, badSidecar.reason)

        val badBase64 =
            assertIs<CrocVerdict.Rejected>(
                CrocVerifier.verify(
                    sealed.manifestBytes,
                    CrocSignature(SignatureAlgorithms.ECDSA_P256_SHA256, "!!!not base64!!!")
                        .serialize(),
                    payload,
                )
            )
        assertEquals(CrocRejection.MALFORMED_SIGNATURE, badBase64.reason)
    }

    @Test
    fun `sealing refuses a manifest whose declared suite the signer cannot produce`() {
        val signer = newProvider()
        val payload = payload("session" to "[]")
        val wrong = manifestFor(signer, payload).copy(signatureAlgorithm = "SHA512withECDSA")
        assertFailsWith<IllegalArgumentException> { CrocSigning.seal(wrong, signer) }
    }

    @Test
    fun `zip entry naming round-trips`() {
        assertEquals("tables/session.json", CrocEnvelope.tableEntry("session"))
        assertEquals("session", CrocEnvelope.tableOfEntry("tables/session.json"))
        assertEquals(null, CrocEnvelope.tableOfEntry("manifest.json"))
        assertEquals(null, CrocEnvelope.tableOfEntry("tables/.json"))
    }
}
