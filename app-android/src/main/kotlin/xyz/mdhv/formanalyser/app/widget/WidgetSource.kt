package xyz.mdhv.formanalyser.app.widget

import android.content.Context
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.CancellationException
import xyz.mdhv.formanalyser.app.BuildConfig
import xyz.mdhv.formanalyser.app.data.AppDatabase
import xyz.mdhv.formanalyser.app.data.AthleteEntity
import xyz.mdhv.formanalyser.app.data.DatabaseRecoveryRequired
import xyz.mdhv.formanalyser.app.data.Repository
import xyz.mdhv.formanalyser.app.domain.JsonLists
import xyz.mdhv.formanalyser.app.domain.PhysioAdherence
import xyz.mdhv.formanalyser.app.domain.TrainingVolume
import xyz.mdhv.formanalyser.app.domain.Tuning
import xyz.mdhv.formanalyser.app.domain.WellnessAssembler
import xyz.mdhv.formanalyser.model.BowType

/** What one widget shows right now: its data, or one of the honest states. */
sealed interface WidgetContent {
    /** No athlete profile yet (first run, or after a full data wipe). */
    data object FirstRun : WidgetContent

    /** The database could not be opened safely; only the app's recovery screen may handle that. */
    data object NeedsApp : WidgetContent

    /** Reading failed for another reason; the widget says so instead of guessing. */
    data object Error : WidgetContent

    /** There is a profile but nothing of this kind yet, said in the widget's own words. */
    data class Empty(val kind: WidgetKind) : WidgetContent

    data class LastScorecard(val model: LastScorecardModel) : WidgetContent

    data class ThisWeek(val model: WeekModel) : WidgetContent

    data class NextPhysio(val model: NextPhysioModel) : WidgetContent

    data class Readiness(val model: ReadinessModel) : WidgetContent

    data class ActiveRig(val model: RigModel) : WidgetContent

    data class QuickStart(val open: OpenScorecard?) : WidgetContent
}

/**
 * Reads the widgets' data from the app's own Room database, live (no snapshot is cached).
 *
 * Suspend functions: call off the main thread. Every read goes through the same DAOs, retraction
 * filters and domain code (WellnessAssembler, PhysioAdherence, TrainingVolume) as the screens, so
 * a widget and the screen it opens can never disagree.
 */
