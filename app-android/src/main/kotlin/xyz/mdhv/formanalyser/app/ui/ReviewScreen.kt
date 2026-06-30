package xyz.mdhv.formanalyser.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import xyz.mdhv.baseline.engine.sport.FeatureScoreRelation
import xyz.mdhv.formanalyser.app.domain.ShotView
import xyz.mdhv.formanalyser.app.domain.SessionViewModel
import xyz.mdhv.formanalyser.app.ui.components.Scatter
import xyz.mdhv.formanalyser.app.ui.components.TrendLine
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.archery.ArcheryFeatureExtractor

@Composable
fun ReviewScreen(vm: SessionViewModel) {
    val shots by vm.shots.collectAsState()
    val baseline by vm.baseline.collectAsState()
    val fatigue by vm.fatigue.collectAsState()
    val correlations by vm.correlations.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Session review", style = MaterialTheme.typography.headlineMedium, color = Hyle.OnBackground)
        }
        item {
            val msg = if (baseline.ready) "Baseline ready (${baseline.repCount} shots)"
            else "Building baseline — mark ${baseline.needed} more good shot(s)"
            Text(msg, color = if (baseline.ready) Hyle.RadiumGreen else Hyle.OnSurfaceDim)
        }

        item {
            SectionCard("Steadiness trend (fatigue = downward slope)") {
                TrendLine(values = shots.sortedBy { it.index }.map { it.features[ArcheryFeatureExtractor.STEADINESS] ?: 0.0 })
                fatigue?.let {
                    Text(
                        "Decay ${fmtPct(it.decayFraction)} across ${it.repCount} shots " +
                            "(trend R²=${fmt(it.trendStrength, 2)})",
                        color = Hyle.OnSurfaceDim,
                    )
                }
            }
        }

        item {
            SectionCard("Pin-drift vs arrow score") {
                val pts = shots.mapNotNull { s ->
                    val drift = s.features[ArcheryFeatureExtractor.PIN_DRIFT_DEG]
                    val score = s.score
                    if (drift != null && score != null) drift to score else null
                }
                Scatter(points = pts)
            }
        }

        if (correlations.isNotEmpty()) {
            item {
                SectionCard("Signal → score") {
                    correlations.take(5).forEach { CorrelationRow(it) }
                }
            }
        }

        item { Text("Shots", style = MaterialTheme.typography.titleLarge, color = Hyle.OnBackground) }

        items(shots, key = { it.id }) { shot ->
            ShotCard(
                shot = shot,
                onScore = { vm.setScore(shot.id, it) },
                onBaseline = { vm.toggleBaseline(shot.id, it) },
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Hyle.Surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Hyle.OnBackground, style = MaterialTheme.typography.labelMedium)
            content()
        }
    }
}

@Composable
private fun CorrelationRow(r: FeatureScoreRelation) {
    val trust = if (r.isTrustworthy()) "" else "  (thin data)"
    Text(
        "${r.feature}: ${fmt(r.pointsPerUnit, 2)} pts/unit · r=${fmt(r.r, 2)} · n=${r.n}$trust",
        color = if (r.isTrustworthy()) Hyle.OnBackground else Hyle.OnSurfaceDim,
    )
}

@Composable
private fun ShotCard(shot: ShotView, onScore: (Double?) -> Unit, onBaseline: (Boolean) -> Unit) {
    var scoreText by remember(shot.id) { mutableStateOf(shot.score?.let { it.toInt().toString() } ?: "") }
    Card(
        colors = CardDefaults.cardColors(containerColor = Hyle.SurfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Shot ${shot.index + 1}", color = Hyle.OnBackground, style = MaterialTheme.typography.titleLarge)
                shot.stability?.let {
                    Text("stability ${it.toInt()}", color = Hyle.RadiumGreen)
                }
            }
            Text(
                "steadiness ${fmt(shot.features[ArcheryFeatureExtractor.STEADINESS], 0)} · " +
                    "drift ${fmt(shot.features[ArcheryFeatureExtractor.PIN_DRIFT_DEG], 2)}° · " +
                    "cant ${fmt(shot.features[ArcheryFeatureExtractor.CANT_DEG], 1)}° · " +
                    "release ${fmt(shot.features[ArcheryFeatureExtractor.RELEASE_PEAK], 0)}°/s",
                color = Hyle.OnSurfaceDim,
            )
            shot.topDeviationFeature?.let { Text("biggest deviation: $it", color = Hyle.OnSurfaceDim) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = scoreText,
                    onValueChange = { raw ->
                        val cleaned = raw.filter { it.isDigit() }.take(2)
                        scoreText = cleaned
                        val v = cleaned.toIntOrNull()
                        onScore(if (v != null && v in 0..10) v.toDouble() else null)
                    },
                    label = { Text("Score 0–10") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(140.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Baseline", color = Hyle.OnSurfaceDim)
                    Switch(checked = shot.isBaseline, onCheckedChange = onBaseline)
                }
            }
        }
    }
}

private fun fmt(d: Double?, digits: Int): String {
    if (d == null) return "—"
    return if (digits == 0) d.toInt().toString() else String.format("%.${digits}f", d)
}

private fun fmtPct(frac: Double): String = "${(frac * 100).toInt()}%"
