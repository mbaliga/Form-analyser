package xyz.mdhv.formanalyser.athlete

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AthleteModelTest {
    @Test fun goalProgressUsesDeclaredAggregation() {
        val goal = GoalDefinition(
            id = "g", metric = GoalMetric.ROUND_TOTAL, title = "Break 650", targetValue = 650.0,
            unit = "pts", direction = GoalDirection.AT_LEAST, aggregation = GoalAggregation.MAX,
            startAtMs = 0, baselineValue = 600.0,
        )
        val progress = GoalEngine.evaluate(goal, listOf(
            MetricObservation(1, 610.0, "a"), MetricObservation(2, 640.0, "b"),
        ))
        assertFalse(progress.achieved)
        assertEquals(640.0, progress.currentValue)
        assertEquals(0.8, progress.progressFraction!!, 1e-9)
        assertEquals(10.0, progress.remaining)
    }

    @Test fun trendReportsDirectionAndStability() {
        val day = 86_400_000L
        val t = TrendEngine.summarize(listOf(
            MetricObservation(0, 600.0, "a"),
            MetricObservation(day, 610.0, "b"),
            MetricObservation(day * 2, 620.0, "c"),
        ))
        assertNotNull(t)
        assertEquals(10.0, t.slopePerDay, 1e-9)
        assertEquals(20.0, t.delta, 1e-9)
        assertTrue(t.stability > 0.98)
    }

    @Test fun smartDefaultsUseRecentPerFieldAndExplicitPin() {
        val history = listOf(
            SessionContextObservation(10, SessionDefaults(venue = "Old", distanceMeters = 18, targetFaceCm = 40)),
            SessionContextObservation(20, SessionDefaults(venue = "Club", distanceMeters = 70, rigId = "r1")),
        )
        val resolved = SmartDefaultsEngine.resolve(
            history,
            pinned = SessionDefaults(distanceMeters = 60, trainingIntent = "volume"),
            activeRigId = "r2",
        )
        assertEquals("Club", resolved.venue)
        assertEquals(60, resolved.distanceMeters)
        assertEquals(40, resolved.targetFaceCm)
        assertEquals("r2", resolved.rigId)
        assertEquals("volume", resolved.trainingIntent)
    }

    @Test fun bodyIntensityIsContextNotProbability() {
        val s = RegionSignal("right_shoulder", pain = 4, injurySeverity = 7, soreness = true, physioTarget = true)
        assertEquals(7, s.contextIntensity)
        assertTrue(BodySignal.INJURY in s.signals)
        assertTrue(BodySignal.PHYSIO_TARGET in s.signals)
    }
}
