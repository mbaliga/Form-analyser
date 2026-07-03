package xyz.mdhv.formanalyser.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import xyz.mdhv.formanalyser.app.domain.BodyViewModel
import xyz.mdhv.formanalyser.app.domain.JsonLists
import xyz.mdhv.formanalyser.app.ui.components.BodyAtlasCanvas
import xyz.mdhv.formanalyser.app.ui.components.BodyEncodings
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.app.ui.theme.HyleListRow
import xyz.mdhv.formanalyser.app.ui.theme.HyleSegmented
import xyz.mdhv.formanalyser.app.ui.theme.HyleStepper
import xyz.mdhv.formanalyser.body.BodyFace
import java.text.DateFormat
import java.util.Date

private val PAIN_TAGS = listOf("sharp", "dull", "ache", "tingling", "stiff")

/** Body tab (Phase 3 §D): Today · History · Injuries · Physio over the 52-region atlas. */
@Composable
fun BodyScreen(vm: BodyViewModel, onEditInjury: (String?) -> Unit, onEditPlan: (String?) -> Unit) {
    LaunchedEffect(Unit) { vm.load() }
    val painToday by vm.painToday.collectAsState()
    val painWeeks by vm.painWeeks.collectAsState()
    val injuries by vm.injuries.collectAsState()
    val plans by vm.plans.collectAsState()
    val planExercises by vm.planExercises.collectAsState()

    var face by remember { mutableStateOf(BodyFace.BACK) }
    var view by remember { mutableStateOf("Today") }
    var dialRegion by remember { mutableStateOf<String?>(null) }
    var historyRegion by remember { mutableStateOf<String?>(null) }
    var logPhysioPlan by remember { mutableStateOf<String?>(null) }

    val activeInjuryRegions = injuries.filter { it.status == "ACTIVE" }
        .flatMap { JsonLists.decode(it.regionsJson) }.toSet()
    val injurySeverity = injuries.filter { it.status == "ACTIVE" }
        .flatMap { i -> JsonLists.decode(i.regionsJson).map { r -> r to i.severity } }
        .toMap()
    val physioRegions = plans.filter { it.endDate == null }
        .flatMap { JsonLists.decode(it.targetRegionsJson) }.toSet()

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Body", style = MaterialTheme.typography.headlineMedium, color = Hyle.OnBackground)
        HyleSegmented(listOf(BodyFace.FRONT, BodyFace.BACK), face, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { face = it }
        HyleSegmented(listOf("Today", "History", "Injuries", "Physio"), view, { it }) { view = it }

        when (view) {
            "Today" -> {
                BodyAtlasCanvas(
                    face = face,
                    fills = painToday.mapValues { BodyEncodings.painColor(it.value) },
                    badges = painToday.filterValues { it > 0 }.mapValues { "${it.value}" },
                    hatched = physioRegions,
                    onTap = { dialRegion = it },
                    onLongPress = { historyRegion = it; vm.loadRegionHistory(it) },
                )
                Text("Tap a region to log pain · long-press for its history.", color = Hyle.OnSurfaceDim)
            }
            "History" -> {
                Text("Last 8 weeks — brighter = worse that week.", color = Hyle.OnSurfaceDim)
                painWeeks.forEachIndexed { i, week ->
                    Text("Week −${painWeeks.size - 1 - i}", color = Hyle.OnSurfaceDim, style = MaterialTheme.typography.labelMedium)
                    BodyAtlasCanvas(
                        face = face,
                        fills = week.mapValues { BodyEncodings.painColor(it.value) },
                        badges = week.filterValues { it > 0 }.mapValues { "${it.value}" },
                    )
                }
            }
            "Injuries" -> {
                BodyAtlasCanvas(
                    face = face,
                    dashed = activeInjuryRegions,
                    badges = injurySeverity.mapValues { "${it.value}" },
                )
                injuries.forEach { inj ->
                    HyleListRow(
                        title = "${inj.status} · severity ${inj.severity}",
                        subtitle = JsonLists.decode(inj.regionsJson).joinToString() +
                            (inj.resolvedDate?.let { " · resolved $it" } ?: ""),
                        onClick = { onEditInjury(inj.id) },
                    )
                }
                Button(onClick = { onEditInjury(null) }, modifier = Modifier.fillMaxWidth()) { Text("Log injury") }
            }
            "Physio" -> {
                BodyAtlasCanvas(face = face, hatched = physioRegions)
                plans.forEach { plan ->
                    HyleListRow(
                        title = plan.title,
                        subtitle = "${planExercises[plan.id]?.size ?: 0} exercises · ${JsonLists.decode(plan.scheduleJson).joinToString()}",
                        onClick = { onEditPlan(plan.id) },
                        trailing = { OutlinedButton(onClick = { logPhysioPlan = plan.id }) { Text("Log") } },
                    )
                }
                Button(onClick = { onEditPlan(null) }, modifier = Modifier.fillMaxWidth()) { Text("New physio plan") }
            }
        }
    }

    dialRegion?.let { region ->
        PainDial(region = region, onSave = { intensity, tags ->
            vm.logPain(region, intensity, tags)
            dialRegion = null
        }, onDismiss = { dialRegion = null })
    }

    historyRegion?.let { region ->
        val history by vm.regionHistory.collectAsState()
        AlertDialog(
            onDismissRequest = { historyRegion = null },
            title = { Text(region) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (history.isEmpty()) Text("No pain logged here.", color = Hyle.OnSurfaceDim)
                    history.take(10).forEach {
                        Text(
                            "${DateFormat.getDateInstance(DateFormat.SHORT).format(Date(it.ts))} — ${it.intensity}/10 " +
                                JsonLists.decode(it.tagsJson).joinToString(","),
                            color = Hyle.OnBackground,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { historyRegion = null }) { Text("Close") } },
        )
    }

    logPhysioPlan?.let { planId ->
        val exercises = planExercises[planId].orEmpty()
        var done by remember(planId) { mutableStateOf(setOf<String>()) }
        AlertDialog(
            onDismissRequest = { logPhysioPlan = null },
            title = { Text("Physio session") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    exercises.forEach { ex ->
                        FilterChip(
                            selected = ex.id in done,
                            onClick = { done = if (ex.id in done) done - ex.id else done + ex.id },
                            label = { Text("${ex.name} · ${ex.sets}×${ex.reps ?: "${ex.holdS}s"}") },
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { vm.logPhysioSession(planId, done.toList(), null); logPhysioPlan = null }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { logPhysioPlan = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PainDial(region: String, onSave: (Int, List<String>) -> Unit, onDismiss: () -> Unit) {
    var intensity by remember { mutableStateOf(5) }
    var tags by remember { mutableStateOf(setOf<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(region) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Intensity", color = Hyle.OnSurfaceDim)
                HyleStepper(intensity, { intensity = it }, 0..10)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PAIN_TAGS.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { t ->
                                    FilterChip(selected = t in tags, onClick = { tags = if (t in tags) tags - t else tags + t }, label = { Text(t) })
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(intensity, tags.toList()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
