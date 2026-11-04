package xyz.mdhv.formanalyser.app.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import xyz.mdhv.formanalyser.app.data.ScoringRepository
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.app.ui.theme.HyleSegmented
import xyz.mdhv.formanalyser.scoring.FaceLayout
import xyz.mdhv.formanalyser.scoring.PlotPoint
import xyz.mdhv.formanalyser.scoring.scoreFromPlot

private data class NormalizedTap(val x: Float, val y: Float)

/**
 * Human-calibrated End Scan. A target photo is calibrated with centre + outer-ring taps, after
 * which arrow taps are converted to exact normalized target coordinates and provisional ring
 * scores. Nothing changes the scorecard until the existing confirmation gate is accepted.
 */
@Composable
fun EndScanPhoto(
    maxArrows: Int,
    faceLayout: FaceLayout,
    enabled: Boolean,
    onCandidates: (List<ScoringRepository.EndScanCandidate>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var uri by remember { mutableStateOf<Uri?>(null) }
    var taps by remember(uri) { mutableStateOf<List<NormalizedTap>>(emptyList()) }
    var faceIndex by remember(uri) { mutableIntStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        if (picked != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    picked,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            uri = picked
        }
    }
    val bitmap by produceState<android.graphics.Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            uri?.let { selected ->
                context.contentResolver.openInputStream(selected)?.use(BitmapFactory::decodeStream)
            }
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { picker.launch(arrayOf("image/*")) }, enabled = enabled) {
                Text(if (uri == null) "Choose target photo" else "Replace photo")
            }
            if (taps.isNotEmpty()) {
                OutlinedButton(onClick = { taps = taps.dropLast(1) }, enabled = enabled) { Text("Undo tap") }
            }
        }
        if (faceLayout != FaceLayout.SINGLE) {
            HyleSegmented(listOf(0, 1, 2), faceIndex, { "Face ${it + 1}" }) { faceIndex = it }
        }
        val image = bitmap
        if (image != null) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp))
                    .background(Hyle.SurfaceVariant)
            ) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = "Target photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Canvas(
                    Modifier.fillMaxSize().pointerInput(enabled, taps, maxArrows) {
                        if (!enabled) return@pointerInput
                        detectTapGestures { point ->
                            val arrowCount = (taps.size - 2).coerceAtLeast(0)
                            if (taps.size < 2 || arrowCount < maxArrows) {
                                taps = taps + NormalizedTap(point.x / size.width, point.y / size.height)
                            }
                        }
                    }
                ) {
                    fun at(tap: NormalizedTap) = Offset(tap.x * size.width, tap.y * size.height)
                    taps.getOrNull(0)?.let { centre ->
                        val c = at(centre)
                        drawCircle(Hyle.AlienCyan, 8f, c)
                        drawLine(Hyle.AlienCyan, c - Offset(16f, 0f), c + Offset(16f, 0f), 2f)
                        drawLine(Hyle.AlienCyan, c - Offset(0f, 16f), c + Offset(0f, 16f), 2f)
                        taps.getOrNull(1)?.let { edge ->
                            val e = at(edge)
                            val radius = hypot(e.x - c.x, e.y - c.y)
                            drawCircle(Hyle.AlienCyan.copy(alpha = .8f), radius, c, style = Stroke(3f))
                        }
                    }
                    taps.drop(2).forEachIndexed { index, tap ->
                        val p = at(tap)
                        drawCircle(Color.White, 11f, p)
                        drawCircle(Color.Black, 11f, p, style = Stroke(3f))
                        drawCircle(Hyle.Accent, 4f, p)
                    }
                }
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(10.dp),
                    color = Hyle.Surface.copy(alpha = .9f),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        when (taps.size) {
                            0 -> "1 · TAP TARGET CENTRE"
                            1 -> "2 · TAP OUTER RING EDGE"
                            else -> "3 · TAP EACH ARROW (${taps.size - 2}/$maxArrows)"
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = Hyle.OnBackground,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            val arrowTaps = taps.drop(2)
            Button(
                onClick = {
                    val centre = taps[0]
                    val edge = taps[1]
                    val radius = hypot(edge.x - centre.x, edge.y - centre.y).coerceAtLeast(.001f)
                    val proposals = arrowTaps.map { arrow ->
                        val plot = PlotPoint(
                            x = ((arrow.x - centre.x) / radius).toDouble(),
                            y = ((centre.y - arrow.y) / radius).toDouble(),
                            faceIndex = faceIndex,
                        )
                        val score = scoreFromPlot(plot, faceLayout)
                        ScoringRepository.EndScanCandidate(
                            points = score.points,
                            isX = score.isX,
                            plot = plot,
                            confidence = null,
                        )
                    }
                    onCandidates(proposals)
                    taps = emptyList()
                },
                enabled = enabled && taps.size >= 3,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Create ${arrowTaps.size} review candidate${if (arrowTaps.size == 1) "" else "s"}")
            }
        } else {
            Text(
                "Use a clear, straight-on target photo. Crocodyl calculates rings after you mark the target and arrows.",
                color = Hyle.OnSurfaceDim,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
