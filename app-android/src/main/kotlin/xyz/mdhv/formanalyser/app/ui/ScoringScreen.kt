package xyz.mdhv.formanalyser.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.hypot
import kotlin.math.roundToInt
import xyz.mdhv.formanalyser.app.capture.ObserverVoice
import xyz.mdhv.formanalyser.app.data.ObserverScoreEventEntity
import xyz.mdhv.formanalyser.app.data.SessionEntity
import xyz.mdhv.formanalyser.app.domain.ScoringInputMode
import xyz.mdhv.formanalyser.app.domain.ScoringUiState
import xyz.mdhv.formanalyser.app.domain.ScoringViewModel
import xyz.mdhv.formanalyser.app.domain.VoiceUiState
import xyz.mdhv.formanalyser.app.ui.components.TargetFaceCanvas
import xyz.mdhv.formanalyser.app.ui.theme.HapticCue
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.app.ui.theme.HyleListRow
import xyz.mdhv.formanalyser.app.ui.theme.rememberHaptics
import xyz.mdhv.formanalyser.scoring.EndScan
import xyz.mdhv.formanalyser.scoring.ObserverSector
import xyz.mdhv.formanalyser.scoring.RoundPack
import xyz.mdhv.formanalyser.scoring.ScoreInput
import xyz.mdhv.formanalyser.scoring.SetMatchSummary

