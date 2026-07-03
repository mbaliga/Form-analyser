package xyz.mdhv.formanalyser.wellness

/** Data-model-wide privacy law (spec §3.5.4). */
enum class PrivacyClass { SHAREABLE, MEDICAL, PRIVATE }

/**
 * The single source of truth for each table's privacy class (Phase 2 §A7). Registered here in
 * Phase 2, enforced by the `core-exchange` consent filter in Phase 5. Later phases add entries
 * (additive). The app carries a Robolectric test that reflects over the Room entity list and
 * asserts every table name has an entry here — the test that keeps future phases honest.
 *
 * Canonical logical table names (spec §8). The app-layer reflection test reconciles these with
 * the actual Room `tableName`s and any historical plural names.
 */
object PrivacyRegistry {
    val byTable: Map<String, PrivacyClass> = buildMap {
        // Phase 0/1 — capture + identity + equipment stub
        put("athlete", PrivacyClass.SHAREABLE)
        put("session", PrivacyClass.SHAREABLE)
        put("shot", PrivacyClass.SHAREABLE)
        put("rig", PrivacyClass.SHAREABLE)

        // Phase 2 — wellness + life layer
        put("checkin", PrivacyClass.SHAREABLE)
        put("soreness", PrivacyClass.SHAREABLE)
        put("rest_day", PrivacyClass.SHAREABLE)
        put("hiatus", PrivacyClass.SHAREABLE)
        put("event", PrivacyClass.SHAREABLE)
        put("mood_entry", PrivacyClass.PRIVATE)
        put("life_event", PrivacyClass.PRIVATE)
        put("cycle_entry", PrivacyClass.PRIVATE)
        put("medication_entry", PrivacyClass.MEDICAL)
    }

    fun classOf(table: String): PrivacyClass? = byTable[table]

    /** Tables that must never appear in any export tier. */
    fun privateTables(): Set<String> = byTable.filterValues { it == PrivacyClass.PRIVATE }.keys

    /** Tables gated behind the per-item medical ceremony. */
    fun medicalTables(): Set<String> = byTable.filterValues { it == PrivacyClass.MEDICAL }.keys
}
