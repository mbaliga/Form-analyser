package xyz.mdhv.formanalyser.app.widget

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import xyz.mdhv.formanalyser.app.domain.PhysioAdherence
import xyz.mdhv.formanalyser.app.domain.RigTuning
import xyz.mdhv.formanalyser.app.domain.TrainingVolume
import xyz.mdhv.formanalyser.app.domain.Tuning
import xyz.mdhv.formanalyser.equipment.PoundageSource
import xyz.mdhv.formanalyser.model.BowType
import xyz.mdhv.formanalyser.wellness.ReadinessLevel

/*
 * Pure widget logic: what each home-screen widget shows, decided from plain values.
 *
 * No Android types live here, so every rule (layout choice, privacy reduction, the arithmetic
 * behind each number) is unit-tested on the JVM. WidgetSource reads the database into these
 * inputs; WidgetRenderer turns the results into RemoteViews and the app's own strings.
 *
 * House rules that bind every model in this file: measurements, never advice; no streak; nothing
 * from the MEDICAL or PRIVATE privacy classes; readiness only as a shape plus a word.
 */

/** The six widgets, in the order they appear in docs/widgets.md. */
enum class WidgetKind {
    LAST_SCORECARD,
    THIS_WEEK,
    NEXT_PHYSIO,
    READINESS,
    ACTIVE_RIG,
    QUICK_START,
}

/**
 * Compact (one line: a single tappable summary, for a 1-row widget in landscape or any height too
 * short for the small layout), small (about 2x1 or 2x2 cells), medium (about 4x1 or 4x2) and large
 * (about 4x3 or taller). Which one a widget gets depends on its template and the font scale.
 */
enum class WidgetSize {
    COMPACT,
    SMALL,
    MEDIUM,
    LARGE,
}

/** The layout family a widget draws with (strategy §5.3), plus the honest-state message. */
enum class WidgetTemplate {
    STAT,
    PROGRESS,
    NEXT,
    CARD,
    PADS,
    MESSAGE,
}

val WidgetKind.template: WidgetTemplate
    get() =
        when (this) {
            WidgetKind.LAST_SCORECARD, WidgetKind.READINESS -> WidgetTemplate.STAT
            WidgetKind.THIS_WEEK -> WidgetTemplate.PROGRESS
            WidgetKind.NEXT_PHYSIO -> WidgetTemplate.NEXT
            WidgetKind.ACTIVE_RIG -> WidgetTemplate.CARD
            WidgetKind.QUICK_START -> WidgetTemplate.PADS
        }

/**
 * What one layout needs to draw without clipping: a minimum width, and a height made of fixed dp
 * (padding, margins, the progress bar, 48dp touch targets) plus the stacked text sizes in sp, which
 * grow with the font scale. WidgetLayoutFitTest measures every layout with real fonts at 1x, 1.3x
 * and 2x and checks that each need is at least what the layout really takes.
 */
data class LayoutNeed(val minWidthDp: Float, val fixedDp: Float, val textSp: Float) {
    fun heightDp(fontScale: Float): Float = fixedDp + textSp * WidgetSizing.LINE_HEIGHT * fontScale
}

object WidgetSizing {
    /** Two-column and multi-pad layouts need at least this width. */
    const val WIDE_MIN_DP = 200f

    /**
     * dp of line height per sp of text size. Roboto's ascent plus descent is 1.17 em (the layouts
     * turn off font padding); rounded up for device fonts with taller metrics.
     */
    const val LINE_HEIGHT = 1.2f

    // Shared parts of the needs below: the root's 12dp padding (top + bottom), and the 6dp root
    // plus 6dp pad padding of the pads layouts.
    private const val ROOT = 24f

