package xyz.mdhv.formanalyser.app.ui

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import xyz.mdhv.formanalyser.app.domain.ExportViewModel
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.app.ui.theme.HyleListRow
import xyz.mdhv.formanalyser.app.ui.theme.HyleSectionHeader
import xyz.mdhv.formanalyser.app.ui.theme.HyleSegmented
import xyz.mdhv.formanalyser.exchange.ExportTier
import xyz.mdhv.formanalyser.exchange.ExchangeTrustState
import xyz.mdhv.formanalyser.exchange.WithheldReason

/**
 * Phase 5 export ceremony screen. Tier selector, per-item MEDICAL grant toggles, a live consent
 * preview (what leaves vs. what stays and why), the athlete's device fingerprint, and the Export
 * button that launches the SAF CreateDocument picker.
 *
 * Provenance material law: data that WILL leave the device is dotted alien-cyan (bound elsewhere);
 * data that stays is dotted radium-green (native/on-device) — the meaning is carried by the dot,
 * not only the words.
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
    val importPreview by vm.importPreview.collectAsState()
    val importOutcome by vm.importOutcome.collectAsState()
    val context = LocalContext.current

    val picker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(ExportViewModel.MIME_ZIP)
        ) { uri ->
            if (uri != null) vm.export(uri)
        }
    val importPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) vm.inspectImport(uri)
        }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Data & exchange", style = MaterialTheme.typography.headlineMedium, color = Hyle.OnBackground)
        Text(
            "Build an identity-stamped .crocbak archive of your data. You choose exactly what leaves " +
                "this device — private data (mood, life events, cycle) never can.",
            color = Hyle.OnSurfaceDim,
        )

        HyleSectionHeader("Tier")
        HyleSegmented(
            options = listOf(ExportTier.SHAREABLE_ONLY, ExportTier.FULL),
            selected = tier,
            label = { if (it == ExportTier.SHAREABLE_ONLY) "Shareable only" else "Full" },
            modifier = Modifier.fillMaxWidth(),
        ) {
            vm.setTier(it)
        }
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
                    subtitle =
                        if (enabled) "Include this medical table in the export"
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
                )
                .forEach { reason ->
                    withheld[reason]?.sorted()?.forEach { table ->
                        ProvenanceRow(prettyTable(table), reasonText(reason), Hyle.RadiumGreen)
                    }
                }
        }

        HyleSectionHeader("This device's identity")
        PairingCard(
            fingerprint = fingerprint,
            onCopy = {
                fingerprint?.let {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText("Crocodyl fingerprint", it))
                }
            },
            onShare = {
                fingerprint?.let {
                    val text = "Crocodyl pairing fingerprint:\n$it\n\nCompare this on both devices before trusting an exchange."
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }, "Share pairing card"))
                }
            },
        )

        outcome?.let { Text(it.message, color = if (it.ok) Hyle.RadiumGreen else Hyle.Danger) }

        Button(
            onClick = { picker.launch(ExportViewModel.SUGGESTED_FILENAME) },
            enabled = !busy && leaving.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (busy) "Exporting…" else "Save .crocbak to this device")
        }

        // Same archive, same consent decision — only the destination differs. Saving to storage
        // was previously the only way out of the app, which meant handing a coach or a physio a
        // file took a file manager and a second app.
        OutlinedButton(
            onClick = {
                vm.exportForSharing { uri ->
                    val send =
                        Intent(Intent.ACTION_SEND).apply {
                            type = ExportViewModel.MIME_ZIP
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    context.startActivity(Intent.createChooser(send, "Share export"))
                }
            },
            enabled = !busy && leaving.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Share…")
        }
        Button(
            onClick = {
                vm.exportSignedForSharing { uri ->
                    val send =
                        Intent(Intent.ACTION_SEND).apply {
                            type = ExportViewModel.MIME_CROC
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    context.startActivity(Intent.createChooser(send, "Share signed Crocodyl exchange"))
                }
            },
            enabled = !busy && leaving.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Share signed .croc")
        }
        Text(
            "Sharing sends exactly what the two lists above describe — nothing more.",
            color = Hyle.OnSurfaceDim,
            style = MaterialTheme.typography.labelMedium,
        )

        HyleSectionHeader("Import & recover")
        Text(
            "Crocodyl validates the manifest, declared tables, row counts and full payload checksum before showing a preview. Import only adds missing rows; it never overwrites your local history.",
            color = Hyle.OnSurfaceDim,
        )
        OutlinedButton(
            onClick = {
                importPicker.launch(
                    arrayOf(
                        ExportViewModel.MIME_CROC,
                        ExportViewModel.MIME_ZIP,
                        "application/octet-stream",
                        "application/json",
                    )
                )
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (busy) "Checking archive…" else "Choose .croc or .crocbak")
        }
        importPreview?.let { preview ->
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Hyle.SurfaceRich),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when (preview.trustState) {
                            ExchangeTrustState.TRUSTED -> "Trusted exchange"
                            ExchangeTrustState.FIRST_CONTACT -> "New signed identity"
                            ExchangeTrustState.KEY_CHANGED -> "Identity change quarantined"
                            ExchangeTrustState.REPLACEMENT_ARMED -> "Confirm identity replacement"
                            ExchangeTrustState.LEGACY_UNSIGNED -> "Legacy checksum archive"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (preview.trustState == ExchangeTrustState.KEY_CHANGED) Hyle.Danger else Hyle.OnBackground,
                    )
                    Text(
                        "${preview.tableCount} tables · ${preview.rowCount} rows · Crocodyl ${preview.appVersion}",
                        color = Hyle.OnSurfaceDim,
                    )
                    Text(
                        "${preview.newRows} new · ${preview.identicalRows} already identical · ${preview.conflictRows} local conflicts kept",
                        color = if (preview.conflictRows > 0) Hyle.Accent else Hyle.RadiumGreen,
                    )
                    if (preview.athleteNames.isNotEmpty()) {
                        Text(preview.athleteNames.joinToString(), color = Hyle.OnBackground)
                    }
                    Text(
                        shortFingerprint(preview.sourceFingerprint),
                        fontFamily = FontFamily.Monospace,
                        color = Hyle.AlienCyan,
                    )
                    Text(trustExplanation(preview.trustState), color = Hyle.OnSurfaceDim)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (preview.trustState) {
                            ExchangeTrustState.KEY_CHANGED ->
                                Button(onClick = vm::armKeyReplacement, enabled = !busy) { Text("Review new key") }
                            ExchangeTrustState.REPLACEMENT_ARMED ->
                                Button(onClick = vm::importInspected, enabled = !busy) { Text("Replace key & import") }
                            ExchangeTrustState.FIRST_CONTACT ->
                                Button(onClick = vm::importInspected, enabled = !busy) { Text("Trust & import") }
                            else -> Button(onClick = vm::importInspected, enabled = !busy) { Text("Import missing rows") }
                        }
                        OutlinedButton(onClick = vm::cancelImport, enabled = !busy) { Text("Cancel") }
                    }
                }
            }
        }
        importOutcome?.let {
            Text(it.message, color = if (it.ok) Hyle.RadiumGreen else Hyle.Danger)
        }
        Text(
            "A .croc verifies its sender key, payload checksum and ECDSA signature before preview. A legacy .crocbak has checksum validation but no signature.",
            color = Hyle.OnSurfaceDim,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun PairingCard(fingerprint: String?, onCopy: () -> Unit, onShare: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Hyle.SurfaceRich)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(56.dp)) {
                val bytes = fingerprint.orEmpty().filter(Char::isLetterOrDigit)
                val cell = size.width / 4f
                repeat(16) { i ->
                    val active = bytes.getOrNull(i)?.digitToIntOrNull(16)?.let { it >= 8 } ?: false
                    drawCircle(
                        color = if (active) Hyle.AlienCyan else Hyle.RadiumGreen.copy(alpha = 0.28f),
                        radius = cell * 0.28f,
                        center = Offset((i % 4 + .5f) * cell, (i / 4 + .5f) * cell),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Pairing card", style = MaterialTheme.typography.titleMedium)
                Text(fingerprint?.let(::shortFingerprint) ?: "Deriving…", fontFamily = FontFamily.Monospace)
                Text("Compare this fingerprint before trusting a new sender.", color = Hyle.OnSurfaceDim, style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCopy, enabled = fingerprint != null) { Text("Copy") }
                    OutlinedButton(onClick = onShare, enabled = fingerprint != null) { Text("Share") }
                }
            }
        }
    }
}

private fun trustExplanation(state: ExchangeTrustState): String =
    when (state) {
        ExchangeTrustState.TRUSTED -> "Signature valid and the sender key matches your saved pairing."
        ExchangeTrustState.FIRST_CONTACT -> "Signature valid. Compare the fingerprint with the sender before trusting it for the first time."
        ExchangeTrustState.KEY_CHANGED -> "This athlete was previously paired with a different key. Import is blocked until you deliberately replace it."
        ExchangeTrustState.REPLACEMENT_ARMED -> "Only continue if the athlete confirmed this exact new fingerprint through another channel."
        ExchangeTrustState.LEGACY_UNSIGNED -> "Checksum valid, but no signed sender identity is available to pin."
    }

@Composable
private fun ProvenanceRow(
    title: String,
    subtitle: String?,
    dot: androidx.compose.ui.graphics.Color,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Column {
            Text(title, color = Hyle.OnBackground, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = Hyle.OnSurfaceDim,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

private fun reasonText(reason: WithheldReason): String =
    when (reason) {
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
