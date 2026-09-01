package xyz.mdhv.formanalyser.wellness

import java.time.LocalDate

/**
 * One session's inputs to the load model. Poundage is passed already resolved per rig
 * (measured > estimated > marked — core-equipment's job); this layer only applies the
 * session→athlete-active fallback and the kg conversion.
 */
data class SessionLoad(
    val date: LocalDate,
    val arrows: Int?,
    val sessionPoundageLbs: Double?,
    val athleteActivePoundageLbs: Double? = null,
    val durationMin: Double? = null,
    val rpe: Double? = null,
)

/** Aggregated load for one calendar day (device-local). [complete] is false if any session that
 * day lacked poundage/arrows — such a day is flagged, never silently zeroed. */
data class DailyLoad(
    val date: LocalDate,
    val shotLoad: Double,
    val srpeLoad: Double,
    val complete: Boolean,
)

object LoadModel {
    /** shot_load = arrows × otf_kg. */
    fun shotLoadKg(arrows: Int, poundageLbs: Double): Double =
        arrows * poundageLbs * WellnessConstants.LBS_TO_KG

    /** srpe_load = duration_min × RPE (CR10). */
    fun srpeLoad(durationMin: Double, rpe: Double): Double = durationMin * rpe

    /** Session poundage with the fallback chain: session rig → athlete's active rig → null. */
    fun resolvePoundageLbs(s: SessionLoad): Double? =
        s.sessionPoundageLbs ?: s.athleteActivePoundageLbs

    /** True when this session contributes to the shot-load series (has poundage + arrows). */
    fun isComplete(s: SessionLoad): Boolean =
        resolvePoundageLbs(s) != null && s.arrows != null

    /** Daily aggregation over the dates that actually have sessions, oldest→newest. */
    fun dailyLoads(sessions: List<SessionLoad>): List<DailyLoad> =
        sessions.groupBy { it.date }.toSortedMap().map { (date, day) ->
            var shot = 0.0
            var srpe = 0.0
            var complete = true
            for (s in day) {
                val lbs = resolvePoundageLbs(s)
                if (lbs != null && s.arrows != null) shot += shotLoadKg(s.arrows, lbs) else complete = false
                if (s.durationMin != null && s.rpe != null) srpe += srpeLoad(s.durationMin, s.rpe)
            }
            DailyLoad(date, shot, srpe, complete)
        }
}
