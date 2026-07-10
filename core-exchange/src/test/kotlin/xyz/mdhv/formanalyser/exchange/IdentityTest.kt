package xyz.mdhv.formanalyser.exchange

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IdentityTest {

    @Test
    fun `fingerprint is deterministic grouped uppercase hex`() {
        val bytes = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte(), 0x01)
        assertEquals("DEAD-BEEF-01", Fingerprint.format(bytes))
        // Deterministic across calls.
        assertEquals(Fingerprint.format(bytes), Fingerprint.format(bytes.copyOf()))
    }

    @Test
    fun `fingerprint respects group size and separator`() {
        val bytes = byteArrayOf(0x00, 0x11, 0x22, 0x33)
        assertEquals("00 11 22 33", Fingerprint.format(bytes, groupSize = 2, separator = " "))
        assertEquals("0011:2233", Fingerprint.format(bytes, groupSize = 4, separator = ":"))
    }

    @Test
    fun `empty key formats to empty string`() {
        assertEquals("", Fingerprint.format(ByteArray(0)))
    }

    @Test
    fun `PubkeyIdentity value semantics and defensive copy`() {
        val raw = byteArrayOf(1, 2, 3, 4)
        val a = PubkeyIdentity.of(raw)
        val b = PubkeyIdentity.of(byteArrayOf(1, 2, 3, 4))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())

        // Mutating the source array must not change the identity (defensive copy in).
        raw[0] = 99
        assertEquals(PubkeyIdentity.of(byteArrayOf(1, 2, 3, 4)), a)

        // Mutating the exposed bytes must not change the identity (defensive copy out).
        a.keyBytes[0] = 99
        assertEquals(PubkeyIdentity.of(byteArrayOf(1, 2, 3, 4)), a)

        assertNotEquals(a, PubkeyIdentity.of(byteArrayOf(9, 9)))
    }

    @Test
    fun `PubkeyIdentity fingerprint matches Fingerprint format`() {
        val id = PubkeyIdentity.of(byteArrayOf(0xAB.toByte(), 0xCD.toByte()))
        assertEquals("ABCD", id.fingerprint)
        assertTrue(id.toString().contains("ABCD"))
    }

    @Test
    fun `KeyProvider seam returns a deterministic identity in tests`() {
        val fixed = PubkeyIdentity.of(byteArrayOf(0x10, 0x20, 0x30))
        val provider = object : KeyProvider {
            override fun identity(): PubkeyIdentity = fixed
        }
        assertEquals(fixed, provider.identity())
        assertEquals("1020-30", provider.identity().fingerprint)
    }
}
