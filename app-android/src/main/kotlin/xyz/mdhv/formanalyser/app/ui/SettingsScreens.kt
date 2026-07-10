package xyz.mdhv.formanalyser.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xyz.mdhv.formanalyser.app.domain.RigsViewModel
import xyz.mdhv.formanalyser.app.domain.SettingsViewModel
import xyz.mdhv.formanalyser.app.domain.Tuning
import xyz.mdhv.formanalyser.app.domain.TuningV0
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.app.ui.theme.HyleListRow
import xyz.mdhv.formanalyser.app.ui.theme.HyleSectionHeader
import xyz.mdhv.formanalyser.app.ui.theme.HyleSegmented
import xyz.mdhv.formanalyser.app.ui.theme.HyleStepper
import xyz.mdhv.formanalyser.model.BowType
import xyz.mdhv.formanalyser.model.Handedness

private fun col() = Modifier.fillMaxSize().padding(16.dp)

@Composable
fun SettingsRootScreen(
    onProfile: () -> Unit,
    onRigs: () -> Unit,
    onCapture: () -> Unit,
    onWellness: () -> Unit,
    onStreak: () -> Unit,
    onCycle: () -> Unit,
    onMedication: () -> Unit,
    onAppearance: () -> Unit,
    onAi: () -> Unit,
    onData: () -> Unit,
    onAbout: () -> Unit,
) {
    Column(col().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = Hyle.OnBackground)
        HyleListRow("Profile & identity", onClick = onProfile)
        HyleListRow("Equipment (rigs)", onClick = onRigs)
        HyleListRow("Capture", onClick = onCapture)
        HyleListRow("Wellness & check-ins", onClick = onWellness)
        HyleListRow("Streak & rest", onClick = onStreak)
        HyleListRow("Cycle tracking", onClick = onCycle)
        HyleListRow("Medication", onClick = onMedication)
        HyleListRow("Appearance", onClick = onAppearance)
        HyleListRow("AI coach", onClick = onAi)
        HyleListRow("Data", onClick = onData)
        HyleListRow("About", onClick = onAbout)
    }
}

/** Settings → Wellness & check-ins (Phase 3 adds the chips-vs-atlas soreness toggle). */
@Composable
fun SettingsWellnessScreen(prefsOwner: SettingsViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { xyz.mdhv.formanalyser.app.data.AppPrefs(context) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val chips by prefs.sorenessChips.collectAsState(initial = false)
    Column(col(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Wellness & check-ins", style = MaterialTheme.typography.headlineMedium, color = Hyle.OnBackground)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Use chips for soreness", color = Hyle.OnBackground)
            Switch(checked = chips, onCheckedChange = { v -> scope.launch { prefs.setSorenessChips(v) } })
        }
        Text(
            if (chips) "Soreness is picked from named chips (accessibility fallback)."
            else "Soreness is tapped on the body map in the pre-session check-in.",
            color = Hyle.OnSurfaceDim,
        )
    }
}

@Composable
fun SettingsProfileScreen(vm: RigsViewModel) {
    LaunchedEffect(Unit) { vm.load() }
    val athlete by vm.athlete.collectAsState()
    val a = athlete
    if (a == null) { Text("Loading…", color = Hyle.OnSurfaceDim, modifier = col()); return }

    var name by remember(a.id) { mutableStateOf(a.displayName) }
    var club by remember(a.id) { mutableStateOf(a.club ?: "") }
    var handed by remember(a.id) { mutableStateOf(Handedness.fromStorage(a.handedness)) }
    var drawSet by remember(a.id) { mutableStateOf(a.drawLengthMm != null) }
    var drawMm by remember(a.id) { mutableStateOf(a.drawLengthMm ?: 700) }

    Column(col().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Profile & identity", style = MaterialTheme.typography.headlineMedium, color = Hyle.OnBackground)
        OutlinedTextField(name, { name = it.take(40) }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(club, { club = it.take(40) }, label = { Text("Club") }, modifier = Modifier.fillMaxWidth())
        HyleSectionHeader("Handedness")
        HyleSegmented(listOf(Handedness.RH, Handedness.LH), handed, { it.name }) { handed = it }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = drawSet, onCheckedChange = { drawSet = it })
            Text(if (drawSet) "Draw length set" else "Draw length not set", color = Hyle.OnSurfaceDim)
        }
        if (drawSet) HyleStepper(drawMm, { drawMm = it }, 500..900, step = 5, suffix = " mm")
        Button(
            onClick = { vm.updateProfile(name, club, handed, if (drawSet) drawMm else null) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }
    }
}