@Composable
fun ScoringScreen(vm: ScoringViewModel) {
    LaunchedEffect(Unit) { vm.load() }
    val state by vm.state.collectAsState()
    val snapshot = state.snapshot
    val haptic = rememberHaptics()
    var chooser by rememberSaveable { mutableStateOf(false) }
    var custom by rememberSaveable { mutableStateOf(false) }
    var linking by rememberSaveable { mutableStateOf(false) }

    if (state.loading) {
        Box(Modifier.fillMaxSize().padding(24.dp)) { CircularProgressIndicator() }
        return
    }
    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = vm::clearError,
            title = { Text("Scoring") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = vm::clearError) { Text("OK") } },
        )
    }

    if (snapshot == null) {
        LazyColumn(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Score",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Hyle.OnBackground,
                )
            }
            item {
                Text(
                    "Manual scoring is local and interruption-safe. Numeric, plot and observer inputs share one authoritative scorecard.",
                    color = Hyle.OnSurfaceDim,
                )
            }
            item {
                Button(onClick = vm::quickStart, modifier = Modifier.fillMaxWidth()) {
                    Text("Quick score")
                }
            }
            item {
                OutlinedButton(onClick = { chooser = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Choose round")
                }
            }
            item {
                OutlinedButton(onClick = { custom = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Custom practice")
                }
            }
            if (state.recent.isNotEmpty()) {
                item { Text("Recent scorecards", style = MaterialTheme.typography.titleLarge) }
                items(state.recent, key = { it.id }) { s ->
                    ListItem(
                        headlineContent = { Text(s.roundName) },
                        supportingContent = {
                            Text("${s.total} · ${s.distanceMeters} m · ${s.status}")
                        },
                        // Without this the list was inert and ScoringViewModel.openScorecard had no
                        // caller: an interrupted card could only be resumed via Quick score, and a
                        // finished one could not be reviewed at all.
                        modifier = Modifier.fillMaxWidth().clickable { vm.openScorecard(s.id) },
                    )
                }
            }
        }
    } else {
        val card = snapshot.card
        val session = snapshot.session
        // An End Scan takes over the whole tab while it runs: the athlete is standing at the boss
        // holding the phone up to a target face, and a scorecard behind the camera is nothing but a
        // mis-tap waiting to happen. Nothing is written until they file the findings, and the flow's
        // own BackHandler brings them back here.
        val scan by vm.endScan.collectAsState()
        val openScan = scan
        if (openScan != null) {
            EndScanFlow(vm, openScan, card.round.faceLayout)
            return
        }
        val match = card.setMatchSummary()
        val grouping = card.grouping()
        // A finished scorecard is read-only. The repository refuses to mutate one anyway, so
        // leaving the controls live would only produce an error dialog on every tap.
        val open = session.status == "ACTIVE"
        val canScore = open && !state.saving
        // Loaded with the card, not with the dialog, so the row can name the linked session rather
        // than saying only "Linked" until the athlete happens to open the picker.
        LaunchedEffect(session.id) { vm.loadLinkableFormSessions() }
        LazyColumn(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(card.round.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${card.total} · ${card.xCount} X · ${card.arrowCount}/${card.round.maxArrows}",
                            color = Hyle.OnSurfaceDim,
                        )
                    }
                    TextButton(onClick = vm::togglePinned) {
                        Text(if (session.pinned) "Pinned" else "Pin")
                    }
                }
            }
            match?.let { m ->
                item {
                    Text(
                        "Set points ${m.athleteSetPoints}–${m.opponentSetPoints}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ScoringInputMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.inputMode == mode,
                            onClick = { vm.setInputMode(mode) },
                            label = {
                                Text(
                                    when (mode) {
                                        ScoringInputMode.NUMBERS -> "Numbers"
                                        ScoringInputMode.PLOT -> "Plot"
                                        ScoringInputMode.OBSERVER -> "Observer"
                                        ScoringInputMode.END_SCAN -> "End Scan"
                                    }
                                )
                            },
                        )
                    }
                }
            }
            when (state.inputMode) {
                ScoringInputMode.NUMBERS ->
                    item {
                        Keypad(
                            tokens = ScoreInput.keypad,
                            onToken = {
                                haptic(HapticCue.ARROW)
                                vm.recordToken(it)
                            },
                            enabled = canScore,
                        )
                    }
                ScoringInputMode.PLOT ->
                    item {
                        Column {
                            TargetFaceCanvas(
                                card.arrows,
                                card.round.faceLayout,
                                canScore,
                                vm::recordPlot,
                            )
                            Text(
                                "Tap the target. Plot coordinates and ring score are stored together.",
                                color = Hyle.OnSurfaceDim,
                            )
                        }
                    }
                ScoringInputMode.OBSERVER -> item { LiveObserverPanel(state, vm, canScore, haptic) }
                ScoringInputMode.END_SCAN ->
                    item { EndScanPanel(state.endScanCandidates, vm, canScore) }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            haptic(HapticCue.UNDO)
                            vm.undo()
                        },
                        enabled = card.arrows.isNotEmpty() && canScore,
                    ) {
                        Text("Undo")
                    }
                    Button(
                        onClick = {
                            haptic(HapticCue.COMPLETE)
                            vm.finish()
                        },
                        enabled = canScore,
                    ) {
                        Text(
                            when {
                                !open -> "Finished"
                                card.isComplete() -> "Finish"
                                else -> "Save practice"
                            }
                        )
                    }
                }
            }
            grouping?.let { g ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Grouping", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Mean radius ${"%.1f".format(g.meanRadiusCm)} cm · spread ${"%.1f".format(g.maxSpreadCm)} cm"
                            )
                            Text(
                                "Centre offset ${"%.1f".format(g.centerOffsetCm)} cm",
                                color = Hyle.OnSurfaceDim,
                            )
                        }
                    }
                }
            }
            item {
                HyleListRow(
                    title = "Training session",
                    subtitle =
                        session.linkedFormSessionId?.let { id ->
                            state.linkableFormSessions
                                .firstOrNull { it.id == id }
                                ?.let { "Linked · ${sessionDay(it.startedAtEpochMs)}" } ?: "Linked"
                        } ?: "Not linked · counted as its own session",
                    onClick = { linking = true },
                )
            }
            if (card.round.scoringKind.name == "SET_MATCH")
                item { MatchControls(card.pendingOpponentEndIndex, vm, match, canScore) }
            state.completionMessage?.let { msg ->
                item { Card(Modifier.fillMaxWidth()) { Text(msg, Modifier.padding(16.dp)) } }
            }
            item {
                // The only way to remove a single bad scorecard. Before this the athlete's choices
                // were to keep a mis-scored round in their PB history forever or wipe everything.
                TextButton(onClick = vm::previewDeletion) {
                    Text("Delete this scorecard", color = Hyle.Danger)
                }
            }
        }
    }

    if (chooser)
        AlertDialog(
            onDismissRequest = { chooser = false },
            title = { Text("Choose round") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RoundPack.builtIns.forEach { r ->
                        TextButton(
                            onClick = {
                                chooser = false
                                vm.startBuiltIn(r.id)
                            }
                        ) {
                            Text(r.name)
                        }
                    }
                }
            },
            confirmButton = {},
        )
    state.deletionPreview?.let { p ->
        AlertDialog(
            onDismissRequest = vm::dismissDeletion,
            title = { Text(if (p.permanent) "Discard ${p.label}?" else "Delete ${p.label}?") },
            text = { Text(p.detail) },
            confirmButton = {
                TextButton(onClick = vm::deleteScorecard) {
                    Text(if (p.permanent) "Discard" else "Delete", color = Hyle.Danger)
                }
            },
            dismissButton = { TextButton(onClick = vm::dismissDeletion) { Text("Keep") } },
        )
    }
    if (linking && snapshot != null)
        LinkFormSessionDialog(
            sessions = state.linkableFormSessions,
            linkedId = snapshot.session.linkedFormSessionId,
            onDismiss = { linking = false },
            onPick = { id ->
                linking = false
                vm.setLinkedFormSession(id)
            },
        )
    if (custom)
        CustomRoundDialog(
            onDismiss = { custom = false },
            onStart = { n, d, f, a, e ->
                custom = false
                vm.startCustom(n, d, f, a, e)
            },
        )
}

