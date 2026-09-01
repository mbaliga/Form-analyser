package xyz.mdhv.formanalyser.exchange

import xyz.mdhv.formanalyser.wellness.PrivacyClass
import xyz.mdhv.formanalyser.wellness.PrivacyRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConsentFilterTest {

    private val allTables = PrivacyRegistry.byTable.keys

    @Test
    fun `PRIVATE is never included in any tier, with or without grants`() {
        for (tier in ExportTier.entries) {
            // Even if we (wrongly) hand every private table as a "grant", it must stay out.
            val decision = ConsentFilter.filter(
                requestedTables = allTables,
                tier = tier,
                medicalGrants = allTables, // adversarial: grant everything
            )
            for (priv in PrivacyRegistry.privateTables()) {
                assertFalse(priv in decision.included, "$priv leaked into $tier")
                assertEquals(
                    WithheldReason.PRIVATE_ALWAYS_EXCLUDED,
                    decision.withheldReasonByTable[priv],
                    "$priv withheld reason under $tier",
                )
            }
        }
    }

    @Test
    fun `MEDICAL only included in FULL and only with an explicit grant`() {
        val medical = PrivacyRegistry.medicalTables()
        require(medical.isNotEmpty())

        // FULL, no grants -> MEDICAL_NEEDS_GRANT
        val noGrant = ConsentFilter.filter(medical, ExportTier.FULL, medicalGrants = emptySet())
        for (m in medical) {
            assertFalse(m in noGrant.included)
            assertEquals(WithheldReason.MEDICAL_NEEDS_GRANT, noGrant.withheldReasonByTable[m])
        }

        // FULL, granted -> included
        val granted = ConsentFilter.filter(medical, ExportTier.FULL, medicalGrants = medical)
        assertEquals(medical, granted.included)

        // SHAREABLE_ONLY, even granted -> NOT_IN_TIER (coarse gate wins)
        val shareableOnly =
            ConsentFilter.filter(medical, ExportTier.SHAREABLE_ONLY, medicalGrants = medical)
        for (m in medical) {
            assertFalse(m in shareableOnly.included)
            assertEquals(WithheldReason.NOT_IN_TIER, shareableOnly.withheldReasonByTable[m])
        }
    }

    @Test
    fun `partial medical grant includes only the granted table`() {
        val medical = PrivacyRegistry.medicalTables().toList()
        require(medical.size >= 2) { "test assumes >=2 medical tables" }
        val decision = ConsentFilter.filter(
            requestedTables = medical.toSet(),
            tier = ExportTier.FULL,
            medicalGrants = setOf(medical.first()),
        )
        assertEquals(setOf(medical.first()), decision.included)
        assertEquals(
            WithheldReason.MEDICAL_NEEDS_GRANT,
            decision.withheldReasonByTable[medical[1]],
        )
    }

    @Test
    fun `SHAREABLE always included in FULL and SHAREABLE_ONLY`() {
        val shareable = PrivacyRegistry.byTable
            .filterValues { it == PrivacyClass.SHAREABLE }.keys
        for (tier in ExportTier.entries) {
            val decision = ConsentFilter.filter(shareable, tier)
            assertEquals(shareable, decision.included, "shareable set under $tier")
        }
    }

    @Test
    fun `unknown table fails closed`() {
        val decision = ConsentFilter.filter(setOf("definitely_not_a_table"), ExportTier.FULL)
        assertTrue(decision.included.isEmpty())
        assertEquals(
            WithheldReason.UNKNOWN_TABLE,
            decision.withheldReasonByTable["definitely_not_a_table"],
        )
    }

    @Test
    fun `coverage - every registered table is classified as included or withheld with a reason`() {
        // Under FULL with all medical granted, every known table must have exactly one verdict.
        val decision = ConsentFilter.filter(
            requestedTables = allTables,
            tier = ExportTier.FULL,
            medicalGrants = PrivacyRegistry.medicalTables(),
        )
        for (table in allTables) {
            val includedHere = table in decision.included
            val withheldHere = decision.withheldReasonByTable.containsKey(table)
            assertTrue(
                includedHere xor withheldHere,
                "$table must be exactly one of included/withheld (included=$includedHere, withheld=$withheldHere)",
            )
        }
        // Sanity: included ∪ withheld covers the whole request set.
        val covered = decision.included + decision.withheldReasonByTable.keys
        assertEquals(allTables, covered)
    }

    @Test
    fun `withheld map is keyed by reason`() {
        val decision = ConsentFilter.filter(allTables, ExportTier.SHAREABLE_ONLY)
        // PRIVATE tables all land under PRIVATE_ALWAYS_EXCLUDED.
        assertEquals(
            PrivacyRegistry.privateTables(),
            decision.withheld[WithheldReason.PRIVATE_ALWAYS_EXCLUDED] ?: emptySet(),
        )
        // MEDICAL tables land under NOT_IN_TIER in the shareable-only tier.
        assertEquals(
            PrivacyRegistry.medicalTables(),
            decision.withheld[WithheldReason.NOT_IN_TIER] ?: emptySet(),
        )
    }
}
