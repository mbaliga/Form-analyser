package xyz.mdhv.formanalyser.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mdhv.formanalyser.app.ai.*
import xyz.mdhv.formanalyser.app.ai.providers.*
import xyz.mdhv.formanalyser.app.ui.theme.*
import xyz.mdhv.formanalyser.coach.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings → AI coach. The one place to choose the coaching model, hold BYOK cloud keys, tune the
 * two redaction defaults (medical grant / keep private), and install an on-device model.
 *
 * Self-contained (no dedicated ViewModel): [AiSettings] flows drive the toggles/selection, and the
 * synchronous [KeyVault] holds the encrypted keys. A [keyVersion] tick forces recomposition after a
 * key is saved or cleared, since the vault is not a Flow.
 *
 * NB: this screen is currently plain Material3 rather than the Hyle design system the rest of the
 * app uses, and it states each model's locus in words ("On-device" / "Cloud · BYOK") instead of the
 * provenance-coloured dot. That dot — cloud = alien-cyan, on-device = radium-green — still carries
 * the meaning on the Coach screen; re-aligning this screen with Hyle is outstanding design work.
 */
@Composable
fun AiSettingsScreen() {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { AiSettings(c) }
    val vault = remember { KeyVault(c) }
    val selected by settings.selectedModelId.collectAsState(initial = AiSettings.DEFAULT_MODEL_ID)
    val medical by settings.medicalGrantDefault.collectAsState(initial = false)
    val keepPrivate by settings.keepPrivate.collectAsState(initial = true)
    val storedPath by settings.onDeviceModelPath.collectAsState(initial = null)
    // Trust the file, not just the preference: a weights file the athlete deleted (or that
    // landed truncated) must read as "not installed", because OnDeviceLlmClient refuses it
    // too. Reporting it as installed would leave them believing local coaching is available
    // while every request fails.
    val path = storedPath?.takeIf { ModelInstall.isInstalled(it) }
    // The vault is synchronous (not a Flow); bump this to re-read hasKey after set/clear.
    var keyVersion by remember { mutableStateOf(0) }
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("AI coach", style = MaterialTheme.typography.headlineMedium, color = Hyle.OnBackground)
        Text(
            "Cloud models are BYOK; facts are redacted for the destination first.",
            color = Hyle.OnSurfaceDim,
        )
        // ── Model selection ──────────────────────────────────────────────────
        HyleSectionHeader("Model")
        ModelRegistry.models.forEach { m ->
            ListItem(
                headlineContent = { Text(m.displayName) },
                supportingContent = {
                    Text(
                        if (m.kind == ModelKind.ON_DEVICE) "On-device"
                        else if (m.requiresByok && !vault.hasKey(m.provider))
                            "Cloud · needs API key"
                        else "Cloud · BYOK"
                    )
                },
                trailingContent = { if (m.id == selected) Text("✓") },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { scope.launch { settings.setSelectedModelId(m.id) } }) {
                Text(if (m.id == selected) "Selected" else "Select")
            }
        }
        // ── BYOK cloud keys ──────────────────────────────────────────────────
        HyleSectionHeader("Cloud API keys (BYOK)")
        // In-memory only, per composition -- a "last checked" that survives process death would need
        // its own DataStore entry; not wired in this pass (see the report's known gaps). Manual-only,
        // never on launch: nothing here calls ModelDiscovery without the athlete pressing the button.
        var discoveryResults by remember { mutableStateOf<Map<Provider, DiscoveryOutcome>>(emptyMap()) }
        var discoveryChecking by remember { mutableStateOf<Provider?>(null) }
        BYOK.forEach { p ->
            val hasKey = vault.hasKey(p).also { keyVersion }
            KeyRow(
                p,
                hasKey,
                { k ->
                    vault.setKey(p, k)
                    keyVersion++
                },
                {
                    vault.clearKey(p)
                    keyVersion++
                    discoveryResults = discoveryResults - p
                },
            )
            DiscoveryRow(
                provider = p,
                hasKey = hasKey,
                outcome = discoveryResults[p],
                checking = discoveryChecking == p,
                onCheck = {
                    val key = vault.getKey(p)
                    if (!key.isNullOrBlank()) {
                        discoveryChecking = p
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { ModelDiscovery.check(p, key) }
                            discoveryResults = discoveryResults + (p to result)
                            discoveryChecking = null
                        }
                    }
                },
            )
        }
        // ── Redaction defaults ───────────────────────────────────────────────
        HyleSectionHeader("Privacy defaults")
        ToggleRow(
            "Include medications by default",
            "Medical facts remain off unless granted.",
            medical,
        ) {
            scope.launch { settings.setMedicalGrantDefault(it) }
        }
        ToggleRow(
            "Keep private notes on device",
            "Private facts do not travel to cloud models.",
            keepPrivate,
        ) {
            scope.launch { settings.setKeepPrivate(it) }
        }
        // ── On-device model ──────────────────────────────────────────────────
        HyleSectionHeader("On-device model")
        Text(
            "No bundled model and no in-app download: Gemma weights are gated behind Google's own " +
                "licence, so fetch the .task file yourself (a browser sign-in + licence acceptance) " +
                "and import it here. Roughly 1.5 GB (E2B) to 3-4 GB (E4B).",
            color = Hyle.OnSurfaceDim,
            style = MaterialTheme.typography.labelMedium,
        )
        // A copy that never finished (process death, a backed-out picker) leaves a ".part" file
        // behind; sweep anything stale once per screen visit so it doesn't sit there forever.
        LaunchedEffect(Unit) { withContext(Dispatchers.IO) { ModelInstall.sweepStalePartFiles(c) } }

        var expectedSha by rememberSaveable { mutableStateOf("") }
        var installProgress by remember { mutableStateOf<Float?>(null) }
        var installMessage by remember { mutableStateOf<String?>(null) }
        var installedInfo by remember { mutableStateOf<InstalledModel?>(null) }
        val installing = installProgress != null

        val picker =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                installMessage = null
                installProgress = 0f
                val previousPath = storedPath
                scope.launch {
                    // Close the running engine BEFORE install() touches disk at all -- not just before
                    // the old file is later removed below. install() itself sets the old final file
                    // aside (restoring it if the new one fails to finalize or probe) and then runs a
                    // real LlmInference.createFromOptions load as its own probe of the NEW file; if the
                    // previous engine is still holding the old model in memory, that probe is loading a
                    // second multi-GB model on top of it. The OOM/failure that follows gets caught as
                    // "this device may not have enough memory for this model" -- a false verdict on the
                    // new file caused by our own un-closed engine, not the device. Mirrors the Remove
                    // button below and the contract in OnDeviceEngineRegistry's KDoc.
                    OnDeviceEngineRegistry.closeActive()
                    val outcome = withContext(Dispatchers.IO) {
                        ModelInstall.install(
                            context = c,
                            source = uri,
                            expectedSha256 = expectedSha.trim().ifBlank { null },
                            onProgress = { copied, total ->
                                installProgress = if (total != null && total > 0) {
                                    (copied.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    // SAF didn't report a size -- 0f is the "size unknown" sentinel that
                                    // keeps the indeterminate bar showing (see `installing` above and the
                                    // `p > 0f` branch below); `null` would mean "not installing" and would
                                    // re-enable the whole install UI mid-copy.
                                    0f
                                }
                            },
                        )
                    }
                    installProgress = null
                    when (outcome) {
                        is InstallOutcome.Success -> {
                            settings.setOnDeviceModelPath(outcome.info.path)
                            installedInfo = outcome.info
                            installMessage = "Installed ${outcome.info.displayName}."
                            expectedSha = "" // stale hash must not silently apply to the NEXT install
                            if (previousPath != null && previousPath != outcome.info.path) {
                                withContext(Dispatchers.IO) { ModelInstall.remove(previousPath) }
                            }
                        }
                        is InstallOutcome.Failed -> installMessage = outcome.message
                    }
                }
            }

        Text(path?.substringAfterLast('/') ?: "No model installed", color = Hyle.OnSurfaceDim)
        installedInfo?.let { info ->
            Text(
                "${humanBytes(info.sizeBytes)} · sha256 ${info.sha256.take(12)}… · installed ${formatDate(info.installedAtEpochMs)}",
                color = Hyle.OnSurfaceDim,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        OutlinedTextField(
            value = expectedSha,
            onValueChange = { expectedSha = it },
            label = { Text("Expected SHA-256 (optional, paste from the publisher)") },
            enabled = !installing,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !installing) {
                Text(if (path == null) "Install model" else "Replace model")
            }
            if (path != null)
                OutlinedButton(
                    enabled = !installing,
                    onClick = {
                        scope.launch {
                            OnDeviceEngineRegistry.closeActive()
                            withContext(Dispatchers.IO) { ModelInstall.remove(storedPath) }
                            settings.setOnDeviceModelPath(null)
                            installedInfo = null
                            installMessage = null
                        }
                    },
                ) {
                    Text("Remove")
                }
        }
        installProgress?.let { p ->
            if (p > 0f) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                "Copying, verifying, and loading the model to confirm it works…",
                color = Hyle.OnSurfaceDim,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        installMessage?.let { Text(it, color = if (installedInfo != null) Hyle.OnSurfaceDim else Hyle.Danger) }

        // ── Usage & cost ─────────────────────────────────────────────────────
        HyleSectionHeader("Usage")
        val ledger by settings.usageLedger.collectAsState(initial = UsageLedger(sinceEpochMs = 0L))
        if (ledger.byModelId.isEmpty()) {
            Text("No coach usage recorded yet.", color = Hyle.OnSurfaceDim)
        } else {
            Text(
                "Since ${formatDate(ledger.sinceEpochMs)}",
                color = Hyle.OnSurfaceDim,
                style = MaterialTheme.typography.labelMedium,
            )
            ledger.byModelId.forEach { (modelId, totals) ->
                val name = ModelRegistry.byId(modelId)?.displayName ?: modelId
                Text("$name — ${totals.asks} asks · ${totals.inputTokens} in / ${totals.outputTokens} out", color = Hyle.OnBackground)
            }
            val aggregate = remember(ledger) { ledger.aggregate() }
            val knownCost = CostEstimator.format(CostEstimate.Usd(aggregate.knownUsd, aggregate.asOfIso ?: ""))
            Text(
                if (aggregate.unpricedAsks > 0) "~$knownCost (+${aggregate.unpricedAsks} asks with no published rate)"
                else "~$knownCost",
                color = Hyle.OnBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Estimate from published list prices, not a bill — see each provider's own usage " +
                    "dashboard for what you're actually charged. No spend cap: an estimate should not " +
                    "block your coaching.",
                color = Hyle.OnSurfaceDim,
                style = MaterialTheme.typography.labelMedium,
            )
            OutlinedButton(onClick = { scope.launch { settings.resetUsageLedger() } }) { Text("Reset") }
        }
    }
}

