package xyz.mdhv.formanalyser.exchange

import xyz.mdhv.formanalyser.wellness.PrivacyClass

/**
 * The export tiers the athlete may choose in the Phase 5 confirmation ceremony.
 *
 * A tier declares which [PrivacyClass]es are *includable* at all. This is the coarse gate; the
 * per-item MEDICAL grant is a second, finer gate enforced by [ConsentFilter]. [PrivacyClass.PRIVATE]
 * is absent from every tier's [includableClasses] — the crown-jewel invariant that PRIVATE data
 * (mood, life events, cycle) can never leave the device via export.
 */
enum class ExportTier(val includableClasses: Set<PrivacyClass>) {
    /** Only freely-shareable performance data. No medical, no private. */
    SHAREABLE_ONLY(setOf(PrivacyClass.SHAREABLE)),

    /**
     * Shareable data plus MEDICAL tables — but each MEDICAL table still requires an explicit
     * per-item grant from the confirmation ceremony (see [ConsentFilter]). PRIVATE stays excluded.
     */
    FULL(setOf(PrivacyClass.SHAREABLE, PrivacyClass.MEDICAL));

    /** Whether this tier could ever include the given class (ignoring the per-item medical grant). */
    fun allows(privacyClass: PrivacyClass): Boolean = privacyClass in includableClasses
}
