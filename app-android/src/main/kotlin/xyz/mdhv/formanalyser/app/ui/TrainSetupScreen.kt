package xyz.mdhv.formanalyser.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    var applied by remember { mutableStateOf(false) }
    var distance by remember { mutableStateOf("") }
    var targetFace by remember { mutableStateOf("") }
    var arrows by remember { mutableStateOf("") }
    var venue by remember { mutableStateOf("") }
    var intent by remember { mutableStateOf("") }
    var selectedRound by remember { mutableStateOf<String?>(null) }
    var pinSetup by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var override by remember { mutableStateOf<Handedness?>(null) }
    var preGate by remember { mutableStateOf(false) }
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
                vm.startSession(d.distanceMeters ?: 18, override, pre, d)
                onStarted()
            },
            onDismiss = { preGate = false },
        )
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "New session",
            style = MaterialTheme.typography.headlineMedium,
            color = Hyle.OnBackground,
        )
        Text("Olympic Recurve · local autofill", color = Hyle.OnSurfaceDim)
        HyleListRow("Rig", activeRig?.name ?: "No rig — tap to add", onClick = onManageRigs)
        Text("Round", style = MaterialTheme.typography.titleMedium, color = Hyle.OnBackground)
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
                    label = { Text(r.name) },
                )
            }
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
        OutlinedTextField(
            venue,
            { venue = it },
            label = { Text("Venue") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            intent,
            { intent = it },
            label = { Text("Training intent") },
            modifier = Modifier.fillMaxWidth(),
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
            Text(if (advanced) "Hide advanced" else "Advanced")
        }
        if (advanced) {
            Text("Handedness (this session)", color = Hyle.OnSurfaceDim)
            HyleSegmented(
                options = listOf<Handedness?>(null, Handedness.RH, Handedness.LH),
                selected = override,
                label = { it?.name ?: "Default" },
                onSelect = { override = it },
            )
        }
        Button(
            onClick = { preGate = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = distance.toIntOrNull()?.let { it > 0 } == true,
        ) {
            Text("Start recording")
        }
        Text(
            "Set the phone side-on and frame the whole athlete. Autofill is only a starting point; every value remains editable.",
            color = Hyle.OnSurfaceDim,
        )
    }
}
