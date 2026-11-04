package xyz.mdhv.formanalyser.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** The volume rule Progress and the "This week" widget share. */
class TrainingVolumeTest {
    @Test
    fun linkedScorecardFoldsIntoItsCaptureSessionAndTheLargerCountWins() {
        val points =
            TrainingVolume.points(
                listOf(TrainingVolume.Capture("cap", atMs = 100, arrows = 30)),
                listOf(
                    TrainingVolume.Card(atMs = 200, linkedCaptureId = "cap", arrows = 24),
                    TrainingVolume.Card(atMs = 210, linkedCaptureId = "cap", arrows = 12),
                ),
            )
        // 24 + 12 scored against 30 captured: one point, at the capture's time, of 36.
        assertEquals(listOf(TrainingVolume.Point(100, 36)), points)
    }

    @Test
    fun captureThatOutCountsItsLinkedCardKeepsItsOwnCount() {
        val points =
            TrainingVolume.points(
                listOf(TrainingVolume.Capture("cap", 100, 60)),
                listOf(TrainingVolume.Card(200, "cap", 36)),
            )
        assertEquals(listOf(TrainingVolume.Point(100, 60)), points)
    }

    @Test
    fun unlinkedCardsAndCardsLinkedToMissingSessionsStandOnTheirOwn() {
        val points =
            TrainingVolume.points(
                listOf(TrainingVolume.Capture("cap", 100, 10)),
                listOf(
                    TrainingVolume.Card(300, null, 72),
                    TrainingVolume.Card(400, "retracted-session", 36),
                ),
            )
        assertEquals(
            listOf(
                TrainingVolume.Point(100, 10),
                TrainingVolume.Point(300, 72),
                TrainingVolume.Point(400, 36),
            ),
            points,
        )
    }

    @Test
    fun noSessionsMeansNoPoints() {
        assertEquals(emptyList<TrainingVolume.Point>(), TrainingVolume.points(emptyList(), emptyList()))
    }
}
