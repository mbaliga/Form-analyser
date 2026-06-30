package xyz.mdhv.formanalyser.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import xyz.mdhv.formanalyser.app.domain.SessionViewModel
import xyz.mdhv.formanalyser.app.ui.theme.Hyle

@Composable
fun HomeScreen(vm: SessionViewModel, onSessionStarted: () -> Unit) {
    val athlete by vm.athleteName.collectAsState()
    var drawWeight by remember { mutableStateOf("40") }
    var distance by remember { mutableStateOf("18") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Crocodyl", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium, color = Hyle.OnBackground)
        Text("Archery · form & shot-sequence analysis", color = Hyle.OnSurfaceDim)
        Text("Athlete: $athlete", color = Hyle.OnBackground)

        OutlinedTextField(
            value = drawWeight,
            onValueChange = { drawWeight = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Draw weight (lbs)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = distance,
            onValueChange = { distance = it.filter { c -> c.isDigit() } },
            label = { Text("Distance (m)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                vm.startSession(
                    drawWeightLbs = drawWeight.toDoubleOrNull() ?: 0.0,
                    distanceMeters = distance.toIntOrNull() ?: 0,
                )
                onSessionStarted()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Start session")
        }

        Text(
            "Set the phone on a tripod, side-on (sagittal) to the archer, framing the whole body. " +
                "Each shot's draw → anchor → release is segmented from your pose automatically.",
            color = Hyle.OnSurfaceDim,
        )
    }
}
