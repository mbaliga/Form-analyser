package xyz.mdhv.formanalyser.archery

import xyz.mdhv.formanalyser.archery.pose.Landmark
import xyz.mdhv.formanalyser.archery.pose.PoseFrame
import xyz.mdhv.formanalyser.archery.pose.PoseSequence
import xyz.mdhv.formanalyser.model.Handedness

/**
 * The single handedness-normalization point (Phase 1 §B). Applied immediately after landmark
 * extraction and before anything consumes the pose (segmenter, feature extractor, live readout).
 * For [Handedness.RH] it is identity; for [Handedness.LH] it mirrors the capture into the canonical
 * right-handed frame — so all downstream code (features, thresholds, segmenter) stays byte-identical
 * and "draw wrist" is always the right wrist.
 *
 * Pose space is MediaPipe **normalized image landmarks** ([0,1], y-down), so the mirror is
 * `x' = 1 − x` with left/right landmark indices swapped; visibility travels with the swapped index;
 * depth (z) is unchanged by a horizontal flip.
 */
object HandednessNormalizer {
    /** BlazePose 33-landmark left↔right swap pairs (index 0 = nose is fixed). */
    val SWAP_PAIRS: List<Pair<Int, Int>> = listOf(
        1 to 4, 2 to 5, 3 to 6,      // eyes inner/center/outer
        7 to 8,                       // ears
        9 to 10,                      // mouth
        11 to 12,                     // shoulders
        13 to 14,                     // elbows
        15 to 16,                     // wrists
        17 to 18,                     // pinky
        19 to 20,                     // index
        21 to 22,                     // thumb
        23 to 24,                     // hips
        25 to 26,                     // knees
        27 to 28,                     // ankles
        29 to 30,                     // heels
        31 to 32,                     // foot index
    )

    fun normalize(frame: PoseFrame, handedness: Handedness): PoseFrame =
        if (handedness == Handedness.RH) frame else mirror(frame)

    fun normalize(sequence: PoseSequence, handedness: Handedness): PoseSequence =
        if (handedness == Handedness.RH) sequence
        else PoseSequence(sequence.frames.map { mirror(it) }, sequence.fps)

    /** Mirror a frame into the opposite-handed frame (its own involution). */
    fun mirror(frame: PoseFrame): PoseFrame {
        val src = frame.landmarks
        val swapped = src.toMutableList()
        for ((a, b) in SWAP_PAIRS) {
            swapped[a] = src[b]
            swapped[b] = src[a]
        }
        // Flip x for every landmark; visibility/z ride along with the (already swapped) landmark.
        val flipped = swapped.map { Landmark(1.0 - it.x, it.y, it.z, it.visibility) }
        return PoseFrame(frame.tNanos, flipped)
    }
}

/** Resolves the handedness a session should be analyzed in: per-session override beats the athlete default. */
object EffectiveHandedness {
    fun resolve(athlete: Handedness, sessionOverride: Handedness?): Handedness = sessionOverride ?: athlete
}
