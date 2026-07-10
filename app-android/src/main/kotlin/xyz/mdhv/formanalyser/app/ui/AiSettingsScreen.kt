package xyz.mdhv.formanalyser.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mdhv.formanalyser.app.ai.AiSettings
import xyz.mdhv.formanalyser.app.ai.KeyVault
import xyz.mdhv.formanalyser.app.ai.ModelInstall
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.app.ui.theme.HyleSectionHeader
import xyz.mdhv.formanalyser.coach.CoachModel
import xyz.mdhv.formanalyser.coach.ModelKind
import xyz.mdhv.formanalyser.coach.ModelRegistry
import xyz.mdhv.formanalyser.coach.Provider

/**
 * Settings → AI coach. The one place to choose the coaching model, hold BYOK cloud keys, tune the
 * two redaction defaults (medical grant / keep private), and install an on-device model.
 *
 * Provenance material law: cloud models glow alien-cyan (bound elsewhere — a key leaves nothing, but
 * the *facts* travel), on-device models glow radium-green (native, nothing leaves). The dot carries
 * the meaning; the words only reinforce it.
 *
 * Self-contained (no dedicated ViewModel): [AiSettings] flows drive the toggles/selection, and the
 * synchronous [KeyVault] holds the encrypted keys. A [keyVersion] tick forces recomposition after a
 * key is saved or cleared, since the vault is not a Flow.
 */
@Composable
fun AiSettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val aiSettings = remember { AiSettings(context) }
    val keyVault = remember { KeyVault(context) }

    val selectedModelId by aiSettings.selectedModelId.collectAsState(initial = AiSettings.DEFAULT_MODEL_ID)
    val medicalGrant by aiSettings.medicalGrantDefault.collectAsState(initial = false)
    val keepPrivate by aiSettings.keepPrivate.collectAsState(initial = true)
    val onDevicePath by aiSettings.onDeviceModelPath.collectAsState(initial = null)

    // The vault is synchronous (not a Flow); bump this to re-read hasKey after set/clear.
    var keyVersion by remember { mutableStateOf(0) }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("AI coach", style = MaterialTheme.typography.headlineMedium, color = Hyle.OnBackground)
        Text(
            "Choose the model that answers when you Ask the coach. Cloud models run on your own API " +
                "key — nothing is billed by Crocodyl, and facts are redacted for the destination first.",
            color = Hyle.OnSurfaceDim,
        )

        // ── Model selection ──────────────────────────────────────────────────
        HyleSectionHeader("Model")
        ModelRegistry.models.forEach { m ->
            ModelChoiceRow(
                model = m,
                selected = m.id == selectedModelId,
                needsKey = m.requiresByok && !keyVault.hasKey(m.provider).also { keyVersion },
                onClick = { scope.launch { aiSettings.setSelectedModelId(m.id) } },
            )
        }

        // ── BYOK cloud keys ──────────────────────────────────────────────────
        HyleSectionHeader("Cloud API keys (BYOK)")
        Text(
            "Keys are encrypted on this device (Android Keystore) and never logged or exported.",
            color = Hyle.OnSurfaceDim,
            style = MaterialTheme.typography.labelMedium,
        )
        BYOK_PROVIDERS.forEach { provider ->
            KeyEntryCard(
                provider = provider,
                hasKey = keyVault.hasKey(provider).also { keyVersion },
                onSave = { entered ->
                    keyVault.setKey(provider, entered)
                    keyVersion++
                },
                onClear = {
                    keyVault.clearKey(provider)
                    keyVersion++
                },
            )
        }

        // ── Redaction defaults ───────────────────────────────────────────────
        HyleSectionHeader("Privacy defaults")
        ToggleRow(
            title = "Include medications by default",
            subtitle = "A medical fact. Off keeps it out unless you grant it per request.",
            checked = medicalGrant,
            onCheckedChange = { scope.launch { aiSettings.setMedicalGrantDefault(it) } },
        )
        ToggleRow(
            title = "Keep private notes on device",
            subtitle = "Mood notes and other private facts never travel to a cloud model.",
            checked = keepPrivate,
            onCheckedChange = { scope.launch { aiSettings.setKeepPrivate(it) } },
        )

        // ── On-device model ──────────────────────────────────────────────────
        HyleSectionHeader("On-device model")
        OnDeviceInstallCard(
            installedPath = onDevicePath?.takeIf { ModelInstall.isInstalled(it) },
            onInstall = { uri ->
                scope.launch {
                    val path = withContext(Dispatchers.IO) {
                        runCatching { ModelInstall.install(context, uri) }.getOrNull()
                    }
                    if (path != null) aiSettings.setOnDeviceModelPath(path)
                }
            },
            onClear = {
                scope.launch {
                    val p = onDevicePath
                    withContext(Dispatchers.IO) { ModelInstall.remove(p) }
                    aiSettings.setOnDeviceModelPath(null)
                }
            },
        )
    }
}

