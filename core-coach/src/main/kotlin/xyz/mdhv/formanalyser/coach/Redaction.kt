package xyz.mdhv.formanalyser.coach

import xyz.mdhv.formanalyser.wellness.PrivacyClass

/**
 * Where a factsheet is headed. The destination changes what may leave the device:
 *  - CLOUD   — a BYOK hosted provider. PRIVATE never allowed; MEDICAL only with a grant.
 *  - EXPORT  — a file/share leaving the app. Same law as CLOUD.
 *  - ON_DEVICE — a local model. May include PRIVATE facts *unless* the athlete keeps them private.
 */
enum class CoachDestination { CLOUD, EXPORT, ON_DEVICE }

/**
 * The per-request redaction policy.
 *
 * @param destination where the prompt/factsheet is going.
 * @param medicalGrant explicit per-request consent to include MEDICAL facts. Default false.
 * @param keepPrivate the athlete's "keep private facts off even on-device" preference. Default
 *   true — PRIVATE stays private everywhere unless the athlete deliberately opts a local model in.
 */
data class RedactionPolicy(
    val destination: CoachDestination,
    val medicalGrant: Boolean = false,
    val keepPrivate: Boolean = true,
)

/** Why a fact was withheld, for an auditable, user-facing "what we didn't send" report. */
data class WithheldFact(
    val key: String,
    val privacy: PrivacyClass,
    val reason: String,
)

/**
 * The output of redaction: the kept lines, the rendered redacted factsheet, and the exact list of
 * what was withheld and why. The factsheet is built ONLY from [kept] — the prompt builder consumes
 * this, so no withheld content can reach a prompt.
 */
data class RedactionResult(
    val kept: List<FactLine>,
    val withheld: List<WithheldFact>,
    val factsheet: String,
    val policy: RedactionPolicy,
) {
    fun wasWithheld(key: String): Boolean = withheld.any { it.key == key }
}

/**
 * The critical safety invariant of the coach domain: a [PrivacyClass]-aware filter that decides,
 * per destination and per grant, which facts may leave the device.
 *
 * Law:
 *  - SHAREABLE — always kept.
 *  - MEDICAL   — kept only when [RedactionPolicy.medicalGrant] is set; withheld otherwise, for ANY
 *                destination (a grant is required even on-device, since a grant is the ceremony).
 *  - PRIVATE   — never kept for CLOUD or EXPORT. For ON_DEVICE, kept only when the athlete has
 *                turned OFF [RedactionPolicy.keepPrivate].
 */
object Redaction {

    private const val R_CLOUD_PRIVATE = "PRIVATE facts never leave the device for a cloud provider"
    private const val R_EXPORT_PRIVATE = "PRIVATE facts are never included in an export"
    private const val R_ONDEVICE_KEEP = "PRIVATE fact withheld by the athlete's keep-private setting"
    private const val R_MEDICAL_GRANT = "MEDICAL fact requires an explicit per-request grant"

    /** Decide a single fact. Returns null when kept, or the reason string when withheld. */
    private fun withholdReason(privacy: PrivacyClass, policy: RedactionPolicy): String? = when (privacy) {
        PrivacyClass.SHAREABLE -> null
        PrivacyClass.MEDICAL -> if (policy.medicalGrant) null else R_MEDICAL_GRANT
        PrivacyClass.PRIVATE -> when (policy.destination) {
            CoachDestination.CLOUD -> R_CLOUD_PRIVATE
            CoachDestination.EXPORT -> R_EXPORT_PRIVATE
            CoachDestination.ON_DEVICE -> if (policy.keepPrivate) R_ONDEVICE_KEEP else null
        }
    }

    fun redact(lines: List<FactLine>, policy: RedactionPolicy): RedactionResult {
        val kept = ArrayList<FactLine>()
        val withheld = ArrayList<WithheldFact>()
        for (line in lines) {
            val reason = withholdReason(line.privacy, policy)
            if (reason == null) kept += line
            else withheld += WithheldFact(line.key, line.privacy, reason)
        }
        return RedactionResult(kept, withheld, Factsheet.render(kept), policy)
    }

    /** Convenience: ground [facts] then redact for [policy]. */
    fun redact(facts: CoachFacts, policy: RedactionPolicy): RedactionResult =
        redact(Grounding.factLines(facts), policy)

    /**
     * Derive the redaction policy straight from the target [model], binding the destination to the
     * model's kind (CLOUD model → CLOUD destination, ON_DEVICE model → ON_DEVICE). Callers should
     * prefer this over constructing [RedactionPolicy] by hand: it removes the footgun of redacting
     * for ON_DEVICE (which may keep PRIVATE facts) and then sending the result to a CLOUD model.
     */
    fun policyFor(model: CoachModel, medicalGrant: Boolean = false, keepPrivate: Boolean = true): RedactionPolicy =
        RedactionPolicy(
            destination = when (model.kind) {
                ModelKind.CLOUD -> CoachDestination.CLOUD
                ModelKind.ON_DEVICE -> CoachDestination.ON_DEVICE
            },
            medicalGrant = medicalGrant,
            keepPrivate = keepPrivate,
        )

    /** Ground [facts] and redact for [model] in one step, with the destination bound to the model. */
    fun redactFor(facts: CoachFacts, model: CoachModel, medicalGrant: Boolean = false, keepPrivate: Boolean = true): RedactionResult =
        redact(facts, policyFor(model, medicalGrant, keepPrivate))
}
