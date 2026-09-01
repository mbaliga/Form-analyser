package xyz.mdhv.formanalyser.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.body.BodyAtlas
import xyz.mdhv.formanalyser.body.BodyFace
import xyz.mdhv.formanalyser.body.RegionShape

/**
 * The encoding law (spec §3.6 / Phase 3 §5), exact:
 * pain = single-hue violet luminance ramp + numeral badge (brighter = worse);
 * physio target = cyan cross-hatch, no fill; hue never the only channel.
 */
object BodyEncodings {
    private val stops = listOf(
        0 to Color.Transparent,
        2 to Color(0xFF2E2354),
        5 to Color(0xFF4A3A8C),
        8 to Color(0xFF6C5BD1),
        10 to Color(0xFF8E7BFF),
    )

    /** Linear interpolation between the anchor stops. */
    fun painColor(intensity: Int): Color {
        val v = intensity.coerceIn(0, 10)
        for (i in 0 until stops.size - 1) {
            val (a, ca) = stops[i]
            val (b, cb) = stops[i + 1]
            if (v in a..b) {
                val t = if (b == a) 0f else (v - a).toFloat() / (b - a)
                return androidx.compose.ui.graphics.lerp(ca, cb, t)
            }
        }
        return stops.last().second
    }

    val physioCyan = Color(0xFF08FED5)
}

/**
 * One reusable atlas renderer over the pure [BodyAtlas] rect geometry. Consumers pass per-region
 * decoration; identity is always position + outline + (numeral) badge — never hue alone.
 */
@Composable
fun BodyAtlasCanvas(
    face: BodyFace,
    modifier: Modifier = Modifier,
    fills: Map<String, Color> = emptyMap(),
    badges: Map<String, String> = emptyMap(),
    hatched: Set<String> = emptySet(),
    dashed: Set<String> = emptySet(),
    selected: Set<String> = emptySet(),
    onTap: ((String) -> Unit)? = null,
    onLongPress: ((String) -> Unit)? = null,
) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.5f) // 1000×2000 viewport
            .pointerInput(face, onTap, onLongPress) {
                detectTapGestures(
                    onTap = { ofs ->
                        if (onTap != null) hit(face, ofs, size.width.toFloat())?.let { onTap(it.id) }
                    },
                    onLongPress = { ofs ->
                        if (onLongPress != null) hit(face, ofs, size.width.toFloat())?.let { onLongPress(it.id) }
                    },
                )
            },
    ) {
        val s = size.width / BodyAtlas.WIDTH.toFloat()
        for (r in BodyAtlas.forFace(face)) {
            val topLeft = Offset(r.x.toFloat() * s, r.y.toFloat() * s)
            val rectSize = Size(r.w.toFloat() * s, r.h.toFloat() * s)
            val corner = CornerRadius(10f * s, 10f * s)

            fills[r.id]?.let { fill ->
                if (fill != Color.Transparent) drawRoundRect(fill, topLeft, rectSize, corner)
            }
            if (r.id in hatched) drawHatch(r, s)

            val outlineColor = when {
                r.id in selected -> Hyle.Accent
                else -> Hyle.SurfaceVariant
            }
            val outlineWidth = if (r.id in selected) 3.5f * s else 2f * s
            drawRoundRect(
                color = outlineColor, topLeft = topLeft, size = rectSize, cornerRadius = corner,
                style = if (r.id in dashed) {
                    Stroke(width = outlineWidth * 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f * s, 8f * s)))
                } else {
                    Stroke(width = outlineWidth)
                },
            )

            badges[r.id]?.let { text ->
                val layout = textMeasurer.measure(
                    text,
                    TextStyle(color = Hyle.OnBackground, fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                )
                drawText(
                    layout,
                    topLeft = Offset(
                        (r.centerX.toFloat() * s) - layout.size.width / 2f,
                        (r.centerY.toFloat() * s) - layout.size.height / 2f,
                    ),
                )
            }
        }
    }
}

/** Cyan 45° cross-hatch clipped to the region — stroke 4, spacing 14 viewport units. */
private fun DrawScope.drawHatch(r: RegionShape, s: Float) {
    val left = r.x.toFloat() * s
    val top = r.y.toFloat() * s
    val right = (r.x + r.w).toFloat() * s
    val bottom = (r.y + r.h).toFloat() * s
    val w = right - left
    val h = bottom - top
    clipRect(left, top, right, bottom) {
        var d = -h
        while (d < w) {
            drawLine(
                color = BodyEncodings.physioCyan,
                start = Offset(left + d, bottom),
                end = Offset(left + d + h, top),
                strokeWidth = 4f * s,
            )
            d += 14f * s
        }
    }
}

private fun hit(face: BodyFace, ofs: Offset, widthPx: Float): RegionShape? {
    if (widthPx <= 0f) return null
    val scale = BodyAtlas.WIDTH / widthPx
    return BodyAtlas.hitTest(face, ofs.x * scale, ofs.y * scale)
}
