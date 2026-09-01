package xyz.mdhv.formanalyser.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.hypot
import kotlin.math.min
import xyz.mdhv.formanalyser.scoring.*

@Composable
fun TargetFaceCanvas(
    arrows: List<ScoredArrow>,
    layout: FaceLayout,
    enabled: Boolean,
    onPlot: (PlotPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier.fillMaxWidth().aspectRatio(1f).pointerInput(enabled, layout) {
            if (!enabled) return@pointerInput
            detectTapGestures { tap ->
                val faces = faceGeometry(size.width.toFloat(), size.height.toFloat(), layout)
                val best =
                    faces.minByOrNull { f ->
                        hypot((tap.x - f.center.x).toDouble(), (tap.y - f.center.y).toDouble()) /
                            f.radius
                    } ?: return@detectTapGestures
                val x = ((tap.x - best.center.x) / best.radius).toDouble()
                val y = ((best.center.y - tap.y) / best.radius).toDouble()
                if (x in -1.5..1.5 && y in -1.5..1.5) onPlot(PlotPoint(x, y, best.index))
            }
        }
    ) {
        val faces = faceGeometry(size.width, size.height, layout)
        faces.forEach { drawTargetFace(it, layout != FaceLayout.SINGLE) }
        arrows.forEachIndexed { i, a ->
            val p = a.plot ?: return@forEachIndexed
            val f = faces.getOrNull(p.faceIndex) ?: faces.first()
            val at =
                Offset(
                    f.center.x + (p.x * f.radius).toFloat(),
                    f.center.y - (p.y * f.radius).toFloat(),
                )
            drawCircle(Color.White, 7f, at)
            drawCircle(Color.Black, 7f, at, style = Stroke(2f))
            drawCircle(if (i == arrows.lastIndex) Color.Black else Color.DarkGray, 2.5f, at)
        }
    }
}

private data class FaceGeometry(val index: Int, val center: Offset, val radius: Float)

private fun faceGeometry(w: Float, h: Float, l: FaceLayout) =
    when (l) {
        FaceLayout.SINGLE -> listOf(FaceGeometry(0, Offset(w / 2, h / 2), min(w, h) * .46f))
        FaceLayout.VERTICAL_TRIPLE -> {
            val r = min(w * .62f, h / 3.05f)
            listOf(
                FaceGeometry(0, Offset(w / 2, h / 6), r),
                FaceGeometry(1, Offset(w / 2, h / 2), r),
                FaceGeometry(2, Offset(w / 2, h * 5 / 6), r),
            )
        }
        FaceLayout.TRIANGULAR_TRIPLE -> {
            val r = min(w, h) * .32f
            listOf(
                FaceGeometry(0, Offset(w / 2, h * .26f), r),
                FaceGeometry(1, Offset(w * .30f, h * .70f), r),
                FaceGeometry(2, Offset(w * .70f, h * .70f), r),
            )
        }
    }

private fun DrawScope.drawTargetFace(f: FaceGeometry, triple: Boolean) {
    for (score in if (triple) 6..10 else 1..10) {
        val r = f.radius * ((11 - score) / 10f)
        drawCircle(targetColour(score), r, f.center)
        drawCircle(Color.Black.copy(alpha = .42f), r, f.center, style = Stroke(1.2f))
    }
    drawCircle(Color.Black.copy(alpha = .55f), f.radius * .05f, f.center, style = Stroke(1.5f))
}

private fun targetColour(s: Int) =
    when (s) {
        10,
        9 -> Color(0xFFFFD740)
        8,
        7 -> Color(0xFFE53935)
        6,
        5 -> Color(0xFF1E88E5)
        4,
        3 -> Color(0xFF212121)
        else -> Color(0xFFF5F5F5)
    }
