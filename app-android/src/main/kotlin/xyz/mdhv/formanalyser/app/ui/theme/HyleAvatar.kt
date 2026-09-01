package xyz.mdhv.formanalyser.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/** Deterministic avatar parameters derived from a seed (pure — unit-testable). */
data class AvatarParams(val angleDeg: Float, val c0: Color, val c1: Color, val c2: Color, val rings: Int)

/**
 * v0 procedural Hyle avatar: a splitmix64 stream from [seed] picks a gradient angle, three color
 * stops sampled along the violet↔cyan lane at distinct luminance steps, and 0–2 concentric rings.
 * Same seed ⇒ same render. Phase 11's AGSL Flex Card replaces the renderer behind this signature.
 */
fun avatarParams(seed: Long): AvatarParams {
    var state = if (seed == 0L) 0x9E3779B97F4A7C15uL else seed.toULong()
    fun next(): ULong {
        state += 0x9E3779B97F4A7C15uL
        var z = state
        z = (z xor (z shr 30)) * 0xBF58476D1CE4E5B9uL
        z = (z xor (z shr 27)) * 0x94D049BB133111EBuL
        return z xor (z shr 31)
    }
    fun unit(): Float = (next() shr 11).toLong().toFloat() / (1L shl 53).toFloat()

    val angle = unit() * 360f
    // three luminance steps along the violet↔cyan lane
    val a = lerp(Hyle.AccentDim, Hyle.Accent, 0.2f + 0.6f * unit())
    val b = lerp(Hyle.Accent, Hyle.AlienCyan, 0.3f + 0.5f * unit())
    val c = lerp(Hyle.AlienCyan, Hyle.RadiumGreen, 0.1f + 0.4f * unit())
    val rings = (next() % 3uL).toInt()
    return AvatarParams(angle, a, b, c, rings)
}

@Composable
fun HyleAvatar(seed: Long, modifier: Modifier = Modifier, sizeDp: Int = 56) {
    val p = remember(seed) { avatarParams(seed) }
    Canvas(modifier = modifier.size(sizeDp.dp)) {
        val brush = Brush.linearGradient(
            0f to p.c0, 0.5f to p.c1, 1f to p.c2,
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height),
        )
        val r = size.minDimension / 2f
        drawCircle(brush = brush, radius = r, center = center)
        for (i in 1..p.rings) {
            drawCircle(
                color = Color.White.copy(alpha = 0.10f),
                radius = r * (1f - i * 0.22f),
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
            )
        }
    }
}
