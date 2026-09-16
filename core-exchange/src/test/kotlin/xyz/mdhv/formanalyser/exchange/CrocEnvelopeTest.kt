package xyz.mdhv.formanalyser.exchange

import java.security.KeyPairGenerator
import java.security.Signature
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrocEnvelopeTest {
    @Test
    fun `signed envelope round trips and rejects payload tampering`() {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val signer: (ByteArray) -> ByteArray = { bytes ->
            Signature.getInstance(CrocEnvelope.SIGNATURE_ALGORITHM).run {
                initSign(pair.private)
                update(bytes)
                sign()
            }
        }
        val envelope =
            CrocEnvelope.create(42L, pair.public.encoded, CrocEnvelope.BACKUP_PAYLOAD, "athlete data".toByteArray(), signer)
        assertTrue(CrocEnvelope.deserialize(envelope.serialize()).verify())

        val altered = envelope.copy(payloadBase64 = envelope.payloadBase64.dropLast(2) + "AA")
        assertFalse(altered.verify())
    }
}
