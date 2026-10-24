package xyz.mdhv.formanalyser.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xyz.mdhv.formanalyser.app.data.AppPrefs

/**
 * First-run setup for the user-facing Local Multiplayer feature.
 *
 * The transport is deliberately not hidden behind this walkthrough: the beta TCP bridge remains
 * opt-in and host-owned until discovery, trust, reconnect and device acceptance are complete.
 */
@Composable
fun PairedSessionScreen(prefs: AppPrefs, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val walkthroughSeen by prefs.pairedSessionWalkthroughSeen.collectAsState(initial = false)
    var reviewing by remember(walkthroughSeen) { mutableStateOf(!walkthroughSeen) }
    var step by remember { mutableIntStateOf(0) }
    val steps = listOf(
        "Choose the session role" to "The coach hosts and the athlete joins. The host displays a fresh six-digit pairing code.",
        "Put both devices nearby" to "Use the same trusted Wi‑Fi network. Turn off VPNs or guest-network isolation if the devices cannot see one another.",
        "Pair, then verify" to "The joiner enters the code. Check the names and session purpose before sharing any score or observation.",
        "Use file exchange when needed" to "A coach without a phone can open a signed .croc export on the desktop viewer. Transfer it by WhatsApp, USB, Bluetooth or LocalSend; the file is inspected locally.",
    )
    if (walkthroughSeen && !reviewing) {
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Local Multiplayer", style = MaterialTheme.typography.headlineMedium)
            Text("Setup guide complete. Live device pairing is still a beta transport path.")
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Current status", style = MaterialTheme.typography.titleLarge)
                    Text("The host/join transport is not enabled in this production candidate. The setup guidance is retained for beta builds.")
                }
            }
            Text(
                "If the devices cannot connect, use the desktop viewer and a signed .croc file instead. This does not upload your training data.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
                Button(onClick = { reviewing = true }, modifier = Modifier.weight(1f)) {
                    Text("Review setup")
                }
            }
        }
        return
    }

    val current = steps[step]
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Local Multiplayer", style = MaterialTheme.typography.headlineMedium)
        Text(
            "A nearby coach-and-athlete session for short-lived sharing. Your data stays on the devices unless you explicitly export it.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { (step + 1).toFloat() / steps.size },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Step ${step + 1} of ${steps.size}", style = MaterialTheme.typography.labelLarge)
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(current.first, style = MaterialTheme.typography.titleLarge)
                Text(current.second)
            }
        }
        Text(
            "Troubleshooting: if pairing fails, confirm the same Wi‑Fi, disable VPN/guest isolation, restart the host, and use a new code. If that is not practical, use the file workflow instead.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
            Button(
                onClick = {
                    if (step == steps.lastIndex) {
                        scope.launch { prefs.setPairedSessionWalkthroughSeen(true) }
                        onBack()
                    } else step++
                },
                modifier = Modifier.weight(1f),
            ) { Text(if (step == steps.lastIndex) "Done" else "Next") }
        }
        TextButton(
            onClick = {
                scope.launch { prefs.setPairedSessionWalkthroughSeen(true) }
                onBack()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Skip for now") }
    }
}
