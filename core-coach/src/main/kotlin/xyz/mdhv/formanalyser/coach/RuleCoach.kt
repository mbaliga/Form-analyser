package xyz.mdhv.formanalyser.coach

import xyz.mdhv.formanalyser.wellness.WellnessConstants as K

/** How loud an insight is. WARNING outranks ADVICE outranks INFO. */
enum class InsightSeverity { INFO, ADVICE, WARNING }

/**
 * A structured coaching insight. [code] is a stable machine key (for i18n / dedupe / testing);
 * [title]/[detail] are the human copy.
 */
data class CoachInsight(
    val code: String,
    val severity: InsightSeverity,
    val title: String,
    val detail: String,
)

/**
 * The deterministic, no-network default coach. It reads [CoachFacts] and emits structured
 * [CoachInsight]s from fixed rules. This is both the offline experience (works with no key, no
 * connectivity) and the test oracle the LLM-backed coach is checked against.
 *
 * Pure function of its input — no clocks, no randomness. Insights come out ordered by severity
 * (WARNING → ADVICE → INFO), ties broken by evaluation order, so output is fully deterministic.
 */
object RuleCoach {

    fun insights(facts: CoachFacts): List<CoachInsight> {
        val out = ArrayList<CoachInsight>()

        // 1. ACWR spike → deload.
        facts.acwr?.let { a ->
            if (a > K.ACWR_CAUTION_HI) {
                out += CoachInsight(
                    "acwr_spike",
                    InsightSeverity.WARNING,
                    "Acute load spike",
                    "Your ACWR is ${fmt2(a)} (> ${K.ACWR_CAUTION_HI}). Deload for a few days — cut volume and keep intensity easy.",
                )
            } else if (a > K.ACWR_SWEET_HI) {
                out += CoachInsight(
                    "acwr_climbing",
                    InsightSeverity.ADVICE,
                    "Load climbing",
                    "Your ACWR is ${fmt2(a)}, above the sweet spot (${K.ACWR_SWEET_HI}). Hold volume steady rather than adding.",
                )
            }
        }

        // 2. Injuries → protect the region. Severity>=3 is a warning, severity 2 is advice.
        facts.activeInjuries.filter { it.severity >= 3 }.forEach { inj ->
            out += CoachInsight(
                "injury_protect_severe",
                InsightSeverity.WARNING,
                "Protect ${inj.regions.joinToString(", ")}",
                "A severity-${inj.severity} injury is active. Avoid loading ${inj.regions.joinToString(", ")}; " +
                    "train around it and consider seeing a clinician.",
            )
        }
        facts.activeInjuries.filter { it.severity == 2 }.forEach { inj ->
            out += CoachInsight(
                "injury_protect",
                InsightSeverity.ADVICE,
                "Ease load on ${inj.regions.joinToString(", ")}",
                "A moderate injury is active in ${inj.regions.joinToString(", ")}. Reduce load on that region and monitor it.",
            )
        }

        // 3. Stale check-in → prompt one.
        facts.checkinAgeHours?.let { h ->
            if (h > K.STALENESS_H) {
                out += CoachInsight(
                    "checkin_stale",
                    InsightSeverity.ADVICE,
                    "Log a check-in",
                    "Your last check-in was ${fmt1(h)}h ago (> ${K.STALENESS_H}h). Log one for a truer readiness read.",
                )
            }
        }

        // 4. Provisional streak → lock it in with a check-in.
        facts.streak?.let { s ->
            if (s.provisionalToday) {
                out += CoachInsight(
                    "streak_provisional",
                    InsightSeverity.INFO,
                    "Today keeps your streak alive",
                    "Your ${s.length}-day streak counts today provisionally. Log a session or check-in to lock it in.",
                )
            }
        }

        // Stable sort: severity descending, ties keep insertion (evaluation) order.
        return out.sortedByDescending { it.severity.ordinal }
    }

    // Locale.US: insight text must be identical across devices (see Grounding.fmt for rationale).
    private fun fmt1(x: Double): String = String.format(java.util.Locale.US, "%.1f", x)
    private fun fmt2(x: Double): String = String.format(java.util.Locale.US, "%.2f", x)
}
