package xyz.mdhv.formanalyser.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.mdhv.formanalyser.app.domain.SessionViewModel
import xyz.mdhv.formanalyser.app.domain.TrainingContextViewModel
import xyz.mdhv.formanalyser.app.ui.theme.*
import xyz.mdhv.formanalyser.athlete.SessionDefaults
import xyz.mdhv.formanalyser.model.Handedness
import xyz.mdhv.formanalyser.scoring.RoundPack

@Composable
fun TrainSetupScreen(vm: SessionViewModel, onStarted: () -> Unit, onManageRigs: () -> Unit) {
    val contextVm: TrainingContextViewModel = viewModel()
    LaunchedEffect(Unit) {
        vm.refreshActiveRig()
        contextVm.load()
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { xyz.mdhv.formanalyser.app.data.AppPrefs(context) }
    val useChips by prefs.sorenessChips.collectAsState(initial = false)
    val handedness by vm.athleteHandedness.collectAsState()
    val activeRig by vm.activeRig.collectAsState()
    val smart by contextVm.defaults.collectAsState()
    var applied by rememberSaveable { mutableStateOf(false) }
    var distance by rememberSaveable { mutableStateOf("") }
    var targetFace by rememberSaveable { mutableStateOf("") }
    var arrows by rememberSaveable { mutableStateOf("") }
    var venue by rememberSaveable { mutableStateOf("") }
    var intent by rememberSaveable { mutableStateOf("") }
    var selectedRound by rememberSaveable { mutableStateOf<String?>(null) }
    var pinSetup by rememberSaveable { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var override by rememberSaveable { mutableStateOf<Handedness?>(null) }
    var preGate by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(smart) {
        val d = smart ?: return@LaunchedEffect
        if (!applied) {
            distance = d.distanceMeters?.toString().orEmpty()
            targetFace = d.targetFaceCm?.toString().orEmpty()
            arrows = d.arrowCount?.toString().orEmpty()
            venue = d.venue.orEmpty()
            intent = d.trainingIntent.orEmpty()
            selectedRound = d.roundId
            applied = true
        }
    }
    fun currentDefaults() =
        SessionDefaults(
            "olympic_recurve",
            activeRig?.id,
            venue.trim().takeIf { it.isNotEmpty() },
            distance.toIntOrNull(),
            targetFace.toIntOrNull(),
            arrows.toIntOrNull(),
            selectedRound,
            intent.trim().takeIf { it.isNotEmpty() },
        )
    if (preGate)
        PreCheckinSheet(
            handedness = override ?: handedness,
            useChips = useChips,
            onStart = { pre ->
                preGate = false
                val d = currentDefaults()
                contextVm.remember(d, pinSetup)
                if (vm.startSession(d.distanceMeters ?: 18, override, pre, d)) onStarted()
            },
            onDismiss = { preGate = false },
        )
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RangeSetupPanel()
            HyleListRow(
                "Active rig",
                subtitle = activeRig?.name ?: "No rig — tap to add",
                onClick = onManageRigs,
            )
            HyleSectionHeader("Round")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RoundPack.builtIns
                    .filter { it.scoringKind.name != "SET_MATCH" }
                    .forEach { r ->
                        FilterChip(
                            selected = selectedRound == r.id,
                            onClick = {
                                selectedRound = if (selectedRound == r.id) null else r.id
                                if (selectedRound != null) {
                                    distance = r.distanceMeters.toString()
                                    targetFace = r.targetFaceCm.toString()
                                    arrows = r.maxArrows.toString()
                                }
                            },
                            label = { Text(r.name, maxLines = 1) },
                        )
                    }
            }
            HyleSectionHeader("Session parameters")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Hyle.Surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, Hyle.Hairline),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            distance,
                            { distance = it.filter(Char::isDigit) },
                            label = { Text("Distance m") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            targetFace,
                            { targetFace = it.filter(Char::isDigit) },
                            label = { Text("Face cm") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        arrows,
                        { arrows = it.filter(Char::isDigit) },
                        label = { Text("Planned arrows") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            HyleSectionHeader("Practice details")
            OutlinedTextField(
                venue,
                { venue = it },
                label = { Text("Venue") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                intent,
                { intent = it },
                label = { Text("Training intent") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Pin this setup", color = Hyle.OnBackground)
                    Text(
                        "Pinned values win over recent history",
                        color = Hyle.OnSurfaceDim,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(pinSetup, { pinSetup = it })
            }
            TextButton(onClick = { advanced = !advanced }) {
                Text(if (advanced) "Hide handedness" else "Session handedness")
            }
            if (advanced) {
                HyleSegmented(
                    options = listOf<Handedness?>(null, Handedness.RH, Handedness.LH),
                    selected = override,
                    label = { it?.name ?: "Default" },
                    onSelect = { override = it },
                )
            }
            Text(
                "Side-on camera · full athlete in frame. Autofill is a starting point; every value stays editable.",
                color = Hyle.OnSurfaceDim,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Column(
            Modifier.fillMaxWidth().background(Hyle.Background)
                .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Button(
                onClick = { preGate = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                enabled = distance.toIntOrNull()?.let { it > 0 } == true,
            ) {
                Text("Start recording")
            }
        }
    }
}

@Composable
private fun RangeSetupPanel() {
    Box(
        Modifier.fillMaxWidth().height(142.dp).clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xff1b1825), Color(0xff101411), Color(0xff101015)))
            )
            .border(1.dp, Hyle.HairlineStrong, RoundedCornerShape(26.dp)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * .86f, size.height * .5f)
            listOf(.78f, .58f, .38f, .18f).forEachIndexed { index, fraction ->
                drawCircle(
                    color = if (index == 3) Hyle.RadiumGreen.copy(alpha = .25f)
                    else Hyle.Accent.copy(alpha = .17f - index * .02f),
                    radius = size.height * fraction,
                    center = center,
                    style = Stroke(if (index == 3) 2.5f else 1.5f),
                )
            }
            drawLine(
                Hyle.OnBackground.copy(alpha = .32f),
                Offset(center.x - 68f, center.y + 55f),
                Offset(center.x + 32f, center.y - 42f),
                2f,
            )
        }
        Column(
            Modifier.fillMaxHeight().fillMaxWidth(.75f).padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("RANGE SETUP  /  OLYMPIC RECURVE", color = Hyle.RadiumGreen, style = MaterialTheme.typography.labelSmall)
                Text("New session", color = Hyle.OnBackground, style = MaterialTheme.typography.headlineMedium)
            }
            Text("Set your distance, then focus on the shot.", color = Hyle.OnSurfaceDim, style = MaterialTheme.typography.bodySmall)
        }
    }
}
