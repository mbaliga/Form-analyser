package xyz.mdhv.formanalyser.app.exchange

import android.content.Context

/**
 * Small local trust-on-first-use registry for signed Crocodyl exchanges.
 *
 * Fingerprints are public identifiers, not secrets, so private SharedPreferences is sufficient.
 * The athlete id is the stable archive identity; the pinned fingerprint is the device key the user
 * explicitly trusted for that athlete. A changed key is never accepted silently.
 */
class ExchangeTrustStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun fingerprintFor(athleteId: String): String? = prefs.getString(key(athleteId), null)

    fun pin(athleteIds: Collection<String>, fingerprint: String) {
        prefs.edit().apply {
            athleteIds.forEach { putString(key(it), fingerprint) }
        }.apply()
    }

    private fun key(athleteId: String): String = "athlete_${athleteId}"

    private companion object {
        const val PREFS_NAME = "crocodyl_exchange_trust"
    }
}
