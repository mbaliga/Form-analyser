package xyz.mdhv.formanalyser.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import xyz.mdhv.formanalyser.app.domain.CalendarViewModel
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.app.ui.theme.HyleSectionHeader
import xyz.mdhv.formanalyser.app.ui.theme.HyleSegmented
import xyz.mdhv.formanalyser.wellness.DayFacts
import xyz.mdhv.formanalyser.wellness.StreakEngine
import xyz.mdhv.formanalyser.wellness.WellnessConstants

/** Calendar tab (Phase 2 §E): month grid + streak strip, with a Load view toggle. */
@Composable
fun CalendarScreen(vm: CalendarViewModel, onLog: () -> Unit) {
    LaunchedEffect(Unit) { vm.load() }
    val month by vm.month.collectAsState()
    val marks by vm.marks.collectAsState()
    val streak by vm.streak.collectAsState()
    val weekStrip by vm.weekStrip.collectAsState()
    val loads by vm.loads.collectAsState()
    val acwr by vm.acwr.collectAsState()
    val srpeAcwr by vm.srpeAcwr.collectAsState()
    var view by rememberSaveable { mutableStateOf("Calendar") }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$month",
                style = MaterialTheme.typography.headlineMedium,
                color = Hyle.OnBackground,
            )
            HyleSegmented(listOf("Calendar", "Load"), view, { it }) { view = it }
        }

        // Streak strip: the count line, plus a 7-glyph week-at-a-glance underneath it (previously
        // text-only — see CROCODYL_BUILD_NOTES.md "Streak week-strip is a text summary line, not 7
        // glyph dots").
        streak?.let { s ->
            val frozen = marks[LocalDate.now()]?.hiatus == true
            Text(
                buildString {
                    append("Streak ${s.length}")
                    if (s.patchedCount > 0) append("  ~${s.patchedCount}")
                    if (frozen) append("  · frozen")
                    if (s.provisionalToday) append("  · today counts")
                },
                color = if (frozen) Hyle.OnSurfaceDim else Hyle.RadiumGreen,
            )
        }
        if (weekStrip.isNotEmpty()) WeekStrip(weekStrip)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { vm.setMonth(month.minusMonths(1)) }) { Text("← prev") }
            TextButton(onClick = { vm.setMonth(month.plusMonths(1)) }) { Text("next →") }
            TextButton(onClick = onLog) { Text("+ Log") }
        }

        if (view == "Calendar") {
            MonthGrid(monthDays(month.atDay(1)), marks)
            Text(
                "● session   ◦ rest   · check-in   ✦ event   ░ hiatus",
                color = Hyle.OnSurfaceDim,
                style = MaterialTheme.typography.labelMedium,
            )
        } else {
            LoadView(
                loads,
                acwr?.let { if (it.warmupComplete) it.latest?.acwr else null },
                acwr?.warmupDaysElapsed,
                acwr?.warmupComplete == true,
                srpeAcwr?.let { if (it.warmupComplete) it.latest?.acwr else null },
            )
        }
    }
}

/**
 * The 7-glyph week strip under the streak count: one dot per day, oldest (6 days ago) to newest
 * (today, rightmost), rendered from the same [StreakEngine.qualifies] rule the streak count itself
 * uses — so the dots and the number can never disagree about what counted. A filled dot is a
 * qualifying day; a hollow ring is a day that has not (yet, for today) qualified; the hiatus tone
 * matches the month grid's frozen band. Today gets an accent ring on top of whichever fill it has,
 * since — unlike every other day here — its outcome is still provisional.
 */
@Composable
private fun WeekStrip(days: List<DayFacts>) {
    val today = LocalDate.now()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { d ->
            val qualifies = StreakEngine.qualifies(d)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    d.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    color = Hyle.OnSurfaceDim,
                    style = MaterialTheme.typography.labelSmall,
                )
                Box(
                    Modifier.size(18.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                d.hiatus -> Hyle.SurfaceVariant
                                qualifies -> Hyle.RadiumGreen
                                else -> Hyle.Background
                            }
                        )
                        .border(
                            width = if (d.date == today) 2.dp else 1.dp,
                            color = if (d.date == today) Hyle.Accent else Hyle.OnSurfaceDim.copy(alpha = 0.35f),
                            shape = CircleShape,
                        )
                )
            }
        }
    }
}

