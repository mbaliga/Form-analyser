package xyz.mdhv.formanalyser.scoring

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * A point in a photograph, in **normalized image coordinates**: `(0,0)` is the top-left corner of
 * the image and `(1,1)` the bottom-right, with y increasing *downwards* as every image API on the
 * platform has it.
 *
 * Normalized rather than pixels on purpose. The Android layer decodes a capture at whatever sample
 * size keeps memory sane, and a Compose overlay measures itself in dp; if calibration were stored in
 * source pixels then all of those would have to agree on a scale factor, and a silent disagreement
 * would move every arrow at once. A fraction of the frame is the one description that survives being
 * downscaled, rotated to match the sensor, and laid out in a `Canvas`.
 *
 * Face coordinates are the *other* space in this file — [PlotPoint]'s space, centred on the target
 * with y increasing upwards — and they never share a type with this one, because the two are
 * indistinguishable as numbers and catastrophic to confuse.
 */
data class ImagePoint(val x: Double, val y: Double) {
    init {
        require(x.isFinite() && y.isFinite())
    }
}

/**
 * A planar projective transform — the exact model for photographing a flat target face.
 *
 * A target face is a rigid plane and a phone camera is close enough to a pinhole, so the map from
 * face coordinates to image coordinates is a homography and nothing weaker. Fitting an affine or
 * similarity transform instead would amount to *assuming* the athlete stood perfectly square to the
 * boss, and standing 15° to one side is the normal case at a busy range, not the exception.
 *
 * Eight degrees of freedom, so four point correspondences determine it exactly — which is the whole
 * reason [FaceCalibration] asks for four handles rather than five. No least squares, no iteration,
 * no outlier rejection: with four exact correspondences there is one answer.
 *
 * Not exposed on its own; [FaceProjection] hands out the two directions with their meaning attached.
 */
class Homography private constructor(private val m: DoubleArray) {

    /**
     * Write the transformed point into [out] (`[0]` = x, `[1]` = y) and report whether it exists.
     *
     * Takes a caller-owned array because the rectifier calls this once per raster pixel — a million
     * times for one photograph — and allocating a point object each time would dominate the cost of
     * the whole detector.
     *
     * False is not a formality. A steeply tilted quad has a real vanishing line, and points on it
     * have no image at all; returning a huge finite number there would place a detected arrow at a
     * plausible-looking coordinate that means nothing. Callers drop the sample instead.
     */
    fun map(x: Double, y: Double, out: DoubleArray): Boolean {
        val den = m[6] * x + m[7] * y + 1.0
        if (abs(den) < 1e-12) return false
        val mx = (m[0] * x + m[1] * y + m[2]) / den
        val my = (m[3] * x + m[4] * y + m[5]) / den
        if (!mx.isFinite() || !my.isFinite()) return false
        out[0] = mx
        out[1] = my
        return true
    }

    internal companion object {
        /**
         * Solve for the transform carrying each `src[i]` to `dst[i]`, or null if either set of four
         * points is degenerate (three collinear, or two coincident).
         *
         * Straight DLT with `h33` pinned to 1, solved by Gauss-Jordan with partial pivoting. Both
         * point sets live inside the unit square by construction, so the system is already well
         * conditioned and Hartley normalisation would buy nothing measurable.
         *
         * Points are passed as flat `[x0, y0, x1, y1, …]` arrays so this stays free of whichever
         * coordinate space the caller is in — that is [FaceProjection]'s job to keep straight.
         */
        fun fromCorrespondences(src: DoubleArray, dst: DoubleArray): Homography? {
            if (src.size != 8 || dst.size != 8) return null
            val a = Array(8) { DoubleArray(9) }
            for (i in 0 until 4) {
                val u = src[2 * i]
                val v = src[2 * i + 1]
                val x = dst[2 * i]
                val y = dst[2 * i + 1]
                a[2 * i] = doubleArrayOf(u, v, 1.0, 0.0, 0.0, 0.0, -u * x, -v * x, x)
                a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, u, v, 1.0, -u * y, -v * y, y)
            }
            return gaussJordan(a)?.let(::Homography)
        }

        private fun gaussJordan(a: Array<DoubleArray>): DoubleArray? {
            val n = 8
            for (col in 0 until n) {
                var pivot = col
                for (r in col + 1 until n) if (abs(a[r][col]) > abs(a[pivot][col])) pivot = r
                // A vanishing pivot means the correspondences do not determine a transform. Fail,
                // rather than return a matrix assembled out of a division by roughly zero — those
                // produce finite, unremarkable-looking, entirely wrong coordinates.
                if (abs(a[pivot][col]) < 1e-10) return null
                val swap = a[col]
                a[col] = a[pivot]
                a[pivot] = swap
                val d = a[col][col]
                for (c in col..n) a[col][c] /= d
                for (r in 0 until n) {
                    if (r == col) continue
                    val f = a[r][col]
                    if (f == 0.0) continue
                    for (c in col..n) a[r][c] -= f * a[col][c]
                }
            }
            val out = DoubleArray(n) { a[it][n] }
            return if (out.all { it.isFinite() }) out else null
        }
    }
}

