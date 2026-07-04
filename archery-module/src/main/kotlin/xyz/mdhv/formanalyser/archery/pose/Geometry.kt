package xyz.mdhv.formanalyser.archery.pose

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * 2D geometry on pose landmarks (x,y in normalised image coords, y-down). All angle outputs
 * are in degrees. These are scale-invariant — they don't depend on the unknown metric scale
 * of a monocular view, which is exactly why form/alignment is vision's well-posed sub-problem.
 */
object Geometry {

    /** Interior angle at vertex [b] formed by points [a]-[b]-[c], in degrees [0,180]. */
    fun angleDeg(a: Landmark, b: Landmark, c: Landmark): Double {
        val ax = a.x - b.x; val ay = a.y - b.y
        val cx = c.x - b.x; val cy = c.y - b.y
        val dot = ax * cx + ay * cy
        val mag = hypot(ax, ay) * hypot(cx, cy)
        if (mag < 1e-12) return 0.0
        val cos = (dot / mag).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cos))
    }

    /**
     * Angle of the segment [a]->[b] relative to horizontal, in degrees [0,90].
     * 0 == perfectly level, 90 == vertical. Sign-free (absolute tilt).
     */
    fun tiltFromHorizontalDeg(a: Landmark, b: Landmark): Double {
        val dx = b.x - a.x
        val dy = b.y - a.y
        if (hypot(dx, dy) < 1e-12) return 0.0
        val deg = Math.toDegrees(atan2(abs(dy), abs(dx)))
        return deg
    }

    /**
     * Angle of the segment [a]->[b] relative to vertical, in degrees [0,90].
     * 0 == perfectly upright, 90 == horizontal. Used for spine / head lean.
     */
    fun leanFromVerticalDeg(a: Landmark, b: Landmark): Double {
        val dx = b.x - a.x
        val dy = b.y - a.y
        if (hypot(dx, dy) < 1e-12) return 0.0
        val deg = Math.toDegrees(atan2(abs(dx), abs(dy)))
        return deg
    }

    fun dist(a: Landmark, b: Landmark): Double = hypot(a.x - b.x, a.y - b.y)

    fun midpoint(a: Landmark, b: Landmark): Landmark =
        Landmark((a.x + b.x) / 2, (a.y + b.y) / 2, (a.z + b.z) / 2, minOf(a.visibility, b.visibility))

    fun rms(values: DoubleArray): Double {
        if (values.isEmpty()) return 0.0
        var s = 0.0
        for (v in values) s += v * v
        return sqrt(s / values.size)
    }
}
