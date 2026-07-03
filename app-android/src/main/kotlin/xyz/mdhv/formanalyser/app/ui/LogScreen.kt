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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.mdhv.formanalyser.app.domain.WellnessViewModel
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.app.ui.theme.HyleListRow
import xyz.mdhv.formanalyser.app.ui.theme.HyleSegmented
import xyz.mdhv.formanalyser.app.ui.theme.HyleStepper
import java.time.LocalDate

private enum class LogType(val label: String, val privateClass: Boolean = false) {
    CHECKIN("Wellness check-in"),
    REST("Rest day"),
    MOOD("Mood", privateClass = true),
    LIFE("Life event", privateClass = true),
    CYCLE("Cycle", privateClass = true),
    MEDICATION("Medication"),
    EVENT("Event"),
}

/** The single "+ Log" surface (Phase 2 §D). One sheet per type, one decision per row. */
@Composable
fun LogScreen(vm: WellnessViewModel, cycleEnabled: Boolean, onDone: () -> Unit) {
    LaunchedEffect(Unit) { vm.load() }
    var picked by remember { mutableStateOf<LogType?>(null) }
    val hiatusOffer by vm.hiatusOfferFor.collectAsState()

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Log", style = MaterialTheme.typography.headlineMedium, color = Hyle.OnBackground)
        if (picked == null) {
            LogType.entries
                .filter { it != LogType.CYCLE || cycleEnabled }
                .forEach { t ->
                    HyleListRow(
                        title = t.label,
                        subtitle = if (t.privateClass) "private — never leaves this device by default" else null,
                        onClick = { picked = t },
                    )
                }
        } else {
            when (picked) {
                LogType.CHECKIN -> CheckinForm(vm, onDone)
                LogType.REST -> RestForm(vm, onDone)
                LogType.MOOD -> MoodForm(vm, onDone)
                LogType.LIFE -> LifeEventForm(vm) { impact -> if (impact < 3) onDone() /* impact 3 stays for the hiatus offer */ }
                LogType.CYCLE -> CycleForm(vm, onDone)
                LogType.MEDICATION -> MedicationForm(vm, onDone)
                LogType.EVENT -> EventForm(vm, onDone)
                null -> {}
            }
            TextButton(onClick = { picked = null }) { Text("Back to types") }
        }
    }

    if (hiatusOffer != null) {
        AlertDialog(
            onDismissRequest = { vm.declineHiatusOffer(); onDone() },
            title = { Text("Pause for a while?") },
            text = { Text("That's a heavy one. Want to pause the streak and quiet the app for a while? Nothing breaks, nothing nags — pick it back up whenever.") },
            confirmButton = { Button(onClick = { vm.acceptHiatusOffer(); onDone() }) { Text("Pause") } },
            dismissButton = { TextButton(onClick = { vm.declineHiatusOffer(); onDone() }) { Text("Not now") } },
        )
    }
}

@Composable
private fun Dial15(label: String, value: Int?, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Hyle.OnSurfaceDim)
        HyleSegmented(listOf(1, 2, 3, 4, 5), value ?: 0, { if (it == 0) "·" else "$it" }) { if (it != 0) onSelect(it) }
    }
}