/**
 * Attach a scorecard to the capture session it was shot alongside.
 *
 * The link is what stops Progress counting one afternoon twice — once as a form session and once as
 * a scorecard. It cannot be guessed: getting it wrong in the other direction would *drop* arrows
 * from the athlete's volume, so this dialog is the only thing that ever writes it, and "Not linked"
 * is always reachable.
 */
@Composable
private fun LinkFormSessionDialog(
    sessions: List<SessionEntity>,
    linkedId: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link to a training session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Linked, this scorecard and that session count as one session in Progress instead of two.",
                    color = Hyle.OnSurfaceDim,
                )
                if (sessions.isEmpty())
                    Text("No recorded training sessions yet.", color = Hyle.OnSurfaceDim)
                // The current choice is marked with a glyph, not a colour: Hyle never encodes state
                // by hue alone.
                TextButton(onClick = { onPick(null) }) {
                    Text(if (linkedId == null) "✓ Not linked" else "Not linked")
                }
                sessions.forEach { s ->
                    TextButton(onClick = { onPick(s.id) }) {
                        Text(
                            (if (s.id == linkedId) "✓ " else "") +
                                "${sessionDay(s.startedAtEpochMs)} · ${s.distanceMeters} m"
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Day-level label for a capture session.
 *
 * Deliberately the athlete's local date and time rather than a raw epoch: they are recognising "the
 * session I shot on Tuesday evening", and two sessions on one day are common enough that the date
 * alone would be ambiguous.
 */
private fun sessionDay(atMs: Long): String =
    Instant.ofEpochMilli(atMs)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))

@Composable
private fun Keypad(tokens: List<String>, onToken: (String) -> Unit, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tokens.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { t ->
                    Button(
                        onClick = { onToken(t) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    ) {
                        Text(t)
                    }
                }
            }
        }
    }
}

/**
 * Live Observer: tap is immediate and authoritative, voice is not.
 *
 * A spoken score is machine output, so under the house invariant it can never become an arrow on
 * its own — it is filed as a proposal ([ScoringViewModel.startListening] →
 * `ScoringRepository.proposeObserverSpoken`) and only [PendingSpokenArrows] below can turn it into
 * one, on an explicit tap. The keypad here is untouched by any of that: a tap already is the human
 * confirming it, exactly as it always has been.
 */
