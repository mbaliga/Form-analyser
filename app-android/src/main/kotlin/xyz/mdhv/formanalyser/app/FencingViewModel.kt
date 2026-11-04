package xyz.mdhv.formanalyser.app

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import xyz.mdhv.crocodyl.fencing.FencerSide
import xyz.mdhv.crocodyl.fencing.FencingWeapon
import xyz.mdhv.crocodyl.fencing.RefereeMatchState
import xyz.mdhv.crocodyl.fencing.RefereeModeSession

data class FencingUiState(
    val selectedWeapon: FencingWeapon = FencingWeapon.SABRE,
    val leftName: String = "Left fencer",
    val rightName: String = "Right fencer",
    val match: RefereeMatchState = RefereeModeSession(FencingWeapon.SABRE).snapshot(),
)

/** Owns the live manual console state so a rotation cannot erase an in-progress match. */
class FencingViewModel : ViewModel() {
    private var session = RefereeModeSession(FencingWeapon.SABRE)
    private val stateHolder = mutableStateOf(FencingUiState())

    val state: MutableState<FencingUiState> = stateHolder

    fun selectWeapon(weapon: FencingWeapon) {
        session = RefereeModeSession(weapon)
        update { it.copy(selectedWeapon = weapon, match = session.snapshot()) }
    }

    fun setLeftName(value: String) = update { it.copy(leftName = value.take(MAX_FENCER_NAME_LENGTH)) }

    fun setRightName(value: String) = update { it.copy(rightName = value.take(MAX_FENCER_NAME_LENGTH)) }

    fun ready() = act { session.ready() }

    fun start() = act { session.start() }

    fun halt() = act { session.halt() }

    fun award(side: FencerSide) = act { session.award(side) }

    fun undoLastPoint() = update {
        session.undoLastPoint()
        it.copy(match = session.snapshot())
    }

    fun reset() = update {
        session.reset()
        it.copy(match = session.snapshot())
    }

    private fun act(action: () -> Any) = update {
        action()
        it.copy(match = session.snapshot())
    }

    private fun update(transform: (FencingUiState) -> FencingUiState) {
        stateHolder.value = transform(stateHolder.value)
    }

    private companion object {
        const val MAX_FENCER_NAME_LENGTH = 40
    }
}
