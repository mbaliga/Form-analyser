package xyz.mdhv.formanalyser.app.capture

import android.content.Context
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xyz.mdhv.formanalyser.archery.pose.ArmLandmarks
import xyz.mdhv.formanalyser.archery.pose.Geometry
import xyz.mdhv.formanalyser.archery.pose.Handedness
import xyz.mdhv.formanalyser.archery.pose.Landmark
import xyz.mdhv.formanalyser.archery.pose.PoseFrame
import xyz.mdhv.formanalyser.archery.pose.PoseLandmarks
import xyz.mdhv.formanalyser.archery.pose.PoseSequence

/**
 * Captures pose frames from CameraX [ImageProxy]s via MediaPipe Pose (BlazePose), accumulating
 * a [PoseSequence] while recording. The phone sits on a tripod, lateral/sagittal to the archer
 * (handoff §3.6) — there is no bow-mounted hardware; vision is the modality.
 *
 * Uses RunningMode.IMAGE with a synchronous detect() per analysed frame (simpler than the
 * live-stream callback dance, and the analysis executor is already off the main thread).
 *
 * Requires the model asset at assets/[MODEL_ASSET] (see app-android/README.md) — a binary not
 * checked into git.
 */
class PoseRecorder(
    context: Context,
    private val handedness: Handedness = Handedness.RIGHT,
) {
    private val arms = ArmLandmarks(handedness)

    private val landmarker: PoseLandmarker? = runCatching {
        PoseLandmarker.createFromOptions(
            context,
            PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
                .setRunningMode(RunningMode.IMAGE)
                .setNumPoses(1)
                .build(),
        )
    }.getOrNull()

    val isAvailable: Boolean get() = landmarker != null

    private val frames = ArrayList<PoseFrame>(4_000)
    @Volatile private var recording = false

    private val _liveTracking = MutableStateFlow(false)
    val liveTracking: StateFlow<Boolean> = _liveTracking

    /** Live bow-arm angle (deg) for an in-the-moment form readout, or null when no pose. */
    private val _liveBowArmAngle = MutableStateFlow<Double?>(null)
    val liveBowArmAngle: StateFlow<Double?> = _liveBowArmAngle

    fun start() {
        frames.clear()
        recording = true
    }

    /** Analyse one camera frame. Always closes [image]. */
    fun process(image: ImageProxy) {
        val lm = landmarker
        if (lm == null) { image.close(); return }
        try {
            val bitmap = image.toBitmap()
            val mp = BitmapImageBuilder(bitmap).build()
            val opts = ImageProcessingOptions.builder()
                .setRotationDegrees(image.imageInfo.rotationDegrees)
                .build()
            val result = lm.detect(mp, opts)
            val poses = result.landmarks()
            if (poses.isEmpty()) {
                _liveTracking.value = false
                return
            }
            val frame = poses[0].toPoseFrame(image.imageInfo.timestamp)
            _liveTracking.value = true
            _liveBowArmAngle.value = Geometry.angleDeg(
                frame[arms.bowShoulder], frame[arms.bowElbow], frame[arms.bowWrist],
            )
            if (recording) frames.add(frame)
        } catch (_: Throwable) {
            _liveTracking.value = false
        } finally {
            image.close()
        }
    }

    /** Stop and return the captured window, or null if too little usable pose was seen. */
    fun stop(): PoseSequence? {
        recording = false
        val snapshot = ArrayList(frames)
        if (snapshot.size < 2) return null
        val durationSec = (snapshot.last().tNanos - snapshot.first().tNanos) / 1e9
        val fps = if (durationSec > 0) (snapshot.size - 1) / durationSec else 30.0
        return PoseSequence(snapshot, fps)
    }

    fun close() = landmarker?.close()

    private fun com.google.mediapipe.tasks.components.containers.NormalizedLandmark.toLandmark(): Landmark =
        Landmark(x().toDouble(), y().toDouble(), z().toDouble(), visibility().orElse(1.0f).toDouble())

    private fun List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>.toPoseFrame(tNanos: Long): PoseFrame {
        // Pad/truncate defensively to the expected 33 landmarks.
        val list = if (size >= PoseLandmarks.COUNT) {
            (0 until PoseLandmarks.COUNT).map { this[it].toLandmark() }
        } else {
            (0 until PoseLandmarks.COUNT).map { if (it < size) this[it].toLandmark() else Landmark(0.0, 0.0, 0.0, 0.0) }
        }
        return PoseFrame(tNanos, list)
    }

    companion object {
        const val MODEL_ASSET = "pose_landmarker_lite.task"
    }
}
