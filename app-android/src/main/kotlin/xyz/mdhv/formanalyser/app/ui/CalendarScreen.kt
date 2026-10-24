package xyz.mdhv.formanalyser.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import xyz.mdhv.formanalyser.app.domain.CalendarViewModel
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.app.ui.theme.HyleSegmented
import xyz.mdhv.formanalyser.wellness.WellnessConstants

/** Calendar tab (Phase 2 §E): month grid + streak strip, with a Load view toggle. */
@Composable
fun CalendarScreen(vm: CalendarViewModel, onLog: () -> Unit) {
    LaunchedEffect(Unit) { vm.load() }
    val month by vm.month.collectAsState()
    val marks by vm.marks.collectAsState()
    val streak by vm.streak.collectAsState()
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

        // Streak strip
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
            StreakStrip(marks)
        }

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

/** Seven explicit day glyphs make the streak understandable without relying on a number alone. */
@Composable
private fun StreakStrip(marks: Map<LocalDate, xyz.mdhv.formanalyser.app.domain.DayMarks>) {
    val today = LocalDate.now()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Last 7 days", color = Hyle.OnSurfaceDim, style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (6 downTo 0).forEach { age ->
                val date = today.minusDays(age.toLong())
                val mark = marks[date]
                val (glyph, meaning, color) = when {
                    mark?.hiatus == true -> Triple("░", "hiatus", Hyle.OnSurfaceDim)
                    mark?.session == true -> Triple("●", "session", Hyle.RadiumGreen)
                    mark?.rest == true -> Triple("◦", "rest", Hyle.OnSurfaceDim)
                    mark?.checkin == true -> Triple("·", "check-in", Hyle.Accent)
                    else -> Triple("○", "no logged activity", Hyle.OnSurfaceDim)
                }
                Column(
                    Modifier.weight(1f).semantics {
                        contentDescription = "$date, $meaning"
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()),
                        color = Hyle.OnSurfaceDim,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Box(
                        Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (date == today) Hyle.Surface else Hyle.Background),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(glyph, color = color, style = MaterialTheme.typography.titleMedium)
                    }
                }
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
            "ACWR ${latestAcwr?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "—"}  (sweet 0.8–1.3)",
            color = Hyle.OnBackground,
        )
    }
    // Weekly bars: shot-load summed per ISO week, last 8 weeks.
    val wf = WeekFields.of(Locale.getDefault())
    val weekly =
        loads
            .groupBy {
                it.date.get(wf.weekBasedYear()) * 100 + it.date.get(wf.weekOfWeekBasedYear())
            }
            .toSortedMap()
            .values
            .map {
                wk ->
                WeeklyLoad(
                    shotLoad = wk.sumOf { it.shotLoad },
                    srpeLoad = wk.sumOf { it.srpeLoad },
                    incompleteShotLoad = wk.any { !it.complete },
                )
            }
            .takeLast(8)
    if (weekly.isEmpty()) {
        Text("No load yet — shoot a session.", color = Hyle.OnSurfaceDim)
        return
    }
    LoadLane(
        weekly = weekly,
        value = { it.shotLoad },
        color = Hyle.Accent,
        hollow = { it.incompleteShotLoad },
        title = "Weekly shot load (arrows × kg). Hollow = missing poundage.",
    )
    // The sRPE secondary lane's ratio (Acwr.computeSrpe): a strain read the shot-load ratio cannot
    // see -- a long, gruelling holds/SPT session adds no arrows. Shown only once its own warm-up
    // is complete, so an immature ratio is never presented as a verdict.
    latestSrpeAcwr?.let {
        Text(
            "sRPE ACWR ${String.format(Locale.ROOT, "%.2f", it)}  (sweet 0.8–1.3)",
            color = Hyle.OnBackground,
        )
    }
    LoadLane(
        weekly = weekly,
        value = { it.srpeLoad },
        color = Hyle.RadiumGreen,
        hollow = { false },
        title = "Weekly sRPE load (minutes × RPE).",
    )
    if (weekly.all { it.srpeLoad == 0.0 }) {
        Text(
            "sRPE appears after a session has a duration and post-session RPE.",
            color = Hyle.OnSurfaceDim,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

private data class WeeklyLoad(
    val shotLoad: Double,
    val srpeLoad: Double,
    val incompleteShotLoad: Boolean,
)

@Composable
private fun LoadLane(
    weekly: List<WeeklyLoad>,
    value: (WeeklyLoad) -> Double,
    color: Color,
    hollow: (WeeklyLoad) -> Boolean,
    title: String,
) {
    val maxLoad = weekly.maxOf { value(it) }.coerceAtLeast(1.0)
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val n = weekly.size
        val barW = size.width / (n * 1.5f)
        weekly.forEachIndexed { i, load ->
            val h = (value(load) / maxLoad * size.height).toFloat()
            val x = i * size.width / n + (size.width / n - barW) / 2
            drawRect(
                color = if (hollow(load)) Hyle.SurfaceVariant else color,
                topLeft = Offset(x, size.height - h),
                size = androidx.compose.ui.geometry.Size(barW, h),
            )
        }
    }
    Text(title, color = Hyle.OnSurfaceDim, style = MaterialTheme.typography.labelMedium)
}
