package xyz.mdhv.formanalyser.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.mdhv.crocodyl.fencing.FencingWeapon
import xyz.mdhv.crocodyl.fencing.RefereeCall
import xyz.mdhv.formanalyser.app.ui.theme.FormAnalyserTheme

/** Visible Fencing shell. Capture/referee inference is intentionally not live yet. */
class FencingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FormAnalyserTheme { FencingScaffold(onBack = ::finish) }
        }
    }
}

@Composable
private fun FencingScaffold(onBack: () -> Unit) {
    Scaffold { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                TextButton(onClick = onBack) { Text("Back") }
                Text("Fencing", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Sabre · Foil · Epee — equipment, training and referee mode are staged here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(FencingWeapon.entries.size) { index ->
                val weapon = FencingWeapon.entries[index]
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(weapon.displayName, style = MaterialTheme.typography.titleLarge)
                        Text("Target: ${weapon.targetSummary}")
                        Text("Priority: ${weapon.priorityRule}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Cinematic equipment card coming next", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Referee mode", style = MaterialTheme.typography.titleLarge)
                        Text("Two-fencer match view and piste calls are scaffolded, not live.")
                        Text(
                            "Call vocabulary: ${RefereeCall.PRET.spoken} · ${RefereeCall.ALLEZ.spoken} · " +
                                "${RefereeCall.POINT_LEFT.spoken} · ${RefereeCall.POINT_RIGHT.spoken}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = {}, enabled = false) { Text("Open referee camera — coming soon") }
                    }
                }
            }
        }
    }
}
