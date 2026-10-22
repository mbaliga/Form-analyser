package xyz.mdhv.formanalyser.app

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.mdhv.crocodyl.fencing.FencerSide
import xyz.mdhv.crocodyl.fencing.FencingWeapon
import xyz.mdhv.crocodyl.fencing.RefereeCall
import xyz.mdhv.crocodyl.fencing.RefereeMatchState
import xyz.mdhv.crocodyl.fencing.RefereePhase
import xyz.mdhv.formanalyser.app.ui.theme.FormAnalyserTheme
import xyz.mdhv.formanalyser.app.ui.theme.Hyle

/** Fencing's first production slice: a human-operated, auditable referee console. */
class FencingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FormAnalyserTheme { FencingScaffold(onBack = ::finish) } }
    }
}

@Composable
private fun FencingScaffold(onBack: () -> Unit) {
    val viewModel: FencingViewModel = viewModel()
    val uiState by viewModel.state
    val selectedWeapon = uiState.selectedWeapon
    val state = uiState.match
    val leftName = uiState.leftName
    val rightName = uiState.rightName

    Scaffold(containerColor = Hyle.Background) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { FencingHero(onBack) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionEyebrow("WEAPON STUDY")
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FencingWeapon.entries.forEach { weapon ->
                            FilterChip(
                                selected = weapon == selectedWeapon,
                                onClick = { viewModel.selectWeapon(weapon) },
                                label = { Text(weapon.displayName) },
                            )
                        }
                    }
                }
            }
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Hyle.SurfaceRich),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Hyle.HairlineStrong),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("${selectedWeapon.displayName.uppercase()} · QUICK REFERENCE", color = Hyle.RadiumGreen, style = MaterialTheme.typography.labelSmall)
                        Text("Target", color = Hyle.OnBackground.copy(alpha = .72f), style = MaterialTheme.typography.labelMedium)
                        Text(selectedWeapon.targetSummary, color = Hyle.OnBackground, style = MaterialTheme.typography.titleMedium)
                        Text("Priority", color = Hyle.OnBackground.copy(alpha = .72f), style = MaterialTheme.typography.labelMedium)
                        Text(selectedWeapon.priorityRule, color = Hyle.OnBackground)
                        Text(selectedWeapon.equipmentSummary, color = Hyle.OnBackground.copy(alpha = .78f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                SectionEyebrow("BOUT SETUP")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = leftName,
                        onValueChange = viewModel::setLeftName,
                        label = { Text("Left fencer") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = rightName,
                        onValueChange = viewModel::setRightName,
                        label = { Text("Right fencer") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
            }
            item { Scoreboard(state, leftName, rightName) }
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Hyle.Surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Hyle.Accent.copy(alpha = .24f)),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SectionEyebrow("MANUAL REFEREE")
                            Text("Call the bout.", style = MaterialTheme.typography.titleLarge, color = Hyle.OnBackground)
                            Text(
                                "Human-operated calls and points. Camera adjudication is not connected; every point is explicit and undoable.",
                                color = Hyle.OnBackground.copy(alpha = .78f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = viewModel::ready, modifier = Modifier.weight(1f)) {
                                Text(RefereeCall.PRET.spoken)
                            }
                            Button(
                                onClick = viewModel::start,
                                enabled = state.phase != RefereePhase.IN_PROGRESS,
                                modifier = Modifier.weight(1f),
                            ) { Text(RefereeCall.ALLEZ.spoken) }
                            OutlinedButton(onClick = viewModel::halt, modifier = Modifier.weight(1f)) {
                                Text(RefereeCall.HALT.spoken)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.award(FencerSide.LEFT) },
                                enabled = state.phase == RefereePhase.IN_PROGRESS,
                                modifier = Modifier.weight(1f).height(56.dp),
                            ) { Text("Point · ${leftName.ifBlank { "Left" }}", textAlign = TextAlign.Center) }
                            Button(
                                onClick = { viewModel.award(FencerSide.RIGHT) },
                                enabled = state.phase == RefereePhase.IN_PROGRESS,
                                modifier = Modifier.weight(1f).height(56.dp),
                            ) { Text("Point · ${rightName.ifBlank { "Right" }}", textAlign = TextAlign.Center) }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            OutlinedButton(onClick = viewModel::undoLastPoint) { Text("Undo last point") }
                            OutlinedButton(onClick = viewModel::reset) { Text("Reset bout") }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionEyebrow("CALL HISTORY")
                    Text("A clear record of the bout", style = MaterialTheme.typography.titleLarge, color = Hyle.OnBackground)
                    if (state.history.isEmpty()) {
                        Text("No calls yet. Begin with Prêt, then Allez.", color = Hyle.OnSurfaceDim)
                    }
                }
            }
            items(state.history.asReversed()) { decision ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Hyle.Surface),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(Hyle.AccentBright))
                        Text(
                            decision.call.spoken + (decision.pointWinner?.let { " · ${sideName(it, leftName, rightName)}" } ?: ""),
                            Modifier.padding(start = 12.dp),
                            color = Hyle.OnBackground,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun FencingHero(onBack: () -> Unit) {
    val context = LocalContext.current
    val photograph = remember {
        mutableStateOf<ImageBitmap?>(null)
    }
    LaunchedEffect(Unit) {
        photograph.value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("atelier/cinematic/fencing-training.jpg").use {
                    BitmapFactory.decodeStream(it)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    Box(
        Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(28.dp))
            .background(Hyle.SurfaceDeep).border(1.dp, Hyle.HairlineStrong, RoundedCornerShape(28.dp)),
    ) {
        photograph.value?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color(0xff08090d).copy(alpha = .94f),
                    .55f to Color(0xff08090d).copy(alpha = .64f),
                    1f to Color(0xff08090d).copy(alpha = .08f),
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Transparent, .55f to Color.Transparent, 1f to Color(0xff08090d).copy(alpha = .5f)),
            ),
        )
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = .4f))
                    .semantics { contentDescription = "Back" },
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Column(Modifier.widthIn(max = 310.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("FENCING  /  THE PISTE", color = Hyle.RadiumGreen, fontSize = 10.sp, letterSpacing = 1.6.sp)
                Text("Read the moment.", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text("Sabre · Foil · Epee", color = Color.White.copy(alpha = .86f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Scoreboard(state: RefereeMatchState, leftName: String, rightName: String) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xff171421)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Hyle.Accent.copy(alpha = .25f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionEyebrow("LIVE BOUT")
                Text(
                    state.phase.name.replace('_', ' '),
                    Modifier.clip(RoundedCornerShape(50)).background(Hyle.SurfaceVariant).padding(horizontal = 11.dp, vertical = 6.dp),
                    color = Hyle.OnBackground,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                ScoreColumn(leftName.ifBlank { "Left" }, state.leftScore, "Left fencer")
                Text("—", color = Hyle.OnSurfaceDim, style = MaterialTheme.typography.headlineMedium)
                ScoreColumn(rightName.ifBlank { "Right" }, state.rightScore, "Right fencer")
            }
        }
    }
}

@Composable
private fun ScoreColumn(name: String, score: Int, sideDescription: String) {
    Column(
        Modifier.widthIn(min = 100.dp).semantics { contentDescription = "$sideDescription $name, $score points" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium, color = Hyle.OnBackground, maxLines = 1)
        Text(score.toString(), style = MaterialTheme.typography.displaySmall, color = Hyle.AccentBright, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionEyebrow(label: String) {
        Text(label, color = Hyle.OnBackground.copy(alpha = .72f), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.2.sp)
}

private fun sideName(side: FencerSide, leftName: String, rightName: String): String =
    if (side == FencerSide.LEFT) leftName.ifBlank { "left fencer" } else rightName.ifBlank { "right fencer" }
