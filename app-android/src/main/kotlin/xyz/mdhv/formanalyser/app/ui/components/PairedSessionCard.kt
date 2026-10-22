package xyz.mdhv.formanalyser.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** User-facing entry point for the nearby coach/athlete local multiplayer flow. */
@Composable
fun PairedSessionCard(onClick: () -> Unit) {
    OutlinedCard(
        Modifier.fillMaxWidth().clickable(
            role = Role.Button,
            onClickLabel = "Open local multiplayer",
            onClick = onClick,
        ),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("LOCAL MULTIPLAYER", style = MaterialTheme.typography.labelSmall)
            Text("Coach + athlete, nearby", style = MaterialTheme.typography.titleMedium)
            Text(
                "Set up a short-lived local session, or use a shareable file when a phone is not available.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