@Composable
private fun LiveObserverPanel(
    state: ScoringUiState,
    vm: ScoringViewModel,
    enabled: Boolean,
    haptic: (HapticCue) -> Unit,
) {
    // One-shot: cleared after every recorded keypad arrow, never sticky. A selection left over from
    // the previous arrow would silently mislabel the next one, which is exactly the fabricated
    // precision ObserverSector's own KDoc forbids.
    var selectedSector by rememberSaveable { mutableStateOf<ObserverSector?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Live Observer · tap or say", style = MaterialTheme.typography.titleMedium)
        Text(
            "Ring-only input stays SHOT_INFERRED; Crocodyl does not invent exact target coordinates.",
            color = Hyle.OnSurfaceDim,
        )
        SectorPicker(selectedSector, enabled) { selectedSector = it }
        Keypad(
            ScoreInput.keypad,
            {
                haptic(HapticCue.ARROW)
                vm.recordObserverToken(it, selectedSector?.name)
                selectedSector = null
            },
            enabled,
        )
        VoicePanel(state.voice, vm, enabled, haptic)
        if (state.pendingObserverEvents.isNotEmpty())
            PendingSpokenArrows(state.pendingObserverEvents, vm, enabled)
    }
}

/**
 * The 3×3 grid the blueprint's spoken grammar fixes ("8 bottom left"), so tap and voice write the
 * same nine-cell vocabulary into the same `observer_score_event.sector` column — a twelve-position
 * clock face would put two incompatible vocabularies in one column and nothing downstream could
 * read either of them reliably. A cell is a description of where the arrow landed, never a
 * coordinate: it is not a substitute for Plot and it never becomes a [xyz.mdhv.formanalyser.scoring.PlotPoint].
 *
 * One-shot by construction: [selected] lives in the caller ([LiveObserverPanel]) and is cleared
 * there after every keypad tap, so a stale selection cannot carry over onto an arrow it was never
 * meant to describe.
 */
@Composable
private fun SectorPicker(
    selected: ObserverSector?,
    enabled: Boolean,
    onSelect: (ObserverSector?) -> Unit,
) {
    val rows =
        listOf(
            listOf(ObserverSector.TOP_LEFT, ObserverSector.TOP_CENTER, ObserverSector.TOP_RIGHT),
            listOf(ObserverSector.MID_LEFT, ObserverSector.CENTER, ObserverSector.MID_RIGHT),
            listOf(ObserverSector.BOTTOM_LEFT, ObserverSector.BOTTOM_CENTER, ObserverSector.BOTTOM_RIGHT),
        )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Sector for the next tap (optional)",
            style = MaterialTheme.typography.labelMedium,
            color = Hyle.OnSurfaceDim,
        )
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { s ->
                    FilterChip(
                        selected = selected == s,
                        onClick = { onSelect(if (selected == s) null else s) },
                        enabled = enabled,
                        label = { Text(s.label) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * The mic control and the feedback for the listen that just finished.
 *
 * Unlike [CaptureScreen], permission is never requested on entering this screen — tap scoring
 * already works without it, so an unprompted mic dialog on a scoring screen would be hostile
 * instead of helpful. It is requested only from this button's own click, the first time it is
 * needed, via the identical [rememberLauncherForActivityResult] pattern CaptureScreen uses for the
 * camera. [ContextCompat.checkSelfPermission] is re-read at click time rather than trusted from
 * composition, so a permission revoked while the app was backgrounded is caught instead of handed
 * silently to a recogniser that will just fail.
 */
@Composable
private fun VoicePanel(
    voice: VoiceUiState,
    vm: ScoringViewModel,
    enabled: Boolean,
    haptic: (HapticCue) -> Unit,
) {
    val context = LocalContext.current
    val micLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // The tap that triggered the request should not be wasted on a second tap.
            if (granted) vm.startListening()
        }
    fun requestOrListen() {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) vm.startListening() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Fires once per genuine rejection, not once per (listening, rejection) value: startListening
    // always resets rejection to null while listening flips to true first, so two consecutive
    // identical rejections still pass through a distinct intermediate key and both buzz.
    LaunchedEffect(voice.listening, voice.rejection) {
        if (!voice.listening && voice.rejection != null) haptic(HapticCue.REJECT)
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (voice.availability) {
            ObserverVoice.Availability.UNSUPPORTED_LANGUAGE ->
                Text(
                    "Voice scoring understands English only right now. Tap scoring is unaffected.",
                    color = Hyle.OnSurfaceDim,
                )
            ObserverVoice.Availability.NO_ON_DEVICE_RECOGNIZER ->
                Text(
                    "Voice scoring needs an on-device recogniser (Android 13+ with offline speech installed for your language). Tap scoring is unaffected.",
                    color = Hyle.OnSurfaceDim,
                )
            ObserverVoice.Availability.LANGUAGE_UNAVAILABLE ->
                Text(
                    "This device's offline speech pack is not installed. Install it from the system settings, or keep using tap.",
                    color = Hyle.OnSurfaceDim,
                )
            ObserverVoice.Availability.NEEDS_PERMISSION,
            ObserverVoice.Availability.READY -> {
                OutlinedButton(onClick = ::requestOrListen, enabled = enabled && !voice.listening) {
                    Text(if (voice.listening) "Listening…" else "🎤 Tap to listen")
                }
                if (voice.availability == ObserverVoice.Availability.NEEDS_PERMISSION)
                    Text(
                        "Microphone permission is required for voice scoring. Tap scoring is unaffected.",
                        color = Hyle.OnSurfaceDim,
                    )
            }
        }
        voice.heard?.let { heard ->
            val reason = voice.rejection
            if (reason != null) {
                Text("Heard: \"$heard\" — ${reason.message}", color = Hyle.Warning)
                TextButton(onClick = { vm.clearVoiceFeedback(); requestOrListen() }) {
                    Text("Listen again")
                }
            } else {
                Text(
                    "Heard: \"$heard\"" +
                        if (voice.sectorDropped) " — sector not recognised, score kept." else "",
                    color = Hyle.OnSurfaceDim,
                )
            }
        }
    }
}

/**
 * Spoken arrows awaiting the human confirm the house invariant requires. Voice never becomes an
 * authoritative arrow on its own — see `ScoringRepository.confirmObserverEvents` — so every row here
 * costs one glance and either "Confirm all" or an individual reject.
 */
@Composable
private fun PendingSpokenArrows(
    pending: List<ObserverScoreEventEntity>,
    vm: ScoringViewModel,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Spoken, awaiting confirm", style = MaterialTheme.typography.titleSmall)
        pending.forEach { e ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    (if (e.isX) "X" else if (e.ring == 0) "M" else e.ring.toString()) +
                        (e.sector?.let { s ->
                            " · " + runCatching { ObserverSector.valueOf(s).label }.getOrDefault(s)
                        } ?: "")
                )
                TextButton(onClick = { vm.rejectSpokenArrow(e.id) }, enabled = enabled) { Text("✕") }
            }
        }
        Button(
            onClick = { vm.confirmSpokenArrows(pending.map { it.id }) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Confirm all (${pending.size})")
        }
    }
}

