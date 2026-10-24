package xyz.mdhv.formanalyser.wellness

/**
 * The one place that says which tables can hold a retracted row and which other tables hang off
 * them.
 *
 * Retracting a parent (a session, a scorecard, a check-in, ...) sets its `deletedAt`; it does not
 * touch the rows that belong to it. Exporting only the parent table's `deletedAt IS NULL` rows left
 * those children — every shot of a deleted session, every arrow of a deleted scorecard, the
 * soreness regions of a deleted check-in — riding out in the archive, so the athlete's delete was a
 * lie to whoever received the export. [refs] records every column that points at a retractable
 * parent, and [owned] says what the export must do about it.
 *
 * Room declares no foreign keys anywhere, so this map is a convention, not something the database
 * knows. `ExportRetractionFilterTest` (pure JVM, runs in CI) reads the entity sources and fails when
 * a reference column to a retractable parent is added without a row here, or a table gains a
 * `deletedAt` column without becoming a parent.
 *
 * Table names are real SQL table names, not [PrivacyRegistry] logical names; the export already maps
 * logical to SQL.
 */
object RetractionRegistry {

    /**
     * A column [column] on [table] that holds the id of a row in [parent].
     *
     * [owned] is true when the column is NOT NULL: the child cannot exist without that parent, so
     * retracting the parent hides the child, in the app and in the export. A nullable reference is a
     * link, not ownership: the child row survives the parent's retraction (the app still lists it) and
     * is still exported, carrying only an id that points at a row the archive does not contain.
     */
    data class Ref(val table: String, val column: String, val parent: String, val owned: Boolean)

    /** Tables with a `deletedAt` column. */
    val parents: Set<String> =
        setOf(
            "sessions",
            "score_session",
            "sport_session",
            "checkin",
            "pain_log",
            "injury",
            "document",
        )

    /**
     * The camel-case suffix a reference column to each parent ends with, minus the trailing `Id`
     * (`scoreSessionId` -> `scoreSession`). The longest case-insensitive match wins, so
     * `scoreSession` is not mistaken for `session`.
     */
    val parentRefSuffix: Map<String, String> =
        mapOf(
            "sessions" to "session",
            "score_session" to "scoreSession",
            "sport_session" to "sportSession",
            "checkin" to "checkin",
            "pain_log" to "painLog",
            "injury" to "injury",
            "document" to "document",
        )

    val refs: List<Ref> =
        listOf(
            // Owned: NOT NULL, the child is meaningless without its parent.
            Ref("shots", "sessionId", "sessions", owned = true),
            Ref("session_context", "sessionId", "sessions", owned = true),
            // PRIVATE, never exported; listed so a new reference cannot go unclassified.
            Ref("capture_media", "sessionId", "sessions", owned = true),
            Ref("score_arrow", "scoreSessionId", "score_session", owned = true),
            Ref("score_opponent_end", "scoreSessionId", "score_session", owned = true),
            Ref("score_candidate", "scoreSessionId", "score_session", owned = true),
            Ref("observer_score_event", "scoreSessionId", "score_session", owned = true),
            Ref("soreness", "checkinId", "checkin", owned = true),
            // Links: nullable, the child outlives the parent's retraction and is still exported.
            Ref("document", "injuryId", "injury", owned = false),
            Ref("pain_log", "injuryId", "injury", owned = false),
            Ref("score_session", "linkedFormSessionId", "sessions", owned = false),
            Ref("sessions", "preCheckinId", "checkin", owned = false),
            Ref("sessions", "postCheckinId", "checkin", owned = false),
        )

    /** The references through which [table] belongs to a parent that can be retracted. */
    fun ownedRefs(table: String): List<Ref> = refs.filter { it.table == table && it.owned }

    init {
        require(refs.all { it.parent in parents }) { "every ref must name a registered parent" }
        require(parentRefSuffix.keys == parents) { "every parent needs a reference suffix" }
    }
}
