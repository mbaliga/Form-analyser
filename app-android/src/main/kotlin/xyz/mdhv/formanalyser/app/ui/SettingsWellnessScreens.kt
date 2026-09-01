package xyz.mdhv.formanalyser.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xyz.mdhv.formanalyser.app.data.AppPrefs
import xyz.mdhv.formanalyser.app.domain.WellnessViewModel
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.app.ui.theme.HyleListRow
import xyz.mdhv.formanalyser.app.ui.theme.HyleSectionHeader
import java.text.DateFormat
import java.util.Date

/** Settings → Streak & Rest: planned-rest weekly pattern + hiatus control (Phase 2 §H/F2). */
@Composable
fun SettingsStreakScreen(vm: WellnessViewModel) {
    LaunchedEffect(Unit) { vm.load() }
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    val scope = rememberCoroutineScope()
    val plannedCsv by prefs.plannedRestDays.collectAsState(initial = "")
    val openHiatus by vm.openHiatus.collectAsState()
    val planned = plannedCsv.split(',').map { it.trim().uppercase() }.filter { it.isNotBlank() }.toSet()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Streak & rest", style = MaterialTheme.typography.headlineMedium, color = Hyle.OnBackground)
        HyleSectionHeader("Planned rest days")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU").forEach { d ->
                FilterChip(
                    selected = d in planned,
                    onClick = {
                        val next = if (d in planned) planned - d else planned + d
                        scope.launch { prefs.setPlannedRestDays(next.joinToString(",")) }
                    },
                    label = { Text(d) },
                )
            }
        }
        Text("A planned rest day preserves your streak when you log any check-in.", color = Hyle.OnSurfaceDim)

        HyleSectionHeader("Hiatus")
        if (openHiatus == null) {
            Text("Pausing freezes the streak and quiets the app — nothing breaks, nothing nags.", color = Hyle.OnSurfaceDim)
            OutlinedButton(onClick = { vm.startHiatus() }, modifier = Modifier.fillMaxWidth()) { Text("Start a pause") }
        } else {
            Text("On pause since ${openHiatus?.startDate}.", color = Hyle.OnBackground)
            Button(onClick = { vm.endHiatus() }, modifier = Modifier.fillMaxWidth()) { Text("End the pause") }
        }
    }
}

/** Settings → Cycle tracking: off until enabled; pattern-discovery framing, no predictions pushed. */
@Composable
fun SettingsCycleScreen(vm: WellnessViewModel) {
    LaunchedEffect(Unit) { vm.load() }
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    val scope = rememberCoroutineScope()
    val enabled by prefs.cycleEnabled.collectAsState(initial = false)
    val cycles by vm.cycles.collectAsState()
    val estimate by vm.cycleEstimate.collectAsState()

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Cycle tracking", style = MaterialTheme.typography.headlineMedium, color = Hyle.OnBackground)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Enable module", color = Hyle.OnBackground)
            Switch(checked = enabled, onCheckedChange = { v -> scope.launch { prefs.setCycleEnabled(v) } })
        }
        if (enabled) {
            Text(
                "This is pattern discovery from your own logs — the science on cycle and training is " +
                    "genuinely mixed, and this app won't pretend otherwise. Estimates carry uncertainty on purpose.",
                color = Hyle.OnSurfaceDim,
            )
            val est = estimate
            if (est != null) {
                HyleListRow(
                    title = "Day ${est.dayInCycle} · ${est.phase.name.lowercase()} (estimate)",
                    subtitle = "cycle ≈ ${"%.0f".format(est.cycleLengthDays)} d ± ${"%.0f".format(est.uncertaintyDays)}",
                )
            } else {
                Text("Needs 3 completed cycles before estimating — ${cycles.size} start(s) logged.", color = Hyle.OnSurfaceDim)
            }
            HyleSectionHeader("History")
            cycles.takeLast(6).reversed().forEach { HyleListRow(title = it.startDate, subtitle = it.flow?.let { f -> "flow $f" }) }
            Text("Log new starts from + Log. Everything here is private-class: it never leaves this device by default.", color = Hyle.OnSurfaceDim)
        }
    }
}

/** Settings → Medication: context for the risk engine, never adherence-nagging. */
@Composable
fun SettingsMedicationScreen(vm: WellnessViewModel) {
    LaunchedEffect(Unit) { vm.load() }
    val meds by vm.medications.collectAsState()
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Medication", style = MaterialTheme.typography.headlineMedium, color = Hyle.OnBackground)
        Text("Medical-class: shared only per-item, with its own confirmation, never in bulk. Log entries from + Log.", color = Hyle.OnSurfaceDim)
        if (meds.isEmpty()) Text("Nothing logged.", color = Hyle.OnSurfaceDim)
        meds.forEach { m ->
            HyleListRow(
                title = m.name + (m.dose?.let { " · $it" } ?: ""),
                subtitle = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(m.ts)) +
                    if (m.taken) "" else " · skipped",
            )
        }
    }
}
