package xyz.mdhv.formanalyser.wellness

import java.time.LocalDate

/** Per-day facts for the streak rule (Phase 2 §A3). */
data class DayFacts(
    val date: LocalDate,
    val session: Boolean = false,
    val restLogged: Boolean = false,
    val plannedRest: Boolean = false,
    val anyCheckin: Boolean = false,
    val hiatus: Boolean = false,
)

data class StreakState(
    val length: Int,
    val patchedCount: Int,
    val brokenAt: LocalDate?,
    val provisionalToday: Boolean,
)

/**
 * Streak engine (Phase 2 §A3). `qualifies = session ∨ rest ∨ (plannedRest ∧ checkin) ∨ hiatus`.
 * One non-qualifying day after a qualifying one is a grace day (streak preserved, patched++);
 * two consecutive non-qualifying days reset it. Today is provisional — it can extend the streak
 * but never breaks it.
 */
object StreakEngine {
    fun qualifies(f: DayFacts): Boolean =
        f.session || f.restLogged || (f.plannedRest && f.anyCheckin) || f.hiatus

    /**
     * @param completedDays oldest→newest, excluding today.
     * @param today optional provisional facts for the current day.
     */
    fun evaluate(completedDays: List<DayFacts>, today: DayFacts? = null): StreakState {
        var length = 0
        var patched = 0
        var brokenAt: LocalDate? = null
        var prevQualified = false

        for (day in completedDays.sortedBy { it.date }) {
            if (qualifies(day)) {
                length += 1
                prevQualified = true
            } else if (prevQualified) {
                // grace: streak preserved, this day patched
                patched += 1
                prevQualified = false
            } else {
                // second consecutive non-qualifying day (or a cold start): reset
                if (length > 0 || patched > 0) brokenAt = day.date
                length = 0
                patched = 0
                prevQualified = false
            }
        }

        var provisional = false
        if (today != null && qualifies(today)) {
            length += 1
            provisional = true
        }
        return StreakState(length, patched, brokenAt, provisional)
    }
}
