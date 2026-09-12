package xyz.mdhv.formanalyser.exchange

import kotlinx.serialization.Serializable
import xyz.mdhv.formanalyser.wellness.PrivacyClass

/** A Crocodyl observation offered to the consent boundary; not all candidates cross it. */
data class BaselineObservationCandidate(
    val id: String,
    val kind: String,
    val capturedAtMs: Long,
    val resolution: String,
    val value: Double,
    val unit: String,
    val provenance: String,
    val confidence: Double? = null,
    val privacyClass: PrivacyClass,
    val validityEndsAtMs: Long? = null,
)

@Serializable
data class InsightObservation(
    val id: String,
    val kind: String,
    val capturedAtMs: Long,
    val resolution: String,
    val value: Double,
    val unit: String,
    val provenance: String,
    val confidence: Double? = null,
    val validityEndsAtMs: Long? = null,
)

/** Versioned payload Crocodyl may hand to a separately implemented Baseline engine. */
@Serializable
data class InsightInput(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val athleteId: String,
    val generatedAtMs: Long,
    val observations: List<InsightObservation>,
) {
    companion object { const val CURRENT_SCHEMA_VERSION = 1 }
}

enum class BaselineWithheldReason { PRIVATE, MEDICAL_NOT_GRANTED }

data class BaselineConsentDecision(
    val input: InsightInput,
    val withheld: Map<BaselineWithheldReason, List<String>>,
)

/** Fail-closed per-observation adapter. PRIVATE never crosses; MEDICAL requires an item grant. */
object BaselineAdapter {
    fun build(
        athleteId: String,
        generatedAtMs: Long,
        candidates: List<BaselineObservationCandidate>,
        medicalGrants: Set<String> = emptySet(),
    ): BaselineConsentDecision {
        require(athleteId.isNotBlank()) { "athleteId is required" }
        val accepted = ArrayList<InsightObservation>()
        val withheld = linkedMapOf<BaselineWithheldReason, MutableList<String>>()
        candidates.forEach { value ->
            require(value.id.isNotBlank() && value.kind.isNotBlank() && value.resolution.isNotBlank()) {
                "observation identity, kind and resolution are required"
            }
            require(value.confidence == null || value.confidence in 0.0..1.0) {
                "confidence must be in 0..1"
            }
            val reason = when (value.privacyClass) {
                PrivacyClass.PRIVATE -> BaselineWithheldReason.PRIVATE
                PrivacyClass.MEDICAL -> if (value.id in medicalGrants) null else BaselineWithheldReason.MEDICAL_NOT_GRANTED
                PrivacyClass.SHAREABLE -> null
            }
            if (reason != null) withheld.getOrPut(reason, ::ArrayList) += value.id
            else accepted += value.toInput()
        }
        return BaselineConsentDecision(
            InsightInput(
                athleteId = athleteId,
                generatedAtMs = generatedAtMs,
                observations = accepted.sortedWith(compareBy({ it.capturedAtMs }, { it.id })),
            ),
            withheld.mapValues { it.value.toList() },
        )
    }

    private fun BaselineObservationCandidate.toInput() = InsightObservation(
        id, kind, capturedAtMs, resolution, value, unit, provenance, confidence, validityEndsAtMs,
    )
}

@Serializable
enum class EvidenceGrade {
    DESCRIPTIVE,
    ASSOCIATION,
    TEMPORALLY_SUPPORTED_ASSOCIATION,
    INDIVIDUALIZED_INTERVENTION_EFFECT,
    ELIGIBLE_CAUSAL_CONTRIBUTION,
}

/** Versioned presentation contract returned by Baseline; no estimator lives in Crocodyl. */
@Serializable
data class Insight(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val id: String,
    val result: String,
    val provenance: List<String>,
    val factObservationIds: List<String>,
    val effectEstimate: Double? = null,
    val uncertainty: String? = null,
    val evidenceGrade: EvidenceGrade,
    val appliesFromMs: Long,
    val appliesUntilMs: Long? = null,
    val invalidationConditions: List<String>,
) {
    init {
        require(id.isNotBlank() && result.isNotBlank())
        require(provenance.isNotEmpty() && factObservationIds.isNotEmpty())
        require(invalidationConditions.isNotEmpty())
        if (evidenceGrade == EvidenceGrade.ELIGIBLE_CAUSAL_CONTRIBUTION) {
            require(effectEstimate != null && effectEstimate in 0.0..1.0)
            require(uncertainty?.isNotBlank() == true)
        }
    }

    companion object { const val CURRENT_SCHEMA_VERSION = 1 }
}
