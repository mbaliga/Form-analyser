package xyz.mdhv.formanalyser.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import xyz.mdhv.formanalyser.app.domain.ProgressViewModel
import xyz.mdhv.formanalyser.app.ui.components.TimeTrendLine
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.app.ui.theme.HyleListRow
import xyz.mdhv.formanalyser.app.ui.theme.HyleSectionHeader
import xyz.mdhv.formanalyser.athlete.*

@Composable
fun ProgressScreen(vm: ProgressViewModel, onBody: () -> Unit) {
    LaunchedEffect(Unit) { vm.load() }
    val s by vm.state.collectAsState()
    var goalOpen by rememberSaveable { mutableStateOf(false) }
    var planOpen by rememberSaveable { mutableStateOf(false) }
    var interventionOpen by rememberSaveable { mutableStateOf(false) }
    if (s.loading) {
        Box(Modifier.fillMaxSize().padding(24.dp)) { CircularProgressIndicator() }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ProgressHeader() }
        item {
            OutlinedButton(onClick = onBody, modifier = Modifier.fillMaxWidth()) {
                Text("Physical readiness and body history")
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("Arrows · 28d", s.arrows28d.toString(), Modifier.weight(1f))
                MetricCard("Sessions · 28d", s.sessions28d.toString(), Modifier.weight(1f))
            }
        }
        s.scoreTrend?.let { t ->
            item {
                SectionCard("Score trend") {
                    // Percent of each round's own maximum, not raw points: a 720 and a 300 in one
                    // series made "pts/day" partly an artefact of which rounds happened to be shot.
                    Text("${t.sampleCount} complete rounds · ${signed(t.delta)}% of round max")
                    Text(
                        "${signed(t.slopePerDay)}% per day · normalised across rounds",
                        color = Hyle.OnSurfaceDim,
                    )
                    TimeTrendLine(
                        s.scorePoints.takeLast(20).map {
                            TimedValue(it.atMs, 100.0 * it.total / it.max.coerceAtLeast(1))
                        },
                        maxV = 100.0,
                    )
                }
            }
        }
        if (s.scoreTrend == null) {
            item {
                SectionCard("Score trend") {
                    Text("A complete scored round will establish your first baseline.")
                    Text(
                        "Only recorded rounds are shown; no projections are added.",
                        color = Hyle.OnSurfaceDim,
                    )
                }
            }
        }
        if (s.pbs.isNotEmpty()) {
            item { HyleSectionHeader("Personal bests") }
            items(s.pbs) { pb ->
                HyleListRow(pb.roundName, trailing = { Text("${pb.score}/${pb.max}") })
            }
        }
        s.formTrend?.let { t ->
            item {
                SectionCard("Form repeatability") {
                    Text("${"%.1f".format(t.lastValue)} / 100")
                    Text(
                        "Transparent 1−CV repeatability across captured pose features; not a causal performance score.",
                        color = Hyle.OnSurfaceDim,
                    )
                    TimeTrendLine(
                        s.formPoints.takeLast(20).map { TimedValue(it.atMs, it.stability) }
                    )
                }
            }
        }
        if (s.formTrend == null) {
            item {
                SectionCard("Form repeatability") {
                    Text("No comparable capture series yet.")
                    Text(
                        "Record more sessions with usable pose data to see repeatability here.",
                        color = Hyle.OnSurfaceDim,
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HyleSectionHeader("Goals", modifier = Modifier.weight(1f))
                TextButton(onClick = { goalOpen = true }) { Text("+ Goal") }
            }
        }
        if (s.goals.isEmpty())
            item {
                Text(
                    "No goals yet. Goals are evaluated against your recorded evidence, not synthetic projections.",
                    color = Hyle.OnSurfaceDim,
                )
            }
        else items(s.goals, key = { it.entity.id }) { g -> GoalRow(g, vm) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HyleSectionHeader("Training plans", modifier = Modifier.weight(1f))
                TextButton(onClick = { planOpen = true }) { Text("+ Plan") }
            }
        }
        items(s.plans, key = { it.id }) { p ->
            HyleListRow(
                p.title,
                subtitle = "${p.phase} · ${p.focus}" +
                    (p.weeklyArrowTarget?.let { " · $it arrows/week" } ?: ""),
            )
        }
        item {
            HyleSectionHeader("Equipment context")
            Text(
                "Descriptive only: different rounds, weather and training states are confounders. This is not causal equipment attribution.",
                color = Hyle.OnSurfaceDim,
            )
        }
        items(s.equipmentContext, key = { it.rigId }) { r ->
            HyleListRow(
                r.rigName,
                subtitle = "${r.rounds} complete rounds",
                trailing = { Text("${"%.1f".format(r.averagePercent)}%") },
            )
        }
        if (s.equipmentContext.isEmpty()) {
            item {
                Text(
                    "Log complete rounds with an active rig to compare descriptive equipment context.",
                    color = Hyle.OnSurfaceDim,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HyleSectionHeader("Interventions", modifier = Modifier.weight(1f))
                TextButton(onClick = { interventionOpen = true }) { Text("+ Change") }
            }
        }
        items(s.interventions, key = { it.id }) { i ->
            HyleListRow(
                i.title,
                subtitle = i.kind + (i.note?.let { " · $it" } ?: ""),
            )
        }
        if (s.missingEvidence.isNotEmpty())
            item {
                SectionCard("Evidence gaps") {
                    s.missingEvidence.forEach { Text("• $it", color = Hyle.OnSurfaceDim) }
                }
            }
    }
    s.error?.let { message ->
        AlertDialog(
            onDismissRequest = vm::clearError,
            title = { Text("Progress") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = vm::clearError) { Text("OK") } },
        )
    }
    if (goalOpen)
        GoalDialog(
            onDismiss = { goalOpen = false },
            onSave = { m, t, v, u, d, a ->
                goalOpen = false
                vm.saveGoal(m, t, v, u, d, a)
            },
        )
    if (planOpen)
        PlanDialog(
            onDismiss = { planOpen = false },
            onSave = { t, p, f, w, i, r ->
                planOpen = false
                vm.savePlan(t, p, f, w, i, r)
            },
        )
    if (interventionOpen)
        InterventionDialog(
            onDismiss = { interventionOpen = false },
            onSave = { k, t, n ->
                interventionOpen = false
                vm.addIntervention(k, t, n)
            },
        )
}