private fun monthDays(first: LocalDate): List<LocalDate?> {
    val month = first.month
    val lead = (first.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val days = mutableListOf<LocalDate?>()
    repeat(lead) { days.add(null) }
    var d = first
    while (d.month == month) {
        days.add(d)
        d = d.plusDays(1)
    }
    return days
}

@Composable
private fun MonthGrid(
    days: List<LocalDate?>,
    marks: Map<LocalDate, xyz.mdhv.formanalyser.app.domain.DayMarks>,
) {
    val today = LocalDate.now()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(
                    it,
                    color = Hyle.OnSurfaceDim,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        days.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { d ->
                    Box(
                        Modifier.weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    d == null -> Hyle.Background
                                    marks[d]?.hiatus == true ->
                                        Hyle.SurfaceVariant.copy(alpha = 0.6f)
                                    d == today -> Hyle.Surface
                                    else -> Hyle.Background
                                }
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (d != null) {
                            val m = marks[d]
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "${d.dayOfMonth}",
                                    color = if (d == today) Hyle.Accent else Hyle.OnBackground,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Text(
                                    buildString {
                                        if (m?.session == true) append("●")
                                        if (m?.rest == true) append("◦")
                                        if (m?.checkin == true) append("·")
                                        if (m?.event == true) append("✦")
                                    },
                                    color = Hyle.RadiumGreen,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
                // pad the final short week
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** One lane's ISO-week sums over the last 8 weeks, paired with whether that week is missing an
 * input the lane depends on (flagged with a hollow bar, never silently folded into the total —
 * same "flagged, never silently zeroed" rule [LoadModel] uses for the day it aggregates from). */
private fun weeklyBars(
    loads: List<xyz.mdhv.formanalyser.wellness.DailyLoad>,
    valueOf: (xyz.mdhv.formanalyser.wellness.DailyLoad) -> Double,
    incompleteOf: (xyz.mdhv.formanalyser.wellness.DailyLoad) -> Boolean,
): List<Pair<Double, Boolean>> {
    val wf = WeekFields.of(Locale.getDefault())
    return loads
        .groupBy { it.date.get(wf.weekBasedYear()) * 100 + it.date.get(wf.weekOfWeekBasedYear()) }
        .toSortedMap()
        .values
        .map { wk -> wk.sumOf(valueOf) to wk.any(incompleteOf) }
        .takeLast(8)
}

@Composable
private fun WeeklyBarsChart(weekly: List<Pair<Double, Boolean>>, barColor: androidx.compose.ui.graphics.Color) {
    val maxLoad = weekly.maxOf { it.first }.coerceAtLeast(1.0)
    Canvas(Modifier.fillMaxWidth().height(100.dp)) {
        val n = weekly.size
        val barW = size.width / (n * 1.5f)
        weekly.forEachIndexed { i, (load, incomplete) ->
            val h = (load / maxLoad * size.height).toFloat()
            val x = i * size.width / n + (size.width / n - barW) / 2
            drawRect(
                color = if (incomplete) Hyle.SurfaceVariant else barColor,
                topLeft = Offset(x, size.height - h),
                size = androidx.compose.ui.geometry.Size(barW, h),
            )
        }
    }
}

@Composable
private fun LoadView(
    loads: List<xyz.mdhv.formanalyser.wellness.DailyLoad>,
    latestAcwr: Double?,
    warmupDays: Int?,
    warm: Boolean,
    latestSrpeAcwr: Double?,
) {
    if (!warm) {
        Text(
            "Building your load baseline — ${warmupDays ?: 0} of ${WellnessConstants.WARMUP_DAYS} days.",
            color = Hyle.OnSurfaceDim,
        )
    } else {
        Text(
            "ACWR ${latestAcwr?.let { String.format("%.2f", it) } ?: "—"}  (sweet 0.8–1.3)",
            color = Hyle.OnBackground,
        )
    }
    val shotWeekly = weeklyBars(loads, { it.shotLoad }, { !it.complete })
    if (shotWeekly.isEmpty()) {
        Text("No load yet — shoot a session.", color = Hyle.OnSurfaceDim)
        return
    }
    WeeklyBarsChart(shotWeekly, Hyle.Accent)
    Text(
        "Weekly shot load (arrows × kg). Hollow = missing poundage.",
        color = Hyle.OnSurfaceDim,
        style = MaterialTheme.typography.labelMedium,
    )

    // sRPE secondary lane (session RPE × duration): a strain read the shot-load lane can't see —
    // a long, gruelling holds/SPT session or a hot exhausting day still loads the body without
    // adding a single arrow to the shot-load total. Previously computed (LoadModel.srpeLoad,
    // Acwr.computeSrpe) but never surfaced here — see CROCODYL_BUILD_NOTES.md "srpe lane is
    // computed but the Load view shows shot-load bars only (secondary lane deferred)".
    HyleSectionHeader("sRPE load")
    Text(
        "sRPE ACWR ${latestSrpeAcwr?.let { String.format("%.2f", it) } ?: "—"}  (sweet 0.8–1.3)",
        color = Hyle.OnBackground,
    )
    val srpeWeekly = weeklyBars(loads, { it.srpeLoad }, { it.shotLoad > 0.0 && it.srpeLoad <= 0.0 })
    if (srpeWeekly.all { it.first <= 0.0 }) {
        Text("No sRPE yet — log a post-session RPE.", color = Hyle.OnSurfaceDim)
    } else {
        WeeklyBarsChart(srpeWeekly, Hyle.RadiumGreen)
        Text(
            "Weekly sRPE load (minutes × RPE). Hollow = shot that week without a logged RPE.",
            color = Hyle.OnSurfaceDim,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
