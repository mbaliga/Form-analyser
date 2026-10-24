package xyz.mdhv.formanalyser.app

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import xyz.mdhv.crocodyl.bouldering.BoulderOutcome
import xyz.mdhv.crocodyl.bouldering.BoulderProblemAttempt
import xyz.mdhv.crocodyl.bouldering.BoulderStyle
import xyz.mdhv.crocodyl.bouldering.BoulderingModule
import xyz.mdhv.crocodyl.bouldering.ClimbingGradeSystem
import xyz.mdhv.crocodyl.muaythai.MuayThaiModule
import xyz.mdhv.crocodyl.muaythai.MuayThaiRound
import xyz.mdhv.crocodyl.muaythai.MuayThaiRoundFocus
import xyz.mdhv.formanalyser.app.data.SportSessionEntity
import xyz.mdhv.formanalyser.app.ui.theme.FormAnalyserTheme

class MuayThaiActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FormAnalyserTheme { SportJournalScreen("muay_thai", ::finish) } }
    }
}

class BoulderingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FormAnalyserTheme { SportJournalScreen("bouldering", ::finish) } }
    }
}

@Composable
private fun SportJournalScreen(sportId: String, onBack: () -> Unit) {
    val vm: SportJournalViewModel = viewModel()
    val state by vm.state.collectAsState()
    LaunchedEffect(sportId) { vm.observe(sportId) }

    Scaffold(containerColor = Color(0xff0c0b0d)) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            if (sportId == "muay_thai") MuayThaiHero() else BoulderingHero()
            if (sportId == "muay_thai") MuayThaiForm(vm, state.saving) else BoulderingForm(vm, state.saving)
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
            EquipmentNotes(sportId)
            Text("RECENT SESSIONS", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .68f))
            if (state.sessions.isEmpty()) {
                Text("Your first entry will appear here.", color = Color.White.copy(alpha = .68f))
            } else {
                state.sessions.forEach { TrainingRecordCard(it) }
            }
            Text(
                "Manual training notes only. Crocodyl does not assess technique or give fight or climbing safety advice here.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = .55f),
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MuayThaiHero() = SportHero(
    imagePath = "atelier/cinematic/muay-thai-training.jpg",
    eyebrow = "MUAY THAI · TRAINING JOURNAL",
    title = "Find your rhythm.",
    subtitle = "Rounds, focus and how the work felt.",
    accent = Color(0xffffb26b),
)

@Composable
private fun BoulderingHero() = SportHero(
    imagePath = "atelier/cinematic/bouldering-session.jpg",
    eyebrow = "BOULDERING · TRAINING JOURNAL",
    title = "One move at a time.",
    subtitle = "Keep the grade system, attempts and sends in your own terms.",
    accent = Color(0xffb6a5ff),
)

@Composable
private fun SportHero(imagePath: String, eyebrow: String, title: String, subtitle: String, accent: Color) {
    val context = LocalContext.current
    var poster by remember(imagePath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(imagePath) {
        poster = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                context.assets.open(imagePath).use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    Box(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(26.dp))) {
        poster?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color(0xff0b0a0e).copy(alpha = .94f), Color(0xff0b0a0e).copy(alpha = .54f), Color.Transparent))
            )
        )
        Column(Modifier.padding(22.dp).widthIn(max = 300.dp).align(androidx.compose.ui.Alignment.CenterStart)) {
            Text(eyebrow, color = accent, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(12.dp))
            Text(title, color = Color.White, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, color = Color.White.copy(alpha = .82f), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun MuayThaiForm(vm: SportJournalViewModel, saving: Boolean) {
    var focus by rememberSaveable { mutableStateOf(MuayThaiRoundFocus.SHADOWBOXING) }
    var roundsText by rememberSaveable { mutableStateOf("3") }
    var workMinutesText by rememberSaveable { mutableStateOf("3") }
    var restSecondsText by rememberSaveable { mutableStateOf("60") }
    var effort by rememberSaveable { mutableIntStateOf(5) }
    val rounds = roundsText.toIntOrNull() ?: 0
    val workMinutes = workMinutesText.toIntOrNull() ?: 0
    val restSeconds = restSecondsText.toIntOrNull() ?: -1
    val valid = rounds in 1..20 && workMinutes in 1..15 && restSeconds in 0..600

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Build a round log", style = MaterialTheme.typography.titleLarge)
            Text("Focus", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MuayThaiRoundFocus.entries.take(3).forEach { option ->
                    FilterChip(focus == option, { focus = option }, label = { Text(option.label) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MuayThaiRoundFocus.entries.drop(3).forEach { option ->
                    FilterChip(focus == option, { focus = option }, label = { Text(option.label) })
                }
            }
            NumericInput("Rounds", roundsText, { roundsText = it }, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumericInput("Work · min", workMinutesText, { workMinutesText = it }, Modifier.weight(1f))
                NumericInput("Rest · sec", restSecondsText, { restSecondsText = it }, Modifier.weight(1f))
            }
            Text("Session effort · RPE $effort", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(effort.toFloat(), { effort = it.toInt().coerceIn(1, 10) }, valueRange = 1f..10f, steps = 8)
            Button(
                onClick = {
                    val entries = (1..rounds).map { index ->
                        MuayThaiRound(index, focus, workMinutes * 60, restSeconds, effort)
                    }
                    val features = entries.flatMap { MuayThaiModule.extractor.extract(it).entries }
                        .groupBy({ it.key }, { it.value }).mapValues { (_, values) -> values.average() }
                    val detail = JsonObject(mapOf(
                        "schemaVersion" to JsonPrimitive(1),
                        "focus" to JsonPrimitive(focus.id),
                        "rounds" to JsonArray(entries.map { round ->
                            JsonObject(mapOf(
                                "index" to JsonPrimitive(round.index),
                                "workSeconds" to JsonPrimitive(round.workSeconds),
                                "restSeconds" to JsonPrimitive(round.restSeconds),
                                "effortRpe" to (round.perceivedEffortRpe?.let { JsonPrimitive(it) } ?: JsonNull),
                            ))
                        }),
                    )).toString()
                    vm.save(
                        sportId = "muay_thai",
                        durationSeconds = entries.sumOf { it.workSeconds + it.restSeconds } - restSeconds,
                        perceivedEffortRpe = effort,
                        summary = "${focus.label} · $rounds rounds",
                        detailsJson = detail,
                        features = features,
                    )
                },
                enabled = valid && !saving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (saving) "Saving…" else "Save training session") }
        }
    }
}

@Composable
private fun BoulderingForm(vm: SportJournalViewModel, saving: Boolean) {
    var gradeSystem by rememberSaveable { mutableStateOf(ClimbingGradeSystem.V_SCALE) }
    var style by rememberSaveable { mutableStateOf(BoulderStyle.UNKNOWN) }
    var outcome by rememberSaveable { mutableStateOf(BoulderOutcome.FALL) }
    var grade by rememberSaveable { mutableStateOf("") }
    var attemptsText by rememberSaveable { mutableStateOf("1") }
    var durationText by rememberSaveable { mutableStateOf("90") }
    var effort by rememberSaveable { mutableIntStateOf(5) }
    val attempts = attemptsText.toIntOrNull() ?: 0
    val durationMinutes = durationText.toIntOrNull() ?: -1
    val valid = grade.isNotBlank() && attempts in 1..100 && durationMinutes in 0..1440

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Log a problem", style = MaterialTheme.typography.titleLarge)
            Text("Grade system", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ClimbingGradeSystem.entries.forEach { option ->
                    FilterChip(gradeSystem == option, { gradeSystem = option }, label = { Text(option.label) })
                }
            }
            OutlinedTextField(grade, { grade = it.take(24) }, label = { Text("Grade label (for example, V2 or 6A+) ") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("Wall style", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BoulderStyle.entries.take(3).forEach { option ->
                    FilterChip(style == option, { style = option }, label = { Text(option.label) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BoulderStyle.entries.drop(3).forEach { option ->
                    FilterChip(style == option, { style = option }, label = { Text(option.label) })
                }
            }
            Text("Outcome", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BoulderOutcome.entries.take(2).forEach { option ->
                    FilterChip(outcome == option, { outcome = option }, label = { Text(option.label) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BoulderOutcome.entries.drop(2).forEach { option ->
                    FilterChip(outcome == option, { outcome = option }, label = { Text(option.label) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumericInput("Attempts", attemptsText, { attemptsText = it }, Modifier.weight(1f))
                NumericInput("Session · min", durationText, { durationText = it }, Modifier.weight(1f))
            }
            Text("Session effort · RPE $effort", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(effort.toFloat(), { effort = it.toInt().coerceIn(1, 10) }, valueRange = 1f..10f, steps = 8)
            Button(
                onClick = {
                    val problem = BoulderProblemAttempt(
                        id = UUID.randomUUID().toString(),
                        gradeSystem = gradeSystem,
                        gradeLabel = grade.trim(),
                        style = style,
                        attempts = attempts,
                        outcome = outcome,
                        perceivedEffortRpe = effort,
                    )
                    val details = JsonObject(mapOf(
                        "schemaVersion" to JsonPrimitive(1),
                        "problems" to JsonArray(listOf(JsonObject(mapOf(
                            "id" to JsonPrimitive(problem.id),
                            "gradeSystem" to JsonPrimitive(problem.gradeSystem.id),
                            "gradeLabel" to JsonPrimitive(problem.gradeLabel),
                            "style" to JsonPrimitive(problem.style.id),
                            "attempts" to JsonPrimitive(problem.attempts),
                            "outcome" to JsonPrimitive(problem.outcome.id),
                        )))),
                    )).toString()
                    vm.save(
                        sportId = "bouldering",
                        durationSeconds = durationMinutes * 60,
                        perceivedEffortRpe = effort,
                        summary = "${gradeSystem.label} ${problem.gradeLabel} · ${problem.outcome.label}",
                        detailsJson = details,
                        features = BoulderingModule.extractor.extract(problem),
                    )
                },
                enabled = valid && !saving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (saving) "Saving…" else "Save session") }
        }
    }
}

@Composable
private fun EquipmentNotes(sportId: String) {
    val title: String
    val detail: String
    if (sportId == "muay_thai") {
        title = "Training kit"
        detail = "Hand wraps · gloves · mouthguard · shin guards for contact sessions. Follow your coach and gym’s requirements."
    } else {
        title = "Climbing kit"
        detail = "Climbing shoes · chalk and bag · brush. Check the gym’s rules for chalk, mats and route etiquette."
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NumericInput(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(4)) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        singleLine = true,
    )
}

@Composable
private fun TrainingRecordCard(record: SportSessionEntity) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(record.summary, style = MaterialTheme.typography.titleMedium)
            Text(
                "${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(record.startedAtEpochMs))} · " +
                    "${record.durationSeconds / 60} min · RPE ${record.perceivedEffortRpe ?: "—"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
