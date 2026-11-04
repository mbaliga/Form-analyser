package xyz.mdhv.formanalyser.app.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mdhv.formanalyser.archery.pose.Landmark
import xyz.mdhv.formanalyser.archery.pose.PoseFrame
import xyz.mdhv.formanalyser.archery.pose.PoseLandmarks

class PoseFrameBufferTest {
    @Test
    fun frameAlreadyInFlightWhenRecordingStartsCannotLeakIntoCapture() {
        val buffer = PoseFrameBuffer()
        val oldFrameGeneration = buffer.generationForFrame()

        buffer.start()

        assertFalse(buffer.appendIfCurrent(oldFrameGeneration, frame(1)))
        assertTrue(buffer.appendIfCurrent(buffer.generationForFrame(), frame(2)))
        assertEquals(listOf(2L), buffer.stop().map { it.tNanos })
    }

    @Test
    fun completionAfterStopCannotMutateReturnedSnapshot() {
        val buffer = PoseFrameBuffer()
        buffer.start()
        val inFlightGeneration = buffer.generationForFrame()
        assertTrue(buffer.appendIfCurrent(buffer.generationForFrame(), frame(1)))

        val snapshot = buffer.stop()

        assertEquals(listOf(1L), snapshot.map { it.tNanos })
        assertFalse(buffer.appendIfCurrent(inFlightGeneration, frame(2)))
        assertEquals(listOf(1L), snapshot.map { it.tNanos })
        assertEquals(listOf(1L), buffer.stop().map { it.tNanos })
    }

    @Test
    fun restartingCaptureClearsPriorFramesAndRejectsOldGeneration() {
        val buffer = PoseFrameBuffer()
        buffer.start()
        val firstGeneration = buffer.generationForFrame()
        assertTrue(buffer.appendIfCurrent(firstGeneration, frame(1)))

        buffer.start()

        assertFalse(buffer.appendIfCurrent(firstGeneration, frame(2)))
        assertTrue(buffer.appendIfCurrent(buffer.generationForFrame(), frame(3)))
        assertEquals(listOf(3L), buffer.stop().map { it.tNanos })
    }

    @Test
    fun framesOutsideRecordingAreIgnored() {
        val buffer = PoseFrameBuffer()

        assertNull(buffer.generationForFrame())
        assertFalse(buffer.appendIfCurrent(null, frame(1)))
        assertTrue(buffer.stop().isEmpty())
    }

    private fun frame(timestamp: Long) =
        PoseFrame(timestamp, List(PoseLandmarks.COUNT) { Landmark(0.0, 0.0) })
}
