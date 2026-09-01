package xyz.mdhv.formanalyser.coach

import xyz.mdhv.formanalyser.wellness.AcwrZone
import xyz.mdhv.formanalyser.wellness.InjurySummary
import xyz.mdhv.formanalyser.wellness.PrivacyClass
import xyz.mdhv.formanalyser.wellness.ReadinessLevel
import xyz.mdhv.formanalyser.wellness.StreakState

/** A compact summary of recent shot/session load for grounding. */
data class ShotLoadSummary(
    val sessions7d: Int,
    val shots7d: Int,
    val sessions28d: Int,
    val shots28d: Int,
) {
    val avgShotsPerSession7d: Int get() = if (sessions7d == 0) 0 else shots7d / sessions7d
}

/** The athlete's active bow setup, for tuning-aware coaching. */
data class RigSummary(
    val label: String,
    val bowType: String? = null,
    val drawWeightLbs: Double? = null,
    val drawLengthInches: Double? = null,
)

/**
 * A recent change in a measured form feature (e.g. clicker reaction time, group size, release
 * timing). Positive/negative [delta] meaning is feature-specific; [improving] carries the read.
 */
data class FormDelta(
    val feature: String,
    val delta: Double,
    val unit: String,
    val improving: Boolean,
)

/**
 * Everything the coach is allowed to reason over, assembled from the athlete's OWN local data.
 * Every field is optional so a sparse athlete profile never breaks grounding.
 *
 * Fields are grouped by privacy class (see [PrivacyClass]); [Redaction] is the gate that decides
 * which of them may leave the device. SHAREABLE facts are the archery/load/readiness story;
 * MEDICAL facts (medications) ride behind an explicit per-request grant; PRIVATE facts (mood,
 * life-event content, cycle phase) never reach a cloud or an export.
 */
data class CoachFacts(
    // ── SHAREABLE ────────────────────────────────────────────────────────────
    val readinessLevel: ReadinessLevel? = null,
    val readinessReasons: List<String> = emptyList(),
    val acwr: Double? = null,
    val acwrZone: AcwrZone? = null,
    val load: ShotLoadSummary? = null,
    val streak: StreakState? = null,
    /** Active injuries as region+severity only (SHAREABLE per PrivacyRegistry: table `injury`). */
    val activeInjuries: List<InjurySummary> = emptyList(),
    val rig: RigSummary? = null,
    val formDeltas: List<FormDelta> = emptyList(),
    val checkinAgeHours: Double? = null,

    // ── MEDICAL (explicit per-request grant only) ────────────────────────────
    /** Active medications — a MEDICAL fact; withheld unless the request carries a medical grant. */
    val medications: List<String> = emptyList(),

    // ── PRIVATE (never to cloud/export) ──────────────────────────────────────
    /** Free-text mood note. PRIVATE — must never reach a cloud provider or an export. */
    val moodNote: String? = null,
    /** Life-event content (not the scrubbed "high-stress window" context). PRIVATE. */
    val lifeEventNote: String? = null,
    /** Menstrual-cycle phase label. PRIVATE. */
    val cyclePhase: String? = null,
)

/** One rendered line of the factsheet, tagged with the privacy class that governs its release. */
data class FactLine(val key: String, val text: String, val privacy: PrivacyClass)

/**
 * Turns [CoachFacts] into an ordered, privacy-tagged list of fact lines. This is the single place
 * that assigns a [PrivacyClass] to each fact; [Redaction] and [Factsheet] both build on it, so the
 * classification can never drift between "what we show" and "what we filter".
 */
object Grounding {

