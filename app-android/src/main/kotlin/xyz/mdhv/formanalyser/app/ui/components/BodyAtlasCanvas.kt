package xyz.mdhv.formanalyser.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import xyz.mdhv.formanalyser.app.ui.theme.Hyle
import xyz.mdhv.formanalyser.body.BodyAtlas
import xyz.mdhv.formanalyser.body.BodyFace
import xyz.mdhv.formanalyser.body.RegionShape

/** Visual encodings shared by pain, injury and physio views. */
object BodyEncodings {
    fun painColor(intensity: Int): Color {
        val value = intensity.coerceIn(0, 10)
        if (value == 0) return Color.Transparent
        return androidx.compose.ui.graphics.lerp(
            Hyle.SurfaceVariant,
            Hyle.AccentBright,
            .12f + value * .088f,
        )
    }

    val physioCyan get() = Hyle.AlienCyan
}

/**
 * Anatomical, interactive 52-region atlas. Region IDs still come from the stable pure-JVM body
 * contract, but their old debug rectangles are rendered as tapered muscle groups over a human
 * silhouette. Generous invisible hit targets remain for phone use.
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
            .aspectRatio(0.67f)
            .pointerInput(face, onTap, onLongPress) {
                detectTapGestures(
                    onTap = { point ->
                        if (onTap != null) hit(face, point, size.width.toFloat())?.let { onTap(it.id) }
                    },
                    onLongPress = { point ->
                        if (onLongPress != null) hit(face, point, size.width.toFloat())?.let { onLongPress(it.id) }
                    },
                )
            },
    ) {
        val scale = size.width / BodyAtlas.WIDTH.toFloat()
        drawBackdrop(scale, face)
        drawSilhouette(scale)

        for (region in BodyAtlas.forFace(face)) {
            val path = musclePath(region, scale)
            val fill = fills[region.id]
            if (fill != null && fill != Color.Transparent) {
                drawPath(path, fill.copy(alpha = 0.18f), style = Stroke(18f * scale))
                drawPath(path, Brush.verticalGradient(listOf(fill.copy(alpha = .96f), fill.copy(alpha = .62f))))
            } else {
                drawPath(
                    path,
                    Brush.verticalGradient(
                        listOf(Hyle.BodyMuscleTop.copy(alpha = .78f), Hyle.BodyMuscleBottom.copy(alpha = .9f))
                    ),
                )
            }
            if (region.id in hatched) drawHatch(path, region, scale)

            val selectedRegion = region.id in selected
            drawPath(
                path = path,
                color = if (selectedRegion) Hyle.AccentBright else Hyle.HairlineStrong,
                style = if (region.id in dashed) {
                    Stroke(
                        width = (if (selectedRegion) 5f else 3.5f) * scale,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(13f * scale, 8f * scale)),
                    )
                } else Stroke((if (selectedRegion) 5f else 2.25f) * scale),
            )

            badges[region.id]?.let { badge ->
                val center = Offset(region.centerX.toFloat() * scale, region.centerY.toFloat() * scale)
                drawCircle(Hyle.Surface.copy(alpha = .94f), 27f * scale, center)
                drawCircle(Hyle.OnBackground.copy(alpha = .45f), 27f * scale, center, style = Stroke(2f * scale))
                val layout = textMeasurer.measure(
                    badge,
                    TextStyle(color = Hyle.OnBackground, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                )
                drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
            }
        }

        val faceLabel = if (face == BodyFace.FRONT) "ANTERIOR" else "POSTERIOR"
        val label = textMeasurer.measure(
            faceLabel,
            TextStyle(color = Hyle.InkFaint, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.8.sp),
        )
        drawText(label, topLeft = Offset((size.width - label.size.width) / 2f, size.height - label.size.height - 8f * scale))
    }
}

private fun DrawScope.drawBackdrop(scale: Float, face: BodyFace) {
    val center = Offset(size.width / 2f, size.height * .42f)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Hyle.Accent.copy(alpha = .12f), Color.Transparent),
            center = center,
            radius = size.width * .56f,
        ),
        radius = size.width * .56f,
        center = center,
    )
    val axis = Hyle.Hairline.copy(alpha = .7f)
    drawLine(axis, Offset(size.width / 2f, 54f * scale), Offset(size.width / 2f, 1390f * scale), 1f * scale)
    for (y in 180..1320 step 190) {
        drawLine(axis.copy(alpha = .45f), Offset(95f * scale, y * scale), Offset(905f * scale, y * scale), .7f * scale)
    }
    val markerX = if (face == BodyFace.FRONT) 116f else 884f
    drawCircle(Hyle.Accent.copy(alpha = .45f), 4f * scale, Offset(markerX * scale, 96f * scale))
}

private fun DrawScope.drawSilhouette(scale: Float) {
    val body = Path().apply {
        moveTo(450f * scale, 48f * scale)
        cubicTo(420f * scale, 72f * scale, 415f * scale, 125f * scale, 440f * scale, 168f * scale)
        lineTo(420f * scale, 215f * scale)
        cubicTo(360f * scale, 225f * scale, 300f * scale, 242f * scale, 250f * scale, 276f * scale)
        cubicTo(205f * scale, 340f * scale, 178f * scale, 465f * scale, 155f * scale, 610f * scale)
        cubicTo(145f * scale, 680f * scale, 150f * scale, 741f * scale, 184f * scale, 778f * scale)
        cubicTo(208f * scale, 790f * scale, 229f * scale, 765f * scale, 230f * scale, 724f * scale)
        lineTo(282f * scale, 518f * scale)
        cubicTo(292f * scale, 655f * scale, 303f * scale, 704f * scale, 332f * scale, 748f * scale)
        cubicTo(306f * scale, 875f * scale, 313f * scale, 1028f * scale, 338f * scale, 1160f * scale)
        lineTo(352f * scale, 1350f * scale)
        cubicTo(350f * scale, 1394f * scale, 391f * scale, 1412f * scale, 442f * scale, 1388f * scale)
        lineTo(477f * scale, 1120f * scale)
        lineTo(523f * scale, 1120f * scale)
        lineTo(558f * scale, 1388f * scale)
        cubicTo(609f * scale, 1412f * scale, 650f * scale, 1394f * scale, 648f * scale, 1350f * scale)
        lineTo(662f * scale, 1160f * scale)
        cubicTo(687f * scale, 1028f * scale, 694f * scale, 875f * scale, 668f * scale, 748f * scale)
        cubicTo(697f * scale, 704f * scale, 708f * scale, 655f * scale, 718f * scale, 518f * scale)
        lineTo(770f * scale, 724f * scale)
        cubicTo(771f * scale, 765f * scale, 792f * scale, 790f * scale, 816f * scale, 778f * scale)
        cubicTo(850f * scale, 741f * scale, 855f * scale, 680f * scale, 845f * scale, 610f * scale)
        cubicTo(822f * scale, 465f * scale, 795f * scale, 340f * scale, 750f * scale, 276f * scale)
        cubicTo(700f * scale, 242f * scale, 640f * scale, 225f * scale, 580f * scale, 215f * scale)
        lineTo(560f * scale, 168f * scale)
        cubicTo(585f * scale, 125f * scale, 580f * scale, 72f * scale, 550f * scale, 48f * scale)
        cubicTo(520f * scale, 25f * scale, 480f * scale, 25f * scale, 450f * scale, 48f * scale)
        close()
    }
    drawPath(body, Brush.verticalGradient(listOf(Hyle.BodySilhouetteTop, Hyle.BodySilhouetteBottom)))
    drawPath(body, Hyle.HairlineStrong, style = Stroke(2.5f * scale))
}

/** Organic muscle lozenge generated inside each stable contract rectangle. */
private fun musclePath(region: RegionShape, scale: Float): Path {
    val left = region.x.toFloat() * scale
    val top = region.y.toFloat() * scale
    val right = (region.x + region.w).toFloat() * scale
    val bottom = (region.y + region.h).toFloat() * scale
    val width = right - left
    val height = bottom - top
    val id = region.id

    if ("hand" in id || "knee" in id) {
        return Path().apply {
            addRoundRect(RoundRect(left, top, right, bottom, width * .34f, height * .34f))
        }
    }

    val horizontal = width > height * 1.05f
    val rightSide = id.endsWith("_r")
    return Path().apply {
        if (horizontal) {
            moveTo(left + width * .08f, top + height * .55f)
            cubicTo(left + width * .15f, top + height * .08f, left + width * .62f, top, right - width * .06f, top + height * .34f)
            cubicTo(right, top + height * .58f, right - width * .22f, bottom - height * .05f, left + width * .18f, bottom)
            cubicTo(left, bottom - height * .16f, left - width * .02f, top + height * .72f, left + width * .08f, top + height * .55f)
        } else {
            val innerTop = if (rightSide) left + width * .22f else right - width * .22f
            val outerTop = if (rightSide) right - width * .08f else left + width * .08f
            val innerBottom = if (rightSide) left + width * .28f else right - width * .28f
            val outerBottom = if (rightSide) right - width * .18f else left + width * .18f
            moveTo(innerTop, top)
            cubicTo(outerTop, top + height * .04f, outerTop, top + height * .48f, outerBottom, bottom)
            cubicTo((left + right) / 2f, bottom - height * .03f, innerBottom, bottom - height * .04f, innerTop, top)
        }
        close()
    }
}

private fun DrawScope.drawHatch(path: Path, region: RegionShape, scale: Float) {
    val left = region.x.toFloat() * scale
    val top = region.y.toFloat() * scale
    val right = (region.x + region.w).toFloat() * scale
    val bottom = (region.y + region.h).toFloat() * scale
    val height = bottom - top
    clipPath(path) {
        var distance = -height
        while (left + distance < right) {
            drawLine(
                BodyEncodings.physioCyan,
                Offset(left + distance, bottom),
                Offset(left + distance + height, top),
                3.5f * scale,
            )
            distance += 17f * scale
        }
    }
}

private fun hit(face: BodyFace, point: Offset, widthPx: Float): RegionShape? {
    if (widthPx <= 0f) return null
    val scale = BodyAtlas.WIDTH / widthPx
    return BodyAtlas.hitTest(face, point.x * scale, point.y * scale)
}
