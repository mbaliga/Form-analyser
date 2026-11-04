package xyz.mdhv.formanalyser.app.capture

import xyz.mdhv.formanalyser.archery.pose.PoseFrame

/** Thread-safe recording window shared by the UI thread and CameraX's analysis executor. */
internal class PoseFrameBuffer {
    private val lock = Any()
    private val frames = ArrayList<PoseFrame>(4_000)
    private var generation = 0L
    private var recording = false

    /** Starts a fresh capture and invalidates work already in flight from an earlier window. */
    fun start() = synchronized(lock) {
        generation += 1
        frames.clear()
        recording = true
        generation
    }

    /** Snapshot the active window when a camera frame begins processing. */
    fun generationForFrame(): Long? = synchronized(lock) {
        generation.takeIf { recording }
    }

    /** Append only if this frame began in the still-active recording window. */
    fun appendIfCurrent(frameGeneration: Long?, frame: PoseFrame): Boolean = synchronized(lock) {
        if (!recording || frameGeneration == null || frameGeneration != generation) return@synchronized false
        frames.add(frame)
        true
    }

    /** Stop atomically with respect to frame appends and return an immutable snapshot. */
    fun stop(): List<PoseFrame> = synchronized(lock) {
        recording = false
        frames.toList()
    }
}