@Composable
fun SettingsRigsScreen(vm: RigsViewModel, onEdit: (String?) -> Unit) {
    LaunchedEffect(Unit) { vm.load() }
    val rigs by vm.rigs.collectAsState()
    var note by remember { mutableStateOf<String?>(null) }

    Column(col(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Rigs", style = MaterialTheme.typography.headlineMedium, color = Hyle.OnBackground)
        note?.let { Text(it, color = Hyle.Danger) }
        rigs.forEach { rig ->
            HyleListRow(
                title = rig.name + if (rig.active) "  • ACTIVE" else "",
                subtitle = rig.bowType.lowercase().replaceFirstChar(Char::uppercase),
                onClick = { onEdit(rig.id) },
                trailing = {
                    if (!rig.active) OutlinedButton(onClick = { vm.activate(rig.id) }) { Text("Activate") }
                },
            )
        }
        Button(onClick = { onEdit(null) }, modifier = Modifier.fillMaxWidth()) { Text("Add rig") }
    }
}

@Composable
fun RigEditScreen(vm: RigsViewModel, rigId: String?, onDone: () -> Unit) {
    LaunchedEffect(Unit) { vm.load() }
    val rigs by vm.rigs.collectAsState()
    val existing = rigs.firstOrNull { it.id == rigId }
    val t = remember(existing?.id) { Tuning.parse(existing?.tuningJson) }

    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "My bow") }
    var bow by remember(existing?.id) { mutableStateOf(BowType.fromStorage(existing?.bowType)) }
    var riser by remember(existing?.id) { mutableStateOf((t.riserLengthIn ?: 25.0).toInt()) }
    var marked by remember(existing?.id) { mutableStateOf(t.markedLbs?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    var otf by remember(existing?.id) { mutableStateOf(t.otfLbs?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    var note by remember { mutableStateOf<String?>(null) }
    // Phase 4: the full tuning spec (advanced fields). Basics stay the source of truth in the fields
    // above and are re-stamped onto this value at save time; here we only carry the advanced setup.
    var adv by remember(existing?.id) { mutableStateOf(Tuning.parseFull(existing?.tuningJson)) }

    Column(col().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (existing == null) "Add rig" else "Edit rig", style = MaterialTheme.typography.headlineMedium, color = Hyle.OnBackground)
        OutlinedTextField(name, { name = it.take(40) }, label = { Text("Rig name") }, modifier = Modifier.fillMaxWidth())
        HyleSegmented(listOf(BowType.RECURVE, BowType.BAREBOW, BowType.COMPOUND), bow, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { bow = it }
        if (bow != BowType.COMPOUND) {
            HyleSectionHeader("Riser length")
            HyleSegmented(listOf(23, 25, 27), riser, { "$it″" }) { riser = it }
        }
        OutlinedTextField(marked, { marked = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text(if (bow == BowType.COMPOUND) "Peak weight (lbs)" else "Marked poundage (lbs)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(otf, { otf = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("On-the-fingers (lbs, measured)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())

        // Phase 4 advanced tuning (collapsible). Live effective poundage (measured OTF, else marked)
        // and the estimated bow length feed the FOC/GPP readouts and the brace-band warnings.
        AdvancedTuningSection(
            initial = adv,
            drawLengthMm = null,
            effectiveLbs = otf.toDoubleOrNull() ?: marked.toDoubleOrNull(),
            bowLengthIn = Tuning.estimatedBowLengthIn(if (bow == BowType.COMPOUND) null else riser.toDouble(), bow),
            onChange = { adv = it },
        )

        note?.let { Text(it, color = Hyle.Danger) }
        Button(
            onClick = {
                // Re-stamp the live basics onto the advanced spec and persist the full tuning. RigTuning
                // keeps marked/riser/otf top-level, so the legacy V0 poundage path keeps resolving.
                val full = adv.copy(
                    markedLbs = marked.toDoubleOrNull(),
                    riserLengthIn = if (bow == BowType.COMPOUND) null else riser.toDouble(),
                    otfLbs = otf.toDoubleOrNull(),
                )
                vm.saveRig(existing?.id, name, bow, Tuning.encodeFull(full))
                onDone()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }
        if (existing != null) {
            OutlinedButton(
                onClick = { vm.delete(existing) { ok -> if (ok) onDone() else note = "Can't delete the active or last rig — activate another first." } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Delete rig") }
        }
    }
}

@Composable
fun SettingsCaptureScreen(vm: SettingsViewModel) {
    val keep by vm.keepRawVideo.collectAsState(initial = false)
    Column(col(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Capture", style = MaterialTheme.typography.headlineMedium, color = Hyle.OnBackground)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Keep raw video", color = Hyle.OnBackground)
            Switch(checked = keep, onCheckedChange = { vm.setKeepRawVideo(it) })
        }
        Text("Off by default. Pose analysis runs on-device; raw video isn't retained.", color = Hyle.OnSurfaceDim)
    }
}

@Composable
fun SettingsAppearanceScreen(vm: SettingsViewModel) {
    val reduce by vm.reduceMotion.collectAsState(initial = false)
    val haptic by vm.hapticStrength.collectAsState(initial = "MED")
    val glow by vm.glowIntensity.collectAsState(initial = 100)
    Column(col(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Appearance", style = MaterialTheme.typography.headlineMedium, color = Hyle.OnBackground)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Reduce motion", color = Hyle.OnBackground)
            Switch(checked = reduce, onCheckedChange = { vm.setReduceMotion(it) })
        }
        HyleSectionHeader("Haptics")
        HyleSegmented(listOf("OFF", "LOW", "MED", "HIGH"), haptic, { it.lowercase().replaceFirstChar(Char::uppercase) }) { vm.setHapticStrength(it) }
        HyleSectionHeader("Glow intensity")
        Slider(value = glow / 100f, onValueChange = { vm.setGlowIntensity((it * 100).toInt()) })
        Text("$glow%", color = Hyle.OnSurfaceDim)
    }
}

@Composable
fun SettingsDataScreen(vm: SettingsViewModel, onWiped: () -> Unit, onExport: () -> Unit) {
    var arming by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val vaultInfo by androidx.compose.runtime.produceState<String?>(initialValue = null) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val repo = xyz.mdhv.formanalyser.app.data.Repository(context)
                val a = repo.currentAthlete() ?: return@runCatching null
                val n = repo.body.documentCount(a.id)
                val kb = repo.body.documentBytes(a.id) / 1024
                "Document vault: $n file(s) · $kb KB (encrypted)"
            }.getOrNull()
        }
    }
    Column(col(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Data", style = MaterialTheme.typography.headlineMedium, color = Hyle.OnBackground)
        vaultInfo?.let { Text(it, color = Hyle.OnSurfaceDim) }
        HyleListRow(
            title = "Export data (.crocbak)",
            subtitle = "Choose exactly what leaves this device",
            onClick = onExport,
        )
        Text(
            "Everything runs on this device — nothing is stored anywhere else, so a wipe has no undo.",
            color = Hyle.OnSurfaceDim,
        )
        if (!arming) {
            OutlinedButton(onClick = { arming = true }, modifier = Modifier.fillMaxWidth()) { Text("Wipe everything") }
        } else {
            OutlinedTextField(confirm, { confirm = it }, label = { Text("Type WIPE to confirm") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { vm.wipe(onWiped) },
                enabled = confirm.trim() == "WIPE",
                colors = ButtonDefaults.buttonColors(containerColor = Hyle.Danger),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Erase all data") }
        }
    }
}

@Composable
fun SettingsAboutScreen() {
    Column(col(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("About", style = MaterialTheme.typography.headlineMedium, color = Hyle.OnBackground)
        Text("Crocodyl 0.1.0", color = Hyle.OnBackground)
        Text(
            "Crocodyl runs entirely on this device. Sessions, poses, scores, and your profile never " +
                "leave it. This build makes zero network calls — there's nothing to opt out of.",
            color = Hyle.OnSurfaceDim,
        )
    }
}
