package xyz.mdhv.formanalyser.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.mdhv.crocodyl.fencing.FencerSide
import xyz.mdhv.crocodyl.fencing.FencingWeapon
import xyz.mdhv.crocodyl.fencing.RefereeCall
import xyz.mdhv.crocodyl.fencing.RefereeMatchState
import xyz.mdhv.crocodyl.fencing.RefereePhase
import xyz.mdhv.formanalyser.app.ui.theme.FormAnalyserTheme

/** Fencing's first production slice: a human-operated, auditable referee console. */
class FencingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FormAnalyserTheme { FencingScaffold(onBack = ::finish) } }
    }
}

@Composable
private fun FencingScaffold(onBack: () -> Unit) {
    val viewModel: FencingViewModel = viewModel()
    val uiState by viewModel.state
    val selectedWeapon = uiState.selectedWeapon
    val state = uiState.match
    val leftName = uiState.leftName
    val rightName = uiState.rightName

    Scaffold { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.semantics { contentDescription = "Back" },
                ) { Text("Back") }
                Text("Fencing", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Sabre · Foil · Epee — equipment, training and referee console.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Text("Weapon", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FencingWeapon.entries.forEach { weapon ->
                        FilterChip(
                            selected = weapon == selectedWeapon,
                            onClick = { viewModel.selectWeapon(weapon) },
                            label = { Text(weapon.displayName) },
                        )
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(selectedWeapon.displayName, style = MaterialTheme.typography.titleLarge)
                        Text("Target: ${selectedWeapon.targetSummary}")
                        Text("Rule: ${selectedWeapon.priorityRule}")
                        Text(selectedWeapon.equipmentSummary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Text("Match", style = MaterialTheme.typography.titleLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = leftName,
                        onValueChange = viewModel::setLeftName,
                        label = { Text("Left fencer") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = rightName,
                        onValueChange = viewModel::setRightName,
                        label = { Text("Right fencer") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
            }
            item { Scoreboard(state, leftName, rightName) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Referee console", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Manual referee input is live. Camera adjudication is not connected; every point is explicit and undoable.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = viewModel::ready) { Text(RefereeCall.PRET.spoken) }
                            Button(
                                onClick = viewModel::start,
                                enabled = state.phase != RefereePhase.IN_PROGRESS,
                            ) { Text(RefereeCall.ALLEZ.spoken) }
                            OutlinedButton(onClick = viewModel::halt) { Text(RefereeCall.HALT.spoken) }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.award(FencerSide.LEFT) },
                                enabled = state.phase == RefereePhase.IN_PROGRESS,
                                modifier = Modifier.weight(1f),
                            ) { Text("Point — ${leftName.ifBlank { "left" }}") }
                            Button(
                                onClick = { viewModel.award(FencerSide.RIGHT) },
                                enabled = state.phase == RefereePhase.IN_PROGRESS,
                                modifier = Modifier.weight(1f),
                            ) { Text("Point — ${rightName.ifBlank { "right" }}") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = viewModel::undoLastPoint) { Text("Undo last point") }
                            OutlinedButton(onClick = viewModel::reset) { Text("Reset match") }
                        }
                    }
                }
            }
            item { Text("Call history", style = MaterialTheme.typography.titleLarge) }
            if (state.history.isEmpty()) {
                item { Text("No calls yet. Begin with Prêt, then Allez.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(state.history.asReversed()) { decision ->
                    Text(
                        decision.call.spoken + (decision.pointWinner?.let { " · ${sideName(it, leftName, rightName)}" } ?: ""),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun Scoreboard(state: RefereeMatchState, leftName: String, rightName: String) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            ScoreColumn(leftName.ifBlank { "Left" }, state.leftScore)
            Text("—", style = MaterialTheme.typography.headlineMedium)
            ScoreColumn(rightName.ifBlank { "Right" }, state.rightScore)
        }
        Text(
            "State: ${state.phase.name.lowercase().replace('_', ' ')}",
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScoreColumn(name: String, score: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, style = MaterialTheme.typography.titleMedium)
        Text(score.toString(), style = MaterialTheme.typography.displaySmall)
    }
}

private fun sideName(side: FencerSide, leftName: String, rightName: String): String =
    if (side == FencerSide.LEFT) leftName.ifBlank { "left fencer" } else rightName.ifBlank { "right fencer" }
