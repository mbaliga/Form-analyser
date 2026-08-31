package xyz.mdhv.formanalyser.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import xyz.mdhv.formanalyser.app.data.SessionEntity
import xyz.mdhv.formanalyser.app.domain.ScoringInputMode
import xyz.mdhv.formanalyser.app.domain.ScoringViewModel
import xyz.mdhv.formanalyser.app.ui.components.TargetFaceCanvas
import xyz.mdhv.formanalyser.app.ui.theme.HapticCue
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.app.ui.theme.HyleListRow
import xyz.mdhv.formanalyser.app.ui.theme.rememberHaptics
import xyz.mdhv.formanalyser.scoring.RoundPack
import xyz.mdhv.formanalyser.scoring.ScoreInput
import xyz.mdhv.formanalyser.scoring.SetMatchSummary

@Composable
fun ScoringScreen(vm: ScoringViewModel) {
    LaunchedEffect(Unit) { vm.load() }
    val state by vm.state.collectAsState()
    val snapshot = state.snapshot
    val haptic = rememberHaptics()
    var chooser by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }
    var linking by remember { mutableStateOf(false) }

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
                ScoringInputMode.OBSERVER ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Live Observer · tap",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "Ring-only input stays SHOT_INFERRED; Crocodyl does not invent exact target coordinates.",
                                color = Hyle.OnSurfaceDim,
                            )
                            Keypad(
                                ScoreInput.keypad,
                                {
                                    haptic(HapticCue.ARROW)
                                    vm.recordObserverToken(it)
                                },
                                canScore,
                            )
                        }
                    }
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

@Composable
private fun EndScanPanel(
    candidates: List<xyz.mdhv.formanalyser.app.data.ScoreCandidateEntity>,
    vm: ScoringViewModel,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("End Scan review", style = MaterialTheme.typography.titleMedium)
        Text(
            "Automatic target detection is not yet range-validated. Only proposed candidates from a validated detector may appear here; they never affect totals until you confirm them.",
            color = Hyle.OnSurfaceDim,
        )
        if (candidates.isEmpty())
            Text(
                "No proposed candidates. Manual numeric/plot scoring remains authoritative.",
                color = Hyle.OnSurfaceDim,
            )
        candidates
            .filter { it.status == "PROPOSED" }
            .forEach { c ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(if (c.isX) "X" else if (c.points == 0) "M" else c.points.toString())
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
    var total by remember(pendingEnd) { mutableStateOf("") }
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
    var n by remember { mutableStateOf("Custom practice") }
    var d by remember { mutableStateOf("18") }
    var f by remember { mutableStateOf("40") }
    var a by remember { mutableStateOf("3") }
    var e by remember { mutableStateOf("10") }
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
