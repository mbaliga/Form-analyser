package xyz.mdhv.formanalyser.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.DateFormat
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import xyz.mdhv.formanalyser.app.R
import xyz.mdhv.formanalyser.equipment.PoundageSource
import xyz.mdhv.formanalyser.model.BowType

/**
 * Turns [WidgetContent] into RemoteViews in the app's own words (string resources) and palette
 * (widget_colors, with a values-night pair). Text colours never depend on a state: every state is
 * carried by a shape and a word, so the widget means the same thing to a colour-blind athlete.
 */
internal object WidgetRenderer {
    /**
     * API 31+: one RemoteViews per size, keyed by the size each layout needs at the current font
     * scale, and the launcher picks the largest that fits without waking the app. Below 31: the
     * layouts for the portrait and landscape sizes the launcher last reported.
     */
    fun views(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        kind: WidgetKind,
        content: WidgetContent,
        now: ZonedDateTime,
    ): RemoteViews {
        val template = templateOf(kind, content)
        val fontScale = context.resources.configuration.fontScale
        fun at(size: WidgetSize) = build(context, appWidgetId, kind, content, size, now)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return RemoteViews(
                WidgetSizing.keys(template, fontScale).entries.associate { (size, key) ->
                    SizeF(key.first, key.second) to at(size)
                },
            )
        }
        val (portrait, landscape) = sizesFromOptions(context, manager, appWidgetId, template, fontScale)
        return if (portrait == landscape) at(portrait) else RemoteViews(at(landscape), at(portrait))
    }

    /** The template whose sizes apply: a widget showing a state draws the message layout. */
    fun templateOf(kind: WidgetKind, content: WidgetContent): WidgetTemplate =
        if (content.isState()) WidgetTemplate.MESSAGE else kind.template

    private fun sizesFromOptions(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        template: WidgetTemplate,
        fontScale: Float,
    ): Pair<WidgetSize, WidgetSize> {
        val options = manager.getAppWidgetOptions(id)
        var minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).toFloat()
        var maxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH).toFloat()
        var minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).toFloat()
        var maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).toFloat()
        if (minW <= 0f || maxW <= 0f || minH <= 0f || maxH <= 0f) {
            // Not reported yet: fall back to the provider's default size (minWidth is in pixels).
            val info = manager.getAppWidgetInfo(id)
            val density = context.resources.displayMetrics.density
            minW = (info?.minWidth ?: 0) / density
            minH = (info?.minHeight ?: 0) / density
            maxW = minW
            maxH = minH
        }
        return WidgetSizing.portraitAndLandscape(template, minW, maxW, minH, maxH, fontScale)
    }

    private fun WidgetContent.isState() =
        this is WidgetContent.FirstRun ||
            this is WidgetContent.NeedsApp ||
            this is WidgetContent.Error ||
            this is WidgetContent.Empty

    fun build(
        context: Context,
        appWidgetId: Int,
        kind: WidgetKind,
        content: WidgetContent,
        size: WidgetSize,
        now: ZonedDateTime,
    ): RemoteViews =
        when (content) {
            is WidgetContent.LastScorecard -> lastScorecard(context, appWidgetId, content.model, size)
            is WidgetContent.ThisWeek -> thisWeek(context, appWidgetId, content.model, size)
            is WidgetContent.NextPhysio -> nextPhysio(context, appWidgetId, content.model, size, now)
            is WidgetContent.Readiness -> readiness(context, appWidgetId, content.model, size)
            is WidgetContent.ActiveRig -> activeRig(context, appWidgetId, content.model, size)
            is WidgetContent.QuickStart -> quickStart(context, appWidgetId, content.open, size)
            else -> state(context, appWidgetId, kind, content, size)
        }

    // -----------------------------------------------------------------------------------------
    // States
    // -----------------------------------------------------------------------------------------

    private fun state(
        context: Context,
        id: Int,
        kind: WidgetKind,
        content: WidgetContent,
        size: WidgetSize,
    ): RemoteViews {
        val (title, body) =
            when (content) {
                WidgetContent.FirstRun -> R.string.widget_first_run_title to R.string.widget_first_run_body
                WidgetContent.NeedsApp -> R.string.widget_needs_app_title to R.string.widget_needs_app_body
                is WidgetContent.Empty ->
                    when (content.kind) {
                        WidgetKind.LAST_SCORECARD ->
                            R.string.widget_empty_last_scorecard_title to R.string.widget_empty_last_scorecard_body
                        WidgetKind.NEXT_PHYSIO ->
                            R.string.widget_empty_next_physio_title to R.string.widget_empty_next_physio_body
                        WidgetKind.ACTIVE_RIG ->
                            R.string.widget_empty_active_rig_title to R.string.widget_empty_active_rig_body
                        else -> R.string.widget_error_title to R.string.widget_error_body
                    }
                else -> R.string.widget_error_title to R.string.widget_error_body
            }
        // A widget in a state still goes somewhere useful: the app decides what comes next.
        val destination =
            if (content is WidgetContent.Empty) destinationOf(kind) else WidgetDestination.HOME
        val spoken =
            "${context.getString(nameOf(kind))}. ${context.getString(title)}. ${context.getString(body)}"
        if (size == WidgetSize.COMPACT) {
            return compact(context, id, context.getString(title), context.getString(body), destination, spoken)
        }
        val rv = RemoteViews(context.packageName, R.layout.widget_message)
        rv.setTextViewText(R.id.widget_label, context.getString(nameOf(kind)))
        rv.setTextViewText(R.id.widget_title, context.getString(title))
        rv.setTextViewText(R.id.widget_caption, context.getString(body))
        // As many lines as WidgetSizing.need(MESSAGE, size) counts for this size.
        rv.setInt(R.id.widget_title, "setMaxLines", if (size == WidgetSize.LARGE) 2 else 1)
        rv.setInt(
            R.id.widget_caption,
            "setMaxLines",
            when (size) {
                WidgetSize.LARGE -> 4
                WidgetSize.MEDIUM -> 3
                else -> 2
            },
        )
        open(context, rv, id, destination, spoken)
        return rv
    }

    /** One line, the value and a caption: a single tappable summary for a very short widget. */
    private fun compact(
        context: Context,
        id: Int,
        value: String,
        caption: String,
        destination: WidgetDestination,
        spoken: String,
    ): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_compact)
        rv.setTextViewText(R.id.widget_value, value)
        rv.setTextViewText(R.id.widget_caption, caption)
        open(context, rv, id, destination, spoken)
        return rv
    }

    // -----------------------------------------------------------------------------------------
    // Last scorecard: stat
    // -----------------------------------------------------------------------------------------

    private fun lastScorecard(context: Context, id: Int, m: LastScorecardModel, size: WidgetSize): RemoteViews {
        val res = context.resources
        val value: String
        val unit: String
        if (m.setPoints != null) {
            value = context.getString(R.string.widget_score_set_points, m.setPoints.first, m.setPoints.second)
            unit = context.getString(R.string.widget_score_sets)
        } else {
            value = m.total.toString()
            unit = m.maxScore?.let { context.getString(R.string.widget_score_out_of, it) }.orEmpty()
        }
        val arrows =
            if (m.arrowsShot < m.arrowsPlanned)
                res.getQuantityString(R.plurals.widget_arrows_of, m.arrowsPlanned, m.arrowsShot, m.arrowsPlanned)
            else res.getQuantityString(R.plurals.widget_arrows, m.arrowsShot, m.arrowsShot)
        val spoken = context.getString(R.string.widget_score_content, m.roundName, "$value $unit, $arrows")
        if (size == WidgetSize.COMPACT) {
            return compact(context, id, "$value $unit".trim(), arrows, WidgetDestination.PROGRESS, spoken)
        }
        val rv = RemoteViews(context.packageName, statLayout(size))
        rv.setTextViewText(R.id.widget_label, context.getString(R.string.widget_last_scorecard_name))
        rv.setTextViewText(R.id.widget_value, value)
        rv.setTextViewText(R.id.widget_unit, unit)
        rv.setTextViewText(R.id.widget_caption, arrows)
        val round =
            sep(context, m.roundName, context.getString(R.string.widget_distance, m.distanceMeters))
        val detail =
            listOfNotNull(
                    context.getString(R.string.widget_score_x_count, m.xCount).takeIf { m.setPoints == null },
                    m.arrowAverage?.let { context.getString(R.string.widget_score_average, it) },
                    context.getString(R.string.widget_score_set_match).takeIf { m.setPoints != null },
                )
                .joinToString(" · ")
        when (size) {
            WidgetSize.COMPACT, WidgetSize.SMALL -> Unit
            WidgetSize.MEDIUM -> {
                rv.setTextViewText(R.id.widget_detail1, round)
                rv.setTextViewText(R.id.widget_detail2, date(m.finishedOn))
                rv.setTextViewText(R.id.widget_detail3, detail)
            }
            WidgetSize.LARGE -> {
                rv.setTextViewText(R.id.widget_detail1, sep(context, round, date(m.finishedOn)))
                rows(
                    context,
                    rv,
                    listOfNotNull(
                        if (m.setPoints == null) R.string.widget_score_x_label to m.xCount.toString()
                        else R.string.widget_score_kind_label to context.getString(R.string.widget_score_set_match),
                        m.arrowAverage?.let {
                            R.string.widget_score_average_label to context.getString(R.string.widget_score_average_value, it)
                        },
                        m.roundBest?.let { R.string.widget_score_round_best to it.toString() },
                        m.rigName?.let { R.string.widget_score_rig to it },
                    ),
                    4,
                )
                rv.setTextViewText(R.id.widget_footer, context.getString(R.string.widget_provenance_native))
            }
        }
        open(context, rv, id, WidgetDestination.PROGRESS, spoken)
        return rv
    }

    // -----------------------------------------------------------------------------------------
    // This week: progress
    // -----------------------------------------------------------------------------------------

    private fun thisWeek(context: Context, id: Int, m: WeekModel, size: WidgetSize): RemoteViews {
        val res = context.resources
        val unitText =
            m.target?.let { res.getQuantityString(R.plurals.widget_week_target_unit, it, it) }
                ?: context.getString(R.string.widget_week_unit)
        val fraction = m.fraction
        val over = m.over
        val caption =
            when {
                m.target == null -> context.getString(R.string.widget_week_no_target)
                over != null -> res.getQuantityString(R.plurals.widget_week_over, over, over)
                else -> context.getString(R.string.widget_week_percent, ((fraction ?: 0.0) * 100).roundToInt())
            }
        val sessions = res.getQuantityString(R.plurals.widget_sessions, m.sessions, m.sessions)
        val spoken = context.getString(R.string.widget_week_content, "${m.total} $unitText, $caption, $sessions")
        if (size == WidgetSize.COMPACT) {
            return compact(context, id, m.total.toString(), sep(context, unitText, caption), WidgetDestination.PROGRESS, spoken)
        }
        val layout =
            when (size) {
                WidgetSize.MEDIUM -> R.layout.widget_progress_medium
                WidgetSize.LARGE -> R.layout.widget_progress_large
                else -> R.layout.widget_progress_small
            }
        val rv = RemoteViews(context.packageName, layout)
        rv.setTextViewText(R.id.widget_label, context.getString(R.string.widget_this_week_name))
        rv.setTextViewText(R.id.widget_value, m.total.toString())
        rv.setTextViewText(R.id.widget_unit, unitText)
        if (fraction != null) {
            rv.setViewVisibility(R.id.widget_progress, View.VISIBLE)
            rv.setProgressBar(R.id.widget_progress, 1000, (fraction * 1000).roundToInt(), false)
        } else {
            rv.setViewVisibility(R.id.widget_progress, View.GONE)
        }
        rv.setTextViewText(R.id.widget_caption, caption)
        if (size != WidgetSize.SMALL) {
            rv.setTextViewText(R.id.widget_meta, context.getString(R.string.widget_week_from, date(m.weekStart)))
            rv.setTextViewText(R.id.widget_detail1, sessions)
        }
        if (size == WidgetSize.LARGE) {
            val dayIds =
                listOf(
                    Triple(R.id.widget_day1, R.id.widget_day1_count, R.id.widget_day1_label),
                    Triple(R.id.widget_day2, R.id.widget_day2_count, R.id.widget_day2_label),
                    Triple(R.id.widget_day3, R.id.widget_day3_count, R.id.widget_day3_label),
                    Triple(R.id.widget_day4, R.id.widget_day4_count, R.id.widget_day4_label),
                    Triple(R.id.widget_day5, R.id.widget_day5_count, R.id.widget_day5_label),
                    Triple(R.id.widget_day6, R.id.widget_day6_count, R.id.widget_day6_label),
                    Triple(R.id.widget_day7, R.id.widget_day7_count, R.id.widget_day7_label),
                )
            val locale = Locale.getDefault()
            dayIds.forEachIndexed { i, (column, count, label) ->
                val day = m.weekStart.plusDays(i.toLong()).dayOfWeek
                val arrows = m.days[i]
                val countText =
                    if (arrows > 0) arrows.toString() else context.getString(R.string.widget_week_no_arrows)
                val narrow = day.getDisplayName(TextStyle.NARROW, locale)
                rv.setTextViewText(count, countText)
                // Today is marked with a shape (▸), not a colour.
                rv.setTextViewText(
                    label,
                    if (i == m.todayIndex) context.getString(R.string.widget_week_today, narrow) else narrow,
                )
                val full = day.getDisplayName(TextStyle.FULL, locale)
                val spokenCount = res.getQuantityString(R.plurals.widget_arrows, arrows, arrows)
                rv.setContentDescription(
                    column,
                    context.getString(
                        if (i == m.todayIndex) R.string.widget_week_day_today_content
                        else R.string.widget_week_day_content,
                        full,
                        spokenCount,
                    ),
                )
            }
            if (m.planTitle != null) {
                rv.setViewVisibility(R.id.widget_detail2, View.VISIBLE)
                rv.setTextViewText(R.id.widget_detail2, context.getString(R.string.widget_week_plan, m.planTitle))
            } else {
                rv.setViewVisibility(R.id.widget_detail2, View.GONE)
            }
            rv.setTextViewText(R.id.widget_footer, context.getString(R.string.widget_provenance_native))
        }
        open(context, rv, id, WidgetDestination.PROGRESS, spoken)
        return rv
    }

    // -----------------------------------------------------------------------------------------
    // Next physio day: next
    // -----------------------------------------------------------------------------------------

    private fun nextPhysio(
        context: Context,
        id: Int,
        m: NextPhysioModel,
        size: WidgetSize,
        now: ZonedDateTime,
    ): RemoteViews {
        val res = context.resources
        val today = now.toLocalDate()
        val distance = WidgetDates.daysFrom(today, m.next.date)
        val dayName =
            when (distance) {
                0L -> context.getString(R.string.widget_physio_today)
                1L -> context.getString(R.string.widget_physio_tomorrow)
                else -> m.next.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            }
        // The absolute date stays true even if a redraw at midnight is late.
        val absolute = date(m.next.date)
        val caption =
            if (distance >= 2) {
                sep(context, res.getQuantityString(R.plurals.widget_physio_in_days, distance.toInt(), distance.toInt()), absolute)
            } else {
                absolute
            }
        val holds = holds(context, m.next)
        val spoken = context.getString(R.string.widget_physio_content, "$dayName, $caption, $holds")
        if (size == WidgetSize.COMPACT) {
            // No label fits, so the caption says what the day is for.
            val what = context.getString(R.string.widget_physio_compact, absolute)
            return compact(context, id, dayName, what, WidgetDestination.BODY, spoken)
        }
        val layout =
            when (size) {
                WidgetSize.MEDIUM -> R.layout.widget_next_medium
                WidgetSize.LARGE -> R.layout.widget_next_large
                else -> R.layout.widget_next_small
            }
        val rv = RemoteViews(context.packageName, layout)
        rv.setTextViewText(R.id.widget_label, context.getString(R.string.widget_next_physio_name))
        rv.setTextViewText(R.id.widget_value, dayName)
        rv.setTextViewText(R.id.widget_caption, caption)
        val adherence =
            m.adherence?.let { w ->
                res.getQuantityString(
                    R.plurals.widget_physio_adherence,
                    w.expected,
                    w.completed,
                    w.expected,
                    (w.percent ?: 0.0).roundToInt(),
                )
            }
        when (size) {
            WidgetSize.COMPACT, WidgetSize.SMALL -> Unit
            WidgetSize.MEDIUM -> {
                rv.setTextViewText(R.id.widget_detail1, holds)
                rv.setTextViewText(
                    R.id.widget_detail2,
                    if (m.todayLogged) context.getString(R.string.widget_physio_today_logged) else "",
                )
                rv.setTextViewText(
                    R.id.widget_detail3,
                    adherence?.let { sep(context, context.getString(R.string.widget_physio_adherence_label), it) }.orEmpty(),
                )
            }
            WidgetSize.LARGE -> {
                rv.setTextViewText(
                    R.id.widget_detail1,
                    if (m.todayLogged) sep(context, holds, context.getString(R.string.widget_physio_today_logged)) else holds,
                )
                val upcoming =
                    m.upcoming.drop(1).map { day -> date(day.date) to holds(context, day) }
                val adherenceRow =
                    adherence?.let { context.getString(R.string.widget_physio_adherence_label) to it }
                rowsText(rv, listOfNotNull(*upcoming.toTypedArray(), adherenceRow), 4)
                rv.setTextViewText(R.id.widget_footer, context.getString(R.string.widget_physio_privacy))
            }
        }
        open(context, rv, id, WidgetDestination.BODY, spoken)
        return rv
    }

    private fun holds(context: Context, day: PhysioDay): String =
        sep(
            context,
            context.resources.getQuantityString(R.plurals.widget_exercises, day.exercises, day.exercises),
            context.resources.getQuantityString(R.plurals.widget_plans, day.plans, day.plans),
        )

    // -----------------------------------------------------------------------------------------
    // Readiness: stat
    // -----------------------------------------------------------------------------------------

    fun readinessWord(context: Context, mark: ReadinessMark): String =
        context.getString(
            when (mark) {
                ReadinessMark.READY -> R.string.widget_readiness_ready
                ReadinessMark.CAUTION -> R.string.widget_readiness_caution
                ReadinessMark.REST_ADVISED -> R.string.widget_readiness_rest
                ReadinessMark.ON_PAUSE -> R.string.widget_readiness_pause
            },
        )

    private fun readiness(context: Context, id: Int, m: ReadinessModel, size: WidgetSize): RemoteViews {
        val res = context.resources
        val word = readinessWord(context, m.mark)
        val level = context.getString(R.string.widget_readiness_value, m.mark.glyph, word)
        // Readiness can lapse unseen (a check-in goes stale), so it always prints when it was read.
        val asOf = context.getString(R.string.widget_as_of, time(context, m.computedAtMs))
        val noCheckin = context.getString(R.string.widget_readiness_no_checkin)
        // With no check-in at all the cascade has nothing to lower the level with; say so wherever
        // the level is shown, so missing data never reads as readiness.
        val shortCaption = if (m.checkin == null) sep(context, noCheckin, asOf) else asOf
        val spoken = context.getString(R.string.widget_readiness_content, "$word, $shortCaption")
        if (size == WidgetSize.COMPACT) return compact(context, id, level, shortCaption, WidgetDestination.HOME, spoken)
        val rv = RemoteViews(context.packageName, statLayout(size))
        rv.setTextViewText(R.id.widget_label, context.getString(R.string.widget_readiness_name))
        rv.setTextViewText(R.id.widget_value, level)
        rv.setTextViewTextSize(
            R.id.widget_value,
            TypedValue.COMPLEX_UNIT_SP,
            if (size == WidgetSize.SMALL) 20f else 24f,
        )
        rv.setContentDescription(R.id.widget_value, word)
        rv.setTextViewText(R.id.widget_unit, "")
        // Medium and large print the check-in (or its absence) on their own line below.
        rv.setTextViewText(R.id.widget_caption, if (size == WidgetSize.SMALL) shortCaption else asOf)
        val checkinLine =
            m.checkin?.let { context.getString(R.string.widget_readiness_checkin, dateTime(context, it.atMs)) }
                ?: noCheckin
        val energy = m.checkin?.energy?.let { context.getString(R.string.widget_readiness_out_of_five, it) }
        val sleep = m.checkin?.sleep?.let { context.getString(R.string.widget_readiness_out_of_five, it) }
        val soreness =
            m.checkin?.let { res.getQuantityString(R.plurals.widget_regions, it.sorenessRegions, it.sorenessRegions) }
        val acwr =
            m.acwr?.let { context.getString(R.string.widget_readiness_acwr_value, it) }
                ?: context.getString(R.string.widget_readiness_acwr_building)
        when (size) {
            WidgetSize.COMPACT, WidgetSize.SMALL -> Unit
            WidgetSize.MEDIUM -> {
                rv.setTextViewText(R.id.widget_detail1, checkinLine)
                rv.setTextViewText(
                    R.id.widget_detail2,
                    listOfNotNull(
                            energy?.let { context.getString(R.string.widget_fact, context.getString(R.string.widget_readiness_energy), it) },
                            sleep?.let { context.getString(R.string.widget_fact, context.getString(R.string.widget_readiness_sleep), it) },
                        )
                        .joinToString(" · "),
                )
                rv.setTextViewText(
                    R.id.widget_detail3,
                    context.getString(R.string.widget_fact, context.getString(R.string.widget_readiness_acwr), acwr),
                )
            }
            WidgetSize.LARGE -> {
                rv.setTextViewText(R.id.widget_detail1, checkinLine)
                rows(
                    context,
                    rv,
                    listOfNotNull(
                        energy?.let { R.string.widget_readiness_energy to it },
                        sleep?.let { R.string.widget_readiness_sleep to it },
                        soreness?.let { R.string.widget_readiness_soreness to it },
                        R.string.widget_readiness_acwr to acwr,
                    ),
                    4,
                )
                rv.setTextViewText(R.id.widget_footer, context.getString(R.string.widget_provenance_native))
                rv.setViewVisibility(R.id.widget_action, View.VISIBLE)
                rv.setTextViewText(R.id.widget_action, context.getString(R.string.widget_readiness_log))
                rv.setOnClickPendingIntent(R.id.widget_action, openIntent(context, id, WidgetDestination.LOG))
                rv.setContentDescription(
                    R.id.widget_action,
                    context.getString(
                        R.string.widget_open_description,
                        context.getString(R.string.widget_readiness_log),
                        context.getString(R.string.widget_destination_log),
                    ),
                )
            }
        }
        open(context, rv, id, WidgetDestination.HOME, spoken)
        return rv
    }

    // -----------------------------------------------------------------------------------------
    // Active rig: card
    // -----------------------------------------------------------------------------------------

    private fun activeRig(context: Context, id: Int, m: RigModel, size: WidgetSize): RemoteViews {
        val bow =
            context.getString(
                when (m.bowType) {
                    BowType.RECURVE -> R.string.widget_bow_recurve
                    BowType.COMPOUND -> R.string.widget_bow_compound
                    BowType.BAREBOW -> R.string.widget_bow_barebow
                },
            )
        val poundage =
            m.poundageLbs?.let { lbs ->
                when (m.poundageSource) {
                    PoundageSource.MEASURED -> context.getString(R.string.widget_rig_lbs_measured, lbs)
                    PoundageSource.ESTIMATED -> context.getString(R.string.widget_rig_lbs_estimated, lbs)
                    else -> context.getString(R.string.widget_rig_lbs_marked, lbs)
                }
            }
        val meta = poundage?.let { sep(context, bow, it) } ?: bow
        val spoken = context.getString(R.string.widget_rig_content, "${m.name}, $meta")
        if (size == WidgetSize.COMPACT) return compact(context, id, m.name, meta, WidgetDestination.RIGS, spoken)
        val layout =
            when (size) {
                WidgetSize.MEDIUM -> R.layout.widget_card_medium
                WidgetSize.LARGE -> R.layout.widget_card_large
                else -> R.layout.widget_card_small
            }
        val rv = RemoteViews(context.packageName, layout)
        rv.setTextViewText(R.id.widget_label, context.getString(R.string.widget_active_rig_name))
        rv.setTextViewText(R.id.widget_title, m.name)
        rv.setTextViewText(R.id.widget_caption, meta)
        val facts = m.facts.map { fact -> context.getString(factLabel(fact.key)) to factValue(context, fact) }
        when (size) {
            WidgetSize.COMPACT, WidgetSize.SMALL -> Unit
            WidgetSize.MEDIUM -> {
                val lines = facts.map { (k, v) -> context.getString(R.string.widget_fact, k, v) }
                rv.setTextViewText(
                    R.id.widget_detail1,
                    lines.getOrNull(0) ?: context.getString(R.string.widget_rig_no_tuning),
                )
                rv.setTextViewText(R.id.widget_detail2, lines.getOrNull(1).orEmpty())
            }
            WidgetSize.LARGE -> {
                if (facts.isEmpty()) {
                    rv.setViewVisibility(R.id.widget_detail1, View.VISIBLE)
                    rv.setTextViewText(R.id.widget_detail1, context.getString(R.string.widget_rig_no_tuning))
                } else {
                    rv.setViewVisibility(R.id.widget_detail1, View.GONE)
                }
                rowsText(rv, facts, 6)
                rv.setTextViewText(R.id.widget_footer, "")
            }
        }
        open(context, rv, id, WidgetDestination.RIGS, spoken)
        return rv
    }

    private fun factLabel(key: RigFactKey): Int =
        when (key) {
            RigFactKey.BRACE_HEIGHT -> R.string.widget_rig_brace
            RigFactKey.TILLER -> R.string.widget_rig_tiller
            RigFactKey.ARROW_SPINE -> R.string.widget_rig_spine
            RigFactKey.ARROW_LENGTH -> R.string.widget_rig_arrow_length
            RigFactKey.POINT_MASS -> R.string.widget_rig_point
            RigFactKey.ARROW_MASS -> R.string.widget_rig_arrow_mass
            RigFactKey.FOC -> R.string.widget_rig_foc
            RigFactKey.GPP -> R.string.widget_rig_gpp
        }

    private fun factValue(context: Context, fact: RigFact): String {
        val v = fact.values
        return when (fact.key) {
            RigFactKey.BRACE_HEIGHT, RigFactKey.ARROW_LENGTH -> context.getString(R.string.widget_value_mm, v[0])
            RigFactKey.TILLER -> context.getString(R.string.widget_value_tiller, v[0], v[1])
            RigFactKey.ARROW_SPINE -> context.getString(R.string.widget_value_number, v[0])
            RigFactKey.POINT_MASS, RigFactKey.ARROW_MASS -> context.getString(R.string.widget_value_grains, v[0])
            RigFactKey.FOC -> context.getString(R.string.widget_value_percent, v[0])
            RigFactKey.GPP -> context.getString(R.string.widget_value_decimal, v[0])
        }
    }

    // -----------------------------------------------------------------------------------------
    // Start training: pads
    // -----------------------------------------------------------------------------------------

    private val padIds =
        listOf(
            Triple(R.id.widget_pad1, R.id.widget_pad1_icon, R.id.widget_pad1_label),
            Triple(R.id.widget_pad2, R.id.widget_pad2_icon, R.id.widget_pad2_label),
            Triple(R.id.widget_pad3, R.id.widget_pad3_icon, R.id.widget_pad3_label),
            Triple(R.id.widget_pad4, R.id.widget_pad4_icon, R.id.widget_pad4_label),
            Triple(R.id.widget_pad5, R.id.widget_pad5_icon, R.id.widget_pad5_label),
            Triple(R.id.widget_pad6, R.id.widget_pad6_icon, R.id.widget_pad6_label),
        )

    private fun quickStart(context: Context, id: Int, open: OpenScorecard?, size: WidgetSize): RemoteViews {
        val layout =
            when (size) {
                WidgetSize.COMPACT -> R.layout.widget_pads_compact
                WidgetSize.SMALL -> R.layout.widget_pads_small
                WidgetSize.MEDIUM -> R.layout.widget_pads_medium
                WidgetSize.LARGE -> R.layout.widget_pads_large
            }
        val rv = RemoteViews(context.packageName, layout)
        QuickStart.pads(size).forEachIndexed { i, pad ->
            val (container, icon, label) = padIds[i]
            val resume = pad == Pad.SCORE && open != null
            val (iconRes, labelRes, descriptionRes) =
                when (pad) {
                    Pad.SCORE ->
                        if (resume) Triple(R.drawable.ic_widget_score, R.string.widget_pad_resume, R.string.widget_pad_resume_description)
                        else Triple(R.drawable.ic_widget_score, R.string.widget_pad_score, R.string.widget_pad_score_description)
                    Pad.TRAIN -> Triple(R.drawable.ic_widget_train, R.string.widget_pad_train, R.string.widget_pad_train_description)
                    Pad.LOG -> Triple(R.drawable.ic_widget_log, R.string.widget_pad_log, R.string.widget_pad_log_description)
                    Pad.PROGRESS ->
                        Triple(R.drawable.ic_widget_progress, R.string.widget_pad_progress, R.string.widget_pad_progress_description)
                    Pad.BODY -> Triple(R.drawable.ic_widget_body, R.string.widget_pad_body, R.string.widget_pad_body_description)
                    Pad.CALENDAR ->
                        Triple(R.drawable.ic_widget_calendar, R.string.widget_pad_calendar, R.string.widget_pad_calendar_description)
                }
            rv.setImageViewResource(icon, iconRes)
            rv.setTextViewText(label, context.getString(labelRes))
            rv.setContentDescription(container, context.getString(descriptionRes))
            rv.setOnClickPendingIntent(container, openIntent(context, id, pad.destination))
        }
        if (size == WidgetSize.LARGE) {
            rv.setTextViewText(
                R.id.widget_caption,
                open?.let {
                    context.resources.getQuantityString(
                        R.plurals.widget_pads_open_card,
                        it.arrowsPlanned,
                        it.roundName,
                        it.arrowsShot,
                        it.arrowsPlanned,
                    )
                } ?: context.getString(R.string.widget_pads_no_open_card),
            )
        }
        return rv
    }

    // -----------------------------------------------------------------------------------------
    // Shared helpers
    // -----------------------------------------------------------------------------------------

    private fun statLayout(size: WidgetSize) =
        when (size) {
            WidgetSize.MEDIUM -> R.layout.widget_stat_medium
            WidgetSize.LARGE -> R.layout.widget_stat_large
            else -> R.layout.widget_stat_small
        }

    private val rowIds =
        listOf(
            Triple(R.id.widget_row1, R.id.widget_row1_key, R.id.widget_row1_value),
            Triple(R.id.widget_row2, R.id.widget_row2_key, R.id.widget_row2_value),
            Triple(R.id.widget_row3, R.id.widget_row3_key, R.id.widget_row3_value),
            Triple(R.id.widget_row4, R.id.widget_row4_key, R.id.widget_row4_value),
            Triple(R.id.widget_row5, R.id.widget_row5_key, R.id.widget_row5_value),
            Triple(R.id.widget_row6, R.id.widget_row6_key, R.id.widget_row6_value),
        )

    private fun rows(context: Context, rv: RemoteViews, rows: List<Pair<Int, String>>, capacity: Int) =
        rowsText(rv, rows.map { (key, value) -> context.getString(key) to value }, capacity)

    /** Fills rows 1..[capacity] of the layout; unused rows stay gone. */
    private fun rowsText(rv: RemoteViews, rows: List<Pair<String, String>>, capacity: Int) {
        rowIds.take(capacity).forEachIndexed { i, (row, key, value) ->
            val entry = rows.getOrNull(i)
            if (entry == null) {
                rv.setViewVisibility(row, View.GONE)
            } else {
                rv.setViewVisibility(row, View.VISIBLE)
                rv.setTextViewText(key, entry.first)
                rv.setTextViewText(value, entry.second)
            }
        }
    }

    private fun sep(context: Context, a: String, b: String) = context.getString(R.string.widget_separator, a, b)

    private fun date(day: LocalDate): String {
        val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEdMMM")
        return day.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    private fun time(context: Context, epochMs: Long): String = DateFormat.getTimeFormat(context).format(Date(epochMs))

    private fun dateTime(context: Context, epochMs: Long): String {
        val day =
            java.time.Instant.ofEpochMilli(epochMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        return "${date(day)} ${time(context, epochMs)}"
    }

    fun nameOf(kind: WidgetKind): Int =
        when (kind) {
            WidgetKind.LAST_SCORECARD -> R.string.widget_last_scorecard_name
            WidgetKind.THIS_WEEK -> R.string.widget_this_week_name
            WidgetKind.NEXT_PHYSIO -> R.string.widget_next_physio_name
            WidgetKind.READINESS -> R.string.widget_readiness_name
            WidgetKind.ACTIVE_RIG -> R.string.widget_active_rig_name
            WidgetKind.QUICK_START -> R.string.widget_quick_start_name
        }

    fun destinationOf(kind: WidgetKind): WidgetDestination =
        when (kind) {
            WidgetKind.LAST_SCORECARD -> WidgetDestination.PROGRESS
            WidgetKind.THIS_WEEK -> WidgetDestination.PROGRESS
            WidgetKind.NEXT_PHYSIO -> WidgetDestination.BODY
            WidgetKind.READINESS -> WidgetDestination.HOME
            WidgetKind.ACTIVE_RIG -> WidgetDestination.RIGS
            WidgetKind.QUICK_START -> WidgetDestination.SCORE
        }

    private fun destinationName(context: Context, d: WidgetDestination): String =
        context.getString(
            when (d) {
                WidgetDestination.HOME -> R.string.widget_destination_home
                WidgetDestination.SCORE -> R.string.widget_destination_score
                WidgetDestination.TRAIN -> R.string.widget_destination_train
                WidgetDestination.LOG -> R.string.widget_destination_log
                WidgetDestination.PROGRESS -> R.string.widget_destination_progress
                WidgetDestination.BODY -> R.string.widget_destination_body
                WidgetDestination.CALENDAR -> R.string.widget_destination_calendar
                WidgetDestination.RIGS -> R.string.widget_destination_rigs
            },
        )

    /** The whole widget is one tap target that opens [destination]; TalkBack reads [spoken]. */
    private fun open(context: Context, rv: RemoteViews, id: Int, destination: WidgetDestination, spoken: String) {
        rv.setOnClickPendingIntent(android.R.id.background, openIntent(context, id, destination))
        rv.setContentDescription(
            android.R.id.background,
            context.getString(R.string.widget_open_description, spoken, destinationName(context, destination)),
        )
    }

    /**
     * An explicit intent for the app's own WidgetTapActivity: a fixed action plus a destination
     * name it validates. Immutable, and keyed per widget id and destination so two widgets never
     * share one. No CLEAR_TOP: WidgetTapActivity decides, from what is on top of the app's task,
     * whether the tap may navigate (see WidgetLaunch.tapAction).
     */
    fun openIntent(context: Context, appWidgetId: Int, destination: WidgetDestination): PendingIntent {
        val intent =
            Intent(context, WidgetTapActivity::class.java)
                .setAction(WidgetLaunch.ACTION_OPEN)
                .putExtra(WidgetLaunch.EXTRA_DESTINATION, destination.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        return PendingIntent.getActivity(
            context,
            WidgetLaunch.requestCode(appWidgetId, destination),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
