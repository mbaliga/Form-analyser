package xyz.mdhv.formanalyser.app.domain

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Physio adherence over a rolling window: how many scheduled days the athlete actually attended.
 *
 * Extracted because Progress and Body context both report this number and had drifted into two
 * copies of the same loop. When one gets corrected and the other does not, the same athlete sees
 * two different adherence figures on two screens and neither is obviously the wrong one.
 *
 * The counting rule that matters: `expected` counts *scheduled days* inside the window, so
 * `completed` must count scheduled days attended — not logged rows. Counting rows instead let two
 * sessions on one day score twice, and let a session on an unscheduled day count against a schedule
 * it was never part of, which pinned adherence at 100% for anyone who double-logged.
 */
object PhysioAdherence {

    data class Window(val expected: Int, val completed: Int) {
        operator fun plus(other: Window) =
            Window(expected + other.expected, completed + other.completed)

        /** 0–100, or null when nothing was scheduled and a percentage would be meaningless. */
        val percent: Double?
            get() = if (expected > 0) (100.0 * completed / expected).coerceIn(0.0, 100.0) else null

        companion object {
            val EMPTY = Window(0, 0)
        }
    }

    /** Parse a stored schedule code (`MO`/`MON`, …). Unknown codes are dropped, not guessed. */
    fun dayOf(code: String): DayOfWeek? =
        when (code.trim().uppercase()) {
            "MO",
            "MON" -> DayOfWeek.MONDAY
            "TU",
            "TUE" -> DayOfWeek.TUESDAY
            "WE",
            "WED" -> DayOfWeek.WEDNESDAY
            "TH",
            "THU" -> DayOfWeek.THURSDAY
            "FR",
            "FRI" -> DayOfWeek.FRIDAY
            "SA",
            "SAT" -> DayOfWeek.SATURDAY
            "SU",
            "SUN" -> DayOfWeek.SUNDAY
            else -> null
        }

    /**
     * Adherence for one plan, clipped to the overlap of the plan's own run and
     * [windowFrom]..[today].
     *
     * [sessionDates] may contain duplicates and dates outside the window; both are handled here so
     * callers cannot get it subtly wrong.
     */
    fun forPlan(
        planStart: LocalDate,
        planEnd: LocalDate?,
        windowFrom: LocalDate,
        today: LocalDate,
        scheduleCodes: List<String>,
        sessionDates: List<LocalDate>,
    ): Window {
        val start = maxOf(planStart, windowFrom)
        val end = minOf(planEnd ?: today, today)
        if (start.isAfter(end)) return Window.EMPTY
        val scheduled = scheduleCodes.mapNotNull(::dayOf).toSet()
        if (scheduled.isEmpty()) return Window.EMPTY

        var expected = 0
        var d = start
        while (!d.isAfter(end)) {
            if (d.dayOfWeek in scheduled) expected++
            d = d.plusDays(1)
        }
        val completed =
            sessionDates
                .filter { !it.isBefore(start) && !it.isAfter(end) && it.dayOfWeek in scheduled }
                .distinct()
                .size
        return Window(expected, completed.coerceAtMost(expected))
    }
}
