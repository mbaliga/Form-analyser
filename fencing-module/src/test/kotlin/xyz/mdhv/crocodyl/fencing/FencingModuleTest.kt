package xyz.mdhv.crocodyl.fencing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FencingModuleTest {
    @Test
    fun `all three weapons have stable ids and rules`() {
        assertEquals(listOf("sabre", "foil", "epee"), FencingWeapon.entries.map { it.id })
        assertEquals("Right of way", FencingWeapon.SABRE.priorityRule)
        assertEquals("Whole body", FencingWeapon.EPEE.targetSummary)
        assertTrue(FencingWeapon.FOIL.equipmentSummary.contains("lamé"))
    }

    @Test
    fun `referee mode announces pret allez and point winner`() {
        val session = RefereeModeSession(FencingWeapon.FOIL)
        assertEquals(RefereeCall.PRET, session.ready().call)
        assertEquals(RefereeCall.ALLEZ, session.start().call)
        val decision = session.award(FencerSide.RIGHT, confidence = 0.8)
        assertEquals(RefereeCall.POINT_RIGHT, decision.call)
        assertEquals(FencerSide.RIGHT, decision.pointWinner)
        assertTrue(decision.provisional)
    }

    @Test
    fun `point cannot be awarded before allez`() {
        assertFailsWith<IllegalStateException> {
            RefereeModeSession(FencingWeapon.EPEE).award(FencerSide.LEFT)
        }
    }

    @Test
    fun `match tracks score halts after a point and can undo it`() {
        val session = RefereeModeSession(FencingWeapon.SABRE)
        session.ready()
        session.start()
        session.award(FencerSide.LEFT, confidence = 1.0)

        val afterPoint = session.snapshot()
        assertEquals(RefereePhase.HALTED, afterPoint.phase)
        assertEquals(1, afterPoint.leftScore)
        assertEquals(0, afterPoint.rightScore)
        assertEquals(RefereeCall.POINT_LEFT, afterPoint.lastDecision?.call)

        assertEquals(RefereeCall.POINT_LEFT, session.undoLastPoint()?.call)
        val afterUndo = session.snapshot()
        assertEquals(0, afterUndo.leftScore)
        assertEquals(0, afterUndo.rightScore)
    }

    @Test
    fun `reset clears score and referee history`() {
        val session = RefereeModeSession(FencingWeapon.EPEE)
        session.start()
        session.award(FencerSide.RIGHT)
        session.reset()

        assertEquals(RefereePhase.READY, session.snapshot().phase)
        assertEquals(0, session.snapshot().leftScore)
        assertEquals(0, session.snapshot().rightScore)
        assertTrue(session.snapshot().history.isEmpty())
    }
}
