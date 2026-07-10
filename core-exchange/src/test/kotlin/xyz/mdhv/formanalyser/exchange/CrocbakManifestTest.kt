package xyz.mdhv.formanalyser.exchange

import xyz.mdhv.formanalyser.wellness.PrivacyRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CrocbakManifestTest {

    private fun sample() = CrocbakManifest(
        schemaVersion = CrocbakManifest.CURRENT_SCHEMA_VERSION,
        appVersion = "1.4.0",
        createdAtMs = 1_700_000_000_000L,
        athletePubkeyFingerprint = "DEAD-BEEF",
        tierName = ExportTier.FULL.name,
        includedTables = listOf("athlete", "session", "medication_entry"),
        rowCounts = mapOf("athlete" to 1L, "session" to 42L, "medication_entry" to 3L),
        contentChecksum = "sha256:abc123",
    )

    @Test
    fun `round-trips through JSON`() {
        val original = sample()
        val restored = CrocbakManifest.deserialize(original.serialize())
        assertEquals(original, restored)
    }

    @Test
    fun `round-trips with null row counts`() {
        val original = sample().copy(rowCounts = null)
        val restored = CrocbakManifest.deserialize(original.serialize())
        assertEquals(original, restored)
        assertNull(restored.rowCounts)
    }

    @Test
    fun `fromDecision stamps schema, tier and sorted included tables`() {
        val decision = ConsentFilter.filter(
            requestedTables = setOf("session", "athlete", "medication_entry", "mood_entry"),
            tier = ExportTier.FULL,
            medicalGrants = setOf("medication_entry"),
        )
        val manifest = CrocbakManifest.fromDecision(
            appVersion = "9.9.9",
            createdAtMs = 123L,
            athletePubkeyFingerprint = "AAAA-BBBB",
            tier = ExportTier.FULL,
            decision = decision,
            contentChecksum = "sha256:deadbeef",
        )
        assertEquals(CrocbakManifest.CURRENT_SCHEMA_VERSION, manifest.schemaVersion)
        assertEquals("FULL", manifest.tierName)
        assertEquals(123L, manifest.createdAtMs)
        // included is sorted; mood_entry (PRIVATE) never present.
        assertEquals(listOf("athlete", "medication_entry", "session"), manifest.includedTables)
        // A private table can never appear in a manifest built from a decision.
        for (priv in PrivacyRegistry.privateTables()) {
            assert(priv !in manifest.includedTables)
        }
        // Round-trip the built manifest too.
        assertEquals(manifest, CrocbakManifest.deserialize(manifest.serialize()))
    }
}