@Composable
private fun CheckinForm(vm: WellnessViewModel, onDone: () -> Unit) {
    var energy by remember { mutableStateOf<Int?>(null) }
    var sleep by remember { mutableStateOf<Int?>(null) }
    var motivation by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf("") }
    Dial15("Energy", energy) { energy = it }
    Dial15("Sleep", sleep) { sleep = it }
    Dial15("Motivation", motivation) { motivation = it }
    OutlinedTextField(note, { note = it.take(200) }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
    Button(onClick = { vm.logStandaloneCheckin(energy, sleep, motivation, emptyList(), note.ifBlank { null }); onDone() }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
}

@Composable
private fun RestForm(vm: WellnessViewModel, onDone: () -> Unit) {
    var yesterday by remember { mutableStateOf(false) }
    var planned by remember { mutableStateOf(true) }
    var note by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Switch(checked = yesterday, onCheckedChange = { yesterday = it }); Text(if (yesterday) "For yesterday" else "For today", color = Hyle.OnSurfaceDim)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Switch(checked = planned, onCheckedChange = { planned = it }); Text(if (planned) "Planned rest" else "Unplanned", color = Hyle.OnSurfaceDim)
    }
    OutlinedTextField(note, { note = it.take(200) }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
    Button(
        onClick = {
            vm.logRestDay(if (yesterday) LocalDate.now().minusDays(1) else LocalDate.now(), planned, note.ifBlank { null })
            onDone()
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Save") }
}

@Composable
private fun MoodForm(vm: WellnessViewModel, onDone: () -> Unit) {
    var mood by remember { mutableStateOf<Int?>(null) }
    var tags by remember { mutableStateOf(setOf<String>()) }
    var note by remember { mutableStateOf("") }
    Dial15("Mood", mood) { mood = it }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("stressed", "anxious", "flat", "okay", "great").forEach { t ->
            FilterChip(selected = t in tags, onClick = { tags = if (t in tags) tags - t else tags + t }, label = { Text(t) })
        }
    }
    OutlinedTextField(note, { note = it.take(200) }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
    Button(
        onClick = { mood?.let { vm.logMood(it, tags.toList(), note.ifBlank { null }); onDone() } },
        enabled = mood != null,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Save") }
}

@Composable
private fun LifeEventForm(vm: WellnessViewModel, onSaved: (Int) -> Unit) {
    val categories = listOf("BEREAVEMENT", "ILLNESS", "EXAMS", "WORK", "TRAVEL", "RELATIONSHIP", "OTHER")
    var category by remember { mutableStateOf("OTHER") }
    var impact by remember { mutableStateOf(1) }
    var title by remember { mutableStateOf("") }
    var ongoing by remember { mutableStateOf(true) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        categories.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { c ->
                    FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.lowercase()) })
                }
            }
        }
    }
    Text("Impact", color = Hyle.OnSurfaceDim)
    HyleSegmented(listOf(1, 2, 3), impact, { "$it" }) { impact = it }
    OutlinedTextField(title, { title = it.take(60) }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Switch(checked = ongoing, onCheckedChange = { ongoing = it }); Text(if (ongoing) "Ongoing" else "One-day", color = Hyle.OnSurfaceDim)
    }
    Button(
        onClick = { vm.logLifeEvent(category, impact, title.ifBlank { category.lowercase() }, ongoing); onSaved(impact) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Save") }
}

@Composable
private fun CycleForm(vm: WellnessViewModel, onDone: () -> Unit) {
    var daysAgo by remember { mutableStateOf(0) }
    var flow by remember { mutableStateOf<Int?>(null) }
    Text("Period start", color = Hyle.OnSurfaceDim)
    HyleStepper(daysAgo, { daysAgo = it }, 0..14, suffix = " days ago")
    Text("Flow", color = Hyle.OnSurfaceDim)
    HyleSegmented(listOf(1, 2, 3), flow ?: 0, { if (it == 0) "·" else "$it" }) { if (it != 0) flow = it }
    Button(
        onClick = { vm.logCycleStart(LocalDate.now().minusDays(daysAgo.toLong()), flow); onDone() },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Save") }
}

@Composable
private fun MedicationForm(vm: WellnessViewModel, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    var taken by remember { mutableStateOf(true) }
    OutlinedTextField(name, { name = it.take(60) }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(dose, { dose = it.take(40) }, label = { Text("Dose (e.g. 400 mg)") }, modifier = Modifier.fillMaxWidth())
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Switch(checked = taken, onCheckedChange = { taken = it }); Text(if (taken) "Taken" else "Skipped", color = Hyle.OnSurfaceDim)
    }
    Button(
        onClick = { if (name.isNotBlank()) { vm.logMedication(name, dose, taken); onDone() } },
        enabled = name.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Save") }
}

@Composable
private fun EventForm(vm: WellnessViewModel, onDone: () -> Unit) {
    var title by remember { mutableStateOf("") }
    OutlinedTextField(title, { title = it.take(80) }, label = { Text("What happened? (e.g. new string)") }, modifier = Modifier.fillMaxWidth())
    Button(
        onClick = { if (title.isNotBlank()) { vm.logEvent(title, emptyList()); onDone() } },
        enabled = title.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Save") }
}
