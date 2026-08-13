package xyz.mdhv.formanalyser.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mdhv.formanalyser.app.ai.*
import xyz.mdhv.formanalyser.app.ui.theme.*
import xyz.mdhv.formanalyser.coach.*

@Composable
fun AiSettingsScreen() {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { AiSettings(c) }
    val vault = remember { KeyVault(c) }
    val selected by settings.selectedModelId.collectAsState(initial = AiSettings.DEFAULT_MODEL_ID)
    val medical by settings.medicalGrantDefault.collectAsState(initial = false)
    val keepPrivate by settings.keepPrivate.collectAsState(initial = true)
    val path by settings.onDeviceModelPath.collectAsState(initial = null)
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
        HyleSectionHeader("Cloud API keys (BYOK)")
        BYOK.forEach { p ->
            KeyRow(
                p,
                vault.hasKey(p).also { keyVersion },
                { k ->
                    vault.setKey(p, k)
                    keyVersion++
                },
                {
                    vault.clearKey(p)
                    keyVersion++
                },
            )
        }
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
        HyleSectionHeader("On-device model")
        val picker =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null)
                    scope.launch {
                        val installed =
                            withContext(Dispatchers.IO) {
                                runCatching { ModelInstall.install(c, uri) }.getOrNull()
                            }
                        if (installed != null) settings.setOnDeviceModelPath(installed)
                    }
            }
        Text(path?.substringAfterLast('/') ?: "No model installed", color = Hyle.OnSurfaceDim)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { picker.launch(arrayOf("*/*")) }) {
                Text(if (path == null) "Install model" else "Replace model")
            }
            if (path != null)
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { ModelInstall.remove(path) }
                            settings.setOnDeviceModelPath(null)
                        }
                    }
                ) {
                    Text("Remove")
                }
        }
    }
}

@Composable
private fun KeyRow(p: Provider, has: Boolean, onSave: (String) -> Unit, onClear: () -> Unit) {
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
