package xyz.mdhv.formanalyser.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var regionPickerOpen by rememberSaveable { mutableStateOf(false) }
    var logPhysioPlan by rememberSaveable { mutableStateOf<String?>(null) }
    val bodyScrollState = rememberScrollState()
    LaunchedEffect(view) { bodyScrollState.animateScrollTo(0) }
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
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(bodyScrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Hyle.SurfaceDeep, Hyle.Surface, Hyle.SurfaceDeep),
                    ),
                )
                .border(1.dp, Hyle.HairlineStrong, RoundedCornerShape(24.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                "RECOVERY  /  PRIVATE TRAINING LOG",
                color = Hyle.RadiumGreen,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.2.sp,
            )
            Text("Body & recovery", style = MaterialTheme.typography.headlineSmall, color = Hyle.OnBackground)
            Text(
                "Your signals, in context — not a diagnosis.",
                style = MaterialTheme.typography.bodySmall,
                color = Hyle.OnSurfaceDim,
            )
        }
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
                Text(
                    "Tap the map, or choose a region by name.",
                    color = Hyle.OnSurfaceDim,
                )
                OutlinedButton(
                    onClick = { regionPickerOpen = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Choose region from list")
                }
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
                Text("Long-press a region to view its history.", color = Hyle.OnSurfaceDim)
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
                        subtitle = JsonLists.decode(inj.regionsJson).joinToString() +
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
                        subtitle = "${planExercises[p.id]?.size?:0} exercises · ${JsonLists.decode(p.scheduleJson).joinToString()}",
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
    if (regionPickerOpen) {
        BodyRegionPickerDialog(
            face = face,
            onSelect = { region ->
                regionPickerOpen = false
                dialRegion = region
            },
            onDismiss = { regionPickerOpen = false },
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
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "${DateFormat.getDateInstance(DateFormat.SHORT).format(Date(it.ts))} — ${it.intensity}/10 ${JsonLists.decode(it.tagsJson).joinToString(",")}",
                                modifier = Modifier.weight(1f),
                            )
                            // A mistyped intensity used to be permanent, and it goes on skewing the
                            // eight-week heat map and the region signals the coach reads.
                            TextButton(onClick = { vm.deletePainLog(it.id, r) }) {
                                Text("Delete", color = Hyle.Danger)
                            }
                        }
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
private fun BodyRegionPickerDialog(
    face: BodyFace,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val regions = xyz.mdhv.formanalyser.body.BodyAtlas.forFace(face)
        .map { it.id }
        .sortedBy(::bodyRegionDisplayName)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose body region") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                regions.forEach { region ->
                    TextButton(
                        onClick = { onSelect(region) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(bodyRegionDisplayName(region), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

internal fun bodyRegionDisplayName(regionId: String): String {
    val side = when {
        regionId.endsWith("_l") -> "left"
        regionId.endsWith("_r") -> "right"
        else -> null
    }
    val stem = regionId.removeSuffix("_l").removeSuffix("_r")
    val name = when (stem) {
        "neck_ant" -> "Front of neck"
        "neck_post" -> "Back of neck"
        "delt_ant" -> "Anterior shoulder"
        "delt_lat" -> "Lateral shoulder"
        "delt_post" -> "Posterior shoulder"
        "pec" -> "Pectoral"
        "biceps" -> "Biceps"
        "forearm_flex" -> "Forearm flexors"
        "forearm_ext" -> "Forearm extensors"
        "hand" -> "Hand"
        "abs_upper" -> "Upper abdominals"
        "abs_lower" -> "Lower abdominals"
        "oblique" -> "Obliques"
        "hip" -> "Hip"
        "quad" -> "Quadriceps"
        "knee" -> "Knee"
        "shin" -> "Shin"
        "trap_upper" -> "Upper trapezius"
        "trap_mid" -> "Middle trapezius"
        "trap_lower" -> "Lower trapezius"
        "rhomboid" -> "Rhomboid"
        "rotator_cuff" -> "Rotator cuff"
        "lat" -> "Latissimus"
        "erector" -> "Spinal erector"
        "triceps" -> "Triceps"
        "glute" -> "Glute"
        "hamstring" -> "Hamstring"
        "calf" -> "Calf"
        else -> error("Unmapped body region: $regionId")
    }
    return if (side == null) name else "$name, $side"
}

@Composable
private fun PainDial(region: String, onSave: (Int, List<String>) -> Unit, onDismiss: () -> Unit) {
    var intensity by rememberSaveable { mutableIntStateOf(5) }
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
