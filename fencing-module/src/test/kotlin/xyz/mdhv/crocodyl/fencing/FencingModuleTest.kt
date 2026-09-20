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
}
