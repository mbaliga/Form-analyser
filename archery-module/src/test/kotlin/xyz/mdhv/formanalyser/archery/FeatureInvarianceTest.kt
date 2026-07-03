package xyz.mdhv.formanalyser.archery

import xyz.mdhv.formanalyser.archery.pose.PoseSequence
import xyz.mdhv.formanalyser.model.Handedness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Phase 1 exit-criterion test: a left-handed archer's capture, once normalized, yields exactly
 * the same features and phase timestamps as the right-handed original. Runs the FULL segmenter +
 * extractor on both `(RH raw)` and `(normalize(LH twin, LH))`.
 */
class FeatureInvarianceTest {
    @Test fun lhTwinYieldsIdenticalFeaturesAndPhases() {
        val rh: PoseSequence = PoseSynth.shotSequence()

        // An LH archer shooting the same form produces the mirror capture.
        val lhTwin = PoseSequence(rh.frames.map { HandednessNormalizer.mirror(it) }, rh.fps)
        val normalized = HandednessNormalizer.normalize(lhTwin, Handedness.LH)

        val rhShots = ArcheryModule.segmenter.segment(rh)
        val lhShots = ArcheryModule.segmenter.segment(normalized)
        assertEquals(1, rhShots.size)
        assertEquals(rhShots.size, lhShots.size)

        val rhShot = rhShots[0]
        val lhShot = lhShots[0]
        // identical phase boundaries
        assertEquals(rhShot.drawStart, lhShot.drawStart)
        assertEquals(rhShot.holdStart, lhShot.holdStart)
        assertEquals(rhShot.releaseStart, lhShot.releaseStart)
        assertEquals(rhShot.releaseEnd, lhShot.releaseEnd)

        // identical features to 1e-6
        val rhF = ArcheryModule.extractor.extract(rhShot)
        val lhF = ArcheryModule.extractor.extract(lhShot)
        assertEquals(rhF.keys, lhF.keys)
        for (k in rhF.keys) assertEquals(rhF.getValue(k), lhF.getValue(k), 1e-6, "feature $k differs")
        assertTrue(rhF.isNotEmpty())
    }
}