    /**
     * Each layout's need, read off its XML: margins and fixed views in dp, then the text sizes
     * that stack vertically (a horizontal row counts its tallest text once). [WidgetSize.COMPACT]
     * is the floor every widget can fall back to, so its key is always 1x1.
     */
    fun need(template: WidgetTemplate, size: WidgetSize): LayoutNeed =
        when (size) {
            // widget_compact: one 18sp line and 8dp padding; counted as 21sp because the readiness
            // shapes come from a fallback font with taller metrics. widget_pads_compact: a 20dp
            // icon beside a 14sp word, 4dp + 4dp padding.
            WidgetSize.COMPACT ->
                if (template == WidgetTemplate.PADS) LayoutNeed(1f, 16f + 4f, 16f) else LayoutNeed(1f, 16f, 21f)
            WidgetSize.SMALL ->
                when (template) {
                    // label 12 + value 28 + caption 12; margins 2 + 2
                    WidgetTemplate.STAT -> LayoutNeed(1f, ROOT + 4f, 52f)
                    // label 12 + value 24 + caption 12; margins 2 + 6 + 4, bar 6
                    WidgetTemplate.PROGRESS -> LayoutNeed(1f, ROOT + 18f, 48f)
                    // label 12 + day 24 + caption 12; margins 2 + 2
                    WidgetTemplate.NEXT -> LayoutNeed(1f, ROOT + 4f, 48f)
                    // label 12 + title 2 x 16 + caption 12; margins 4 + 2
                    WidgetTemplate.CARD -> LayoutNeed(1f, ROOT + 6f, 56f)
                    // one pad: 6 + 6 padding twice, 24dp icon, 2dp gap, a 14sp word (counted as 16)
                    WidgetTemplate.PADS -> LayoutNeed(1f, 12f + 12f + 24f + 2f, 16f)
                    // label 12 + title 16 + caption 2 x 12; margins 4 + 2
                    WidgetTemplate.MESSAGE -> LayoutNeed(1f, ROOT + 6f, 52f)
                }
            WidgetSize.MEDIUM ->
                when (template) {
                    // two columns; the left one (as small) is the taller
                    WidgetTemplate.STAT -> LayoutNeed(WIDE_MIN_DP, ROOT + 4f, 52f)
                    // as small, with the week and sessions beside the label and the caption
                    WidgetTemplate.PROGRESS -> LayoutNeed(WIDE_MIN_DP, ROOT + 18f, 48f)
                    WidgetTemplate.NEXT -> LayoutNeed(WIDE_MIN_DP, ROOT + 4f, 48f)
                    // label 12 + title 16 + caption 12 + two 14sp lines (68, counted as 70 for the
                    // "≈" a fallback font may draw); margins 4 + 2 + 6 + 2
                    WidgetTemplate.CARD -> LayoutNeed(WIDE_MIN_DP, ROOT + 14f, 70f)
                    // three pads in a row, each as the small pad
                    WidgetTemplate.PADS -> LayoutNeed(WIDE_MIN_DP, 12f + 12f + 24f + 2f, 16f)
                    // caption 3 lines
                    WidgetTemplate.MESSAGE -> LayoutNeed(1f, ROOT + 6f, 64f)
                }
            WidgetSize.LARGE ->
                when (template) {
                    // label 12 + value 28 + caption 12 + line 14 + 4 rows x 14; margins 2 + 2 + 6 +
                    // 4 x 4 + 10, and the 48dp Log check-in action beside the 1-line footer
                    WidgetTemplate.STAT -> LayoutNeed(WIDE_MIN_DP, ROOT + 36f + 48f, 122f)
                    // label 12 + value 24 + caption 12 + day strip 14 + 12 + plan 14 + footer 12 (100,
                    // counted as 104 for today's "▸" and the footer's "◆", fallback-font glyphs);
                    // margins 2 + 6 + 4 + 12 + 2 + 10 + 10, bar 6
                    WidgetTemplate.PROGRESS -> LayoutNeed(WIDE_MIN_DP, ROOT + 52f, 104f)
                    // label 12 + day 24 + caption 12 + line 14 + 4 rows x 14 + footer 12;
                    // margins 2 + 2 + 6 + 4 x 4 + 10
                    WidgetTemplate.NEXT -> LayoutNeed(WIDE_MIN_DP, ROOT + 36f, 130f)
                    // label 12 + title 16 + caption 12 + line 14 + 6 rows x 14 + footer 12;
                    // margins 4 + 2 + 6 + 6 x 4 + 10
                    WidgetTemplate.CARD -> LayoutNeed(WIDE_MIN_DP, ROOT + 46f, 150f)
                    // caption 12 (4dp top padding, 6dp gap) and two rows of pads (as the small pad)
                    WidgetTemplate.PADS -> LayoutNeed(WIDE_MIN_DP, 12f + 4f + 6f + 2 * (12f + 24f + 2f) + 6f, 44f)
                    // title 2 lines, caption 4 lines
                    WidgetTemplate.MESSAGE -> LayoutNeed(1f, ROOT + 6f, 92f)
                }
        }

