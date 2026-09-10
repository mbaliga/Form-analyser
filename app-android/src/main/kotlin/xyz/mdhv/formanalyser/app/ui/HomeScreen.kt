package xyz.mdhv.formanalyser.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.mdhv.formanalyser.app.domain.HomeViewModel
import xyz.mdhv.formanalyser.app.ui.components.AthleteActionTile
import xyz.mdhv.formanalyser.app.ui.components.CrocodylHero
import xyz.mdhv.formanalyser.app.ui.components.ScoreActionIcon
import xyz.mdhv.formanalyser.app.ui.theme.*

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onStartSession: () -> Unit,
    onScore: () -> Unit,
    onOpenReview: (String) -> Unit,
    onManageRigs: () -> Unit,
    onLog: () -> Unit,
    onCoach: () -> Unit,
) {
    LaunchedEffect(Unit) { vm.load() }
    val athlete by vm.athlete.collectAsState()
    val activeRig by vm.activeRig.collectAsState()
    val recent by vm.recent.collectAsState()
    val readiness by vm.readiness.collectAsState()
    val streak by vm.streak.collectAsState()
    val quiet = readiness?.level == xyz.mdhv.formanalyser.wellness.ReadinessLevel.QUIET
    val readinessLabel =
        when (readiness?.level) {
            xyz.mdhv.formanalyser.wellness.ReadinessLevel.READY -> "Ready to train"
            xyz.mdhv.formanalyser.wellness.ReadinessLevel.CAUTION -> "Train with care"
            xyz.mdhv.formanalyser.wellness.ReadinessLevel.REST_ADVISED -> "Recovery first"
            else -> if (quiet) "On pause" else "Check in to calibrate"
        }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            CrocodylHero(
                athleteName = athlete?.displayName ?: "Athlete",
                readinessLabel = readinessLabel,
                readinessDetail = readiness?.reasons?.firstOrNull()
                    ?: athlete?.club
                    ?: "Log sleep, energy and soreness for a useful training signal.",
                ready = readiness?.level == xyz.mdhv.formanalyser.wellness.ReadinessLevel.READY,
                streak = streak?.length,
            )
        }
        item {
            HyleListRow(
                "Active rig",
                vm.rigLabel() ?: activeRig?.name ?: "No rig yet",
                onClick = onManageRigs,
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AthleteActionTile(
                    "Train",
                    "Capture & analyse",
                    Icons.Filled.CameraAlt,
                    if (quiet) Hyle.OnSurfaceDim else Hyle.AccentBright,
                    onStartSession,
                    Modifier.weight(1f),
                )
                AthleteActionTile(
                    "Score",
                    "Fast arrow entry",
                    ScoreActionIcon,
                    Hyle.RadiumGreen,
                    onScore,
                    Modifier.weight(1f),
                )
            }
        }
        item {
            OutlinedButton(onClick = onLog, modifier = Modifier.fillMaxWidth()) {
                Text("+ Log — wellness, rest, more")
            }
        }
        item {
            HyleListRow("Coach", "What your data says — and ask a model over it", onClick = onCoach)
        }
        item {
            Text(
                "Recent sessions",
                style = MaterialTheme.typography.titleLarge,
                color = Hyle.OnBackground,
            )
        }
        if (recent.isEmpty())
            item {
                HyleEmptyState(
                    "🎯",
                    listOf("No sessions yet.", "Your first recorded end will show up here."),
                )
            }
        else
            items(recent, key = { it.id }) { s ->
                HyleListRow(
                    "Session · ${s.distanceMeters} m",
                    "tap to review",
                    onClick = { onOpenReview(s.id) },
                )
            }
    }
}
