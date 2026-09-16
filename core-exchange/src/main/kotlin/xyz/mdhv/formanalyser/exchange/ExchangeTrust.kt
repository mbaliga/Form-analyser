package xyz.mdhv.formanalyser.exchange

/** Trust state for an inspected exchange, kept pure so quarantine rules are JVM-testable. */
enum class ExchangeTrustState {
    LEGACY_UNSIGNED,
    FIRST_CONTACT,
    TRUSTED,
    KEY_CHANGED,
    REPLACEMENT_ARMED,
}

object ExchangeTrust {
    fun evaluate(signedFingerprint: String?, pinnedFingerprints: Collection<String>): ExchangeTrustState {
        if (signedFingerprint == null) return ExchangeTrustState.LEGACY_UNSIGNED
        return when {
            pinnedFingerprints.any { it != signedFingerprint } -> ExchangeTrustState.KEY_CHANGED
            pinnedFingerprints.isNotEmpty() -> ExchangeTrustState.TRUSTED
            else -> ExchangeTrustState.FIRST_CONTACT
        }
    }
}