@Composable
private fun DiscoveryRow(
    provider: Provider,
    hasKey: Boolean,
    outcome: DiscoveryOutcome?,
    checking: Boolean,
    onCheck: () -> Unit,
) {
    if (!hasKey) return // nothing to check without a key -- checking IS the key test (see ModelDiscovery's KDoc)
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCheck, enabled = !checking) {
                Text(if (checking) "Checking…" else "Check available models")
            }
        }
        Text(
            "Checks which model ids your key can reach right now. It does not change the model list " +
                "and cannot fetch prices.",
            color = Hyle.OnSurfaceDim,
            style = MaterialTheme.typography.labelMedium,
        )
        when (outcome) {
            null -> Text("Never checked.", color = Hyle.OnSurfaceDim, style = MaterialTheme.typography.labelMedium)
            is DiscoveryOutcome.Unavailable ->
                Text(
                    "Couldn't check: ${outcome.error.message}",
                    color = Hyle.Danger,
                    style = MaterialTheme.typography.labelMedium,
                )
            is DiscoveryOutcome.Listed -> {
                Text(
                    "Checked ${formatDate(outcome.checkedAtEpochMs)}",
                    color = Hyle.OnSurfaceDim,
                    style = MaterialTheme.typography.labelMedium,
                )
                ModelRegistry.byProvider(provider).forEach { m ->
                    val badge = when (ModelDiscovery.availability(outcome, m.id)) {
                        Availability.AVAILABLE -> "available"
                        Availability.NOT_LISTED_BY_PROVIDER -> "not listed by provider"
                        Availability.UNKNOWN -> "unknown"
                    }
                    Text("${m.displayName}: $badge", color = Hyle.OnSurfaceDim, style = MaterialTheme.typography.labelMedium)
                }
                val unlisted = outcome.ids - ModelRegistry.byProvider(provider).map { it.id }.toSet()
                if (unlisted.isNotEmpty()) {
                    Text(
                        "Your key can also reach (not in Crocodyl's list): ${unlisted.sorted().joinToString(", ")}",
                        color = Hyle.OnSurfaceDim,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

private fun humanBytes(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 0.1) "%.1f GB".format(gb) else "%.0f MB".format(bytes / (1024.0 * 1024.0))
}

private fun formatDate(epochMs: Long): String =
    if (epochMs <= 0L) "—" else SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(epochMs))

@Composable
private fun KeyRow(p: Provider, has: Boolean, onSave: (String) -> Unit, onClear: () -> Unit) {
    // Plain remember, not rememberSaveable, unlike every other text field in the app: saved
    // instance state is written to disk to survive process death, and a BYOK provider key is the
    // one thing here that must not be left lying in it. Re-typing a key after a rotation is the
    // cheaper of the two costs.
    var key by remember(p) { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(providerLabel(p))
            Text(if (has) "Key saved" else "No key", color = Hyle.OnSurfaceDim)
            OutlinedTextField(
                key,
                { key = it },
                label = { Text(if (has) "Replace key" else "Paste API key") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row {
                Button(
                    onClick = {
                        onSave(key.trim())
                        key = ""
                    },
                    enabled = key.isNotBlank(),
                ) {
                    Text("Save")
                }
                if (has) TextButton(onClick = onClear) { Text("Clear") }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, color = Hyle.OnSurfaceDim)
        }
        Switch(checked, onChange)
    }
}

private val BYOK = listOf(Provider.ANTHROPIC, Provider.OPENAI, Provider.GOOGLE, Provider.DEEPSEEK)

private fun providerLabel(p: Provider) =
    when (p) {
        Provider.ANTHROPIC -> "Anthropic"
        Provider.OPENAI -> "OpenAI"
        Provider.GOOGLE -> "Google"
        Provider.DEEPSEEK -> "DeepSeek"
        Provider.ON_DEVICE -> "On-device"
        Provider.OTHER -> "Other"
    }