/**
 * A solved, usable calibration: the two directions between one target face and one photograph.
 *
 * Both directions are solved from the same four correspondences rather than by inverting a matrix,
 * so each is exactly as accurate as the athlete's handle placement and neither accumulates the
 * other's error.
 */
class FaceProjection
internal constructor(
    /** Which sub-face of a multi-face layout this describes; stamped onto every [PlotPoint]. */
    val faceIndex: Int,
    private val toImageH: Homography,
    private val toFaceH: Homography,
) {
    /**
     * Face coordinates → normalized image coordinates, into [out]. The rectifier's inner loop; see
     * [Homography.map] for why it writes into a caller-owned array.
     */
    fun toImage(faceX: Double, faceY: Double, out: DoubleArray): Boolean =
        toImageH.map(faceX, faceY, out)

    /** Allocating form of [toImage], for drawing a handful of markers over the photograph. */
    fun toImage(faceX: Double, faceY: Double): ImagePoint? {
        val out = DoubleArray(2)
        return if (toImage(faceX, faceY, out)) ImagePoint(out[0], out[1]) else null
    }

    /**
     * A point on the photograph → the target coordinate it names, or null when that lands outside
     * the domain [PlotPoint] accepts.
     *
     * The direction a correction inspector needs to turn a tap on the photo into a plottable arrow.
     * The detector itself never calls it — it works on an already-rectified raster, where face
     * coordinates come out of the raster index directly.
     */
    fun toFace(p: ImagePoint): PlotPoint? {
        val out = DoubleArray(2)
        if (!toFaceH.map(p.x, p.y, out)) return null
        if (out[0] !in -1.5..1.5 || out[1] !in -1.5..1.5) return null
        return PlotPoint(out[0], out[1], faceIndex)
    }
}

/** Why a set of calibration handles cannot be used. Rendered to the athlete verbatim. */
enum class CalibrationProblem(val message: String) {
    DEGENERATE("Those marks are too close together to describe the face."),
    NOT_CONVEX("The marks cross over each other. Put one on each edge: top, right, bottom, left."),
    MIS_ORDERED("The marks are mirrored. Go round the face top, right, bottom, then left."),
    EXTREME_PERSPECTIVE("That photo is at too steep an angle. Stand square to the target and retake."),
    UNSOLVABLE("Those marks do not describe a target face."),
}

/**
 * Where the target face is in a photograph — four handles the athlete places, not a detection.
 *
 * **This is deliberately not automatic ring detection.** The End Scan review panel already tells the
 * athlete that automatic target detection "is not yet range-validated"; shipping a ring finder that
 * silently mislocates the face under a cloud shadow or a boss with a second face pinned beside it
 * would make that sentence false in the worst available direction — every arrow downstream inherits
 * the same error, and they all still look plausible. Four drags cost about five seconds an end and
 * make the frame of reference something the athlete can see and correct. Seeding those handles
 * automatically (see [ellipse]) is a convenience layered on top; it never becomes an authority.
 *
 * The handles sit on the outer edge of one printed ring, at the face's own 12, 3, 6 and 9 o'clock —
 * *the face's* cardinal points, not the photograph's. That distinction is load-bearing: four exact
 * samples of the true face→image map determine that map exactly, so placing handles at the extremes
 * of whichever ellipse the camera happened to produce, rather than at the face's cardinal points, is
 * exactly the error the model cannot detect. It is also why calibration error surfaces as a
 * whole-face bias visible in the overlay rather than as one wrong arrow that is not.
 *
 * [boundaryScore] names which ring's outer edge the handles are on, so this works on faces where the
 * 1-ring is not printed at all. Ring *n*'s outer edge is at face radius `(11 - n)/10` — the relation
 * [scoreFromPlot] scores by, read backwards. Default 1: the outer edge of the whole scoring area,
 * which is what a single 122 cm / 80 cm / 40 cm face shows. Vertical and triangular triple faces
 * print nothing below 6, so their edge is the 6-ring's; [defaultBoundaryScore] picks per layout.
 */
