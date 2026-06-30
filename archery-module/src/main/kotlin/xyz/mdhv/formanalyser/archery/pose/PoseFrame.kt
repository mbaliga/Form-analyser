package xyz.mdhv.formanalyser.archery.pose

/**
 * A single pose landmark from BlazePose / MediaPipe Pose. Coordinates are image-normalised:
 * [x], [y] in [0,1] (origin top-left, y increases downward), [z] is relative depth, and
 * [visibility] in [0,1]. The vision pipeline works in 2D (x,y) on a lateral/sagittal view;
 * z is treated cautiously (monocular depth is weak — handoff §6 caveat).
 */
data class Landmark(
    val x: Double,
    val y: Double,
    val z: Double = 0.0,
    val visibility: Double = 1.0,
)

/** BlazePose 33-landmark indices (the subset the archery form features use). */
object PoseLandmarks {
    const val NOSE = 0
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val LEFT_ANKLE = 27
    const val RIGHT_ANKLE = 28
    const val COUNT = 33
}

/** Which hand draws the string. A right-handed archer holds the bow in the LEFT hand. */
enum class Handedness { RIGHT, LEFT }

/**
 * The landmark indices for the bow arm and draw arm given [handedness]. Right-handed:
 * bow arm = left side, draw arm = right side.
 */
class ArmLandmarks(handedness: Handedness) {
    val bowShoulder: Int
    val bowElbow: Int
    val bowWrist: Int
    val drawShoulder: Int
    val drawElbow: Int
    val drawWrist: Int

    init {
        if (handedness == Handedness.RIGHT) {
            bowShoulder = PoseLandmarks.LEFT_SHOULDER
            bowElbow = PoseLandmarks.LEFT_ELBOW
            bowWrist = PoseLandmarks.LEFT_WRIST
            drawShoulder = PoseLandmarks.RIGHT_SHOULDER
            drawElbow = PoseLandmarks.RIGHT_ELBOW
            drawWrist = PoseLandmarks.RIGHT_WRIST
        } else {
            bowShoulder = PoseLandmarks.RIGHT_SHOULDER
            bowElbow = PoseLandmarks.RIGHT_ELBOW
            bowWrist = PoseLandmarks.RIGHT_WRIST
            drawShoulder = PoseLandmarks.LEFT_SHOULDER
            drawElbow = PoseLandmarks.LEFT_ELBOW
            drawWrist = PoseLandmarks.LEFT_WRIST
        }
    }
}

/** One captured frame: a timestamp and 33 landmarks (index-aligned to [PoseLandmarks]). */
class PoseFrame(val tNanos: Long, val landmarks: List<Landmark>) {
    init { require(landmarks.size == PoseLandmarks.COUNT) { "expected ${PoseLandmarks.COUNT} landmarks, got ${landmarks.size}" } }
    operator fun get(index: Int): Landmark = landmarks[index]
}

/** A uniformly-sampled window of pose frames — the vision analogue of the engine's TimeSeries. */
class PoseSequence(val frames: List<PoseFrame>, val fps: Double) {
    init { require(fps > 0) { "fps must be positive" } }
    val size: Int get() = frames.size
    val durationSeconds: Double get() = if (frames.size < 2) 0.0 else (frames.size - 1) / fps

    fun slice(startInclusive: Int, endExclusive: Int): PoseSequence {
        require(startInclusive in 0..endExclusive && endExclusive <= frames.size) {
            "bad slice [$startInclusive, $endExclusive) over size ${frames.size}"
        }
        return PoseSequence(frames.subList(startInclusive, endExclusive), fps)
    }
}
