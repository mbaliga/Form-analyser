package xyz.mdhv.formanalyser.archery

import xyz.mdhv.formanalyser.archery.pose.Landmark
import xyz.mdhv.formanalyser.archery.pose.PoseFrame
import xyz.mdhv.formanalyser.archery.pose.PoseLandmarks
import xyz.mdhv.formanalyser.model.Handedness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HandednessNormalizerTest {
    /** A pose where every landmark has a distinguishable payload (x = index/100, vis = index). */
    private fun distinctPose(): PoseFrame =
        PoseFrame(42L, (0 until PoseLandmarks.COUNT).map { Landmark(it / 100.0, it / 1000.0, it / 10.0, it.toDouble()) })

    @Test fun swapTableIsCorrectAndComplete() {
        // pairs cover all non-zero indices exactly once; 0 (nose) excluded
        val covered = HandednessNormalizer.SWAP_PAIRS.flatMap { listOf(it.first, it.second) }.toSet()
        assertEquals((1 until PoseLandmarks.COUNT).toSet(), covered)
        assertEquals(16, HandednessNormalizer.SWAP_PAIRS.size)

        val src = distinctPose()
        val m = HandednessNormalizer.mirror(src)
        // wrists (15,16) swap: mirrored[15] carries source[16]'s visibility/z; x is flipped
        assertEquals(src[16].visibility, m[15].visibility)
        assertEquals(src[16].z, m[15].z)
        assertEquals(1.0 - src[16].x, m[15].x, 1e-12)
        // nose (0) keeps its payload, only x flips
        assertEquals(src[0].visibility, m[0].visibility)
        assertEquals(1.0 - src[0].x, m[0].x, 1e-12)
    }

    @Test fun mirrorIsAnInvolution() {
        val src = distinctPose()
        val twice = HandednessNormalizer.mirror(HandednessNormalizer.mirror(src))
        for (i in 0 until PoseLandmarks.COUNT) {
            assertEquals(src[i].x, twice[i].x, 1e-12)
            assertEquals(src[i].y, twice[i].y, 1e-12)
            assertEquals(src[i].z, twice[i].z, 1e-12)
            assertEquals(src[i].visibility, twice[i].visibility, 1e-12)
        }
    }

    @Test fun mirrorIsNotIdentityOnAsymmetricPose() {
        val src = distinctPose()
        val m = HandednessNormalizer.mirror(src)
        assertTrue((0 until PoseLandmarks.COUNT).any { m[it].x != src[it].x || m[it].visibility != src[it].visibility })
    }

    @Test fun normalizeRhIsIdentity() {
        val src = distinctPose()
        val n = HandednessNormalizer.normalize(src, Handedness.RH)
        assertEquals(src.landmarks, n.landmarks)
    }

    @Test fun effectiveHandednessResolverPrecedence() {
        assertEquals(Handedness.LH, EffectiveHandedness.resolve(Handedness.RH, Handedness.LH))
        assertEquals(Handedness.RH, EffectiveHandedness.resolve(Handedness.RH, null))
        assertEquals(Handedness.LH, EffectiveHandedness.resolve(Handedness.LH, null))
    }
}
