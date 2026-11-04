package xyz.mdhv.formanalyser.app.exchange

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidKeyProviderTest {
    @Test
    fun `envelope payload sorts tables and remains delimiter stable`() {
        assertEquals(
            "sha256:abc\nathlete,session\nSHAREABLE_ONLY",
            AndroidKeyProvider.envelopePayload(
                checksum = "sha256:abc",
                includedTables = listOf("session", "athlete"),
                tierName = "SHAREABLE_ONLY",
            ),
        )
    }
}