@Composable
private fun ProgressHeader() {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 144.dp).clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xff211b2b), Color(0xff111814), Color(0xff101015)))
            )
            .border(1.dp, Hyle.HairlineStrong, RoundedCornerShape(26.dp)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * .88f, size.height * .5f)
            listOf(.78f, .56f, .34f, .13f).forEachIndexed { index, fraction ->
                drawCircle(
                    color = if (index == 3) Hyle.RadiumGreen.copy(alpha = .28f)
                    else Hyle.Accent.copy(alpha = .17f - index * .025f),
                    radius = size.height * fraction,
                    center = center,
                    style = Stroke(if (index == 3) 2.5f else 1.5f),
                )
            }
            drawLine(
                Hyle.OnBackground.copy(alpha = .3f),
                Offset(center.x - 75f, center.y + 56f),
                Offset(center.x + 28f, center.y - 44f),
                2f,
            )
        }
        Column(
            Modifier.fillMaxWidth(.76f).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("THE LONG VIEW  /  LOCAL EVIDENCE", color = Hyle.RadiumGreen, style = MaterialTheme.typography.labelSmall)
            Text(
                "Training, in context",
                color = Hyle.OnBackground,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                "Understand what changes performance. No projections, no causal shortcuts.",
                color = Hyle.OnSurfaceDim,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier) {
    Card(
        modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Hyle.Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Hyle.Hairline),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = Hyle.OnBackground)
            Text(label.uppercase(), color = Hyle.OnSurfaceDim, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Hyle.Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Hyle.Hairline),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun GoalRow(g: ProgressViewModel.GoalCard, vm: ProgressViewModel) {
    val p = g.progress
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(g.entity.title)
                Text(
                    if (p.achieved) "Achieved"
                    else
                        p.currentValue?.let { "${"%.1f".format(it)} / ${g.entity.targetValue}" }
                            ?: "No evidence"
                )
            }
            p.progressFraction?.let {
                LinearProgressIndicator(
                    progress = { it.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                "${g.entity.metric.replace('_',' ')} · ${g.entity.aggregation}",
                color = Hyle.OnSurfaceDim,
                style = MaterialTheme.typography.bodySmall,
            )
            if (g.entity.state == GoalState.ACTIVE.name)
                TextButton(onClick = { vm.setGoalState(g.entity.id, GoalState.PAUSED) }) {
                    Text("Pause")
                }
        }
    }
}

