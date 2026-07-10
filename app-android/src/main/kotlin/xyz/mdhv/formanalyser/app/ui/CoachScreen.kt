package xyz.mdhv.formanalyser.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.mdhv.formanalyser.app.domain.CoachAskState
import xyz.mdhv.formanalyser.app.domain.CoachViewModel
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.app.ui.theme.HyleEmptyState
import xyz.mdhv.formanalyser.app.ui.theme.HyleSectionHeader
import xyz.mdhv.formanalyser.app.ui.theme.provenanceGlow
import xyz.mdhv.formanalyser.coach.CoachInsight
import xyz.mdhv.formanalyser.coach.CoachIntent
import xyz.mdhv.formanalyser.coach.CoachModel
import xyz.mdhv.formanalyser.coach.InsightSeverity
import xyz.mdhv.formanalyser.coach.ModelKind
import xyz.mdhv.formanalyser.coach.WithheldFact

/**
 * The coach surface. The always-on half is [CoachViewModel.insights] — the deterministic offline
 * rule coach, ranked WARNING → ADVICE → INFO. The optional half asks an LLM over the same facts:
 * a task picker, a model picker (cloud = alien-cyan, on-device = radium-green — provenance is shown
 * by material, never named), an Ask button, the grounded response, and a collapsible audit of what
 * redaction withheld before anything left the device.
 *
 * [onOpenKeySettings] routes to the BYOK key entry screen when a chosen cloud model has no key.
 */
@Composable
fun CoachScreen(
    vm: CoachViewModel,
    onOpenKeySettings: () -> Unit,
) {
    LaunchedEffect(Unit) { vm.load() }

    val loaded by vm.loaded.collectAsState()
    val insights by vm.insights.collectAsState()
    val intent by vm.intent.collectAsState()
    val model by vm.model.collectAsState()
    val ask by vm.ask.collectAsState()

    var medicalGrant by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Coach", style = MaterialTheme.typography.headlineMedium, color = Hyle.OnBackground)

        // ── Offline rule coach ───────────────────────────────────────────────
        HyleSectionHeader("What your data says")
        if (insights.isEmpty()) {
            if (loaded) {
                HyleEmptyState("🧭", listOf("Nothing flagged right now.", "Log sessions and check-ins and insights show up here."))
            } else {
                HyleEmptyState("🧭", listOf("Reading your data…"))
            }
        } else {
            insights.forEach { InsightCard(it) }
        }

        // ── Ask the coach ────────────────────────────────────────────────────
        HyleSectionHeader("Ask the coach")

        Text("Task", color = Hyle.OnSurfaceDim, style = MaterialTheme.typography.labelMedium)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CoachIntent.entries.forEach { option ->
                SelectPill(
                    label = intentLabel(option),
                    selected = option == intent,
                    accent = Hyle.Accent,
                    onClick = { vm.setIntent(option) },
                )
            }
        }

        Text("Model", color = Hyle.OnSurfaceDim, style = MaterialTheme.typography.labelMedium)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            vm.models.forEach { m ->
                ModelPill(
                    model = m,
                    selected = m.id == model.id,
                    onClick = { vm.setModel(m) },
                )
            }
        }

        // Medical grant — an explicit per-request ceremony (default OFF).
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.padding(end = 12.dp)) {
                Text("Include medications", color = Hyle.OnBackground)
                Text("A medical fact — off unless you grant it, per request.", color = Hyle.OnSurfaceDim, style = MaterialTheme.typography.labelMedium)
            }
            Switch(checked = medicalGrant, onCheckedChange = { medicalGrant = it })
        }

        Button(
            onClick = { vm.ask(intent, model, medicalGrant) },
            enabled = ask != CoachAskState.Loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (model.kind == ModelKind.ON_DEVICE) "Ask on-device" else "Ask ${model.displayName}")
        }

        if (vm.needsKey(model)) {
            Text(
                "${model.displayName} needs your API key. Add one in Settings to use it.",
                color = Hyle.AlienCyan,
                style = MaterialTheme.typography.labelMedium,
            )
        }

        // ── Ask result ───────────────────────────────────────────────────────
        when (val s = ask) {
            CoachAskState.Idle -> Unit
            CoachAskState.Loading -> Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp), color = Hyle.Accent)
                Text("Coaching…", color = Hyle.OnSurfaceDim)
            }
            is CoachAskState.NeedsKey -> Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Hyle.Surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Add a key to use ${s.model.displayName}", color = Hyle.OnBackground, fontWeight = FontWeight.SemiBold)
                Text("Cloud models run on your own API key — nothing is billed by Crocodyl.", color = Hyle.OnSurfaceDim, style = MaterialTheme.typography.labelMedium)
                Button(onClick = onOpenKeySettings, modifier = Modifier.fillMaxWidth()) { Text("Add a key in Settings") }
            }
            is CoachAskState.Ready -> ResponseCard(
                provenance = s.model.provenanceColor(),
                title = s.model.displayName,
                body = s.text,
                withheld = s.withheld,
            )
            is CoachAskState.Error -> Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Hyle.Danger, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Couldn't complete that", color = Hyle.Danger, fontWeight = FontWeight.SemiBold)
                Text(s.message, color = Hyle.OnSurfaceDim)
                WithheldSection(s.withheld)
            }
        }
    }
}

