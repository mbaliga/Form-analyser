package xyz.mdhv.formanalyser.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import xyz.mdhv.formanalyser.app.domain.PostPending
import xyz.mdhv.formanalyser.app.domain.PreCheckinData
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.app.ui.theme.HyleSegmented
import xyz.mdhv.formanalyser.app.ui.theme.HyleStepper
import xyz.mdhv.formanalyser.body.BodyFace
import xyz.mdhv.formanalyser.body.SorenessChip
import xyz.mdhv.formanalyser.body.SorenessChipResolver
import xyz.mdhv.formanalyser.app.ui.components.BodyAtlasCanvas
import xyz.mdhv.formanalyser.model.Handedness

private fun chipLabel(c: SorenessChip): String = when (c) {
    SorenessChip.NECK -> "Neck"; SorenessChip.DRAW_SHOULDER -> "Draw shoulder"
    SorenessChip.BOW_SHOULDER -> "Bow shoulder"; SorenessChip.UPPER_BACK -> "Upper back"
    SorenessChip.LOWER_BACK -> "Lower back"; SorenessChip.DRAW_FOREARM -> "Draw forearm"
    SorenessChip.BOW_ARM -> "Bow arm"; SorenessChip.CORE -> "Core"; SorenessChip.LEGS -> "Legs"
}

/** Pre-check-in gate (≤15 s by construction): three dials, soreness, note, Start or Skip. */
@Composable
fun PreCheckinSheet(
    handedness: Handedness,
    useChips: Boolean,
    onStart: (PreCheckinData) -> Unit,
    onDismiss: () -> Unit,
) {
    var energy by remember { mutableStateOf<Int?>(null) }
    var sleep by remember { mutableStateOf<Int?>(null) }
    var motivation by remember { mutableStateOf<Int?>(null) }
    var chips by remember { mutableStateOf(setOf<SorenessChip>()) }
    var regions by remember { mutableStateOf(setOf<String>()) }
    var atlasFace by remember { mutableStateOf(BodyFace.BACK) }
    var note by remember { mutableStateOf("") }

    fun sorenessIds(): List<String> =
        if (useChips) chips.flatMap { SorenessChipResolver.resolve(it, handedness) } else regions.toList()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick check-in") },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DialRow("Energy", energy) { energy = it }
                DialRow("Sleep", sleep) { sleep = it }
                DialRow("Motivation", motivation) { motivation = it }
                Text("Sore anywhere?", color = Hyle.OnSurfaceDim)
                if (useChips) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // two rows of chips via simple wrap: split the list
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SorenessChip.entries.chunked(3).forEach { rowChips ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    rowChips.forEach { c ->
                                        FilterChip(
                                            selected = c in chips,
                                            onClick = { chips = if (c in chips) chips - c else chips + c },
                                            label = { Text(chipLabel(c)) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    HyleSegmented(listOf(BodyFace.FRONT, BodyFace.BACK), atlasFace, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { atlasFace = it }
                    BodyAtlasCanvas(
                        face = atlasFace,
                        selected = regions,
                        onTap = { id -> regions = if (id in regions) regions - id else regions + id },
                    )
                }
                OutlinedTextField(note, { note = it.take(200) }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                onStart(PreCheckinData(skipped = false, energy = energy, sleep = sleep, motivation = motivation, sorenessRegionIds = sorenessIds(), note = note.ifBlank { null }))
            }) { Text("Start session") }
        },
        dismissButton = {
            TextButton(onClick = { onStart(PreCheckinData(skipped = true)) }) { Text("Skip") }
        },
    )
}

@Composable
private fun DialRow(label: String, value: Int?, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Hyle.OnSurfaceDim)
        HyleSegmented(listOf(1, 2, 3, 4, 5), value ?: 0, { if (it == 0) "·" else "$it" }) { if (it != 0) onSelect(it) }
    }
}

private val CR10 = mapOf(
    0 to "Rest", 1 to "Very, very easy", 2 to "Easy", 3 to "Moderate", 4 to "Somewhat hard",
    5 to "Hard", 7 to "Very hard", 10 to "Maximal",
)

/** Post-check-in: RPE (CR10), feel, duration confirm/override, arrow reconciliation. */
@Composable
fun PostCheckinSheet(
    pending: PostPending,
    onSave: (rpe: Double?, feel: Int?, durationOverrideS: Int?, arrows: Int?) -> Unit,
    onSkip: () -> Unit,
) {
    var rpe by remember { mutableStateOf<Int?>(null) }
    var feel by remember { mutableStateOf<Int?>(null) }
    var durationMin by remember { mutableStateOf(((pending.durationAutoS + 30) / 60).toString()) }
    var arrows by remember { mutableStateOf(pending.detectedArrows.toString()) }

    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("How did it go?") },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Effort (RPE)", color = Hyle.OnSurfaceDim)
                HyleStepper(value = rpe ?: 0, onChange = { rpe = it }, range = 0..10)
                Text(rpe?.let { CR10[it] ?: "$it" } ?: "Borg CR10 · 0 rest → 10 maximal", color = Hyle.OnSurfaceDim)
                DialRow("Feel", feel) { feel = it }
                OutlinedTextField(
                    value = durationMin,
                    onValueChange = { durationMin = it.filter(Char::isDigit) },
                    label = { Text("Duration (min) · auto ${(pending.durationAutoS + 30) / 60}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = arrows,
                    onValueChange = { arrows = it.filter(Char::isDigit) },
                    label = { Text("Arrows · detected ${pending.detectedArrows}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(rpe?.toDouble(), feel, durationMin.toIntOrNull()?.times(60), arrows.toIntOrNull())
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onSkip) { Text("Skip") } },
    )
}
