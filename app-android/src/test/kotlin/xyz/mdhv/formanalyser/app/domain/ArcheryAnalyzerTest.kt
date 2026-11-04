package xyz.mdhv.formanalyser.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArcheryAnalyzerTest {
    @Test
    fun featurePersistenceRoundTripPreservesValuesAndInsertionOrder() {
        val original =
            linkedMapOf(
                "bow_arm_angle" to 42.5,
                "release_stability" to 0.875,
                "draw_duration_s" to 1.25,
            )

        val restored = ArcheryAnalyzer.featuresFromJson(ArcheryAnalyzer.featuresToJson(original))

        assertEquals(original, restored)
        assertEquals(listOf("bow_arm_angle", "release_stability", "draw_duration_s"), restored.keys.toList())
    }

    @Test
    fun baselineBridgeReportsReadinessAndStableCenterScore() {
        val baselineSamples =
            (0 until 8).map { index ->
                mapOf(
                    "bow_arm_angle" to 40.0 + index,
                    "release_stability" to 0.80 + index * 0.01,
                )
            }

        val baseline = ArcheryAnalyzer.buildBaseline(baselineSamples)
        val result = ArcheryAnalyzer.score(baseline, baselineSamples[4])

        assertTrue(baseline.isReady())
        assertEquals(8L, baseline.repCount)
        assertTrue(result.stability in 0.0..100.0)
        assertTrue(result.perFeature.keys.containsAll(baseline.featureNames))
    }
}
