package xyz.mdhv.formanalyser.exchange

import xyz.mdhv.formanalyser.wellness.PrivacyClass
import xyz.mdhv.formanalyser.wellness.PrivacyRegistry

/** Why a requested table did not make it into the export. */
enum class WithheldReason {
    /** PRIVATE class — excluded unconditionally, in every tier, no grant can override it. */
    PRIVATE_ALWAYS_EXCLUDED,

    /** MEDICAL class, includable in the chosen tier, but no explicit per-item grant was given. */
    MEDICAL_NEEDS_GRANT,

    /** The table's class is not includable in the chosen tier (e.g. MEDICAL under SHAREABLE_ONLY). */
    NOT_IN_TIER,

    /** The table is not registered in [PrivacyRegistry]; excluded by default (fail-closed). */
    UNKNOWN_TABLE,
}

/**
 * The outcome of running the consent filter.
 *
 * @property included tables that are cleared for export.
 * @property withheld tables that were dropped, grouped by the reason they were dropped.
 */
data class ConsentDecision(
    val included: Set<String>,
    val withheld: Map<WithheldReason, Set<String>>,
) {
    /** Flat lookup: table -> the reason it was withheld (absent if the table was included). */
    val withheldReasonByTable: Map<String, WithheldReason> =
        withheld.entries.flatMap { (reason, tables) -> tables.map { it to reason } }.toMap()
}

/**
 * The Phase 5 consent filter — the enforcement point for [PrivacyRegistry] at export time.
 *
 * Pure, deterministic function of its inputs. The Android layer supplies the requested table set,
 * the chosen [ExportTier], and the set of MEDICAL tables the athlete explicitly granted in the
 * confirmation ceremony; it does no privacy logic of its own.
 */
object ConsentFilter {

    /**
     * @param requestedTables logical table names the caller wants to export.
     * @param tier the chosen export tier.
     * @param medicalGrants MEDICAL table names the athlete explicitly granted this export.
     */
    fun filter(
        requestedTables: Set<String>,
        tier: ExportTier,
        medicalGrants: Set<String> = emptySet(),
    ): ConsentDecision {
        val included = LinkedHashSet<String>()
        val withheld = LinkedHashMap<WithheldReason, LinkedHashSet<String>>()

        fun withhold(reason: WithheldReason, table: String) {
            withheld.getOrPut(reason) { LinkedHashSet() }.add(table)
        }

        for (table in requestedTables) {
            when (val cls = PrivacyRegistry.classOf(table)) {
                null -> withhold(WithheldReason.UNKNOWN_TABLE, table)

                // Crown-jewel invariant: PRIVATE is checked FIRST and excluded unconditionally.
                PrivacyClass.PRIVATE -> withhold(WithheldReason.PRIVATE_ALWAYS_EXCLUDED, table)

                else -> when {
                    !tier.allows(cls) -> withhold(WithheldReason.NOT_IN_TIER, table)
                    cls == PrivacyClass.MEDICAL && table !in medicalGrants ->
                        withhold(WithheldReason.MEDICAL_NEEDS_GRANT, table)
                    else -> included.add(table)
                }
            }
        }

        return ConsentDecision(included, withheld.mapValues { it.value.toSet() })
    }
}