data class FaceCalibration(
    val top: ImagePoint,
    val right: ImagePoint,
    val bottom: ImagePoint,
    val left: ImagePoint,
    val faceIndex: Int = 0,
    val boundaryScore: Int = 1,
) {
    init {
        require(faceIndex >= 0)
        require(boundaryScore in 1..10)
    }

    /** The face radius the handles lie on: the outer edge of ring [boundaryScore]. */
    val boundaryRadius: Double
        get() = (11 - boundaryScore) / 10.0

    /**
     * The reason these handles are unusable, or null when they are fine.
     *
     * Checked before any transform is solved, because a degenerate quad still *has* an algebraic
     * solution — one that folds the face inside out and maps arrows onto coordinates that are
     * finite, wrong, and unremarkable to look at. Each test below is a cheap question about
     * something the athlete can see on screen and fix with one drag.
     */
    fun problem(): CalibrationProblem? {
        val c = listOf(top, right, bottom, left)
        for (i in 0 until 4) for (j in i + 1 until 4) {
            if (hypot(c[i].x - c[j].x, c[i].y - c[j].y) < MIN_HANDLE_SEPARATION)
                return CalibrationProblem.DEGENERATE
        }
        // Convexity and winding in one pass, testing the turn at each of the four vertices. Image y
        // points down, so top → right → bottom → left is clockwise on screen and every turn is
        // positive. All four negative means the athlete went round anticlockwise (left and right
        // handles swapped), which is recoverable advice; mixed signs mean the quad self-intersects,
        // which no photograph of a circle can produce.
        var positive = 0
        var negative = 0
        for (i in 0 until 4) {
            val a = c[i]
            val b = c[(i + 1) % 4]
            val d = c[(i + 2) % 4]
            val cross = (b.x - a.x) * (d.y - b.y) - (b.y - a.y) * (d.x - b.x)
            if (cross > 0) positive++ else if (cross < 0) negative++
        }
        if (negative == 4) return CalibrationProblem.MIS_ORDERED
        if (positive != 4) return CalibrationProblem.NOT_CONVEX
        // A circle photographed at a steep angle projects to a long thin ellipse. Past roughly 65°
        // off the face normal, the near and far edges differ so much in scale that handle-placement
        // error at the far edge dominates every arrow, and the honest answer is "retake it" rather
        // than a confident number derived from a few pixels of foreshortened white.
        val vertical = hypot(top.x - bottom.x, top.y - bottom.y)
        val horizontal = hypot(left.x - right.x, left.y - right.y)
        if (vertical <= 0.0 || horizontal <= 0.0) return CalibrationProblem.DEGENERATE
        if (max(vertical, horizontal) / min(vertical, horizontal) > MAX_AXIS_RATIO)
            return CalibrationProblem.EXTREME_PERSPECTIVE
        return null
    }

    /**
     * Solve both directions, or report why the handles cannot be used.
     *
     * Returns a [Result] rather than a nullable projection so the caller has the athlete-facing
     * sentence to show; "calibration failed" with no reason is the kind of dead end that sends
     * someone back to manual scoring for good.
     */
    fun project(): Result {
        problem()?.let {
            return Result.Unusable(it)
        }
        val b = boundaryRadius
        // Face-space positions of the four handles, in the same order as the image-space list.
        val face = doubleArrayOf(0.0, b, b, 0.0, 0.0, -b, -b, 0.0)
        val image = doubleArrayOf(top.x, top.y, right.x, right.y, bottom.x, bottom.y, left.x, left.y)
        val toImage =
            Homography.fromCorrespondences(face, image)
                ?: return Result.Unusable(CalibrationProblem.UNSOLVABLE)
        val toFace =
            Homography.fromCorrespondences(image, face)
                ?: return Result.Unusable(CalibrationProblem.UNSOLVABLE)
        return Result.Usable(FaceProjection(faceIndex, toImage, toFace))
    }

    sealed interface Result {
        data class Usable(val projection: FaceProjection) : Result

        data class Unusable(val problem: CalibrationProblem) : Result
    }

    companion object {
        /**
         * Two handles must be at least this far apart, as a fraction of the frame, before the quad
         * is believed. Anything closer describes a target face a few pixels across.
         */
        const val MIN_HANDLE_SEPARATION: Double = 0.02

        /**
         * The most lopsided quad still accepted, as the ratio of its two diameters. 2.5 corresponds
         * to roughly 66° off the face normal.
         *
         * Derived from the projective geometry above, **not** measured against real range photos —
         * no such set exists in this repository and none can be produced in a headless build. It is
         * a backstop against obvious nonsense, not a validated capture envelope.
         */
        const val MAX_AXIS_RATIO: Double = 2.5

        /**
         * Handles for a face seen square-on: an axis-aligned ellipse for the athlete to drag.
         *
         * The seed, not the answer. Starting from four points already near the face is what makes
         * calibration a nudge rather than four cold taps, and on a genuinely square-on photo it may
         * need no correction at all — but nothing except the athlete looking at the overlay decides
         * that.
         */
        fun ellipse(
            centre: ImagePoint,
            radiusX: Double,
            radiusY: Double,
            faceIndex: Int = 0,
            boundaryScore: Int = 1,
        ): FaceCalibration =
            FaceCalibration(
                top = ImagePoint(centre.x, centre.y - radiusY),
                right = ImagePoint(centre.x + radiusX, centre.y),
                bottom = ImagePoint(centre.x, centre.y + radiusY),
                left = ImagePoint(centre.x - radiusX, centre.y),
                faceIndex = faceIndex,
                boundaryScore = boundaryScore,
            )

        /** The ring whose printed outer edge a layout actually shows, for seeding [boundaryScore]. */
        fun defaultBoundaryScore(layout: FaceLayout): Int =
            if (layout == FaceLayout.SINGLE) 1 else 6
    }
}
