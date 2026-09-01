package xyz.mdhv.formanalyser.app.domain

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mdhv.formanalyser.app.data.AppPrefs
import xyz.mdhv.formanalyser.app.data.Repository
import xyz.mdhv.formanalyser.wellness.AcwrSeries
import xyz.mdhv.formanalyser.wellness.DailyLoad
import xyz.mdhv.formanalyser.wellness.StreakState
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** What the calendar knows about one day (icon stack + hiatus band). */
data class DayMarks(
    val session: Boolean = false,
    val rest: Boolean = false,
    val checkin: Boolean = false,
    val event: Boolean = false,
    val hiatus: Boolean = false,
)

class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    private val prefs = AppPrefs(app)
    private val assembler = WellnessAssembler(repo)

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month

    private val _marks = MutableStateFlow<Map<LocalDate, DayMarks>>(emptyMap())
    val marks: StateFlow<Map<LocalDate, DayMarks>> = _marks

    private val _streak = MutableStateFlow<StreakState?>(null)
    val streak: StateFlow<StreakState?> = _streak

    private val _loads = MutableStateFlow<List<DailyLoad>>(emptyList())
    val loads: StateFlow<List<DailyLoad>> = _loads

    private val _acwr = MutableStateFlow<AcwrSeries?>(null)
    val acwr: StateFlow<AcwrSeries?> = _acwr

    fun setMonth(m: YearMonth) { _month.value = m; load() }

    fun load() {
        viewModelScope.launch {
            val athlete = withContext(Dispatchers.IO) { repo.currentAthlete() } ?: return@launch
            val today = LocalDate.now()
            val zone = ZoneId.systemDefault()
            val m = _month.value

            val data = withContext(Dispatchers.IO) {
                val sessions = repo.allSessions(athlete.id)
                    .map { Instant.ofEpochMilli(it.startedAtEpochMs).atZone(zone).toLocalDate() }.toSet()
                val rests = repo.wellness.allRestDays().map { LocalDate.parse(it.date) }.toSet()
                val checkins = repo.wellness.checkinsSince(athlete.id, 0L)
                    .map { Instant.ofEpochMilli(it.ts).atZone(zone).toLocalDate() }.toSet()
                val events = repo.wellness.recentEvents(500)
                    .map { Instant.ofEpochMilli(it.ts).atZone(zone).toLocalDate() }.toSet()
                val hiatuses = repo.wellness.allHiatuses().map {
                    LocalDate.parse(it.startDate) to (it.endDate?.let(LocalDate::parse) ?: today)
                }
                val marks = buildMap {
                    var d = m.atDay(1)
                    while (!d.isAfter(m.atEndOfMonth())) {
                        put(
                            d,
                            DayMarks(
                                session = d in sessions,
                                rest = d in rests,
                                checkin = d in checkins,
                                event = d in events,
                                hiatus = hiatuses.any { (s, e) -> !d.isBefore(s) && !d.isAfter(e) },
                            ),
                        )
                        d = d.plusDays(1)
                    }
                }
                val planned = prefs.plannedRestDays.first()
                Triple(marks, assembler.streak(athlete.id, planned, today), assembler.dailyLoads(athlete.id))
            }
            _marks.value = data.first
            _streak.value = data.second
            _loads.value = data.third
            _acwr.value = withContext(Dispatchers.IO) { assembler.acwr(athlete.id, today) }
        }
    }
}
