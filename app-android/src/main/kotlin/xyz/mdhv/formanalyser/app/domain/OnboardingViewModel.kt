package xyz.mdhv.formanalyser.app.domain

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mdhv.formanalyser.app.data.AppPrefs
import xyz.mdhv.formanalyser.app.data.AthleteEntity
import xyz.mdhv.formanalyser.app.data.Repository
import xyz.mdhv.formanalyser.app.data.RigEntity
import xyz.mdhv.formanalyser.model.BowType
import xyz.mdhv.formanalyser.model.Handedness
import java.util.UUID
import kotlin.random.Random

data class OnboardingState(
    val step: Int = 0,
    val role: String = "ARCHER",           // ARCHER | COACH | BOTH
    val name: String = "",
    val avatarSeed: Long = Random.nextLong().let { if (it == 0L) 1L else it },
    val handedness: Handedness = Handedness.RH,
    val drawLengthMm: Int = 700,
    val drawLengthSet: Boolean = false,
    val rigName: String = "My bow",
    val bowType: BowType = BowType.RECURVE,
    val riserLengthIn: Int = 25,
    val markedLbs: Double? = null,
    val otfLbs: Double? = null,
) {
    val lastStep = 6
    val canProceed: Boolean get() = when (step) {
        1 -> name.trim().length in 1..40
        else -> true
    }
}

class OnboardingViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    private val prefs = AppPrefs(app)

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state

    fun update(transform: (OnboardingState) -> OnboardingState) { _state.value = transform(_state.value) }
    fun next() { if (_state.value.step < _state.value.lastStep) update { it.copy(step = it.step + 1) } }
    fun back() { if (_state.value.step > 0) update { it.copy(step = it.step - 1) } }
    fun shuffleAvatar() = update { it.copy(avatarSeed = Random.nextLong().let { s -> if (s == 0L) 1L else s }) }

    /** Atomic commit: athlete + active rig + onboarded flag. Then [onDone]. */
    fun commit(onDone: () -> Unit) {
        val s = _state.value
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val athleteId = UUID.randomUUID().toString()
                repo.updateAthlete(
                    AthleteEntity(
                        id = athleteId,
                        displayName = s.name.trim().ifBlank { "Athlete" },
                        bodyMassKg = 70.0,
                        handedness = s.handedness.name,
                        drawLengthMm = if (s.drawLengthSet) s.drawLengthMm else null,
                        avatarSeed = s.avatarSeed,
                    ),
                )
                val tuning = TuningV0(
                    markedLbs = s.markedLbs,
                    riserLengthIn = if (s.bowType == BowType.COMPOUND) null else s.riserLengthIn.toDouble(),
                    otfLbs = s.otfLbs,
                )
                repo.upsertRig(
                    RigEntity(
                        id = UUID.randomUUID().toString(),
                        athleteId = athleteId,
                        name = s.rigName.ifBlank { "My bow" },
                        bowType = s.bowType.name,
                        tuningJson = Tuning.encode(tuning),
                        active = true,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                prefs.setRole(s.role)
                prefs.setOnboarded(true)
            }
            onDone()
        }
    }
}