/**
 * The one place a machine-proposed score can become an arrow, and only by being pressed.
 *
 * The detector behind it ([xyz.mdhv.formanalyser.scoring.EndScan]) is derived from target geometry
 * and has never been run against a real range photograph, which is why the warning below stays put
 * and why every row still costs a deliberate tap. What the detector adds to each row is the material
 * for that decision: how sure it was, and whether the arrow sits close enough to a ring line that
 * only someone standing at the boss can settle it.
 */
@Composable
private fun EndScanPanel(
    candidates: List<xyz.mdhv.formanalyser.app.data.ScoreCandidateEntity>,
    vm: ScoringViewModel,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("End Scan review", style = MaterialTheme.typography.titleMedium)
        Text(
            "Automatic target detection is not yet range-validated. Candidates are proposals only; they never affect totals until you confirm them.",
            color = Hyle.OnSurfaceDim,
        )
        Button(onClick = vm::beginEndScan, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text("Photograph the target")
        }
        val proposed = candidates.filter { it.status == "PROPOSED" }
        if (proposed.isEmpty())
            Text(
                "No proposed candidates. Manual numeric/plot scoring remains authoritative.",
                color = Hyle.OnSurfaceDim,
            )
        proposed.forEach { c ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                if (c.isX) "X" else if (c.points == 0) "M" else c.points.toString(),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            c.confidence?.let {
                                Text(
                                    "${(it * 100).roundToInt()}% confident",
                                    color = Hyle.OnSurfaceDim,
                                )
                            }
                        }
                        Row {
                            TextButton(
                                onClick = { vm.rejectEndScanCandidate(c.id) },
                                enabled = enabled,
                            ) {
                                Text("Reject")
                            }
                            Button(
                                onClick = { vm.confirmEndScanCandidate(c.id) },
                                enabled = enabled,
                            ) {
                                Text("Confirm")
                            }
                        }
                    }
                    // Recomputed from the stored coordinates rather than read from a column: the
                    // flag is a function of the plot and the current tolerance, and a stored copy
                    // would keep asserting last release's answer after the tolerance moved.
                    val plotX = c.plotX
                    val plotY = c.plotY
                    val plotted = if (plotX != null && plotY != null) hypot(plotX, plotY) else null
                    if (plotted != null && EndScan.isLineCutter(plotted))
                        Text(
                            "On a ring line. A line-cutter scores the higher value and a photo cannot see whether it touches — check this one on the boss.",
                            color = Hyle.Warning,
                        )
                }
            }
        }
    }
}

