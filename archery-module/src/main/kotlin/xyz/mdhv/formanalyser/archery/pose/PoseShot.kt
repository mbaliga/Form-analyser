package xyz.mdhv.formanalyser.archery.pose

/**
 * One segmented archery shot from a pose capture, with the draw cycle's phase boundaries
 * (frame indices into [window], half-open): DRAW [drawStart, holdStart),
 * ANCHOR/HOLD [holdStart, releaseStart), RELEASE+FOLLOW-THROUGH [releaseStart, releaseEnd).
 */
data class PoseShot(
    val window: PoseSequence,
    val handedness: Handedness,
    val drawStart: Int,
    val holdStart: Int,
    val releaseStart: Int,
    val releaseEnd: Int,
) {
    val fps: Double get() = window.fps

    fun holdSlice(): PoseSequence = window.slice(holdStart, releaseStart)

    /** A representative full-draw frame (mid-hold) for static form measurement. */
    fun anchorFrame(): PoseFrame {
        val mid = ((holdStart + releaseStart) / 2).coerceIn(0, window.size - 1)
        return window.frames[mid]
    }

    val holdDurationSeconds: Double get() = (releaseStart - holdStart).coerceAtLeast(0) / fps
    val drawDurationSeconds: Double get() = (holdStart - drawStart).coerceAtLeast(0) / fps
}
