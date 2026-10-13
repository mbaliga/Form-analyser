package xyz.mdhv.formanalyser.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.mdhv.formanalyser.app.domain.HomeViewModel
import xyz.mdhv.formanalyser.app.ui.components.CrocodylHero
import xyz.mdhv.formanalyser.app.ui.components.EquipmentAtelierCard
import xyz.mdhv.formanalyser.app.ui.components.FencingStudiesCard
import xyz.mdhv.formanalyser.app.ui.components.MuayThaiFacetCard
import xyz.mdhv.formanalyser.app.ui.components.BoulderingFacetCard
import xyz.mdhv.formanalyser.app.ui.components.CommunityDirectoryCard
import xyz.mdhv.formanalyser.app.ui.components.PairedSessionCard
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
    onPairedSession: () -> Unit,
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
            val rigName = vm.rigLabel() ?: activeRig?.name ?: "No rig yet"
            CrocodylHero(
                readinessLabel = readinessLabel,
                readinessDetail = readiness?.reasons?.firstOrNull()
                    ?: athlete?.club
                    ?: "Log sleep, energy and soreness for a useful training signal.",
                ready = readiness?.level == xyz.mdhv.formanalyser.wellness.ReadinessLevel.READY,
                streak = streak?.length,
                rigName = rigName,
                onManageRigs = onManageRigs,
                onStartSession = onStartSession,
                onScore = onScore,
            )
        }
        item {
            HomeSectionLabel("YOUR PRACTICE")
        }
        item {
            if (recent.isEmpty()) {
                HyleListRow(
                    "No sessions yet",
                    subtitle = "Your first recorded end will show up here",
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.take(2).forEach { s ->
                        HyleListRow(
                            "Session · ${s.distanceMeters} m",
                            subtitle = "Review session  →",
                            onClick = { onOpenReview(s.id) },
                        )
                    }
                }
            }
        }
        item { HomeSectionLabel("EQUIPMENT ATELIER") }
        item { EquipmentAtelierCard() }
        item { HomeSectionLabel("EXPLORE OTHER DISCIPLINES") }
        item { FencingStudiesCard() }
        item { MuayThaiFacetCard() }
        item { BoulderingFacetCard() }
        item { HomeSectionLabel("TRAIN TOGETHER") }
        item { PairedSessionCard(onClick = onPairedSession) }
        item { CommunityDirectoryCard() }
        item {
            OutlinedButton(onClick = onLog, modifier = Modifier.fillMaxWidth()) {
                Text("+ Log — wellness, rest, more")
            }
        }
        item {
            HyleListRow(
                "Coach",
                subtitle = "What your data says — and ask a model over it",
                onClick = onCoach,
            )
        }
    }
}

@Composable
private fun HomeSectionLabel(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = Hyle.RadiumGreen,
        letterSpacing = 1.5.sp,
    )
}
