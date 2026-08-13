package xyz.mdhv.formanalyser.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Active injuries", color = Hyle.OnBackground)
                    Text(
                        state.activeInjuryCount.toString(),
                        color = Hyle.OnBackground,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                state.physioAdherence28d?.let {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Physio 28d", color = Hyle.OnBackground)
                        Text(
                            "$it% · ${state.physioCompleted28d}/${state.physioExpected28d}",
                            color = Hyle.OnBackground,
                        )
                    }
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
        Text(
            "Number = pain · S = soreness · I = active injury · dashed = injury · hatch = physio target",
            color = Hyle.OnSurfaceDim,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
