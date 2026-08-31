package xyz.mdhv.formanalyser.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.mdhv.formanalyser.app.domain.ProgressViewModel
import xyz.mdhv.formanalyser.app.ui.components.TimeTrendLine
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.athlete.*

@Composable
fun ProgressScreen(vm: ProgressViewModel) {
    LaunchedEffect(Unit) { vm.load() }
    val s by vm.state.collectAsState()
    var goalOpen by remember { mutableStateOf(false) }
    var planOpen by remember { mutableStateOf(false) }
    var interventionOpen by remember { mutableStateOf(false) }
    if (s.loading) {
        Box(Modifier.fillMaxSize().padding(24.dp)) { CircularProgressIndicator() }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Progress",
                style = MaterialTheme.typography.headlineMedium,
                color = Hyle.OnBackground,
            )
            Text(
                "Understand what changes performance · local evidence only",
                color = Hyle.OnSurfaceDim,
            )
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
        if (s.pbs.isNotEmpty()) {
            item { Text("Personal bests", style = MaterialTheme.typography.titleLarge) }
            items(s.pbs) { pb ->
                ListItem(
                    headlineContent = { Text(pb.roundName) },
                    trailingContent = { Text("${pb.score}/${pb.max}") },
                )
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
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Goals", style = MaterialTheme.typography.titleLarge)
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
                Text("Training plans", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { planOpen = true }) { Text("+ Plan") }
            }
        }
        items(s.plans, key = { it.id }) { p ->
            ListItem(
                headlineContent = { Text(p.title) },
                supportingContent = {
                    Text(
                        "${p.phase} · ${p.focus}" +
                            (p.weeklyArrowTarget?.let { " · $it arrows/week" } ?: "")
                    )
                },
            )
        }
        item {
            Text("Equipment context", style = MaterialTheme.typography.titleLarge)
            Text(
                "Descriptive only: different rounds, weather and training states are confounders. This is not causal equipment attribution.",
                color = Hyle.OnSurfaceDim,
            )
        }
        items(s.equipmentContext, key = { it.rigId }) { r ->
            ListItem(
                headlineContent = { Text(r.rigName) },
                supportingContent = { Text("${r.rounds} complete rounds") },
                trailingContent = { Text("${"%.1f".format(r.averagePercent)}%") },
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Interventions", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { interventionOpen = true }) { Text("+ Change") }
            }
        }
        items(s.interventions, key = { it.id }) { i ->
            ListItem(
                headlineContent = { Text(i.title) },
                supportingContent = { Text(i.kind + (i.note?.let { " · $it" } ?: "")) },
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
private fun MetricCard(label: String, value: String, m: Modifier) {
    Card(m) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, color = Hyle.OnSurfaceDim)
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
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
    var metric by remember { mutableStateOf(GoalMetric.ROUND_TOTAL) }
    var title by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("pts") }
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
    var t by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("BUILD") }
    var f by remember { mutableStateOf("") }
    var w by remember { mutableStateOf("") }
    var r by remember { mutableStateOf("") }
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
    var k by remember { mutableStateOf("EQUIPMENT") }
    var t by remember { mutableStateOf("") }
    var n by remember { mutableStateOf("") }
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
