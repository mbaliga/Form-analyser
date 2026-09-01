package xyz.mdhv.formanalyser.wellness

import kotlin.math.min

/** One shot's timing within the recording, in seconds. */
data class ShotSpan(val drawStartS: Double, val releaseS: Double)

data class DurationResult(val seconds: Double, val flaggedNoShots: Boolean)

/**
 * Auto session duration, idle-trimmed (Phase 2 §A4):
 * `Σ (release − draw_start) + Σ min(gap, GAP_CAP)`. Zero shots → recording length, flagged.
 */
object DurationModel {
    fun auto(shots: List<ShotSpan>, recordingLengthS: Double?): DurationResult {
        if (shots.isEmpty()) return DurationResult(recordingLengthS ?: 0.0, flaggedNoShots = true)
        val ordered = shots.sortedBy { it.drawStartS }
        var total = 0.0
        for (i in ordered.indices) {
            total += (ordered[i].releaseS - ordered[i].drawStartS).coerceAtLeast(0.0)
            if (i > 0) {
                val gap = ordered[i].drawStartS - ordered[i - 1].releaseS
                if (gap > 0) total += min(gap, WellnessConstants.GAP_CAP_S)
            }
        }
        return DurationResult(total, flaggedNoShots = false)
    }
}
