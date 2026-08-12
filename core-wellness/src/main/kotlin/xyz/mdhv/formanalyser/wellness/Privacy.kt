package xyz.mdhv.formanalyser.wellness

enum class PrivacyClass { SHAREABLE, MEDICAL, PRIVATE }
object PrivacyRegistry {
    val byTable: Map<String, PrivacyClass> = buildMap {
        put("athlete", PrivacyClass.SHAREABLE); put("session", PrivacyClass.SHAREABLE); put("shot", PrivacyClass.SHAREABLE); put("rig", PrivacyClass.SHAREABLE)
        put("score_session", PrivacyClass.SHAREABLE); put("score_arrow", PrivacyClass.SHAREABLE); put("score_opponent_end", PrivacyClass.SHAREABLE)
        put("score_candidate", PrivacyClass.SHAREABLE); put("observer_score_event", PrivacyClass.SHAREABLE)
        put("goal", PrivacyClass.SHAREABLE); put("intervention", PrivacyClass.SHAREABLE); put("training_plan", PrivacyClass.SHAREABLE); put("session_context", PrivacyClass.SHAREABLE); put("session_default", PrivacyClass.PRIVATE)
        put("checkin", PrivacyClass.SHAREABLE); put("soreness", PrivacyClass.SHAREABLE); put("rest_day", PrivacyClass.SHAREABLE); put("hiatus", PrivacyClass.SHAREABLE); put("event", PrivacyClass.SHAREABLE)
        put("mood_entry", PrivacyClass.PRIVATE); put("life_event", PrivacyClass.PRIVATE); put("cycle_entry", PrivacyClass.PRIVATE); put("medication_entry", PrivacyClass.MEDICAL)
        put("pain_log", PrivacyClass.SHAREABLE); put("injury", PrivacyClass.SHAREABLE); put("physio_plan", PrivacyClass.SHAREABLE); put("physio_exercise", PrivacyClass.SHAREABLE); put("physio_session", PrivacyClass.SHAREABLE); put("document", PrivacyClass.MEDICAL)
    }
    fun classOf(table:String)=byTable[table]
    fun privateTables():Set<String> = byTable.filterValues{it==PrivacyClass.PRIVATE}.keys
    fun medicalTables():Set<String> = byTable.filterValues{it==PrivacyClass.MEDICAL}.keys
}
