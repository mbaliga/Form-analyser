package xyz.mdhv.formanalyser.app.domain

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mdhv.formanalyser.app.data.Repository
import xyz.mdhv.formanalyser.athlete.RegionSignal

class BodyContextViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)

    data class UiState(
        val signals: Map<String, RegionSignal> = emptyMap(),
        val activeInjuryCount: Int = 0,
        val physioAdherence28d: Int? = null,
        val physioCompleted28d: Int = 0,
        val physioExpected28d: Int = 0,
        val evidenceNote: String =
            "Body context combines your own logs; it is not a diagnosis or injury-risk score.",
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    fun load() {
        viewModelScope.launch { _state.value = withContext(Dispatchers.IO) { buildState() } }
    }

    private suspend fun buildState(): UiState {
        val athlete = repo.currentAthlete() ?: return UiState()
        val now = System.currentTimeMillis()
        val pain = repo.body.painSince(athlete.id, now - 7L * 86_400_000L)
        val latest = repo.wellness.latestCheckin(athlete.id)
        val soreness = latest?.let { repo.wellness.sorenessFor(it.id).toSet() }.orEmpty()
        val injuries = repo.body.activeInjuries(athlete.id)
        val today = LocalDate.now()
        val plans = repo.body.activePlans(athlete.id, today.toString())
        val painBy = pain.groupBy { it.regionId }.mapValues { (_, r) -> r.maxOf { it.intensity } }
        val injuryBy =
            injuries
                .flatMap { injury ->
                    JsonLists.decode(injury.regionsJson).map {
                        it to (injury.severity * 3).coerceIn(1, 10)
                    }
                }
                .groupBy({ it.first }, { it.second })
                .mapValues { it.value.maxOrNull() ?: 0 }
        val physio = plans.flatMap { JsonLists.decode(it.targetRegionsJson) }.toSet()
        val ids = painBy.keys + soreness + injuryBy.keys + physio
        val signals =
            ids.associateWith { r ->
                RegionSignal(r, painBy[r] ?: 0, r in soreness, injuryBy[r] ?: 0, r in physio)
            }
        val from = today.minusDays(27)
        val window =
            plans.fold(PhysioAdherence.Window.EMPTY) { acc, p ->
                acc +
                    PhysioAdherence.forPlan(
                        LocalDate.parse(p.startDate),
                        p.endDate?.let(LocalDate::parse),
                        from,
                        today,
                        JsonLists.decode(p.scheduleJson),
                        repo.body.physioSessionsFor(p.id).map {
                            Instant.ofEpochMilli(it.ts).atZone(ZoneId.systemDefault()).toLocalDate()
                        },
                    )
            }
        val expected = window.expected
        val completed = window.completed
        val adherence = window.percent?.toInt()
        return UiState(signals, injuries.size, adherence, completed, expected)
    }
}
