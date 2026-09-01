package xyz.mdhv.formanalyser.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import xyz.mdhv.formanalyser.app.domain.OnboardingViewModel
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.app.ui.theme.HyleAvatar
import xyz.mdhv.formanalyser.app.ui.theme.HyleSegmented
import xyz.mdhv.formanalyser.app.ui.theme.HyleStepper
import xyz.mdhv.formanalyser.equipment.PoundageEstimator
import xyz.mdhv.formanalyser.model.BowType
import xyz.mdhv.formanalyser.model.Handedness

@Composable
fun OnboardingScreen(vm: OnboardingViewModel, onDone: () -> Unit) {
    val s by vm.state.collectAsState()

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // progress dots
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0..s.lastStep) {
                Box(
                    Modifier.size(if (i == s.step) 10.dp else 7.dp).clip(CircleShape)
                        .background(if (i <= s.step) Hyle.Accent else Hyle.SurfaceVariant),
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (s.step) {
                0 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Title("What do you do?")
                    HyleSegmented(listOf("ARCHER", "COACH", "BOTH"), s.role, { it.lowercase().replaceFirstChar(Char::uppercase) }) { vm.update { st -> st.copy(role = it) } }
                    if (s.role != "ARCHER") Hint("The coach workspace arrives in a later build — everything for training yourself is ready now.")
                }
                1 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Title("Your name")
                    OutlinedTextField(
                        value = s.name, onValueChange = { v -> vm.update { it.copy(name = v.take(40)) } },
                        label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        HyleAvatar(seed = s.avatarSeed, sizeDp = 72)
                        OutlinedButton(onClick = vm::shuffleAvatar) { Text("Shuffle avatar") }
                    }
                }
                2 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Title("Which hand draws the string?")
                    HyleSegmented(
                        listOf(Handedness.RH, Handedness.LH), s.handedness,
                        { if (it == Handedness.RH) "Right-handed" else "Left-handed" },
                    ) { vm.update { st -> st.copy(handedness = it) } }
                    Hint("This is about your draw hand, not your writing hand. You can change it later, or override it for a single session.")
                }
                3 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Title("Draw length")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Switch(checked = s.drawLengthSet, onCheckedChange = { vm.update { st -> st.copy(drawLengthSet = it) } })
                        Text(if (s.drawLengthSet) "Set" else "Skip for now", color = Hyle.OnSurfaceDim)
                    }
                    if (s.drawLengthSet) {
                        HyleStepper(s.drawLengthMm, { vm.update { st -> st.copy(drawLengthMm = it) } }, 500..900, step = 5, suffix = " mm")
                    }
                    Hint("Best measured at full draw — nock groove to the grip pivot, plus 1¾″ (AMO). Skip if unsure.")
                }
                4 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Title("Your bow")
                    OutlinedTextField(s.rigName, { v -> vm.update { it.copy(rigName = v.take(40)) } }, label = { Text("Rig name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    HyleSegmented(listOf(BowType.RECURVE, BowType.BAREBOW, BowType.COMPOUND), s.bowType, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) { vm.update { st -> st.copy(bowType = it) } }
                    if (s.bowType != BowType.COMPOUND) {
                        Text("Riser length", color = Hyle.OnSurfaceDim)
                        HyleSegmented(listOf(23, 25, 27), s.riserLengthIn, { "$it″" }) { vm.update { st -> st.copy(riserLengthIn = it) } }
                    }
                    NumberField(if (s.bowType == BowType.COMPOUND) "Peak weight (lbs)" else "Marked poundage (lbs)", s.markedLbs) { v -> vm.update { it.copy(markedLbs = v) } }
                    NumberField("On-the-fingers poundage (lbs, measured)", s.otfLbs) { v -> vm.update { it.copy(otfLbs = v) } }
                    val marked = s.markedLbs
                    if (s.bowType != BowType.COMPOUND && s.otfLbs == null && marked != null && s.drawLengthSet) {
                        val est = PoundageEstimator.estimateOtfLbs(marked, s.riserLengthIn.toDouble(), s.drawLengthMm)
                        Hint("≈ ${"%.1f".format(est)} lbs on the fingers — convention estimate; limb bolts shift it ±10%. A measured value replaces this.")
                    }
                }
                5 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Title("One thing before you shoot")
                    Hint("Crocodyl asks for the camera the first time you enter Train — it's how your form gets analyzed, entirely on this phone. Notifications come later, and every one defaults to off.")
                }
                else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Title("You're set")
                    Hint("Tap Finish to start training.")
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (s.step > 0) OutlinedButton(onClick = vm::back, modifier = Modifier.weight(1f)) { Text("Back") }
            Button(
                onClick = { if (s.step == s.lastStep) vm.commit(onDone) else vm.next() },
                enabled = s.canProceed,
                modifier = Modifier.weight(1f),
            ) { Text(if (s.step == s.lastStep) "Finish" else "Next") }
        }
    }
}

@Composable private fun Title(t: String) = Text(t, style = MaterialTheme.typography.headlineMedium, color = Hyle.OnBackground)
@Composable private fun Hint(t: String) = Text(t, color = Hyle.OnSurfaceDim)

@Composable
private fun NumberField(label: String, value: Double?, onChange: (Double?) -> Unit) {
    OutlinedTextField(
        value = value?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "",
        onValueChange = { raw ->
            val cleaned = raw.filter { it.isDigit() || it == '.' }
            onChange(cleaned.toDoubleOrNull())
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
