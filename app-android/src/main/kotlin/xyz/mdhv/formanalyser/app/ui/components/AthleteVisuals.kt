package xyz.mdhv.formanalyser.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.mdhv.formanalyser.app.domain.ShotView
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.archery.FormFeatureExtractor

@Composable
fun CrocodylHero(
    athleteName: String,
    readinessLabel: String,
    readinessDetail: String?,
    ready: Boolean,
    streak: Int?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(224.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.linearGradient(
                    listOf(Hyle.SurfaceRich, Hyle.Surface, Hyle.SurfaceDeep),
                    start = Offset.Zero,
                    end = Offset(1100f, 700f),
                )
            )
            .border(1.dp, Hyle.HairlineStrong, RoundedCornerShape(30.dp)),
    ) {
        TargetConstellation(Modifier.fillMaxSize().align(Alignment.CenterEnd))
        Column(
            Modifier.fillMaxHeight().padding(22.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("TODAY · ATHLETE", color = Hyle.OnSurfaceDim, fontSize = 11.sp, letterSpacing = 1.7.sp)
                Text(athleteName, style = MaterialTheme.typography.headlineMedium, color = Hyle.OnBackground)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.size(9.dp).clip(CircleShape)
                            .background(if (ready) Hyle.RadiumGreen else Hyle.Warning)
                    )
                    Text(readinessLabel, style = MaterialTheme.typography.titleLarge, color = Hyle.OnBackground)
                }
                readinessDetail?.let {
                    Text(it, color = Hyle.OnSurfaceDim, style = MaterialTheme.typography.bodyMedium)
                }
                streak?.let {
                    Text("$it day rhythm", color = Hyle.RadiumGreen, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun TargetConstellation(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val center = Offset(size.width * .82f, size.height * .43f)
        listOf(.37f, .28f, .19f, .10f).forEachIndexed { index, fraction ->
            drawCircle(
                color = if (index == 3) Hyle.RadiumGreen.copy(alpha = .32f) else Hyle.Accent.copy(alpha = .16f - index * .02f),
                radius = size.minDimension * fraction,
                center = center,
                style = if (index == 3) Stroke(3f) else Stroke(1.5f),
            )
        }
        drawLine(Hyle.OnBackground.copy(alpha = .22f), Offset(center.x - 120f, center.y + 112f), Offset(center.x + 42f, center.y - 34f), 2f)
        drawCircle(Hyle.OnBackground.copy(alpha = .6f), 4f, center)
    }
}

@Composable
fun AthleteActionTile(
    title: String,
    detail: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(126.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Hyle.Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .18f)),
    ) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(26.dp))
            Column {
                Text(title, color = Hyle.OnBackground, fontWeight = FontWeight.SemiBold)
                Text(detail, color = Hyle.OnSurfaceDim, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun CaptureHowTo(modifier: Modifier = Modifier) {
    Card(
        modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Hyle.SurfaceDeep),
        border = androidx.compose.foundation.BorderStroke(1.dp, Hyle.Accent.copy(alpha = .2f)),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.CameraAlt, null, tint = Hyle.AccentBright)
                Column {
                    Text("Set up the shot", color = Hyle.OnBackground, fontWeight = FontWeight.SemiBold)
                    Text("A clean side view makes every measurement better.", color = Hyle.OnSurfaceDim, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GuideStep("1", "Full body", "Head to feet", Modifier.weight(1f))
                GuideStep("2", "Side-on", "90° to target", Modifier.weight(1f))
                GuideStep("3", "Stable", "Phone at waist", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GuideStep(number: String, title: String, detail: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Hyle.SurfaceVariant.copy(alpha = .65f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(number, color = Hyle.AccentBright, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text(title, color = Hyle.OnBackground, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(detail, color = Hyle.OnSurfaceDim, fontSize = 10.sp, lineHeight = 12.sp)
    }
}

/** Camera overlay: floor, safe frame and a side-on archer schematic. */
@Composable
fun CaptureFramingOverlay(tracking: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val ink = if (tracking) Hyle.RadiumGreen else Color.White.copy(alpha = .78f)
        val margin = size.width * .1f
        val corner = size.width * .09f
        val stroke = 3f
        fun cornerLines(x: Float, y: Float, sx: Float, sy: Float) {
            drawLine(ink, Offset(x, y), Offset(x + sx * corner, y), stroke)
            drawLine(ink, Offset(x, y), Offset(x, y + sy * corner), stroke)
        }
        cornerLines(margin, margin, 1f, 1f)
        cornerLines(size.width - margin, margin, -1f, 1f)
        cornerLines(margin, size.height - margin, 1f, -1f)
        cornerLines(size.width - margin, size.height - margin, -1f, -1f)
        val floorY = size.height * .87f
        drawLine(ink.copy(alpha = .55f), Offset(margin, floorY), Offset(size.width - margin, floorY), 2f)

        val x = size.width * .51f
        val head = Offset(x, size.height * .19f)
        drawCircle(ink.copy(alpha = .26f), size.width * .045f, head, style = Stroke(3f))
        val shoulder = Offset(x, size.height * .31f)
        val hip = Offset(x - size.width * .015f, size.height * .56f)
        drawLine(ink.copy(alpha = .35f), shoulder, hip, 5f)
        drawLine(ink.copy(alpha = .35f), Offset(x - size.width * .11f, size.height * .33f), Offset(x + size.width * .19f, size.height * .34f), 5f)
        drawLine(ink.copy(alpha = .35f), hip, Offset(x - size.width * .08f, floorY), 5f)
        drawLine(ink.copy(alpha = .35f), hip, Offset(x + size.width * .1f, floorY), 5f)
        val bow = Path().apply {
            moveTo(x + size.width * .19f, size.height * .22f)
            quadraticBezierTo(x + size.width * .3f, size.height * .34f, x + size.width * .19f, size.height * .49f)
        }
        drawPath(bow, ink.copy(alpha = .45f), style = Stroke(3f))
    }
}

@Composable
fun ImprovementAreas(shots: List<ShotView>, modifier: Modifier = Modifier) {
    val ranked = shots.mapNotNull { it.topDeviationFeature }.groupingBy { it }.eachCount()
        .entries.sortedByDescending { it.value }.take(3)
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Hyle.SurfaceRich),
        border = androidx.compose.foundation.BorderStroke(1.dp, Hyle.Accent.copy(alpha = .24f)),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.DirectionsRun, null, tint = Hyle.AccentBright)
                Column {
                    Text("Improvement areas", color = Hyle.OnBackground, style = MaterialTheme.typography.titleMedium)
                    Text("Prioritised from this session—not a generic checklist.", color = Hyle.OnSurfaceDim, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (ranked.isEmpty()) {
                Text(
                    if (shots.isEmpty()) "Capture an end to reveal your highest-leverage form cues."
                    else "Build your baseline to separate normal variation from a real correction.",
                    color = Hyle.OnSurfaceDim,
                )
            } else ranked.forEachIndexed { index, entry ->
                val cue = improvementCue(entry.key)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(Hyle.SurfaceVariant.copy(alpha = .62f)).padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(Hyle.Accent.copy(alpha = .2f)),
                        contentAlignment = Alignment.Center,
                    ) { Text("${index + 1}", color = Hyle.AccentBright, fontWeight = FontWeight.Bold) }
                    Column(Modifier.weight(1f)) {
                        Text(cue.first, color = Hyle.OnBackground, fontWeight = FontWeight.SemiBold)
                        Text(cue.second, color = Hyle.OnSurfaceDim, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${entry.value}×", color = Hyle.RadiumGreen, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun improvementCue(feature: String): Pair<String, String> = when (feature) {
    FormFeatureExtractor.BOW_ARM_ANGLE -> "Bow-arm line" to "Keep shoulder, elbow and wrist long through release."
    FormFeatureExtractor.DRAW_ELBOW_ANGLE -> "Draw elbow" to "Bring the elbow into the arrow line at anchor."
    FormFeatureExtractor.DRAW_ARM_TILT -> "Draw-side alignment" to "Keep the upper arm close to shoulder height."
    FormFeatureExtractor.SHOULDER_TILT -> "Shoulder level" to "Set a quiet, level shoulder line before drawing."
    FormFeatureExtractor.HEAD_LEAN -> "Head position" to "Bring the string to the face; avoid chasing it."
    FormFeatureExtractor.SPINE_LEAN -> "Posture" to "Stack ribs over pelvis and stay tall through expansion."
    FormFeatureExtractor.STANCE_WIDTH -> "Stance" to "Build a repeatable base near shoulder width."
    FormFeatureExtractor.HOLD_DURATION_S -> "Hold rhythm" to "Keep the aiming window decisive and repeatable."
    FormFeatureExtractor.DRAW_DURATION_S -> "Draw rhythm" to "Use one smooth tempo from set-up to anchor."
    else -> feature.replace(Regex("([a-z])([A-Z])"), "$1 $2").replaceFirstChar(Char::uppercase) to "Repeat the next end and watch whether this signal settles."
}

val ScoreActionIcon = Icons.Filled.TrackChanges