// ── pieces ────────────────────────────────────────────────────────────────────

@Composable
private fun ModelChoiceRow(model: CoachModel, selected: Boolean, needsKey: Boolean, onClick: () -> Unit) {
    val prov = model.provenanceColor()
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Hyle.Surface)
            .then(if (selected) Modifier.border(1.dp, prov, RoundedCornerShape(12.dp)) else Modifier)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Dot(prov)
        Column(Modifier.padding(end = 8.dp)) {
            Text(model.displayName, color = Hyle.OnBackground, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            val tag = if (model.kind == ModelKind.ON_DEVICE) "On-device · nothing leaves"
            else if (needsKey) "Cloud · needs your API key"
            else "Cloud · BYOK"
            Text(tag, color = Hyle.OnSurfaceDim, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun KeyEntryCard(provider: Provider, hasKey: Boolean, onSave: (String) -> Unit, onClear: () -> Unit) {
    var entry by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Hyle.Surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dot(Hyle.AlienCyan)
            Text(providerLabel(provider), color = Hyle.OnBackground, fontWeight = FontWeight.SemiBold)
            Text(
                if (hasKey) "· key saved" else "· no key",
                color = if (hasKey) Hyle.RadiumGreen else Hyle.OnSurfaceDim,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        OutlinedTextField(
            value = entry,
            onValueChange = { entry = it },
            label = { Text(if (hasKey) "Replace key" else "Paste API key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { onSave(entry.trim()); entry = "" },
                enabled = entry.isNotBlank(),
            ) { Text("Save key") }
            if (hasKey) {
                OutlinedButton(onClick = { onClear(); entry = "" }) { Text("Clear") }
            }
        }
    }
}

@Composable
private fun OnDeviceInstallCard(installedPath: String?, onInstall: (android.net.Uri) -> Unit, onClear: () -> Unit) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) onInstall(uri) }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Hyle.Surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dot(Hyle.RadiumGreen)
            Text(
                if (installedPath != null) "Model installed" else "No model installed",
                color = Hyle.OnBackground,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            "Load a Gemma 3n .task/.bin weights file to coach entirely on this phone — no key, no network.",
            color = Hyle.OnSurfaceDim,
            style = MaterialTheme.typography.labelMedium,
        )
        installedPath?.let {
            Text(
                it.substringAfterLast('/'),
                color = Hyle.OnSurfaceDim,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { picker.launch(arrayOf("*/*")) }) {
                Text(if (installedPath != null) "Replace model" else "Install model")
            }
            if (installedPath != null) {
                OutlinedButton(onClick = onClear) { Text("Remove") }
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Hyle.Surface).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.padding(end = 12.dp).weight(1f)) {
            Text(title, color = Hyle.OnBackground)
            Text(subtitle, color = Hyle.OnSurfaceDim, style = MaterialTheme.typography.labelMedium)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun Dot(color: Color) {
    Row(Modifier.size(10.dp).clip(CircleShape).background(color)) {}
}

private val BYOK_PROVIDERS = listOf(Provider.ANTHROPIC, Provider.OPENAI, Provider.GOOGLE)

private fun providerLabel(provider: Provider): String = when (provider) {
    Provider.ANTHROPIC -> "Anthropic"
    Provider.OPENAI -> "OpenAI"
    Provider.GOOGLE -> "Google"
    Provider.ON_DEVICE -> "On-device"
    Provider.OTHER -> "Other"
}

private fun CoachModel.provenanceColor(): Color =
    if (kind == ModelKind.ON_DEVICE) Hyle.RadiumGreen else Hyle.AlienCyan
