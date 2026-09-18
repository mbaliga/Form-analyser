package xyz.mdhv.formanalyser.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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
 *
 * Each region is drawn as a [regionPath] silhouette *inscribed inside* its [BodyAtlas] bounding
 * rect, not as the rect itself (see that function's KDoc for exactly what "inscribed" means for
 * hit-testing). Tap/long-press hit-testing below still goes through [BodyAtlas.hitTest] against the
 * rect, unchanged — the silhouette is a pure rendering upgrade, not a new geometry source.
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
            val path = regionPath(r.id, topLeft, rectSize)

            fills[r.id]?.let { fill ->
                if (fill != Color.Transparent) drawPath(path, color = fill)
            }
            if (r.id in hatched) drawHatch(path, topLeft, rectSize, s)

            val outlineColor = when {
                r.id in selected -> Hyle.Accent
                else -> Hyle.SurfaceVariant
            }
            val outlineWidth = if (r.id in selected) 3.5f * s else 2f * s
            drawPath(
                path = path,
                color = outlineColor,
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

/** Cyan 45° cross-hatch clipped to the region's drawn silhouette — stroke 4, spacing 14 viewport units. */
private fun DrawScope.drawHatch(path: Path, topLeft: Offset, boxSize: Size, s: Float) {
    val w = boxSize.width
    val h = boxSize.height
    clipPath(path) {
        var d = -h
        while (d < w) {
            drawLine(
                color = BodyEncodings.physioCyan,
                start = Offset(topLeft.x + d, topLeft.y + h),
                end = Offset(topLeft.x + d + h, topLeft.y),
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

// ---------------------------------------------------------------------------------------------
// Procedural region silhouettes.
//
// HONESTY NOTE (read before "improving" this further): everything below is *generated* geometry —
// a handful of parameterized Bezier-path archetypes (tapered band, teardrop, lens, flat-edged dome,
// ellipse blob), each inscribed inside one BodyAtlas rectangle. It is a rendering upgrade over the
// v0 plain rounded-rects (reads as rough limb/muscle silhouettes instead of boxes), not commissioned
// or hand-drawn anatomy. Real illustrated atlas art is still the pending item BodyAtlas.kt's KDoc and
// CROCODYL_STATUS.md §4.3 describe — this file's `body_atlas.override.json` drop-in seam is exactly
// where a human illustrator's SVG paths would replace the archetypes below, id-for-id, without
// touching BodyAtlas.kt, RegionIds, hit-testing, or any consumer. None of this is device-rendered or
// visually verified in this environment (app-android has no Android SDK here); it is correct only to
// the extent careful reading of the Compose `Path`/`DrawScope` APIs and the geometry below is —
// confirm on a device or emulator before treating the shapes themselves as final.
//
// What IS untouched, and remains the real contract: BodyAtlas.kt's rectangles — position, size,
// left/right mirroring, no-overlap, ≥60×60 finger-target size, and the 52-region id coverage are all
// exactly as authored, and AtlasIntegrityTest (core-body, pure JVM) still exercises that geometry
// directly. Hit-testing above still resolves a tap to the full rectangle, not to the (visually
// smaller/inset) silhouette drawn here — shrinking the tappable area to hug a narrower drawn limb
// would violate the finger-sized-target invariant for no real benefit, so the two are deliberately
// decoupled.
// ---------------------------------------------------------------------------------------------

/** A point in a region's local [0,1]×[0,1] box, already mapped to on-canvas pixels. */
private typealias NormPoint = (nx: Float, ny: Float) -> Offset

/**
 * The silhouette for region [id], inscribed inside the pixel-space box [topLeft]/[size] that
 * [BodyAtlas] authored for it. Left-side ids are drawn in their natural orientation; right-side ids
 * (`_r`) get the same archetype horizontally mirrored inside their own (already-mirrored) box, via
 * [NormPoint] folding `nx -> 1 - nx` — the same "author left, mirror right" idiom BodyAtlas.kt uses
 * for the rectangles themselves, applied one level down to the shape drawn inside each rectangle.
 */
private fun regionPath(id: String, topLeft: Offset, size: Size): Path {
    val mirror = id.endsWith("_r")
    val w = size.width
    val h = size.height
    val p: NormPoint = { nx, ny ->
        val x = if (mirror) 1f - nx else nx
        Offset(topLeft.x + x * w, topLeft.y + ny * h)
    }
    return when (baseRegionId(id)) {
        // Neck: a short cylinder, narrower up near the jaw than down at the shoulder line.
        "neck_ant" -> taperedBand(p, topFrac = 0.60f, bottomFrac = 0.86f, bulge = 0.04f, bellyY = 0.5f)
        "neck_post" -> taperedBand(p, topFrac = 0.62f, bottomFrac = 0.90f, bulge = 0.04f, bellyY = 0.5f)

        // Long limb muscles: a belly that bulges outward partway down, tapering toward each tendon.
        "biceps" -> taperedBand(p, topFrac = 0.70f, bottomFrac = 0.50f, bulge = 0.14f, bellyY = 0.38f)
        "triceps" -> taperedBand(p, topFrac = 0.62f, bottomFrac = 0.50f, bulge = 0.12f, bellyY = 0.46f)
        "forearm_flex" -> taperedBand(p, topFrac = 0.62f, bottomFrac = 0.42f, bulge = 0.08f, bellyY = 0.28f)
        "forearm_ext" -> taperedBand(p, topFrac = 0.60f, bottomFrac = 0.42f, bulge = 0.08f, bellyY = 0.30f)
        "quad" -> taperedBand(p, topFrac = 0.60f, bottomFrac = 0.46f, bulge = 0.14f, bellyY = 0.38f)
        "hamstring" -> taperedBand(p, topFrac = 0.58f, bottomFrac = 0.48f, bulge = 0.12f, bellyY = 0.42f)
        "shin" -> taperedBand(p, topFrac = 0.48f, bottomFrac = 0.36f, bulge = 0.06f, bellyY = 0.26f)
        "calf" -> taperedBand(p, topFrac = 0.46f, bottomFrac = 0.32f, bulge = 0.22f, bellyY = 0.32f)

        // Erector spinae: a straighter column hugging the spine, only a slight midline bulge.
        "erector" -> taperedBand(p, topFrac = 0.75f, bottomFrac = 0.70f, bulge = 0.08f, bellyY = 0.5f)

        // Abs: same band shape but with a *negative* bulge — a soft inward waist at the midline
        // (linea alba) instead of a muscle belly, so it doesn't just read as a plain rounded rect.
        "abs_upper" -> taperedBand(p, topFrac = 0.86f, bottomFrac = 0.86f, bulge = -0.07f, bellyY = 0.5f)
        "abs_lower" -> taperedBand(p, topFrac = 0.86f, bottomFrac = 0.86f, bulge = -0.07f, bellyY = 0.5f)

        // Obliques and rhomboid: a pointed lens/diamond — obliques run as a long tapered strip,
        // rhomboid is literally named for its diamond shape.
        "oblique" -> lens(p, pointInset = 0.08f, bulge = 0.42f)
        "rhomboid" -> lens(p, pointInset = 0.14f, bulge = 0.40f)

        // Fan/cap muscles: broad at the origin, tapering to a single point at the tendon insertion.
        "delt_ant", "delt_lat", "delt_post" -> teardrop(p, TeardropPoint.DOWN) // shoulder cap → arm
        "pec" -> teardrop(p, TeardropPoint.TOWARD_CANON_LEFT) // sternum → armpit
        "lat" -> teardrop(p, TeardropPoint.UP) // lower back → armpit

        // Glute/hip: flat along the top (waistline), rounding into a bulge below.
        "glute" -> dCap(p, FlatSide.TOP)
        "hip" -> dCap(p, FlatSide.TOP, inset = 0.10f)

        // Trapezius bands: flat along the medial (spine-side) edge, rounding out toward the shoulder.
        "trap_upper", "trap_mid", "trap_lower" -> dCap(p, FlatSide.RIGHT_CANON)

        // Small joint/extremity regions: real muscle-belly tapering isn't meaningful at this scale —
        // a soft rounded blob reads better than a box without overstating anatomical detail.
        "rotator_cuff", "knee", "hand" -> blob(p)

        // Exhaustive over the 52-region contract (RegionIds.ALL / AtlasIntegrityTest); this branch
        // is unreachable in practice and only guards against a future region id this file forgot.
        else -> blob(p)
    }
}

private fun baseRegionId(id: String): String = id.removeSuffix("_l").removeSuffix("_r")

private fun Path.moveToPt(o: Offset) = moveTo(o.x, o.y)

private fun Path.lineToPt(o: Offset) = lineTo(o.x, o.y)

private fun Path.quadToPt(control: Offset, end: Offset) = quadraticTo(control.x, control.y, end.x, end.y)

private fun Path.cubicToPt(c1: Offset, c2: Offset, end: Offset) = cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)

/**
 * A vertical band that necks in or out along its length — [topFrac]/[bottomFrac] set the width (as
 * a fraction of the box width) at the top and bottom edges, and [bulge] adds (or, negative, removes)
 * width at [bellyY] partway down, e.g. a muscle belly or an inward waist. Symmetric about the box's
 * vertical centerline, so it needs no left/right mirroring.
 */
private fun taperedBand(p: NormPoint, topFrac: Float, bottomFrac: Float, bulge: Float, bellyY: Float): Path {
    val topHalf = topFrac / 2f
    val botHalf = bottomFrac / 2f
    val bulgeHalf = (maxOf(topHalf, botHalf) + bulge).coerceIn(0.05f, 0.49f)
    val topCenter = p(0.5f, 0f)
    val botCenter = p(0.5f, 1f)
    val topL = p(0.5f - topHalf, 0.05f)
    val topR = p(0.5f + topHalf, 0.05f)
    val botL = p(0.5f - botHalf, 0.95f)
    val botR = p(0.5f + botHalf, 0.95f)
    val belL = p(0.5f - bulgeHalf, bellyY)
    val belR = p(0.5f + bulgeHalf, bellyY)
    val upperR = p(0.5f + topHalf, 0.22f)
    val lowerR = p(0.5f + botHalf, 0.76f)
    val upperL = p(0.5f - topHalf, 0.22f)
    val lowerL = p(0.5f - botHalf, 0.76f)
    return Path().apply {
        moveToPt(topL)
        quadToPt(topCenter, topR)
        cubicToPt(upperR, belR, belR)
        cubicToPt(belR, lowerR, botR)
        quadToPt(botCenter, botL)
        cubicToPt(lowerL, belL, belL)
        cubicToPt(belL, upperL, topL)
        close()
    }
}

/** Where a [teardrop]'s point sits. `TOWARD_CANON_LEFT` is in the box's *pre-mirror* frame (see
 * [regionPath]'s [NormPoint]), so it always means "toward this region's authored (left-id) side" —
 * for an `_r` id the mirroring in [regionPath] carries the point to the correct mirrored side. */
private enum class TeardropPoint { DOWN, UP, TOWARD_CANON_LEFT }

/**
 * A rounded cap tapering to a single point — the shape of a muscle that fans out from one broad
 * origin to a narrow tendinous insertion (deltoids capping the shoulder and narrowing down the arm;
 * pec fanning from the sternum to a point near the armpit; lat fanning up from the lower back to a
 * point near the armpit from the opposite direction).
 */
private fun teardrop(p: NormPoint, point: TeardropPoint, roundness: Float = 0.44f): Path {
    val r = roundness
    return when (point) {
        TeardropPoint.DOWN -> {
            val tip = p(0.5f, 0.97f)
            val capTop = p(0.5f, 0f)
            val capL = p(0.5f - r * 0.7f, 0.10f)
            val capR = p(0.5f + r * 0.7f, 0.10f)
            val bulgeL = p(0.5f - r, 0.32f)
            val bulgeR = p(0.5f + r, 0.32f)
            val waistL = p(0.5f - r * 0.6f, 0.80f)
            val waistR = p(0.5f + r * 0.6f, 0.80f)
            Path().apply {
                moveToPt(tip)
                cubicToPt(waistL, bulgeL, capL)
                quadToPt(capTop, capR)
                cubicToPt(bulgeR, waistR, tip)
                close()
            }
        }
        TeardropPoint.UP -> {
            val tip = p(0.5f, 0.03f)
            val capBottom = p(0.5f, 1f)
            val capL = p(0.5f - r * 0.7f, 0.90f)
            val capR = p(0.5f + r * 0.7f, 0.90f)
            val bulgeL = p(0.5f - r, 0.68f)
            val bulgeR = p(0.5f + r, 0.68f)
            val waistL = p(0.5f - r * 0.6f, 0.20f)
            val waistR = p(0.5f + r * 0.6f, 0.20f)
            Path().apply {
                moveToPt(tip)
                cubicToPt(waistL, bulgeL, capL)
                quadToPt(capBottom, capR)
                cubicToPt(bulgeR, waistR, tip)
                close()
            }
        }
        TeardropPoint.TOWARD_CANON_LEFT -> {
            val tip = p(0.04f, 0.46f)
            val capOuter = p(1f, 0.46f)
            val capT = p(0.90f, 0.06f)
            val capB = p(0.90f, 0.86f)
            val bulgeT = p(0.66f, 0.5f - r)
            val bulgeB = p(0.66f, 0.5f + r)
            val waistT = p(0.20f, 0.5f - r * 0.6f)
            val waistB = p(0.20f, 0.5f + r * 0.6f)
            Path().apply {
                moveToPt(tip)
                cubicToPt(waistT, bulgeT, capT)
                quadToPt(capOuter, capB)
                cubicToPt(bulgeB, waistB, tip)
                close()
            }
        }
    }
}

/**
 * A pointed lens/almond shape — narrow at top and bottom, bulging out on both sides partway down.
 * Symmetric left/right, no mirroring needed.
 */
private fun lens(p: NormPoint, pointInset: Float, bulge: Float): Path {
    val top = p(0.5f, pointInset)
    val bottom = p(0.5f, 1f - pointInset)
    val midL = p(0.5f - bulge, 0.5f)
    val midR = p(0.5f + bulge, 0.5f)
    val upperR = p(0.5f + bulge * 0.7f, 0.28f)
    val lowerR = p(0.5f + bulge * 0.7f, 0.72f)
    val upperL = p(0.5f - bulge * 0.7f, 0.28f)
    val lowerL = p(0.5f - bulge * 0.7f, 0.72f)
    return Path().apply {
        moveToPt(top)
        cubicToPt(upperR, midR, midR)
        cubicToPt(midR, lowerR, bottom)
        cubicToPt(lowerL, midL, midL)
        cubicToPt(midL, upperL, top)
        close()
    }
}

/** Which edge of a [dCap] stays a straight line. `RIGHT_CANON` is pre-mirror (see [regionPath]'s
 * [NormPoint]) — "toward this region's authored (left-id) medial side", mirrored automatically for
 * an `_r` id the same way [TeardropPoint.TOWARD_CANON_LEFT] is. */
private enum class FlatSide { TOP, RIGHT_CANON }

/**
 * A dome: one straight edge, rounded/bulging everywhere else. Used for muscles that meet a flat
 * anatomical boundary on one side and round outward on the rest — glute/hip flat along the waistline
 * and rounding into the buttock/thigh curve below; trapezius flat along the spine and rounding out
 * toward the shoulder.
 */
private fun dCap(p: NormPoint, flat: FlatSide, inset: Float = 0.08f): Path {
    val rx = 0.5f - inset
    val ry = 0.5f - inset
    val k = 0.5523f // cubic-Bezier circle-approximation constant
    return when (flat) {
        FlatSide.TOP -> {
            val flatY = 0.5f - ry
            val edgeL = p(0.5f - rx, flatY)
            val edgeR = p(0.5f + rx, flatY)
            val right = p(0.5f + rx, 0.5f)
            val bottom = p(0.5f, 0.5f + ry)
            val left = p(0.5f - rx, 0.5f)
            Path().apply {
                moveToPt(edgeL)
                lineToPt(edgeR)
                // edgeR->right and left->edgeL each share an x (they run down/up the sides of the
                // ellipse's bounding square from a box corner to an axis extreme) — a straight line,
                // not a k-constant arc, which only approximates a curve *between two axis extremes*.
                lineToPt(right)
                cubicToPt(p(0.5f + rx, 0.5f + k * ry), p(0.5f + k * rx, 0.5f + ry), bottom)
                cubicToPt(p(0.5f - k * rx, 0.5f + ry), p(0.5f - rx, 0.5f + k * ry), left)
                lineToPt(edgeL)
                close()
            }
        }
        FlatSide.RIGHT_CANON -> {
            val flatX = 0.5f + rx
            val edgeT = p(flatX, 0.5f - ry)
            val edgeB = p(flatX, 0.5f + ry)
            val bottom = p(0.5f, 0.5f + ry)
            val left = p(0.5f - rx, 0.5f)
            val top = p(0.5f, 0.5f - ry)
            Path().apply {
                moveToPt(edgeT)
                lineToPt(edgeB)
                // edgeB->bottom and top->edgeT each share a y (they run in/out from the ellipse's
                // bounding square corner to an axis extreme) — a straight line, not a k-constant arc,
                // which only approximates a curve *between two axis extremes*.
                lineToPt(bottom)
                cubicToPt(p(0.5f - k * rx, 0.5f + ry), p(0.5f - rx, 0.5f + k * ry), left)
                cubicToPt(p(0.5f - rx, 0.5f - k * ry), p(0.5f - k * rx, 0.5f - ry), top)
                lineToPt(edgeT)
                close()
            }
        }
    }
}

/** A soft rounded oval, inset from the box edges — for small joint/extremity regions where a
 * tapered muscle-belly shape wouldn't mean anything. */
private fun blob(p: NormPoint, inset: Float = 0.10f): Path {
    val rx = 0.5f - inset
    val ry = 0.5f - inset
    val k = 0.5523f
    val top = p(0.5f, 0.5f - ry)
    val right = p(0.5f + rx, 0.5f)
    val bottom = p(0.5f, 0.5f + ry)
    val left = p(0.5f - rx, 0.5f)
    return Path().apply {
        moveToPt(top)
        cubicToPt(p(0.5f + k * rx, 0.5f - ry), p(0.5f + rx, 0.5f - k * ry), right)
        cubicToPt(p(0.5f + rx, 0.5f + k * ry), p(0.5f + k * rx, 0.5f + ry), bottom)
        cubicToPt(p(0.5f - k * rx, 0.5f + ry), p(0.5f - rx, 0.5f + k * ry), left)
        cubicToPt(p(0.5f - rx, 0.5f - k * ry), p(0.5f - k * rx, 0.5f - ry), top)
        close()
    }
}
