package xyz.mdhv.formanalyser.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mdhv.crocodyl.fencing.FencerSide
import xyz.mdhv.crocodyl.fencing.FencingWeapon
import xyz.mdhv.crocodyl.fencing.RefereeCall
import xyz.mdhv.crocodyl.fencing.RefereePhase

class FencingViewModelTest {
    @Test
    fun matchStateSurvivesUiRecompositionAndUsesNamedFencers() {
        val viewModel = FencingViewModel()

        viewModel.setLeftName("A very long fencer name that is deliberately longer than the UI limit")
        viewModel.setRightName("Rival")
        viewModel.ready()
        viewModel.start()
        viewModel.award(FencerSide.LEFT)

        assertEquals("A very long fencer name that is deliberately longer than the UI limit".take(40), viewModel.state.value.leftName)
        assertEquals("Rival", viewModel.state.value.rightName)
        assertEquals(1, viewModel.state.value.match.leftScore)
        assertEquals(RefereePhase.HALTED, viewModel.state.value.match.phase)
        assertEquals(RefereeCall.POINT_LEFT, viewModel.state.value.match.lastDecision?.call)
    }

    @Test
    fun changingWeaponStartsAnAuditableFreshMatchWithoutErasingNames() {
        val viewModel = FencingViewModel()
        viewModel.setLeftName("Alice")
        viewModel.setRightName("Bea")
        viewModel.ready()
        viewModel.selectWeapon(FencingWeapon.EPEE)

        assertEquals(FencingWeapon.EPEE, viewModel.state.value.selectedWeapon)
        assertEquals(FencingWeapon.EPEE, viewModel.state.value.match.weapon)
        assertTrue(viewModel.state.value.match.history.isEmpty())
        assertEquals("Alice", viewModel.state.value.leftName)
        assertEquals("Bea", viewModel.state.value.rightName)
    }
}
