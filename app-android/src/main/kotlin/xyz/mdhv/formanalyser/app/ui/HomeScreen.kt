package xyz.mdhv.formanalyser.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.mdhv.formanalyser.app.domain.HomeViewModel
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.app.ui.theme.HyleAvatar
import xyz.mdhv.formanalyser.app.ui.theme.HyleEmptyState
import xyz.mdhv.formanalyser.app.ui.theme.HyleListRow

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onStartSession: () -> Unit,
    onOpenReview: (String) -> Unit,
    onManageRigs: () -> Unit,
) {
    LaunchedEffect(Unit) { vm.load() }
    val athlete by vm.athlete.collectAsState()
    val activeRig by vm.activeRig.collectAsState()
    val recent by vm.recent.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                HyleAvatar(seed = athlete?.avatarSeed ?: 1L, sizeDp = 56)
                Column {
                    Text(athlete?.displayName ?: "Athlete", style = MaterialTheme.typography.headlineMedium, color = Hyle.OnBackground)
                    athlete?.club?.let { Text(it, color = Hyle.OnSurfaceDim) }
                }
            }
        }
        item {
            HyleListRow(
                title = "Active rig",
                subtitle = vm.rigLabel() ?: activeRig?.name ?: "No rig yet",
                onClick = onManageRigs,
            )
        }
        item {
            Button(onClick = onStartSession, modifier = Modifier.fillMaxWidth()) { Text("Start session") }
        }
        item { Text("Recent sessions", style = MaterialTheme.typography.titleLarge, color = Hyle.OnBackground) }
        if (recent.isEmpty()) {
            item { HyleEmptyState("🎯", listOf("No sessions yet.", "Your first recorded end will show up here.")) }
        } else {
            items(recent, key = { it.id }) { s ->
                HyleListRow(
                    title = "Session · ${s.distanceMeters} m",
                    subtitle = "tap to review",
                    onClick = { onOpenReview(s.id) },
                )
            }
        }
    }
}
