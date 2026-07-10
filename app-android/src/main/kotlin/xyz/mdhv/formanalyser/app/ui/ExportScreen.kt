package xyz.mdhv.formanalyser.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.mdhv.formanalyser.app.domain.ExportViewModel
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.app.ui.theme.HyleListRow
import xyz.mdhv.formanalyser.app.ui.theme.HyleSectionHeader
import xyz.mdhv.formanalyser.app.ui.theme.HyleSegmented
import xyz.mdhv.formanalyser.exchange.ExportTier
import xyz.mdhv.formanalyser.exchange.WithheldReason

/**
 * Phase 5 export ceremony screen. Tier selector, per-item MEDICAL grant toggles, a live consent
 * preview (what leaves vs. what stays and why), the athlete's device fingerprint, and the Export
 * button that launches the SAF CreateDocument picker.
 *
 * Provenance material law: data that WILL leave the device is dotted alien-cyan (bound elsewhere);
 * data that stays is dotted radium-green (native/on-device) — the meaning is carried by the dot, not
 * only the words.
 */
@Composable
fun ExportScreen(vm: ExportViewModel) {
    LaunchedEffect(Unit) { vm.load() }

    val tier by vm.tier.collectAsState()
    val grants by vm.medicalGrants.collectAsState()
    val decision by vm.decision.collectAsState()
    val fingerprint by vm.fingerprint.collectAsState()
    val busy by vm.busy.collectAsState()
    val outcome by vm.outcome.collectAsState()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportViewModel.MIME_ZIP),
    ) { uri -> if (uri != null) vm.export(uri) }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Export", style = MaterialTheme.typography.headlineMedium, color = Hyle.OnBackground)
        Text(
            "Build a signed-identity .crocbak archive of your data. You choose exactly what leaves " +
                "this device — private data (mood, life events, cycle) never can.",
            color = Hyle.OnSurfaceDim,
        )

        HyleSectionHeader("Tier")
        HyleSegmented(
            options = listOf(ExportTier.SHAREABLE_ONLY, ExportTier.FULL),
            selected = tier,
            label = { if (it == ExportTier.SHAREABLE_ONLY) "Shareable only" else "Full" },
            modifier = Modifier.fillMaxWidth(),
        ) { vm.setTier(it) }
        Text(
            if (tier == ExportTier.SHAREABLE_ONLY) "Freely shareable performance data only."
            else "Shareable data plus any medical items you explicitly grant below.",
            color = Hyle.OnSurfaceDim,
            style = MaterialTheme.typography.labelMedium,
        )

        if (vm.medicalTables.isNotEmpty()) {
            HyleSectionHeader("Medical grants")
            val enabled = tier == ExportTier.FULL
            vm.medicalTables.forEach { table ->
                HyleListRow(
                    title = prettyTable(table),
                    subtitle = if (enabled) "Include this medical table in the export"
                    else "Switch to the Full tier to grant",
                    trailing = {
                        Switch(
                            checked = table in grants,
                            onCheckedChange = { vm.toggleMedicalGrant(table) },
                            enabled = enabled,
                            colors = SwitchDefaults.colors(checkedTrackColor = Hyle.Accent),
                        )
                    },
                )
            }
        }

        HyleSectionHeader("Will leave this device")
        val leaving = decision.included.sorted()
        if (leaving.isEmpty()) {
            Text("Nothing selected yet.", color = Hyle.OnSurfaceDim)
        } else {
            leaving.forEach { ProvenanceRow(prettyTable(it), null, Hyle.AlienCyan) }
        }

        HyleSectionHeader("Stays on this device")
        val withheld = decision.withheld
        if (withheld.isEmpty()) {
            Text("Everything requested is cleared to export.", color = Hyle.OnSurfaceDim)
        } else {
            // PRIVATE first — the crown-jewel guarantee — then medical, then the rest.
            listOf(
                WithheldReason.PRIVATE_ALWAYS_EXCLUDED,
                WithheldReason.MEDICAL_NEEDS_GRANT,
                WithheldReason.NOT_IN_TIER,
                WithheldReason.UNKNOWN_TABLE,
            ).forEach { reason ->
                withheld[reason]?.sorted()?.forEach { table ->
                    ProvenanceRow(prettyTable(table), reasonText(reason), Hyle.RadiumGreen)
                }
            }
        }

        HyleSectionHeader("This device's identity")
        Text(
            fingerprint?.let { shortFingerprint(it) } ?: "Deriving…",
            color = Hyle.OnBackground,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Recorded in the archive manifest so a reader can tell which device produced it.",
            color = Hyle.OnSurfaceDim,
            style = MaterialTheme.typography.labelMedium,
        )

        outcome?.let {
            Text(it.message, color = if (it.ok) Hyle.RadiumGreen else Hyle.Danger)
        }

        Button(
            onClick = { picker.launch(ExportViewModel.SUGGESTED_FILENAME) },
            enabled = !busy && leaving.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "Exporting…" else "Export .crocbak") }
    }
}

@Composable
private fun ProvenanceRow(title: String, subtitle: String?, dot: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Column {
            Text(title, color = Hyle.OnBackground, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(subtitle, color = Hyle.OnSurfaceDim, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun reasonText(reason: WithheldReason): String = when (reason) {
    WithheldReason.PRIVATE_ALWAYS_EXCLUDED -> "private — never leaves this device"
    WithheldReason.MEDICAL_NEEDS_GRANT -> "medical — grant it above to include"
    WithheldReason.NOT_IN_TIER -> "not included in this tier"
    WithheldReason.UNKNOWN_TABLE -> "unrecognised table"
}

/** "medication_entry" -> "Medication entry". */
private fun prettyTable(table: String): String =
    table.replace('_', ' ').replaceFirstChar { it.uppercase() }

/** Compact the long grouped-hex fingerprint to head…tail for at-a-glance comparison. */
private fun shortFingerprint(fp: String): String {
    val groups = fp.split("-")
    if (groups.size <= 8) return fp
    return groups.take(4).joinToString("-") + " … " + groups.takeLast(2).joinToString("-")
}