    fun factLines(facts: CoachFacts): List<FactLine> {
        val out = ArrayList<FactLine>()

        facts.readinessLevel?.let { lvl ->
            val reasons = facts.readinessReasons.takeIf { it.isNotEmpty() }?.joinToString("; ")
            val text = if (reasons != null) "Readiness: $lvl ($reasons)" else "Readiness: $lvl"
            out += FactLine("readiness", text, PrivacyClass.SHAREABLE)
        }
        facts.acwr?.let { a ->
            val zone = facts.acwrZone?.let { " [$it]" } ?: ""
            out += FactLine("acwr", "ACWR: ${fmt2(a)}$zone", PrivacyClass.SHAREABLE)
        }
        facts.load?.let { l ->
            out += FactLine(
                "load",
                "Load: ${l.sessions7d} sessions / ${l.shots7d} shots in 7d " +
                    "(28d: ${l.sessions28d} sessions / ${l.shots28d} shots; " +
                    "avg ${l.avgShotsPerSession7d} shots/session)",
                PrivacyClass.SHAREABLE,
            )
        }
        facts.streak?.let { s ->
            val prov = if (s.provisionalToday) ", today provisional" else ""
            out += FactLine("streak", "Streak: ${s.length} days (patched ${s.patchedCount}$prov)", PrivacyClass.SHAREABLE)
        }
        if (facts.activeInjuries.isNotEmpty()) {
            val text = facts.activeInjuries.joinToString("; ") { "${it.regions.joinToString(",")} (severity ${it.severity})" }
            out += FactLine("injury", "Active injuries: $text", PrivacyClass.SHAREABLE)
        }
        facts.rig?.let { r ->
            val bits = buildList {
                add(r.label)
                r.bowType?.let { add(it) }
                r.drawWeightLbs?.let { add("${fmt1(it)} lb") }
                r.drawLengthInches?.let { add("${fmt1(it)}\" DL") }
            }
            out += FactLine("rig", "Rig: ${bits.joinToString(", ")}", PrivacyClass.SHAREABLE)
        }
        facts.formDeltas.forEach { d ->
            val dir = if (d.improving) "improving" else "regressing"
            out += FactLine("form_delta", "Form ${d.feature}: ${fmtSigned(d.delta)} ${d.unit} ($dir)", PrivacyClass.SHAREABLE)
        }
        facts.checkinAgeHours?.let { h ->
            out += FactLine("checkin_age", "Last check-in: ${fmt1(h)}h ago", PrivacyClass.SHAREABLE)
        }

        // MEDICAL
        if (facts.medications.isNotEmpty()) {
            out += FactLine("medication", "Medications: ${facts.medications.joinToString(", ")}", PrivacyClass.MEDICAL)
        }

        // PRIVATE
        facts.moodNote?.let { out += FactLine("mood", "Mood note: $it", PrivacyClass.PRIVATE) }
        facts.lifeEventNote?.let { out += FactLine("life_event", "Life event: $it", PrivacyClass.PRIVATE) }
        facts.cyclePhase?.let { out += FactLine("cycle", "Cycle phase: $it", PrivacyClass.PRIVATE) }

        return out
    }

    // Locale.US so the factsheet renders identically on every device (comma-decimal locales would
    // otherwise emit "1,42" and break the deterministic-grounding contract).
    private fun fmt1(x: Double): String = String.format(java.util.Locale.US, "%.1f", x)
    private fun fmt2(x: Double): String = String.format(java.util.Locale.US, "%.2f", x)
    private fun fmtSigned(x: Double): String = (if (x >= 0) "+" else "") + fmt2(x)
}

/**
 * Renders privacy-tagged fact lines into a compact, deterministic factsheet text block. Callers
 * usually render the *redacted* lines from [Redaction]; [full] is a convenience for local/on-device
 * inspection and for the rule-coach test oracle.
 */
object Factsheet {

    /** Render an arbitrary (already-filtered) line list. */
    fun render(lines: List<FactLine>): String =
        if (lines.isEmpty()) "(no facts available)"
        else lines.joinToString("\n") { "- ${it.text}" }

    /** Full, UNREDACTED factsheet — never send this to a cloud provider directly. */
    fun full(facts: CoachFacts): String = render(Grounding.factLines(facts))
}