    /**
     * The smallest widget size, in dp, each layout is used at, for [fontScale]. On API 31+ these are
     * the keys of `RemoteViews(Map<SizeF, RemoteViews>)`. Each key is at least the one before it in
     * both directions, so the launcher's choice (the fitting key nearest the widget's size) is
     * always the largest layout that fits, which is exactly [of]; nothing fits, the compact one.
     */
    fun keys(template: WidgetTemplate, fontScale: Float): Map<WidgetSize, Pair<Float, Float>> {
        var width = 1f
        var height = 1f
        return WidgetSize.entries.associateWith { size ->
            if (size != WidgetSize.COMPACT) {
                val need = need(template, size)
                width = maxOf(width, need.minWidthDp)
                height = maxOf(height, kotlin.math.ceil(need.heightDp(fontScale)))
            }
            width to height
        }
    }

    /** The layout for a widget of [widthDp] x [heightDp]; the rule below API 31 too. */
    fun of(template: WidgetTemplate, widthDp: Float, heightDp: Float, fontScale: Float): WidgetSize =
        keys(template, fontScale).entries.last { (_, key) -> key.first <= widthDp && key.second <= heightDp }.key

    /**
     * Below API 31 a launcher reports a range: the widget is min width x max height in portrait
     * and max width x min height in landscape. Returns (portrait, landscape).
     */
    fun portraitAndLandscape(
        template: WidgetTemplate,
        minWidthDp: Float,
        maxWidthDp: Float,
        minHeightDp: Float,
        maxHeightDp: Float,
        fontScale: Float,
    ): Pair<WidgetSize, WidgetSize> =
        of(template, minWidthDp, maxHeightDp, fontScale) to of(template, maxWidthDp, minHeightDp, fontScale)
}

/** Where a tap lands inside the app. Validated by name when MainActivity receives it. */
enum class WidgetDestination {
    HOME,
    SCORE,
    TRAIN,
    LOG,
    PROGRESS,
    BODY,
    CALENDAR,
    RIGS;

    companion object {
        fun fromExtra(value: String?): WidgetDestination? = entries.firstOrNull { it.name == value }
    }
}

/**
 * Readiness exactly as the app's cascade decides it, drawn as a shape plus a word. The shapes are
 * the meaning; no colour is attached to any of them (red and green never carry meaning).
 */
enum class ReadinessMark(val glyph: String) {
    READY("●"),
    CAUTION("◐"),
    REST_ADVISED("○"),
    ON_PAUSE("‖");

    companion object {
        fun of(level: ReadinessLevel): ReadinessMark =
            when (level) {
                ReadinessLevel.READY -> READY
                ReadinessLevel.CAUTION -> CAUTION
                ReadinessLevel.REST_ADVISED -> REST_ADVISED
                ReadinessLevel.QUIET -> ON_PAUSE
            }
    }
}

/** Calendar arithmetic shared by every dated widget. */
object WidgetDates {
    fun daysFrom(today: LocalDate, date: LocalDate): Long = ChronoUnit.DAYS.between(today, date)

    fun localDate(epochMs: Long, zone: ZoneId): LocalDate =
        java.time.Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    /** The first day of the week containing [today], for a locale whose week starts on [firstDay]. */
    fun weekStart(today: LocalDate, firstDay: DayOfWeek): LocalDate =
        today.with(TemporalAdjusters.previousOrSame(firstDay))

    /**
     * When the widgets next need redrawing with no new data: one minute after local midnight (the
     * day, the week and every "today" move then), or earlier when a readiness check-in goes stale.
     * A boundary already in the past is ignored.
     */
    fun nextRefreshAtMs(now: ZonedDateTime, readinessStaleAtMs: Long?): Long {
        val midnight =
            now.toLocalDate().plusDays(1).atStartOfDay(now.zone).plusMinutes(1).toInstant().toEpochMilli()
        val stale = readinessStaleAtMs?.takeIf { it > now.toInstant().toEpochMilli() }
        return if (stale != null) minOf(midnight, stale) else midnight
    }
}

// ---------------------------------------------------------------------------------------------
// Last scorecard (template: stat)
// ---------------------------------------------------------------------------------------------

/** A scorecard row as the widget needs it; retracted cards never reach this type. */
data class ScorecardRow(
    val id: String,
    val status: String,
    val roundId: String,
    val roundName: String,
    val distanceMeters: Int,
    val scoringKind: String,
    val arrowsPerEnd: Int,
    val endCount: Int,
    val total: Int,
    val xCount: Int,
    val roundComplete: Boolean,
    val completedAtMs: Long?,
    val updatedAtMs: Long,
    val athleteSetPoints: Int?,
    val opponentSetPoints: Int?,
    val rigId: String?,
)

