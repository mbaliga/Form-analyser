package xyz.mdhv.formanalyser.wellness

/** Data-model-wide privacy law (spec §3.5.4). */
enum class PrivacyClass {
    SHAREABLE,
    MEDICAL,
    PRIVATE,
}

/**
 * The single source of truth for each table's privacy class (Phase 2 §A7). Registered here in Phase
 * 2, enforced by the `core-exchange` consent filter in Phase 5. Later phases add entries
 * (additive). PrivacyRegistryTest.everyAndroidTableIsClassified scans the Room migration DDL and
 * fails if any table created there is missing from this map — the test that keeps future phases
 * honest. An unregistered table has no class for the consent filter to check, so it is a gap in the
 * privacy model, not a harmless omission.
 *
 * Canonical logical table names (spec §8). The app-layer reflection test reconciles these with the
 * actual Room `tableName`s and any historical plural names.
 */
object PrivacyRegistry {
    val byTable: Map<String, PrivacyClass> = buildMap {
        // Phase 0/1 — capture + identity + equipment stub
        put("athlete", PrivacyClass.SHAREABLE)
        put("session", PrivacyClass.SHAREABLE)
        put("shot", PrivacyClass.SHAREABLE)
        put("rig", PrivacyClass.SHAREABLE)
        // Phase 2 (0.6.0) — manual scoring, plus advisory End Scan / Live Observer candidates
        put("score_session", PrivacyClass.SHAREABLE)
        put("score_arrow", PrivacyClass.SHAREABLE)
        put("score_opponent_end", PrivacyClass.SHAREABLE)
        put("score_candidate", PrivacyClass.SHAREABLE)
        put("observer_score_event", PrivacyClass.SHAREABLE)
        // Athlete-feature layer — goals, interventions, plans, explainable Train defaults
        put("goal", PrivacyClass.SHAREABLE)
        put("intervention", PrivacyClass.SHAREABLE)
        put("training_plan", PrivacyClass.SHAREABLE)
        put("session_context", PrivacyClass.SHAREABLE)
        put("session_default", PrivacyClass.SHAREABLE)
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
        // Phase 3 — body layer
        put("pain_log", PrivacyClass.SHAREABLE)
        put("injury", PrivacyClass.SHAREABLE)
        put("physio_plan", PrivacyClass.SHAREABLE)
        put("physio_exercise", PrivacyClass.SHAREABLE)
        put("physio_session", PrivacyClass.SHAREABLE)
        put("document", PrivacyClass.MEDICAL)
    }

    fun classOf(table: String) = byTable[table]

    /** Tables that must never appear in any export tier. */
    fun privateTables(): Set<String> = byTable.filterValues { it == PrivacyClass.PRIVATE }.keys

    /** Tables gated behind the per-item medical ceremony. */
    fun medicalTables(): Set<String> = byTable.filterValues { it == PrivacyClass.MEDICAL }.keys
}
