package xyz.mdhv.formanalyser.exchange

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import xyz.mdhv.formanalyser.wellness.PrivacyClass

class BaselineContractTest {
    private fun candidate(id: String, privacy: PrivacyClass, at: Long = 1) =
        BaselineObservationCandidate(id, "form.angle", at, "SHOT_CONFIRMED", 42.0, "deg", "camera", .9, privacy)

    @Test
    fun `adapter excludes private and gates each medical observation`() {
        val decision = BaselineAdapter.build(
            "athlete-1",
            99,
            listOf(candidate("private", PrivacyClass.PRIVATE), candidate("medical", PrivacyClass.MEDICAL), candidate("form", PrivacyClass.SHAREABLE)),
        )
        assertEquals(listOf("form"), decision.input.observations.map { it.id })
        assertEquals(listOf("private"), decision.withheld[BaselineWithheldReason.PRIVATE])
        assertEquals(listOf("medical"), decision.withheld[BaselineWithheldReason.MEDICAL_NOT_GRANTED])
        assertEquals(listOf("form", "medical"), BaselineAdapter.build("athlete-1", 99, listOf(candidate("medical", PrivacyClass.MEDICAL, 2), candidate("form", PrivacyClass.SHAREABLE)), setOf("medical")).input.observations.map { it.id })
    }

    @Test
    fun `causal grade requires bounded estimate uncertainty and invalidation conditions`() {
        assertFailsWith<IllegalArgumentException> {
            Insight(
                id = "i", result = "Material contribution estimate", provenance = listOf("baseline:v1"),
                factObservationIds = listOf("o"), effectEstimate = null, uncertainty = null,
                evidenceGrade = EvidenceGrade.ELIGIBLE_CAUSAL_CONTRIBUTION,
                appliesFromMs = 1, invalidationConditions = listOf("equipment changes"),
            )
        }
    }
}