// ── pieces ──────────────────────────────────────────────────────────────────

@Composable
private fun InsightCard(insight: CoachInsight) {
    val (glyph, color) = when (insight.severity) {
        InsightSeverity.WARNING -> "▲" to Hyle.Warning
        InsightSeverity.ADVICE -> "◆" to Hyle.Accent
        InsightSeverity.INFO -> "•" to Hyle.OnSurfaceDim
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Hyle.Surface).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(glyph, color = color, fontWeight = FontWeight.SemiBold)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(insight.title, color = Hyle.OnBackground, fontWeight = FontWeight.SemiBold)
            Text(insight.detail, color = Hyle.OnSurfaceDim, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ResponseCard(provenance: Color, title: String, body: String, withheld: List<WithheldFact>) {
    Column(
        Modifier.fillMaxWidth()
            .provenanceGlow(color = provenance, cornerRadiusDp = 16)
            .clip(RoundedCornerShape(16.dp))
            .background(Hyle.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dot(provenance)
            Text(title, color = Hyle.OnSurfaceDim, style = MaterialTheme.typography.labelMedium)
        }
        Text(body, color = Hyle.OnBackground, style = MaterialTheme.typography.bodyLarge)
        WithheldSection(withheld)
    }
}

/** Collapsible "what wasn't sent" audit — the redaction's withheld list, keyed and reasoned. */
@Composable
private fun WithheldSection(withheld: List<WithheldFact>) {
    if (withheld.isEmpty()) {
        Text("Everything shareable was sent; nothing was withheld.", color = Hyle.OnSurfaceDim, style = MaterialTheme.typography.labelMedium)
        return
    }
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            (if (expanded) "▾ " else "▸ ") + "What wasn't sent (${withheld.size})",
            color = Hyle.OnSurfaceDim,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { expanded = !expanded }.padding(vertical = 4.dp),
        )
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                withheld.forEach { w ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Hyle.SurfaceVariant).padding(10.dp),
                    ) {
                        Text("${w.key} · ${w.privacy}", color = Hyle.OnBackground, style = MaterialTheme.typography.labelMedium)
                        Text(w.reason, color = Hyle.OnSurfaceDim, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectPill(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Hyle.OnBackground else Hyle.OnSurfaceDim,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) accent.copy(alpha = 0.25f) else Hyle.SurfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun ModelPill(model: CoachModel, selected: Boolean, onClick: () -> Unit) {
    val prov = model.provenanceColor()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) prov.copy(alpha = 0.18f) else Hyle.SurfaceVariant)
            .then(if (selected) Modifier.border(1.dp, prov, RoundedCornerShape(20.dp)) else Modifier)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Dot(prov)
        Text(
            model.displayName,
            color = if (selected) Hyle.OnBackground else Hyle.OnSurfaceDim,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun Dot(color: Color) {
    Row(
        Modifier.size(10.dp).clip(CircleShape).background(color),
    ) {}
}

private fun CoachModel.provenanceColor(): Color =
    if (kind == ModelKind.ON_DEVICE) Hyle.RadiumGreen else Hyle.AlienCyan

private fun intentLabel(intent: CoachIntent): String = when (intent) {
    CoachIntent.SESSION_DEBRIEF -> "Debrief"
    CoachIntent.READINESS_EXPLAINER -> "Readiness"
    CoachIntent.INJURY_AWARE_ADVICE -> "Injury-aware"
    CoachIntent.FORM_CUE -> "Form cue"
    CoachIntent.TUNING_SUGGESTION -> "Tuning"
}
