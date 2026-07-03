package xyz.mdhv.formanalyser.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import xyz.mdhv.formanalyser.app.domain.SessionViewModel
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.app.ui.theme.HyleListRow
import xyz.mdhv.formanalyser.app.ui.theme.HyleSegmented
import xyz.mdhv.formanalyser.model.Handedness

@Composable
fun TrainSetupScreen(vm: SessionViewModel, onStarted: () -> Unit, onManageRigs: () -> Unit) {
    LaunchedEffect(Unit) { vm.refreshActiveRig() }
    val activeRig by vm.activeRig.collectAsState()
    var distance by remember { mutableStateOf("18") }
    var advanced by remember { mutableStateOf(false) }
    var override by remember { mutableStateOf<Handedness?>(null) }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("New session", style = MaterialTheme.typography.headlineMedium, color = Hyle.OnBackground)

        HyleListRow(
            title = "Rig",
            subtitle = activeRig?.name ?: "No rig — tap to add",
            onClick = onManageRigs,
        )

        OutlinedTextField(
            value = distance,
            onValueChange = { distance = it.filter { c -> c.isDigit() } },
            label = { Text("Distance (m)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide advanced" else "Advanced") }
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
            onClick = {
                vm.startSession(distanceMeters = distance.toIntOrNull() ?: 0, handednessOverride = override)
                onStarted()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Start recording") }

        Text(
            "Set the phone on a tripod, side-on to the archer, framing the whole body. " +
                "Each shot's draw → anchor → release is segmented from your pose automatically.",
            color = Hyle.OnSurfaceDim,
        )
    }
}
