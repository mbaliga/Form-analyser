package xyz.mdhv.formanalyser.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.util.Date
import xyz.mdhv.formanalyser.app.domain.*
import xyz.mdhv.formanalyser.app.ui.components.*
import xyz.mdhv.formanalyser.app.ui.theme.*
import xyz.mdhv.formanalyser.body.BodyFace

private val PAIN_TAGS = listOf("sharp", "dull", "ache", "tingling", "stiff")

@Composable
/** Body tab (Phase 3 §D): Today · History · Injuries · Physio over the 52-region atlas. */
fun BodyScreen(vm: BodyViewModel, onEditInjury: (String?) -> Unit, onEditPlan: (String?) -> Unit) {
    val contextVm: BodyContextViewModel = viewModel()
    LaunchedEffect(Unit) {
        vm.load()
        contextVm.load()
    }
    val bodyContext by contextVm.state.collectAsState()
    val painToday by vm.painToday.collectAsState()
    val painWeeks by vm.painWeeks.collectAsState()
    val injuries by vm.injuries.collectAsState()
    val plans by vm.plans.collectAsState()
    val planExercises by vm.planExercises.collectAsState()
    var face by rememberSaveable { mutableStateOf(BodyFace.BACK) }
    var view by rememberSaveable { mutableStateOf("Context") }
    var dialRegion by rememberSaveable { mutableStateOf<String?>(null) }
    var historyRegion by rememberSaveable { mutableStateOf<String?>(null) }
    var logPhysioPlan by rememberSaveable { mutableStateOf<String?>(null) }
    val activeInjuryRegions =
        injuries
            .filter { it.status == "ACTIVE" }
            .flatMap { JsonLists.decode(it.regionsJson) }
            .toSet()
    val injurySeverity =
        injuries
            .filter { it.status == "ACTIVE" }
            .flatMap { i -> JsonLists.decode(i.regionsJson).map { r -> r to i.severity } }
            .toMap()
    val physioRegions =
        plans
            .filter { it.endDate == null }
            .flatMap { JsonLists.decode(it.targetRegionsJson) }
            .toSet()
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Body", style = MaterialTheme.typography.headlineMedium, color = Hyle.OnBackground)
        HyleSegmented(
            listOf(BodyFace.FRONT, BodyFace.BACK),
            face,
            { it.name.lowercase().replaceFirstChar(Char::uppercase) },
        ) {
            face = it
        }
        HyleSegmented(listOf("Context", "Today", "History", "Injuries", "Physio"), view, { it }) {
            view = it
        }
        when (view) {
            "Context" ->
                BodyContextPanel(face, bodyContext) {
                    historyRegion = it
                    vm.loadRegionHistory(it)
                }
            "Today" -> {
                BodyAtlasCanvas(
                    face = face,
                    fills = painToday.mapValues { BodyEncodings.painColor(it.value) },
                    badges = painToday.filterValues { it > 0 }.mapValues { "${it.value}" },
                    hatched = physioRegions,
                    onTap = { dialRegion = it },
                    onLongPress = {
                        historyRegion = it
                        vm.loadRegionHistory(it)
                    },
                )
                Text(
                    "Tap a region to log pain · long-press for its history.",
                    color = Hyle.OnSurfaceDim,
                )
            }
            "History" -> {
                Text("Last 8 weeks — brighter = worse that week.", color = Hyle.OnSurfaceDim)
                painWeeks.forEachIndexed { i, w ->
                    Text("Week −${painWeeks.size-1-i}", color = Hyle.OnSurfaceDim)
                    BodyAtlasCanvas(
                        face = face,
                        fills = w.mapValues { BodyEncodings.painColor(it.value) },
                        badges = w.filterValues { it > 0 }.mapValues { "${it.value}" },
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
                        "${inj.status} · severity ${inj.severity}",
                        JsonLists.decode(inj.regionsJson).joinToString() +
                            (inj.resolvedDate?.let { " · resolved $it" } ?: ""),
                        onClick = { onEditInjury(inj.id) },
                    )
                }
                Button(onClick = { onEditInjury(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Log injury")
                }
            }
            "Physio" -> {
                BodyAtlasCanvas(face = face, hatched = physioRegions)
                plans.forEach { p ->
                    HyleListRow(
                        p.title,
                        "${planExercises[p.id]?.size?:0} exercises · ${JsonLists.decode(p.scheduleJson).joinToString()}",
                        onClick = { onEditPlan(p.id) },
                        trailing = {
                            OutlinedButton(onClick = { logPhysioPlan = p.id }) { Text("Log") }
                        },
                    )
                }
                Button(onClick = { onEditPlan(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text("New physio plan")
                }
            }
        }
    }
    dialRegion?.let { r ->
        PainDial(
            r,
            { intensity, tags ->
                vm.logPain(r, intensity, tags)
                dialRegion = null
            },
            { dialRegion = null },
        )
    }
    historyRegion?.let { r ->
        val history by vm.regionHistory.collectAsState()
        AlertDialog(
            onDismissRequest = { historyRegion = null },
            title = { Text(r) },
            text = {
                Column {
                    if (history.isEmpty()) Text("No pain logged here.")
                    history.take(10).forEach {
                        Text(
                            "${DateFormat.getDateInstance(DateFormat.SHORT).format(Date(it.ts))} — ${it.intensity}/10 ${JsonLists.decode(it.tagsJson).joinToString(",")}"
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { historyRegion = null }) { Text("Close") } },
        )
    }
    logPhysioPlan?.let { id ->
        val exercises = planExercises[id].orEmpty()
        var done by remember(id) { mutableStateOf(setOf<String>()) }
        AlertDialog(
            onDismissRequest = { logPhysioPlan = null },
            title = { Text("Physio session") },
            text = {
                Column {
                    exercises.forEach { ex ->
                        FilterChip(
                            ex.id in done,
                            { done = if (ex.id in done) done - ex.id else done + ex.id },
                            label = { Text(ex.name) },
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.logPhysioSession(id, done.toList(), null)
                        logPhysioPlan = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = { TextButton(onClick = { logPhysioPlan = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PainDial(region: String, onSave: (Int, List<String>) -> Unit, onDismiss: () -> Unit) {
    var intensity by rememberSaveable { mutableStateOf(5) }
    var tags by remember { mutableStateOf(setOf<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(region) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Intensity")
                HyleStepper(intensity, { intensity = it }, 0..10)
                PAIN_TAGS.chunked(3).forEach { row ->
                    Row {
                        row.forEach { t ->
                            FilterChip(
                                t in tags,
                                { tags = if (t in tags) tags - t else tags + t },
                                label = { Text(t) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(intensity, tags.toList()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