private fun signed(v: Double) = (if (v >= 0) "+" else "") + "%.1f".format(v)

@Composable
private fun GoalDialog(
    onDismiss: () -> Unit,
    onSave: (GoalMetric, String, Double, String, GoalDirection, GoalAggregation) -> Unit,
) {
    var metric by rememberSaveable { mutableStateOf(GoalMetric.ROUND_TOTAL) }
    var title by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf("pts") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Metric")
                GoalMetric.entries.forEach { m ->
                    FilterChip(
                        metric == m,
                        { metric = m },
                        label = { Text(m.name.replace('_', ' ')) },
                    )
                }
                OutlinedTextField(title, { title = it }, label = { Text("Goal") })
                OutlinedTextField(target, { target = it }, label = { Text("Target") })
                OutlinedTextField(unit, { unit = it }, label = { Text("Unit") })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        metric,
                        title,
                        target.toDoubleOrNull() ?: 0.0,
                        unit,
                        GoalDirection.AT_LEAST,
                        when (metric) {
                            GoalMetric.VOLUME_ARROWS,
                            GoalMetric.TRAINING_SESSIONS -> GoalAggregation.SUM
                            GoalMetric.ROUND_TOTAL -> GoalAggregation.MAX
                            else -> GoalAggregation.LATEST
                        },
                    )
                },
                enabled = target.toDoubleOrNull() != null,
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PlanDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int?, String, String?) -> Unit,
) {
    var t by rememberSaveable { mutableStateOf("") }
    var p by rememberSaveable { mutableStateOf("BUILD") }
    var f by rememberSaveable { mutableStateOf("") }
    var w by rememberSaveable { mutableStateOf("") }
    var r by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Training plan") },
        text = {
            Column {
                OutlinedTextField(t, { t = it }, label = { Text("Title") })
                OutlinedTextField(p, { p = it }, label = { Text("Phase") })
                OutlinedTextField(f, { f = it }, label = { Text("Focus") })
                OutlinedTextField(
                    w,
                    { w = it.filter(Char::isDigit) },
                    label = { Text("Arrows/week") },
                )
                OutlinedTextField(r, { r = it }, label = { Text("Recovery notes") })
            }
        },
        confirmButton = {
            Button(onClick = { onSave(t, p, f, w.toIntOrNull(), "MIXED", r) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun InterventionDialog(onDismiss: () -> Unit, onSave: (String, String, String?) -> Unit) {
    var k by rememberSaveable { mutableStateOf("EQUIPMENT") }
    var t by rememberSaveable { mutableStateOf("") }
    var n by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record change") },
        text = {
            Column {
                OutlinedTextField(k, { k = it }, label = { Text("Kind") })
                OutlinedTextField(t, { t = it }, label = { Text("Title") })
                OutlinedTextField(n, { n = it }, label = { Text("Note") })
            }
        },
        confirmButton = { Button(onClick = { onSave(k, t, n) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
