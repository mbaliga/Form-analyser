package xyz.mdhv.formanalyser.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.mdhv.formanalyser.app.domain.SessionViewModel
import xyz.mdhv.formanalyser.app.ui.components.SteadinessGauge
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.app.ui.theme.provenanceGlow
import xyz.mdhv.formanalyser.archery.ArcheryFeatureExtractor
import kotlin.math.exp

@Composable
fun CaptureScreen(vm: SessionViewModel, onReview: () -> Unit) {
    val recording by vm.isRecording.collectAsState()
    val live by vm.liveAngularSpeed.collectAsState()
    val shots by vm.shots.collectAsState()

    val liveSteadiness = 100.0 * exp(-live / ArcheryFeatureExtractor.STEADINESS_SCALE_DEG_PER_S)

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Live hold steadiness", style = MaterialTheme.typography.titleLarge, color = Hyle.OnBackground)

        // Radium-green provenance glow: this number is on-device inference.
        SteadinessGauge(
            value = if (recording) liveSteadiness else 0.0,
            modifier = Modifier.provenanceGlow(Hyle.RadiumGreen),
        )
        Text(
            if (recording) "Recording — shoot your end" else "Ready",
            color = if (recording) Hyle.RadiumGreen else Hyle.OnSurfaceDim,
        )

        Button(
            onClick = { if (recording) vm.stopRecordingAndAnalyze() else vm.startRecording() },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (recording) Hyle.Danger else Hyle.Accent,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (recording) "Stop & analyze end" else "Start recording")
        }

        if (!vm.recorder.isAvailable) {
            Text("This device lacks a gyroscope + accelerometer.", color = Hyle.Danger)
        }

        Text("Shots detected this session: ${shots.size}", color = Hyle.OnBackground)

        OutlinedButton(onClick = onReview, modifier = Modifier.fillMaxWidth()) {
            Text("Review session →")
        }
    }
}
