package xyz.mdhv.formanalyser.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import kotlin.math.max
import kotlin.math.min

/** Circular steadiness gauge (0–100). Higher = steadier. */
@Composable
fun SteadinessGauge(value: Double, modifier: Modifier = Modifier) {
    val v = value.coerceIn(0.0, 100.0)
    Box(modifier = modifier.size(160.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(160.dp)) {
            val stroke = Stroke(width = 14.dp.toPx())
            val inset = 14.dp.toPx()
            val arcSize = androidx.compose.ui.geometry.Size(size.width - inset, size.height - inset)
            val topLeft = Offset(inset / 2, inset / 2)
            drawArc(
                color = Hyle.SurfaceVariant,
                startAngle = 135f, sweepAngle = 270f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = stroke,
            )
            drawArc(
                color = Hyle.RadiumGreen,
                startAngle = 135f, sweepAngle = (270f * (v / 100.0)).toFloat(), useCenter = false,
                topLeft = topLeft, size = arcSize, style = stroke,
            )
        }
        Text(
            text = v.toInt().toString(),
            style = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.SemiBold),
            color = Hyle.OnBackground,
        )
    }
}

/** Steadiness-across-shots line (the session trend; fatigue is its downward slope). */
@Composable
fun TrendLine(values: List<Double>, modifier: Modifier = Modifier) {
    if (values.isEmpty()) {
        Text("No shots yet.", color = Hyle.OnSurfaceDim)
        return
    }
    Canvas(modifier = modifier.fillMaxWidth().height(140.dp)) {
        val maxV = 100.0
        val minV = 0.0
        val n = values.size
        fun x(i: Int) = if (n == 1) size.width / 2 else size.width * i / (n - 1)
        fun y(v: Double) = (size.height * (1 - (v - minV) / (maxV - minV))).toFloat()
        // baseline grid line at 50
        drawLine(Hyle.SurfaceVariant, Offset(0f, y(50.0)), Offset(size.width, y(50.0)), 1.5f)
        for (i in 0 until n - 1) {
            drawLine(
                color = Hyle.Accent,
                start = Offset(x(i), y(values[i])),
                end = Offset(x(i + 1), y(values[i + 1])),
                strokeWidth = 4f,
            )
        }
        for (i in 0 until n) {
            drawCircle(Hyle.RadiumGreen, radius = 5f, center = Offset(x(i), y(values[i])))
        }
    }
}

/** Signal-vs-score scatter — the differentiator made visible. */
@Composable
fun Scatter(points: List<Pair<Double, Double>>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) {
        Text("Log arrow scores to see signal↔score.", color = Hyle.OnSurfaceDim)
        return
    }
    val xs = points.map { it.first }
    val minX = xs.min(); val maxX = max(xs.max(), minX + 1e-6)
    Canvas(modifier = modifier.fillMaxWidth().height(160.dp)) {
        fun px(x: Double) = (size.width * (x - minX) / (maxX - minX)).toFloat()
        fun py(score: Double) = (size.height * (1 - score.coerceIn(0.0, 10.0) / 10.0)).toFloat()
        for (s in 0..10 step 5) {
            val yy = py(s.toDouble())
            drawLine(Hyle.SurfaceVariant, Offset(0f, yy), Offset(size.width, yy), 1f)
        }
        points.forEach { (feat, score) ->
            drawCircle(Hyle.Accent, radius = 6f, center = Offset(px(feat), py(score)))
        }
    }
}

internal fun clampUnit(x: Double) = min(1.0, max(0.0, x))
