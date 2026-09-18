package xyz.mdhv.formanalyser.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import xyz.mdhv.formanalyser.app.domain.ImportViewModel
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.app.ui.theme.HyleSectionHeader
import xyz.mdhv.formanalyser.exchange.CrocRejection
import xyz.mdhv.formanalyser.exchange.CrocVerdict
import xyz.mdhv.formanalyser.exchange.SenderTrust

/**
 * Opens a signed `.croc` someone sent and says, plainly, whether it checks out and what is in it.
 *
 * This screen imports nothing. Saying so on the screen itself is not a disclaimer — it is the
 * honest description of what this slice does, and an athlete who is told "verified" would otherwise
 * reasonably assume their data had changed.
 *
 * Provenance material law, as on the export screen: what came from somewhere else is dotted
 * alien-cyan, what is native to this device is dotted radium-green. A refusal is Danger, never a
 * softened warning — a file that fails its signature is not a file with a small problem.
 */
@Composable
fun ImportScreen(vm: ImportViewModel) {
    LaunchedEffect(Unit) { vm.load() }
    // Leaving the screen drops the preview: a verified envelope names a sender and lists tables,
    // and that should not still be sitting there when the phone is handed to someone at the range.
    DisposableEffect(Unit) { onDispose { vm.clear() } }

    val verdict by vm.verdict.collectAsState()
    val busy by vm.busy.collectAsState()
    val bytes by vm.payloadBytes.collectAsState()
    val myFingerprint by vm.myFingerprint.collectAsState()

    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) vm.inspect(uri)
        }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Open a .croc", style = MaterialTheme.typography.headlineMedium, color = Hyle.OnBackground)
        Text(
            "Check a signed envelope someone sent you. Nothing is added to your data — this shows " +
                "you who signed the file and what it contains.",
            color = Hyle.OnSurfaceDim,
        )

        Button(
            onClick = { picker.launch(ImportViewModel.OPEN_TYPES) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (busy) "Checking…" else "Choose a file…")
        }

        when (val v = verdict) {
            null -> Unit
            is CrocVerdict.Rejected -> {
                HyleSectionHeader("Not accepted")
                Text(rejectionText(v.reason), color = Hyle.Danger)
                Text(
                    v.detail,
                    color = Hyle.OnSurfaceDim,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    "Nothing from this file has been read into your data.",
                    color = Hyle.OnSurfaceDim,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            is CrocVerdict.Verified -> {
                val m = v.manifest
                HyleSectionHeader("Signature")
                Text(
                    if (v.requiresQuarantine) "Signed, but by an unexpected key"
                    else "Signature checks out",
                    color = if (v.requiresQuarantine) Hyle.Warning else Hyle.RadiumGreen,
                )
                Text(
                    trustText(v.trust),
                    color = Hyle.OnSurfaceDim,
                    style = MaterialTheme.typography.labelMedium,
                )

                HyleSectionHeader("Sender")
                Text(m.senderLabel ?: "Not named", color = Hyle.OnBackground)
                Text(
                    shortFp(m.senderFingerprint),
                    color = Hyle.OnBackground,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Compare this with the fingerprint they read out to you. The name above is " +
                        "whatever they typed; the fingerprint is the part that cannot be faked.",
                    color = Hyle.OnSurfaceDim,
                    style = MaterialTheme.typography.labelMedium,
                )

                HyleSectionHeader("Envelope")
                Text(kindText(m.kindName), color = Hyle.OnBackground)
                Text(
                    "Made ${formatMoment(m.createdAtMs)} · Crocodyl ${m.appVersion} · " +
                        "${m.tierName.lowercase().replace('_', ' ')} tier · ${bytes / 1024} KB",
                    color = Hyle.OnSurfaceDim,
                    style = MaterialTheme.typography.labelMedium,
                )
                m.recipientFingerprint?.let { addressed ->
                    val mine = myFingerprint
                    Text(
                        when {
                            mine == null -> "Addressed to a specific device."
                            addressed == mine -> "Addressed to this device."
                            else -> "Addressed to a DIFFERENT device, not this one."
                        },
                        color =
                            if (mine != null && addressed != mine) Hyle.Warning
                            else Hyle.OnSurfaceDim,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                HyleSectionHeader("Contents")
                if (m.includedTables.isEmpty()) {
                    Text("Empty.", color = Hyle.OnSurfaceDim)
                } else {
                    m.includedTables.forEach { table ->
                        val rows = m.rowCounts?.get(table)
                        ProvenanceRow(
                            prettyTableName(table),
                            rows?.let { "$it row(s)" },
                            Hyle.AlienCyan,
                        )
                    }
                }
                Text(
                    "Read-only preview. Merging another person's records into your history needs " +
                        "rules for duplicates and disagreements that Crocodyl does not have yet, " +
                        "so nothing here has been written.",
                    color = Hyle.OnSurfaceDim,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/** Plain-language reason a file was refused. One sentence, no jargon, no blame on the reader. */
private fun rejectionText(reason: CrocRejection): String =
    when (reason) {
        CrocRejection.MALFORMED_ENVELOPE -> "This file is not a readable .croc envelope."
        CrocRejection.UNSUPPORTED_SCHEMA_VERSION ->
            "Made by a newer version of Crocodyl than this one. Update, then try again."
        CrocRejection.ALGORITHM_MISMATCH ->
            "The file disagrees with itself about how it was signed. Treat it as tampered with."
        CrocRejection.UNSUPPORTED_ALGORITHM -> "Signed in a way this version cannot check."
        CrocRejection.MALFORMED_PUBLIC_KEY -> "The sender's key in this file is not usable."
        CrocRejection.MALFORMED_SIGNATURE -> "The signature in this file is not usable."
        CrocRejection.FINGERPRINT_MISMATCH ->
            "The fingerprint shown in the file is not the fingerprint of the key that signed it."
        CrocRejection.SIGNATURE_INVALID ->
            "The signature does not match the contents. The file was changed after it was signed, " +
                "or it was never signed by the sender it names."
        CrocRejection.PAYLOAD_TABLE_SET_MISMATCH ->
            "The file contains different data than its signed description says it does."
        CrocRejection.PAYLOAD_CHECKSUM_MISMATCH ->
            "The data inside does not match the signed checksum. It arrived damaged or altered."
        CrocRejection.UNSUPPORTED_TIER -> "The sharing level named in this file is not one we know."
        CrocRejection.PRIVATE_TABLE_PRESENT ->
            "This file claims to carry private data, which nothing may send or receive. Refused " +
                "regardless of who signed it."
        CrocRejection.TIER_CONTRADICTION ->
            "This file carries more than the sharing level it declares allows."
        CrocRejection.UNKNOWN_TABLE_CLAIMED ->
            "This file claims data of a kind this version does not recognise."
    }

private fun trustText(trust: SenderTrust): String =
    when (trust) {
        SenderTrust.FIRST_CONTACT ->
            "First time seeing this key. A valid signature proves the file was not altered — it " +
                "does not prove who the sender is. Check the fingerprint with them directly."
        SenderTrust.PINNED_MATCH -> "Same key as the last envelope you accepted from this sender."
        SenderTrust.PINNED_MISMATCH ->
            "A different key than the one you saw before. That happens when someone reinstalls — " +
                "and it also happens when someone is impersonating them. Ask before trusting it."
    }

private fun kindText(kindName: String): String =
    when (kindName) {
        "ATHLETE_REPORT" -> "Athlete report"
        "COACH_ASSIGNMENT" -> "Coach assignment"
        "COACH_OBSERVATION" -> "Coach observation"
        else -> "Unrecognised kind ($kindName)"
    }

/** "medication_entry" -> "Medication entry". Local twin of the export screen's helper. */
private fun prettyTableName(table: String): String =
    table.replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun formatMoment(atMs: Long): String =
    Instant.ofEpochMilli(atMs)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))

/** Compact the long grouped-hex fingerprint to head…tail for at-a-glance comparison. */
private fun shortFp(fp: String): String {
    val groups = fp.split("-")
    if (groups.size <= 8) return fp
    return groups.take(4).joinToString("-") + " … " + groups.takeLast(2).joinToString("-")
}
