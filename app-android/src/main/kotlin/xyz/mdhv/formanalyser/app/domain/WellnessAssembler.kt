package xyz.mdhv.formanalyser.app.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import xyz.mdhv.formanalyser.app.data.Repository
import xyz.mdhv.formanalyser.wellness.Acwr
import xyz.mdhv.formanalyser.wellness.AcwrSeries
import xyz.mdhv.formanalyser.wellness.DailyLoad
import xyz.mdhv.formanalyser.wellness.DayFacts
import xyz.mdhv.formanalyser.wellness.InjurySummary
import xyz.mdhv.formanalyser.wellness.LoadModel
import xyz.mdhv.formanalyser.wellness.Readiness
import xyz.mdhv.formanalyser.wellness.ReadinessInput
import xyz.mdhv.formanalyser.wellness.ReadinessResult
import xyz.mdhv.formanalyser.wellness.SessionLoad
import xyz.mdhv.formanalyser.wellness.StreakEngine
import xyz.mdhv.formanalyser.wellness.StreakState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** JSON helpers for the string-list columns (tags, regions, schedules). */
object JsonLists {
    private val json = Json { ignoreUnknownKeys = true }
    private val ser = ListSerializer(String.serializer())
    fun encode(list: List<String>): String = json.encodeToString(ser, list)
    fun decode(s: String?): List<String> =
        if (s.isNullOrBlank()) emptyList() else runCatching { json.decodeFromString(ser, s) }.getOrElse { emptyList() }
}

/**
 * Assembles core-wellness inputs (load, ACWR, streak, readiness) from the database. All the math
 * lives in core-wellness; this is IO + mapping. Suspend functions — call from Dispatchers.IO.
 */
class WellnessAssembler(private val repo: Repository) {

    private fun epochToDate(ms: Long): LocalDate =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

    suspend fun dailyLoads(athleteId: String): List<DailyLoad> {
        val sessions = repo.allSessions(athleteId)
        val loads = sessions.map { s ->
            val arrows = s.arrowsActual ?: repo.shotCount(s.id).takeIf { it > 0 }
            val post = s.postCheckinId?.let { repo.wellness.checkinById(it) }
            SessionLoad(
                date = epochToDate(s.startedAtEpochMs),
                arrows = arrows,
                sessionPoundageLbs = s.drawWeightLbs.takeIf { it > 0.0 },
                durationMin = s.durationS?.let { it / 60.0 },
                rpe = post?.rpe,
            )
        }
        return LoadModel.dailyLoads(loads)
    }

    suspend fun acwr(athleteId: String, today: LocalDate = LocalDate.now()): AcwrSeries =
        Acwr.compute(dailyLoads(athleteId), today)

    suspend fun streak(athleteId: String, plannedRestCsv: String, today: LocalDate = LocalDate.now()): StreakState {
        val sessions = repo.allSessions(athleteId)
        val sessionDays = sessions.map { epochToDate(it.startedAtEpochMs) }.toSet()
        val restDays = repo.wellness.allRestDays().associateBy { LocalDate.parse(it.date) }
        val checkinDays = repo.wellness.checkinsSince(athleteId, 0L).map { epochToDate(it.ts) }.toSet()
        val hiatuses = repo.wellness.allHiatuses().map {
            LocalDate.parse(it.startDate) to (it.endDate?.let(LocalDate::parse) ?: today)
        }
        val planned = plannedRestCsv.split(',').mapNotNull { code ->
            when (code.trim().uppercase()) {
                "MO" -> java.time.DayOfWeek.MONDAY; "TU" -> java.time.DayOfWeek.TUESDAY
                "WE" -> java.time.DayOfWeek.WEDNESDAY; "TH" -> java.time.DayOfWeek.THURSDAY
                "FR" -> java.time.DayOfWeek.FRIDAY; "SA" -> java.time.DayOfWeek.SATURDAY
                "SU" -> java.time.DayOfWeek.SUNDAY; else -> null
            }
        }.toSet()

        val firstDay = listOfNotNull(
            sessionDays.minOrNull(),
            restDays.keys.minOrNull(),
            checkinDays.minOrNull(),
        ).minOrNull() ?: return StreakState(0, 0, null, provisionalToday = false)
        val start = maxOf(firstDay, today.minusDays(365))

        fun factsFor(d: LocalDate) = DayFacts(
            date = d,
            session = d in sessionDays,
            restLogged = restDays.containsKey(d),
            plannedRest = d.dayOfWeek in planned,
            anyCheckin = d in checkinDays,
            hiatus = hiatuses.any { (s, e) -> !d.isBefore(s) && !d.isAfter(e) },
        )

        val completed = generateSequence(start) { it.plusDays(1) }
            .takeWhile { it.isBefore(today) }
            .map(::factsFor)
            .toList()
        return StreakEngine.evaluate(completed, today = factsFor(today))
    }

    suspend fun readiness(athleteId: String, today: LocalDate = LocalDate.now(), nowMs: Long = System.currentTimeMillis()): ReadinessResult {
        val hiatus = repo.wellness.openHiatus() != null
        val acwrSeries = acwr(athleteId, today)
        val acwrVal = if (acwrSeries.warmupComplete) acwrSeries.latest?.acwr else null
        val latest = repo.wellness.latestCheckin(athleteId)
        val soreness = latest?.let { repo.wellness.sorenessFor(it.id).size } ?: 0
        val lifeImpact = repo.wellness.activeLifeEvents(today.toString()).maxOfOrNull { it.impact }
        val injuries = repo.body.activeInjuries(athleteId).map {
            InjurySummary(regions = JsonLists.decode(it.regionsJson), severity = it.severity)
        }
        return Readiness.assess(
            ReadinessInput(
                hiatusActive = hiatus,
                acwr = acwrVal,
                energy = latest?.energy,
                sleep = latest?.sleep,
                sorenessRegionCount = soreness,
                activeLifeEventMaxImpact = lifeImpact,
                latestCheckinAgeHours = latest?.let { (nowMs - it.ts) / 3_600_000.0 },
                activeInjuries = injuries,
            ),
        )
    }
}