internal object WidgetSource {
    suspend fun load(context: Context, kind: WidgetKind, now: ZonedDateTime): WidgetContent {
        val db = WidgetDatabase.openOrNull(context) ?: return WidgetContent.NeedsApp
        return try {
            val athlete = db.athleteDao().firstOrNull() ?: return WidgetContent.FirstRun
            when (kind) {
                WidgetKind.LAST_SCORECARD -> lastScorecard(db, athlete, now.zone)
                WidgetKind.THIS_WEEK -> thisWeek(db, athlete, now)
                WidgetKind.NEXT_PHYSIO -> nextPhysio(db, athlete, now)
                WidgetKind.READINESS -> readiness(db, athlete, now)
                WidgetKind.ACTIVE_RIG -> activeRig(db, athlete)
                WidgetKind.QUICK_START -> quickStart(db, athlete)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            WidgetContent.Error
        }
    }

    private suspend fun lastScorecard(db: AppDatabase, athlete: AthleteEntity, zone: ZoneId): WidgetContent {
        val scoring = db.scoringDao()
        val finished =
            scoring.completedForAthlete(athlete.id).map {
                ScorecardRow(
                    it.id,
                    it.status,
                    it.roundId,
                    it.roundName,
                    it.distanceMeters,
                    it.scoringKind,
                    it.arrowsPerEnd,
                    it.endCount,
                    it.total,
                    it.xCount,
                    it.roundComplete,
                    it.completedAt,
                    it.updatedAt,
                    it.athleteSetPoints,
                    it.opponentSetPoints,
                    it.rigId,
                )
            }
        val row = LastScorecard.pick(finished) ?: return WidgetContent.Empty(WidgetKind.LAST_SCORECARD)
        // The same basis as the scorer's "PB" and Progress's personal bests (complete, finished,
        // not retracted, set matches excluded).
        val best = scoring.bestPerRound(athlete.id).firstOrNull { it.roundId == row.roundId }?.best
        val rigName = row.rigId?.let { id -> db.rigDao().forAthleteOnce(athlete.id).firstOrNull { it.id == id }?.name }
        return WidgetContent.LastScorecard(
            LastScorecard.model(row, scoring.activeArrowCount(row.id), best, rigName, zone),
        )
    }

    private suspend fun thisWeek(db: AppDatabase, athlete: AthleteEntity, now: ZonedDateTime): WidgetContent {
        val zone = now.zone
        val today = now.toLocalDate()
        val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        val start = WidgetDates.weekStart(today, firstDay)
        fun inWeek(ms: Long) = WidgetDates.daysFrom(start, WidgetDates.localDate(ms, zone)) in 0..6

        val captures = db.sessionDao().allForAthlete(athlete.id)
        val captureStart = captures.associate { it.id to it.startedAtEpochMs }
        // The same card set Progress reads: the 250 most recent, finished ones.
        val cards = db.scoringDao().recent(athlete.id, 250).filter { it.status == "FINISHED" }
        val shots = db.shotDao()
        val scoring = db.scoringDao()
        // Counting needs a query per session, so only sessions whose point can land in this week
        // are counted; every other point is discarded below, so its count cannot change the result.
        val volume =
            TrainingVolume.points(
                captures.map { s ->
                    val arrows =
                        if (inWeek(s.startedAtEpochMs)) s.arrowsActual ?: shots.countForSession(s.id) else 0
                    TrainingVolume.Capture(s.id, s.startedAtEpochMs, arrows)
                },
                cards.map { c ->
                    val at = c.completedAt ?: c.updatedAt
                    val pointAt = c.linkedFormSessionId?.let(captureStart::get) ?: at
                    TrainingVolume.Card(
                        at,
                        c.linkedFormSessionId,
                        if (inWeek(pointAt)) scoring.activeArrowCount(c.id) else 0,
                    )
                },
            )
        val plans =
            db.athleteFeatureDao().trainingPlans(athlete.id).map {
                PlanRow(
                    it.title,
                    it.state,
                    parseDate(it.startDate),
                    it.endDate?.let(::parseDate),
                    it.weeklyArrowTarget,
                    it.updatedAtMs,
                )
            }
        return WidgetContent.ThisWeek(
            WeekVolume.model(volume, zone, today, firstDay, WeekVolume.activePlan(plans, today)),
        )
    }

    private suspend fun nextPhysio(db: AppDatabase, athlete: AthleteEntity, now: ZonedDateTime): WidgetContent {
        val today = now.toLocalDate()
        val body = db.bodyDao()
        // Same plan set as Progress's adherence figure. Titles, regions and notes are never read
        // into the widget model (PhysioPlanRow has no field for them).
        val plans =
            body.activePlans(athlete.id, today.toString()).mapNotNull { p ->
                val start = parseDate(p.startDate) ?: return@mapNotNull null
                PhysioPlanRow(
                    start = start,
                    end = p.endDate?.let(::parseDate),
                    schedule = JsonLists.decode(p.scheduleJson).mapNotNull(PhysioAdherence::dayOf).toSet(),
                    exerciseCount = body.exercisesFor(p.id).size,
                    loggedDates =
                        body.physioSessionsFor(p.id).mapTo(HashSet()) { WidgetDates.localDate(it.ts, now.zone) },
                )
            }
        val model = NextPhysio.model(plans, today) ?: return WidgetContent.Empty(WidgetKind.NEXT_PHYSIO)
        return WidgetContent.NextPhysio(model)
    }

    private suspend fun readiness(db: AppDatabase, athlete: AthleteEntity, now: ZonedDateTime): WidgetContent {
        val repo = Repository(db)
        val assembler = WellnessAssembler(repo)
        val today = now.toLocalDate()
        val nowMs = now.toInstant().toEpochMilli()
        // One pass over the load history, shared by the level and the ratio the widget prints.
        val acwr = assembler.acwr(athlete.id, today)
        // The level exactly as Home computes it. Its reason strings are dropped here.
        val result = assembler.readiness(athlete.id, today, nowMs, acwr)
        val latest = repo.wellness.latestCheckin(athlete.id)
        val checkin =
            latest?.let {
                CheckinFacts(it.ts, it.energy, it.sleep, repo.wellness.sorenessFor(it.id).size)
            }
        return WidgetContent.Readiness(
            ReadinessModel.of(result.level, checkin, acwr.latest?.acwr, acwr.warmupComplete, nowMs),
        )
    }

    private suspend fun activeRig(db: AppDatabase, athlete: AthleteEntity): WidgetContent {
        val rig = db.rigDao().activeForAthlete(athlete.id) ?: return WidgetContent.Empty(WidgetKind.ACTIVE_RIG)
        return WidgetContent.ActiveRig(
            RigCard.model(rig.name, BowType.fromStorage(rig.bowType), Tuning.parseFull(rig.tuningJson), athlete.drawLengthMm),
        )
    }

    private suspend fun quickStart(db: AppDatabase, athlete: AthleteEntity): WidgetContent {
        val scoring = db.scoringDao()
        val open =
            scoring.activeForAthlete(athlete.id)?.let {
                OpenScorecard(it.roundName, scoring.activeArrowCount(it.id), it.arrowsPerEnd * it.endCount)
            }
        return WidgetContent.QuickStart(open)
    }

    /** When the readiness widget's level next changes on its own: its check-in going stale. */
    suspend fun readinessStaleAtMs(context: Context): Long? {
        val db = WidgetDatabase.openOrNull(context) ?: return null
        return runCatching {
            val athlete = db.athleteDao().firstOrNull() ?: return null
            ReadinessTiming.staleAtMs(
                db.wellnessDao().latestCheckin(athlete.id)?.ts,
                xyz.mdhv.formanalyser.wellness.WellnessConstants.STALENESS_H,
            )
        }.getOrNull()
    }

    private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()
}

/**
 * Opens the app's database for a widget without ever turning a broken database into a loop.
 *
 * Opening may migrate; if it fails, [AppDatabase] preserves a recovery copy and throws. The app
 * then shows its blocking recovery screen, and that is the only place the athlete decides what
 * happens next. A widget must not keep re-trying in the background (each failed open writes
 * another copy), so the first failure on an app version is remembered in a one-line file under
 * `noBackupFilesDir` holding only the version code, and widgets stop opening the database until the
 * app has opened it successfully again ([clearFailure], called once the app is past recovery).
 */
internal object WidgetDatabase {
    private fun flag(context: Context) = File(context.noBackupFilesDir, "widgets/database-open-failed")

    fun openOrNull(context: Context): AppDatabase? {
        val app = context.applicationContext
        val version = BuildConfig.VERSION_CODE.toString()
        val flag = flag(app)
        if (flag.isFile && runCatching { flag.readText().trim() }.getOrNull() == version) return null
        return try {
            AppDatabase.get(app)
        } catch (blocked: DatabaseRecoveryRequired) {
            runCatching {
                flag.parentFile?.mkdirs()
                flag.writeText(version)
            }
            null
        }
    }

    fun clearFailure(context: Context) {
        runCatching { flag(context.applicationContext).delete() }
    }
}