/**
 * Opponent totals and the shoot-off decision for a set match.
 *
 * [pendingEnd] is the set awaiting a total ([Scorecard.pendingOpponentEndIndex]) — never
 * `currentEndIndex`, which has already advanced to the set about to be shot. Filing a total against
 * that future set leaves the set actually owed one unfilled, and `Scorecard.record` then refuses
 * every subsequent arrow, deadlocking the match after set 1. When nothing is outstanding the input
 * is hidden, but the shoot-off prompt still has to render, so it lives outside that branch.
 */
@Composable
private fun MatchControls(
    pendingEnd: Int?,
    vm: ScoringViewModel,
    summary: xyz.mdhv.formanalyser.scoring.SetMatchSummary?,
    enabled: Boolean,
) {
    var total by rememberSaveable(pendingEnd) { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (pendingEnd != null) {
            OutlinedTextField(
                total,
                { total = it.filter(Char::isDigit) },
                label = { Text("Opponent total for set ${pendingEnd + 1}") },
                enabled = enabled,
            )
            Button(
                onClick = {
                    total.toIntOrNull()?.let { vm.setOpponentEndTotal(pendingEnd, it) }
                    total = ""
                },
                enabled = enabled && total.toIntOrNull() in 0..30,
            ) {
                Text("Record opponent")
            }
        }
        if (summary?.completedSets == 5 && summary.athleteSetPoints == summary.opponentSetPoints) {
            Text("Shoot-off winner")
            Row {
                TextButton(
                    onClick = { vm.setShootOffWinner(SetMatchSummary.Winner.ATHLETE) },
                    enabled = enabled,
                ) {
                    Text("Athlete")
                }
                TextButton(
                    onClick = { vm.setShootOffWinner(SetMatchSummary.Winner.OPPONENT) },
                    enabled = enabled,
                ) {
                    Text("Opponent")
                }
            }
        }
    }
}

@Composable
private fun CustomRoundDialog(
    onDismiss: () -> Unit,
    onStart: (String, Int, Int, Int, Int) -> Unit,
) {
    var n by rememberSaveable { mutableStateOf("Custom practice") }
    var d by rememberSaveable { mutableStateOf("18") }
    var f by rememberSaveable { mutableStateOf("40") }
    var a by rememberSaveable { mutableStateOf("3") }
    var e by rememberSaveable { mutableStateOf("10") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom practice") },
        text = {
            Column {
                OutlinedTextField(n, { n = it }, label = { Text("Name") })
                OutlinedTextField(
                    d,
                    { d = it.filter(Char::isDigit) },
                    label = { Text("Distance m") },
                )
                OutlinedTextField(f, { f = it.filter(Char::isDigit) }, label = { Text("Face cm") })
                OutlinedTextField(
                    a,
                    { a = it.filter(Char::isDigit) },
                    label = { Text("Arrows/end") },
                )
                OutlinedTextField(e, { e = it.filter(Char::isDigit) }, label = { Text("Ends") })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onStart(
                        n,
                        d.toIntOrNull() ?: 18,
                        f.toIntOrNull() ?: 40,
                        a.toIntOrNull() ?: 3,
                        e.toIntOrNull() ?: 10,
                    )
                }
            ) {
                Text("Start")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
