package xyz.mdhv.formanalyser.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.mdhv.formanalyser.app.domain.BodyContextViewModel
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.athlete.BodySignal
import xyz.mdhv.formanalyser.body.BodyFace

@Composable
fun BodyContextPanel(
    face: BodyFace,
    state: BodyContextViewModel.UiState,
    onRegion: (String) -> Unit,
) {
    val fills = state.signals.mapValues { BodyEncodings.painColor(it.value.contextIntensity) }
    val badges =
        state.signals
            .mapValues { (_, s) ->
                buildString {
                        if (s.pain > 0) append(s.pain)
                        if (s.soreness) append("S")
                        if (s.injurySeverity > 0) append("I")
                    }
                    .takeIf { it.isNotEmpty() }
                    .orEmpty()
            }
            .filterValues { it.isNotEmpty() }
    val injured = state.signals.filterValues { BodySignal.INJURY in it.signals }.keys
    val physio = state.signals.filterValues { BodySignal.PHYSIO_TARGET in it.signals }.keys
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF171421)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Hyle.Accent.copy(alpha = .2f)),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("CURRENT LOAD", color = Hyle.OnSurfaceDim, style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BodyMetric("${state.activeInjuryCount}", "active injuries", Hyle.Warning, Modifier.weight(1f))
                    BodyMetric(
                        state.physioAdherence28d?.let { "$it%" } ?: "—",
                        "physio · 28d",
                        BodyEncodings.physioCyan,
                        Modifier.weight(1f),
                    )
                }
                Text(
                    state.evidenceNote,
                    color = Hyle.OnSurfaceDim,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        BodyAtlasCanvas(
            face = face,
            fills = fills,
            badges = badges,
            dashed = injured,
            hatched = physio,
            onTap = onRegion,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            AtlasLegend("#", "Pain", Hyle.AccentBright)
            AtlasLegend("S", "Sore", Hyle.Warning)
            AtlasLegend("I", "Injury", Hyle.Danger)
            AtlasLegend("╱", "Physio", BodyEncodings.physioCyan)
        }
    }
}

@Composable
private fun BodyMetric(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Hyle.SurfaceVariant.copy(alpha = .55f)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(value, color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(label, color = Hyle.OnSurfaceDim, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AtlasLegend(symbol: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(color.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
            Text(symbol, color = color, fontWeight = FontWeight.Bold)
        }
        Text(label, color = Hyle.OnSurfaceDim, style = MaterialTheme.typography.labelMedium)
    }
}