data class LastScorecardModel(
    val roundName: String,
    val distanceMeters: Int,
    val total: Int,
    /** Null for a set match, whose meaningful value is the set score. */
    val maxScore: Int?,
    val arrowsShot: Int,
    val arrowsPlanned: Int,
    val xCount: Int,
    val complete: Boolean,
    val setPoints: Pair<Int, Int>?,
    val finishedOn: LocalDate,
    /** Mean points per arrow, or null when no arrow was recorded or for a set match. */
    val arrowAverage: Double?,
    /** Best complete round of this kind, this card included; null when this card is incomplete. */
    val roundBest: Int?,
    val rigName: String?,
)

object LastScorecard {
    const val SET_MATCH = "SET_MATCH"

    /** The most recently finished scorecard; an unfinished card is not a "last" result. */
    fun pick(rows: List<ScorecardRow>): ScorecardRow? =
        rows.filter { it.status == "FINISHED" }.maxByOrNull { it.completedAtMs ?: it.updatedAtMs }

    fun model(
        row: ScorecardRow,
        arrowsShot: Int,
        roundBest: Int?,
        rigName: String?,
        zone: ZoneId,
    ): LastScorecardModel {
        val setMatch = row.scoringKind == SET_MATCH
        val planned = row.arrowsPerEnd * row.endCount
        val setPoints =
            if (setMatch && row.athleteSetPoints != null && row.opponentSetPoints != null)
                row.athleteSetPoints to row.opponentSetPoints
            else null
        return LastScorecardModel(
            roundName = row.roundName,
            distanceMeters = row.distanceMeters,
            total = row.total,
            maxScore = if (setMatch) null else planned * 10,
            arrowsShot = arrowsShot,
            arrowsPlanned = planned,
            xCount = row.xCount,
            complete = row.roundComplete,
            setPoints = setPoints,
            finishedOn = WidgetDates.localDate(row.completedAtMs ?: row.updatedAtMs, zone),
            arrowAverage =
                if (!setMatch && arrowsShot > 0) row.total.toDouble() / arrowsShot else null,
            roundBest = if (!setMatch && row.roundComplete) roundBest else null,
            rigName = rigName,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// This week (template: progress)
// ---------------------------------------------------------------------------------------------

/** A training plan as the widget needs it. Recovery notes are never read. */
data class PlanRow(
    val title: String,
    val state: String,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val weeklyArrowTarget: Int?,
    val updatedAtMs: Long,
)

data class WeekModel(
    val weekStart: LocalDate,
    /** Arrows per day, [weekStart] first; always seven entries. */
    val days: List<Int>,
    val todayIndex: Int,
    val total: Int,
    val sessions: Int,
    val target: Int?,
    val planTitle: String?,
) {
    /** 0..1 for the progress bar, or null when there is no target to measure against. */
    val fraction: Double?
        get() = target?.takeIf { it > 0 }?.let { (total.toDouble() / it).coerceIn(0.0, 1.0) }

    /** Arrows still short of the target (0 once met), or null without a target. */
    val remaining: Int?
        get() = target?.let { (it - total).coerceAtLeast(0) }

    /** Arrows beyond the target, or null without a target or below it. */
    val over: Int?
        get() = target?.let { total - it }?.takeIf { it > 0 }
}

object WeekVolume {
    /**
     * The plan whose weekly target applies today: state ACTIVE, already started, not yet ended. The
     * most recently started wins; a tie goes to the most recently edited.
     */
    fun activePlan(plans: List<PlanRow>, today: LocalDate): PlanRow? =
        plans
            .filter { it.state == "ACTIVE" }
            .filter { it.startDate == null || !it.startDate.isAfter(today) }
            .filter { it.endDate == null || !it.endDate.isBefore(today) }
            .maxWithOrNull(compareBy<PlanRow>({ it.startDate ?: LocalDate.MIN }, { it.updatedAtMs }))

    fun model(
        points: List<TrainingVolume.Point>,
        zone: ZoneId,
        today: LocalDate,
        firstDay: DayOfWeek,
        plan: PlanRow?,
    ): WeekModel {
        val start = WidgetDates.weekStart(today, firstDay)
        val days = IntArray(7)
        var sessions = 0
        points.forEach { p ->
            val index = WidgetDates.daysFrom(start, WidgetDates.localDate(p.atMs, zone))
            if (index in 0..6) {
                days[index.toInt()] += p.arrows
                sessions++
            }
        }
        return WeekModel(
            weekStart = start,
            days = days.toList(),
            todayIndex = WidgetDates.daysFrom(start, today).toInt(),
            total = days.sum(),
            sessions = sessions,
            target = plan?.weeklyArrowTarget?.takeIf { it > 0 },
            planTitle = plan?.title,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Next physio day (template: next)
// ---------------------------------------------------------------------------------------------

/**
 * A physio plan reduced to what the widget may use: its dates, its weekly schedule, how many
 * exercises it has and the days a session was logged. Its title, target body regions and notes are
 * deliberately absent, so they cannot reach a home screen.
 */
data class PhysioPlanRow(
    val start: LocalDate,
    val end: LocalDate?,
    val schedule: Set<DayOfWeek>,
    val exerciseCount: Int,
    val loggedDates: Set<LocalDate>,
)

data class PhysioDay(val date: LocalDate, val exercises: Int, val plans: Int)

data class NextPhysioModel(
    val next: PhysioDay,
    /** Today was scheduled and every plan due today has a session logged. */
    val todayLogged: Boolean,
    /** Up to three scheduled days, [next] first. */
    val upcoming: List<PhysioDay>,
    /** Scheduled days attended over the last 28 days, as Progress counts them; null if none. */
    val adherence: PhysioAdherence.Window?,
)

object NextPhysio {
    /** Two weeks covers any weekly schedule; a plan with nothing due in that span has no next day. */
    const val HORIZON_DAYS = 14L

    private fun PhysioPlanRow.runsOn(d: LocalDate) =
        !d.isBefore(start) && (end == null || !d.isAfter(end)) && d.dayOfWeek in schedule

    fun model(plans: List<PhysioPlanRow>, today: LocalDate): NextPhysioModel? {
        val days = mutableListOf<PhysioDay>()
        var todayLogged = false
        for (offset in 0..HORIZON_DAYS) {
            val d = today.plusDays(offset)
            var due = plans.filter { it.runsOn(d) }
            if (offset == 0L && due.isNotEmpty()) {
                val open = due.filter { d !in it.loggedDates }
                todayLogged = open.isEmpty()
                due = open
            }
            if (due.isNotEmpty()) days += PhysioDay(d, due.sumOf { it.exerciseCount }, due.size)
            if (days.size == 3) break
        }
        val next = days.firstOrNull() ?: return null
        val window =
            plans.fold(PhysioAdherence.Window.EMPTY) { acc, p ->
                acc +
                    PhysioAdherence.forPlan(
                        p.start,
                        p.end,
                        today.minusDays(27),
                        today,
                        p.schedule.map { it.name.take(3) },
                        p.loggedDates.toList(),
                    )
            }
        return NextPhysioModel(next, todayLogged, days, window.takeIf { it.expected > 0 })
    }
}

// ---------------------------------------------------------------------------------------------
// Readiness (template: stat)
// ---------------------------------------------------------------------------------------------

data class CheckinFacts(
    val atMs: Long,
    val energy: Int?,
    val sleep: Int?,
    val sorenessRegions: Int,
)

/**
 * Readiness for a public surface: the level the app computes, and the measurements anyone could
 * read off the latest check-in and the load series. The cascade's reason strings are not carried:
 * they can name an injured body region or stand in for a private life event.
 */
data class ReadinessModel(
    val mark: ReadinessMark,
    val checkin: CheckinFacts?,
    /** Acute:chronic workload ratio, only once the app's own warm-up is complete. */
    val acwr: Double?,
    val computedAtMs: Long,
) {
    companion object {
        fun of(
            level: ReadinessLevel,
            checkin: CheckinFacts?,
            acwr: Double?,
            acwrWarmupComplete: Boolean,
            computedAtMs: Long,
        ) =
            ReadinessModel(
                ReadinessMark.of(level),
                checkin,
                acwr?.takeIf { acwrWarmupComplete && it.isFinite() },
                computedAtMs,
            )
    }
}

object ReadinessTiming {
    /** When the app's own cascade starts calling the latest check-in stale. */
    fun staleAtMs(checkinAtMs: Long?, stalenessHours: Int): Long? =
        checkinAtMs?.let { it + stalenessHours * 3_600_000L }
}

// ---------------------------------------------------------------------------------------------
// Active rig (template: card)
// ---------------------------------------------------------------------------------------------

enum class RigFactKey {
    BRACE_HEIGHT,
    TILLER,
    ARROW_SPINE,
    ARROW_LENGTH,
    POINT_MASS,
    ARROW_MASS,
    FOC,
    GPP,
}

/** One recorded tuning measurement; [values] carries one number, or two for tiller (top, bottom). */
data class RigFact(val key: RigFactKey, val values: List<Double>)

data class RigModel(
    val name: String,
    val bowType: BowType,
    val poundageLbs: Double?,
    val poundageSource: PoundageSource?,
    /** Only what the athlete recorded (or what follows from it), in a fixed order. */
    val facts: List<RigFact>,
)

object RigCard {
    fun model(name: String, bowType: BowType, tuning: RigTuning, drawLengthMm: Int?): RigModel {
        val poundage =
            Tuning.effectivePoundage(tuning, bowType, drawLengthMm)
                ?.takeIf { it.source != PoundageSource.NONE && it.lbs > 0.0 }
        val facts = buildList {
            tuning.braceHeightMm?.let { add(RigFact(RigFactKey.BRACE_HEIGHT, listOf(it))) }
            val top = tuning.tillerTopMm
            val bottom = tuning.tillerBottomMm
            if (top != null && bottom != null) add(RigFact(RigFactKey.TILLER, listOf(top, bottom)))
            tuning.arrow?.spine?.let { add(RigFact(RigFactKey.ARROW_SPINE, listOf(it.toDouble()))) }
            tuning.arrow?.lengthMm?.let { add(RigFact(RigFactKey.ARROW_LENGTH, listOf(it))) }
            tuning.arrow?.pointGrains?.let { add(RigFact(RigFactKey.POINT_MASS, listOf(it))) }
            tuning.arrow?.totalMassGrains?.let { add(RigFact(RigFactKey.ARROW_MASS, listOf(it))) }
            Tuning.focPercent(tuning)?.let { add(RigFact(RigFactKey.FOC, listOf(it))) }
            Tuning.gpp(tuning, poundage?.lbs)?.let { add(RigFact(RigFactKey.GPP, listOf(it))) }
        }.filter { fact -> fact.values.all { it.isFinite() } }
        return RigModel(name, bowType, poundage?.lbs, poundage?.source, facts)
    }
}

// ---------------------------------------------------------------------------------------------
// Quick start (template: pads)
// ---------------------------------------------------------------------------------------------

enum class Pad(val destination: WidgetDestination) {
    SCORE(WidgetDestination.SCORE),
    TRAIN(WidgetDestination.TRAIN),
    LOG(WidgetDestination.LOG),
    PROGRESS(WidgetDestination.PROGRESS),
    BODY(WidgetDestination.BODY),
    CALENDAR(WidgetDestination.CALENDAR),
}

/** The scorecard still open on the device, if any: what the Score pad resumes. */
data class OpenScorecard(val roundName: String, val arrowsShot: Int, val arrowsPlanned: Int)

object QuickStart {
    /**
     * Below the pads template's 2x2 minimum the widget collapses to one target, Score; compact, the
     * whole widget is that one target.
     */
    fun pads(size: WidgetSize): List<Pad> =
        when (size) {
            WidgetSize.COMPACT, WidgetSize.SMALL -> listOf(Pad.SCORE)
            WidgetSize.MEDIUM -> listOf(Pad.SCORE, Pad.TRAIN, Pad.LOG)
            WidgetSize.LARGE -> Pad.entries.toList()
        }
}

// ---------------------------------------------------------------------------------------------
// Push refresh: which widgets a database write can change
// ---------------------------------------------------------------------------------------------

object WidgetTables {
    /** Room table names each widget reads (directly or through the readiness cascade). */
    val byKind: Map<WidgetKind, Set<String>> =
        mapOf(
            WidgetKind.LAST_SCORECARD to setOf("athletes", "rig", "score_session", "score_arrow"),
            WidgetKind.THIS_WEEK to
                setOf("athletes", "sessions", "shots", "score_session", "score_arrow", "training_plan"),
            WidgetKind.NEXT_PHYSIO to
                setOf("athletes", "physio_plan", "physio_exercise", "physio_session"),
            WidgetKind.READINESS to
                setOf(
                    "athletes",
                    "sessions",
                    "shots",
                    "checkin",
                    "soreness",
                    "hiatus",
                    "life_event",
                    "injury",
                ),
            WidgetKind.ACTIVE_RIG to setOf("athletes", "rig"),
            WidgetKind.QUICK_START to setOf("athletes", "score_session", "score_arrow"),
        )

    val all: Set<String> = byKind.values.flatten().toSortedSet()

    fun kindsFor(tables: Set<String>): Set<WidgetKind> =
        byKind.filterValues { read -> read.any { it in tables } }.keys
}
