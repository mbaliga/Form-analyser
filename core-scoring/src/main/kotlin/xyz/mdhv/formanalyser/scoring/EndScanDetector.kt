package xyz.mdhv.formanalyser.scoring

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * An 8-bit greyscale photograph, row-major, as plain JVM data.
 *
 * Deliberately not an Android `Bitmap`. Everything in this file is arithmetic on a plane, it is the
 * part of End Scan most likely to be wrong, and it is the only part that can be *tested* in this
 * repository — `app-android` cannot even be compiled without an SDK. The Android layer's whole job
 * is to hand one of these over and to draw what comes back.
 */
class GrayImage(val width: Int, val height: Int, val luma: ByteArray) {
    init {
        require(width > 0 && height > 0) { "Image must have positive dimensions" }
        require(luma.size == width * height) { "luma must hold exactly width*height samples" }
    }

    fun at(x: Int, y: Int): Int = luma[y * width + x].toInt() and 0xFF

    /**
     * Bilinear sample at a pixel-centre coordinate, or null when it falls outside the frame.
     *
     * Bilinear rather than nearest-neighbour because rectification resamples at an arbitrary,
     * spatially varying scale; nearest-neighbour aliasing there would break a shaft silhouette into
     * a dotted line, and the connected-component pass would then see several fragments where there
     * is one arrow.
     */
    fun sample(px: Double, py: Double): Int? {
        if (!px.isFinite() || !py.isFinite()) return null
        if (px < 0.0 || py < 0.0 || px > width - 1.0 || py > height - 1.0) return null
        val x0 = px.toInt()
        val y0 = py.toInt()
        val x1 = min(x0 + 1, width - 1)
        val y1 = min(y0 + 1, height - 1)
        val fx = px - x0
        val fy = py - y0
        val top = at(x0, y0) * (1 - fx) + at(x1, y0) * fx
        val bottom = at(x0, y1) * (1 - fx) + at(x1, y1) * fx
        return (top * (1 - fy) + bottom * fy).toInt().coerceIn(0, 255)
    }
}

/**
 * The target face resampled into its own square — face coordinates `[-1,1]²`, y up, one raster.
 *
 * Rectifying before detecting, rather than detecting in the photograph, is what keeps the rest of
 * this file simple and honest. In here a millimetre is the same number of pixels wherever you are on
 * the face, "how far from the centre" is a subtraction, and every threshold below is written in face
 * units — a fixed, inspectable constant — instead of in pixels that mean different things at the top
 * and the bottom of a tilted photograph.
 *
 * It also makes the obvious future extension free: a *reference* photograph of the same face,
 * calibrated separately, resamples into this identical square, so differencing the two to isolate
 * the arrows shot in this end needs no image registration at all. That is the real answer to
 * previous ends' arrow holes (see [EndScan]); it is not built here only because it costs the athlete
 * a second calibration every end.
 *
 * [valid] marks samples that both landed inside the source frame and lie inside the face disc. Those
 * are the only pixels any statistic in this file is computed over.
 */
class FaceRaster
internal constructor(
    val size: Int,
    internal val luma: ByteArray,
    internal val valid: BooleanArray,
    /** Fraction of the face disc that was actually inside the photograph. */
    val faceCoverage: Double,
)

/**
 * Proposes where the arrows of one end landed, from one photograph of the target.
 *
 * ### What this is allowed to do
 *
 * Nothing here scores anything. It turns blobs into [PlotPoint]s and hands them to [scoreFromPlot] —
 * the same function manual Plot mode has used since scoring shipped — so the app holds exactly one
 * implementation of "which ring is this", and End Scan cannot drift away from what the athlete sees
 * when they tap the target themselves. Everything produced here is a *proposal*: the repository's
 * `proposeEndScanCandidates` / `confirmEndScanCandidate` pair is the only route onto a scorecard,
 * and it requires a human to press Confirm on every single arrow.
 *
 * ### The two things a photograph of a shot end actually contains
 *
 * The design follows from one uncomfortable domain fact: **the arrows are still in the boss.** They
 * have to be — pull them first and the face carries every hole from every previous end, so on end 6
 * of a 72-arrow round a hole detector would find eighteen equally good holes and no way to say which
 * three belong to this end.
 *
 * So the thing to detect is a nocked arrow, and a nocked arrow is not a hole. It is a shaft standing
 * up to 70 cm out of the target plane towards the camera, so it appears *displaced from its own
 * impact point* — the further out on the face, the further displaced. Photographed roughly square-on,
 * that displacement is purely radially outward from the face centre, which gives the two rules that
 * do all the real work here:
 *
 *  - a nocked arrow appears as a **radial streak**, and the impact is its **inner tip** — the end
 *    nearest the centre — never its centroid, which sits somewhere up the shaft;
 *  - the printed ring boundaries, the largest source of spurious blobs on a target face, run
 *    **tangentially** — exactly perpendicular to every real arrow. Rejecting tangential elongation
 *    removes that whole class of false positive with one comparison, and it is the reason this
 *    detector can work at all without a learned model.
 *
 * Near the centre the displacement vanishes and there is no meaningful radial direction, so a small
 * compact blob inside [COMPACT_MAX_FACE_RADIUS] is accepted on its centroid. Further out, a compact
 * blob is far more likely to be an older hole than an arrow, and is refused.
 *
 * ### The capture envelope this assumes
 *
 * Everything above depends on the athlete walking to the target and photographing it from a couple
 * of paces, square-on, with the face filling the frame and the arrows still in it. That is the
 * geometry in which shafts streak. From twenty metres with a zoom, nothing streaks, every arrow
 * looks exactly like every old hole, and no amount of thresholding fixes it — that capture is
 * outside what this detector supports, and the flow that drives it says so rather than quietly
 * proposing holes from three ends ago.
 *
 * ### Precision over recall, everywhere
 *
 * A missed arrow costs the athlete a few taps on the keypad they were reaching for anyway. A
 * plausible false arrow costs them a corrupted end — and, because it arrives pre-scored and looking
 * official, it is exactly the kind of error a tired archer confirms without reading. So every gate
 * below refuses when unsure, every refusal is counted and reportable so that "why did it miss my
 * arrow?" has an answer, and **nothing is ever proposed as a miss**: a machine that cannot see an
 * arrow has to say so, not score a zero.
 *
 * ### Honesty about the numbers
 *
 * Every constant here is derived from target geometry and arrow dimensions, or chosen as a
 * conservative backstop. **None is validated against real range photographs** — no such set exists in
 * this repository and none can be produced in a headless build, and nothing in this file has ever run
 * on a device. That is exactly why the End Scan panel says automatic detection is not yet
 * range-validated, and why the only thing this object is wired to is a review queue.
 */
object EndScan {

    // --- Capture and rectification -------------------------------------------------------------

    /**
     * Side of the rectified square, in pixels.
     *
     * 768 puts the face radius at 384 px, so a 5.5 mm shaft is about 3.5 px across on a 122 cm face
     * and about 11 px on a 40 cm one — thin, but a streak is tens of pixels *long*, and length is
     * what the shape tests actually measure. Doubling it would roughly quadruple both the working set
     * (~9 MB of transient arrays at this size) and the pass time, for detail the source photograph
     * usually does not have.
     */
    const val DEFAULT_RASTER_SIZE: Int = 768

    /**
     * How much of the face disc must have been inside the photograph before anything is proposed.
     *
     * A face running off the edge of the frame is not a partially usable photograph: the missing
     * part is exactly where an unproposed arrow would be, and the athlete has no way to tell a face
     * that was clean from one that was cropped. Retaking costs seconds.
     */
    const val MIN_FACE_COVERAGE: Double = 0.98

    // --- Impact geometry -----------------------------------------------------------------------

    /**
     * Lower end of the half-width band an impact silhouette must fall inside, in millimetres on the
     * face — a little under a bare shaft, which gives a ~2.5 mm half-width for a 4–5.5 mm arrow.
     *
     * Converted to face units against the round's real face diameter, so a 40 cm face at 18 m and a
     * 122 cm face at 70 m get the same *physical* band rather than the same pixel band.
     */
    const val MIN_IMPACT_HALF_WIDTH_MM: Double = 2.5

    /** Upper end of that band: shaft plus nock, a fletching edge, and the shaft's own shadow. */
    const val MAX_IMPACT_HALF_WIDTH_MM: Double = 14.0

    /**
     * How far outside the width band a blob may still be accepted, as a multiplier.
     *
     * The band is a physical estimate, not a specification, so a hard edge on it would silently drop
     * arrows for having a slightly fat shadow. Blobs in the margin are accepted with a reduced size
     * factor, and usually land under [REVIEW_CAREFULLY_BELOW] — flagged for a closer look rather than
     * hidden.
     */
    const val SIZE_TOLERANCE: Double = 1.6

    /** Confidence multiplier at the very edge of the widened band; 1.0 inside the band proper. */
    const val SIZE_EDGE_FACTOR: Double = 0.45

    /**
     * Longest plausible streak, in face radii.
     *
     * An arrow in the 1-ring photographed from two metres can project a streak of roughly half a
     * face radius. Past this the blob is a shadow across the boss, the boss edge, or the athlete's
     * own silhouette, and no arrow explains it.
     */
    const val MAX_STREAK_LENGTH_FACE: Double = 0.60

    /** Below this many raster pixels a blob is sensor noise, whatever shape it has. */
    const val MIN_BLOB_AREA_PX: Int = 8

    /** Elongation — the ratio of principal axes — at which a blob is read as a streak, not a mark. */
    const val STREAK_ELONGATION: Double = 2.0

    /**
     * How closely a streak's axis must point at the face centre: the cosine of the largest accepted
     * angle, 35°.
     *
     * The single gate that removes printed ring boundaries, the black 3/4 band's edges and the face's
     * outer circle, all of which run tangentially. It also removes genuine arrows when the photograph
     * was taken from well off to one side, because then the displacement is radial about the camera's
     * axis rather than about the face centre — which is the correct behaviour: the athlete gets fewer
     * candidates and a count warning, not confidently mislocated ones.
     */
    const val MIN_RADIAL_ALIGNMENT: Double = 0.819

    /**
     * Face radius inside which a compact blob may be an arrow seen end-on.
     *
     * Out-of-plane displacement scales with distance from the centre, so inside the 10-ring an arrow
     * pointing at the camera really does appear as a dot on its own impact point. Outside it, a real
     * nocked arrow streaks, so a compact blob out there is something else — most often one of the
     * previous ends' holes — and is refused.
     */
    const val COMPACT_MAX_FACE_RADIUS: Double = 0.10

    /** Fraction of its bounding box a compact blob must fill; a curved printed line fills far less. */
    const val MIN_COMPACT_FILL: Double = 0.45

    /** Ceiling on a compact blob's confidence: it is the morphology this detector is least sure of. */
    const val COMPACT_CONFIDENCE_CEILING: Double = 0.75

    // --- Thresholding --------------------------------------------------------------------------

    /**
     * Local-mean window, as a fraction of the raster side.
     *
     * Wide enough that a whole streak is a negligible share of its own background window — otherwise
     * long shafts suppress themselves and break into fragments — and narrow enough to follow the
     * lighting gradient across a boss lit from one side. It deliberately does *not* try to be
     * narrower than a ring band: separating arrows from printed rings is [MIN_RADIAL_ALIGNMENT]'s
     * job, not the window's.
     */
    const val THRESHOLD_WINDOW_FRACTION: Double = 0.125

    /** Luma a pixel must differ from its local mean by before it is foreground at all, out of 255. */
    const val THRESHOLD_OFFSET: Int = 16

    /** Mean absolute deviation from local background at which the contrast factor reaches 1.0. */
    const val CONTRAST_SATURATION: Double = 40.0

    // --- Proposal thresholds -------------------------------------------------------------------

    /**
     * Below this, a candidate is not shown at all.
     *
     * The threshold protects the review queue, not the scorecard — the scorecard is already protected
     * by the human confirm. But a review list padded with junk is read less carefully with every end,
     * and a list the athlete has stopped reading is worse than no detector at all, because that is
     * the state in which a wrong arrow gets waved through. Set where a blob has to be at least
     * arrow-shaped, arrow-sized and pointing the right way before it earns a row.
     */
    const val PROPOSE_MIN_CONFIDENCE: Double = 0.35

    /**
     * Between [PROPOSE_MIN_CONFIDENCE] and this, a candidate is still proposed but must be presented
     * as needing a closer look. Nothing above it is auto-confirmed either — there is no confidence at
     * which a machine may write an authoritative score.
     */
    const val REVIEW_CAREFULLY_BELOW: Double = 0.60

    /**
     * Positional uncertainty budget in face radii, used only to flag possible line-cutters.
     *
     * 0.02 is 1.2 cm on a 122 cm face — roughly what a 1 % handle-placement error at the face edge
     * costs, plus centroid noise. Any impact this close to a ring boundary is flagged: the rules give
     * a line-cutter the higher value, a photograph from the front cannot see whether the shaft
     * touches the line, and guessing is not this detector's business.
     */
    const val POSITION_TOLERANCE_FACE: Double = 0.02

    /** Two impacts closer than this in face radii are one object found by both polarity passes. */
    const val DEDUPE_FACE_DISTANCE: Double = 0.03

    /**
     * A version stamp stored alongside every candidate this object proposes.
     *
     * The blueprint requires a machine proposal to carry the version of the model that produced it,
     * and a detector whose constants change between releases must not leave last month's candidates
     * looking like this month's. Bump it whenever anything above changes the output.
     */
    const val DETECTOR_VERSION: String = "endscan-geom-1"

    /** What a candidate looks like, and therefore how its position was derived. */
    enum class Shape {
        /** A radial streak — a nocked arrow. Position is the inner tip. */
        STREAK,
        /** A compact mark near the centre — an arrow seen end-on. Position is the centroid. */
        COMPACT,
    }

    /** Why a blob was not proposed. Counted and reportable, so a missing arrow has an explanation. */
    enum class Rejection(val label: String) {
        NOT_IN_FRAME("the whole face was not in the photo"),
        TOO_SMALL("too small for an arrow"),
        TOO_LARGE("too big for an arrow"),
        TANGENTIAL("lying along a ring line rather than pointing out from the centre"),
        AMBIGUOUS_COMPACT("a round mark away from the centre — most likely an older hole"),
        TOO_SPARSE("too ragged to be a shaft"),
        OUTSIDE_FACE("off the scoring face"),
        LOW_CONFIDENCE("too uncertain to be worth reviewing"),
        EXCESS_OVER_ARROW_COUNT("more marks than there are arrows left in this end"),
    }

    /**
     * One proposed impact.
     *
     * [confidence] is the detector's opinion of this blob and nothing more — not a probability, not
     * calibrated, and never an authorisation. It exists so the review screen can order the list and
     * mark the shaky ones, and so a candidate a human agreed with carries a record of how sure the
     * machine had been.
     */
    data class Impact(
        val plot: PlotPoint,
        val score: ArrowScore,
        val confidence: Double,
        val shape: Shape,
        /**
         * True when the impact sits within [POSITION_TOLERANCE_FACE] of a ring boundary, so the ring
         * shown may be one out. The rules award a line-cutter the higher value and this detector
         * cannot see whether the shaft touches the line, so the athlete decides.
         */
        val lineCutter: Boolean,
        /** Equivalent circular radius of the blob in face units; sizes the marker on the overlay. */
        val radiusFace: Double,
    ) {
        /** Whether the review screen must present this one as needing a closer look. */
        val needsCloseLook: Boolean
            get() = confidence < REVIEW_CAREFULLY_BELOW || lineCutter
    }

    /**
     * Everything one photograph produced.
     *
     * [countMismatch] is deliberately prominent. The blueprint requires reconciliation whenever the
     * machine's count disagrees with the end's, and the athlete is the only one who knows whether the
     * missing arrow is buried in the black 3-ring where nothing is visible, or in the grass.
     */
    data class Result(
        val impacts: List<Impact>,
        val rejections: Map<Rejection, Int>,
        val faceCoverage: Double,
        val arrowsExpected: Int,
        val detectorVersion: String = DETECTOR_VERSION,
    ) {
        val countMismatch: Boolean
            get() = impacts.size != arrowsExpected
    }

    /** What the detector needs to know about the round it is scanning for. */
    data class Options(
        /** Face diameter in centimetres — turns the millimetre size band into face units. */
        val targetFaceCm: Int,
        val faceLayout: FaceLayout = FaceLayout.SINGLE,
        /** Arrows still to be recorded in this end. Nothing beyond this many is ever proposed. */
        val arrowsExpected: Int,
        val rasterSize: Int = DEFAULT_RASTER_SIZE,
    ) {
        init {
            require(targetFaceCm > 0)
            require(arrowsExpected >= 0)
            require(rasterSize in 128..4096)
        }
    }

    /**
     * Resample the calibrated face into its own square.
     *
     * Public because the calibration screen wants to show the athlete exactly what the detector will
     * see: a rectified face that looks like a target face is the clearest confirmation the four
     * handles are in the right places, and one that looks like an egg is the clearest possible sign
     * they are not.
     */
    fun rectify(
        image: GrayImage,
        projection: FaceProjection,
        size: Int = DEFAULT_RASTER_SIZE,
    ): FaceRaster {
        require(size in 128..4096)
        val luma = ByteArray(size * size)
        val valid = BooleanArray(size * size)
        val out = DoubleArray(2)
        var inside = 0
        var covered = 0
        for (j in 0 until size) {
            val faceY = 1.0 - (j + 0.5) * 2.0 / size
            for (i in 0 until size) {
                val faceX = (i + 0.5) * 2.0 / size - 1.0
                if (faceX * faceX + faceY * faceY > 1.0) continue
                inside++
                if (!projection.toImage(faceX, faceY, out)) continue
                val sample =
                    image.sample(out[0] * image.width - 0.5, out[1] * image.height - 0.5) ?: continue
                val index = j * size + i
                luma[index] = sample.toByte()
                valid[index] = true
                covered++
            }
        }
        return FaceRaster(size, luma, valid, if (inside == 0) 0.0 else covered.toDouble() / inside)
    }

    /**
     * Find the arrows of one end in one photograph of one target face.
     *
     * [projection] comes from [FaceCalibration.project], so a calibration the athlete has not yet
     * made usable can never reach here — which is why this signature has no way to express a
     * calibration failure. Multi-face layouts call this once per calibrated sub-face.
     */
    fun detect(image: GrayImage, projection: FaceProjection, options: Options): Result {
        val raster = rectify(image, projection, options.rasterSize)
        val rejections = mutableMapOf<Rejection, Int>()
        if (raster.faceCoverage < MIN_FACE_COVERAGE) {
            rejections.record(Rejection.NOT_IN_FRAME)
            return Result(emptyList(), rejections, raster.faceCoverage, options.arrowsExpected)
        }

        val faceRadiusMm = options.targetFaceCm * 10.0 / 2.0
        val minHalfWidth = MIN_IMPACT_HALF_WIDTH_MM / faceRadiusMm
        val maxHalfWidth = MAX_IMPACT_HALF_WIDTH_MM / faceRadiusMm
        val integral = Integral(raster)

        val found = mutableListOf<Impact>()
        // Both polarities, because "an arrow is darker than the face" is only half true: a black
        // carbon shaft on the white 1-ring and a white aluminium shaft on the black 3-ring are both
        // ordinary equipment, and either way the shaft's own shadow shows up in the other pass.
        // Objects both passes find are merged below.
        for (darker in listOf(true, false)) {
            for (blob in components(raster, integral, darker)) {
                val faceIndex = projection.faceIndex
                when (
                    val outcome =
                        classify(blob, raster, faceIndex, minHalfWidth, maxHalfWidth, options)
                ) {
                    is Classified.Found -> found += outcome.impact
                    is Classified.Refused -> rejections.record(outcome.reason)
                }
            }
        }

        val deduped = dedupe(found)
        val (confident, weak) = deduped.partition { it.confidence >= PROPOSE_MIN_CONFIDENCE }
        rejections.record(Rejection.LOW_CONFIDENCE, weak.size)

        val ranked = confident.sortedByDescending { it.confidence }
        val kept = ranked.take(options.arrowsExpected)
        rejections.record(Rejection.EXCESS_OVER_ARROW_COUNT, ranked.size - kept.size)

        // Presented highest score first, the order an athlete writes an end down in. Ordering is
        // presentational only: a confirmed candidate is recorded with END_ONLY resolution, so
        // nothing here claims to know which arrow was shot when.
        val ordered =
            kept.sortedWith(
                compareByDescending<Impact> { it.score.points }
                    .thenByDescending { it.score.isX }
                    .thenByDescending { it.confidence }
            )
        return Result(ordered, rejections, raster.faceCoverage, options.arrowsExpected)
    }

    // --- Internals -----------------------------------------------------------------------------

    private fun MutableMap<Rejection, Int>.record(reason: Rejection, times: Int = 1) {
        if (times > 0) this[reason] = (this[reason] ?: 0) + times
    }

    private sealed interface Classified {
        data class Found(val impact: Impact) : Classified

        data class Refused(val reason: Rejection) : Classified
    }

    /**
     * Summed-area tables over the *valid* pixels only, so a local mean never quietly averages in the
     * black nothing outside the face disc or a corner the photograph did not cover.
     */
    private class Integral(raster: FaceRaster) {
        val size = raster.size
        private val stride = size + 1
        private val sum = IntArray(stride * stride)
        private val count = IntArray(stride * stride)

        init {
            for (y in 0 until size) {
                var rowSum = 0
                var rowCount = 0
                for (x in 0 until size) {
                    val index = y * size + x
                    if (raster.valid[index]) {
                        rowSum += raster.luma[index].toInt() and 0xFF
                        rowCount++
                    }
                    sum[(y + 1) * stride + (x + 1)] = sum[y * stride + (x + 1)] + rowSum
                    count[(y + 1) * stride + (x + 1)] = count[y * stride + (x + 1)] + rowCount
                }
            }
        }

        /** Mean luma over the window around (x, y), or null when too little of it is valid. */
        fun mean(x: Int, y: Int, half: Int): Double? {
            val x0 = max(0, x - half)
            val y0 = max(0, y - half)
            val x1 = min(size - 1, x + half)
            val y1 = min(size - 1, y + half)
            val a = y0 * stride + x0
            val b = y0 * stride + (x1 + 1)
            val c = (y1 + 1) * stride + x0
            val d = (y1 + 1) * stride + (x1 + 1)
            val n = count[d] - count[b] - count[c] + count[a]
            val window = (x1 - x0 + 1) * (y1 - y0 + 1)
            // A window mostly outside the face describes the face's edge, not its surface. Refusing
            // here is what stops the outer rim of the raster lighting up as one enormous blob.
            if (n * 4 < window) return null
            return (sum[d] - sum[b] - sum[c] + sum[a]).toDouble() / n
        }
    }

    /** A connected run of foreground pixels, with its own pixel indices held contiguously. */
    private class Blob(val pixels: IntArray, val contrast: Double)

    /**
     * Adaptive threshold plus 8-connected labelling, in one sweep.
     *
     * The flood fill uses its own output array as its queue: a component's pixels are written
     * contiguously as they are discovered, and the fill then walks that same run with a head pointer.
     * One allocation, no recursion — a shadow lying across the whole boss would otherwise be a stack
     * overflow rather than a rejected blob.
     */
    private fun components(raster: FaceRaster, integral: Integral, darker: Boolean): List<Blob> {
        val size = raster.size
        val half = max(4, (size * THRESHOLD_WINDOW_FRACTION / 2).toInt())
        val n = size * size
        val foreground = BooleanArray(n)
        val deviation = FloatArray(n)
        var count = 0
        for (y in 0 until size) {
            for (x in 0 until size) {
                val index = y * size + x
                if (!raster.valid[index]) continue
                val mean = integral.mean(x, y, half) ?: continue
                val value = raster.luma[index].toInt() and 0xFF
                val delta = if (darker) mean - value else value - mean
                if (delta < THRESHOLD_OFFSET) continue
                foreground[index] = true
                deviation[index] = delta.toFloat()
                count++
            }
        }
        if (count == 0) return emptyList()

        val order = IntArray(count)
        val visited = BooleanArray(n)
        val blobs = mutableListOf<Blob>()
        var written = 0
        for (seed in 0 until n) {
            if (!foreground[seed] || visited[seed]) continue
            val start = written
            visited[seed] = true
            order[written++] = seed
            var head = start
            var contrast = 0.0
            while (head < written) {
                val p = order[head++]
                contrast += deviation[p]
                val px = p % size
                val py = p / size
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val qx = px + dx
                    val qy = py + dy
                    if (qx < 0 || qy < 0 || qx >= size || qy >= size) continue
                    val q = qy * size + qx
                    if (!foreground[q] || visited[q]) continue
                    visited[q] = true
                    order[written++] = q
                }
            }
            val length = written - start
            if (length >= MIN_BLOB_AREA_PX)
                blobs += Blob(order.copyOfRange(start, written), contrast / length)
        }
        return blobs
    }

    /**
     * Decide what one blob is, and where — the whole of the shape reasoning described in this
     * object's header, applied to a single connected run of pixels.
     */
    private fun classify(
        blob: Blob,
        raster: FaceRaster,
        faceIndex: Int,
        minHalfWidth: Double,
        maxHalfWidth: Double,
        options: Options,
    ): Classified {
        val size = raster.size
        val unitsPerPx = 2.0 / size
        val area = blob.pixels.size
        var sumX = 0.0
        var sumY = 0.0
        var minX = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var minY = Int.MAX_VALUE
        var maxY = Int.MIN_VALUE
        for (p in blob.pixels) {
            val x = p % size
            val y = p / size
            sumX += x
            sumY += y
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
        val cx = sumX / area
        val cy = sumY / area

        var sxx = 0.0
        var syy = 0.0
        var sxy = 0.0
        for (p in blob.pixels) {
            val dx = (p % size) - cx
            val dy = (p / size) - cy
            sxx += dx * dx
            syy += dy * dy
            sxy += dx * dy
        }
        sxx /= area
        syy /= area
        sxy /= area
        // Closed-form eigenvalues of a 2x2 symmetric covariance. The floor is the variance of a
        // one-pixel-wide line (1/12), below which "how elongated is it" stops being a question about
        // the object and becomes a question about the sampling grid.
        val mid = (sxx + syy) / 2.0
        val spread = sqrt(((sxx - syy) / 2.0) * ((sxx - syy) / 2.0) + sxy * sxy)
        val major = max(mid + spread, 1.0 / 12.0)
        val minor = max(mid - spread, 1.0 / 12.0)
        // For a uniform ellipse with semi-axes a and b the second moments are a²/4 and b²/4, so this
        // recovers the semi-axes themselves.
        val halfWidthPx = 2.0 * sqrt(minor)
        val halfLengthPx = 2.0 * sqrt(major)
        val elongation = sqrt(major / minor)

        val halfWidthFace = halfWidthPx * unitsPerPx
        if (halfWidthFace < minHalfWidth / SIZE_TOLERANCE)
            return Classified.Refused(Rejection.TOO_SMALL)
        if (halfWidthFace > maxHalfWidth * SIZE_TOLERANCE)
            return Classified.Refused(Rejection.TOO_LARGE)
        if (2.0 * halfLengthPx * unitsPerPx > MAX_STREAK_LENGTH_FACE)
            return Classified.Refused(Rejection.TOO_LARGE)

        val centre = size / 2.0
        val radialX = cx - centre
        val radialY = cy - centre
        val radialLength = hypot(radialX, radialY)

        val shape: Shape
        val shapeFactor: Double
        val impactX: Double
        val impactY: Double
        if (elongation >= STREAK_ELONGATION) {
            // A streak through the exact centre has no outward direction, so nothing can say which
            // end of it the arrow entered at.
            if (radialLength < 1e-9) return Classified.Refused(Rejection.AMBIGUOUS_COMPACT)
            val ux = radialX / radialLength
            val uy = radialY / radialLength
            val axis = principalAxis(sxx, syy, sxy, major)
            val alignment = abs(axis[0] * ux + axis[1] * uy)
            if (alignment < MIN_RADIAL_ALIGNMENT) return Classified.Refused(Rejection.TANGENTIAL)
            val tip = innerTip(blob.pixels, size, centre, ux, uy, halfWidthPx)
            shape = Shape.STREAK
            shapeFactor =
                ((alignment - MIN_RADIAL_ALIGNMENT) / (1.0 - MIN_RADIAL_ALIGNMENT)).coerceIn(0.0, 1.0)
            impactX = tip[0]
            impactY = tip[1]
        } else {
            val fill = area.toDouble() / ((maxX - minX + 1).toDouble() * (maxY - minY + 1))
            if (fill < MIN_COMPACT_FILL) return Classified.Refused(Rejection.TOO_SPARSE)
            if (radialLength * unitsPerPx > COMPACT_MAX_FACE_RADIUS)
                return Classified.Refused(Rejection.AMBIGUOUS_COMPACT)
            shape = Shape.COMPACT
            shapeFactor = COMPACT_CONFIDENCE_CEILING
            impactX = cx
            impactY = cy
        }

        val faceX = (impactX + 0.5) * unitsPerPx - 1.0
        val faceY = 1.0 - (impactY + 0.5) * unitsPerPx
        val radius = hypot(faceX, faceY)
        if (radius > 1.0) return Classified.Refused(Rejection.OUTSIDE_FACE)

        val plot = PlotPoint(faceX, faceY, faceIndex)
        val score = scoreFromPlot(plot, options.faceLayout)
        // Never proposed as a miss — including the sub-6 area of a triple face, which scores zero.
        // A machine-proposed zero is a score invented out of an absence; only the athlete can say an
        // arrow missed.
        if (score.points == 0) return Classified.Refused(Rejection.OUTSIDE_FACE)

        val confidence =
            (blob.contrast / CONTRAST_SATURATION).coerceIn(0.0, 1.0) *
                sizeFactor(halfWidthFace, minHalfWidth, maxHalfWidth) *
                shapeFactor
        return Classified.Found(
            Impact(
                plot = plot,
                score = score,
                confidence = confidence.coerceIn(0.0, 1.0),
                shape = shape,
                lineCutter = isLineCutter(radius),
                radiusFace = sqrt(area / PI) * unitsPerPx,
            )
        )
    }

    /** Unit eigenvector of the covariance for eigenvalue [major] — the blob's long axis. */
    private fun principalAxis(sxx: Double, syy: Double, sxy: Double, major: Double): DoubleArray {
        val vx = sxy
        val vy = major - sxx
        val n = hypot(vx, vy)
        // sxy ≈ 0 means the axes already line up with the grid and the eigenvector above degenerates
        // to (0,0); fall back to whichever grid axis carries the larger variance.
        if (n < 1e-12) return if (sxx >= syy) doubleArrayOf(1.0, 0.0) else doubleArrayOf(0.0, 1.0)
        return doubleArrayOf(vx / n, vy / n)
    }

    /**
     * The end of a streak nearest the face centre — the arrow's impact point.
     *
     * Averaged over a band one shaft-width deep rather than taken as the single most extreme pixel,
     * so one stray thresholded pixel cannot move an arrow a ring inwards.
     */
    private fun innerTip(
        pixels: IntArray,
        size: Int,
        centre: Double,
        ux: Double,
        uy: Double,
        bandPx: Double,
    ): DoubleArray {
        var least = Double.MAX_VALUE
        for (p in pixels) {
            val t = ((p % size) - centre) * ux + ((p / size) - centre) * uy
            if (t < least) least = t
        }
        val limit = least + max(2.0, bandPx)
        var sumX = 0.0
        var sumY = 0.0
        var n = 0
        for (p in pixels) {
            val x = (p % size).toDouble()
            val y = (p / size).toDouble()
            if ((x - centre) * ux + (y - centre) * uy > limit) continue
            sumX += x
            sumY += y
            n++
        }
        return if (n == 0) doubleArrayOf(centre, centre) else doubleArrayOf(sumX / n, sumY / n)
    }

    /** 1.0 inside the physical width band, tapering to [SIZE_EDGE_FACTOR] at the accepted margins. */
    private fun sizeFactor(halfWidth: Double, min: Double, max: Double): Double {
        if (halfWidth in min..max) return 1.0
        val edge: Double
        val bound: Double
        if (halfWidth < min) {
            edge = min / SIZE_TOLERANCE
            bound = min
        } else {
            edge = max * SIZE_TOLERANCE
            bound = max
        }
        val span = abs(bound - edge)
        if (span < 1e-12) return SIZE_EDGE_FACTOR
        val t = (abs(halfWidth - edge) / span).coerceIn(0.0, 1.0)
        return SIZE_EDGE_FACTOR + (1.0 - SIZE_EDGE_FACTOR) * t
    }

    /**
     * Whether an impact at this face radius sits within [POSITION_TOLERANCE_FACE] of any scoring
     * boundary, the X ring included — so the ring it was given may be one out.
     *
     * Public because the review screen re-derives the flag from a stored candidate's coordinates
     * rather than from a column: the flag is a function of the plot and the tolerance, and storing
     * it would let the two drift apart the moment the tolerance is revised.
     */
    fun isLineCutter(radius: Double): Boolean {
        if (abs(radius - 0.05) <= POSITION_TOLERANCE_FACE) return true
        for (ring in 1..10) {
            if (abs(radius - ring / 10.0) <= POSITION_TOLERANCE_FACE) return true
        }
        return false
    }

    /** Keep the stronger of any two impacts the dark and bright passes both found. */
    private fun dedupe(impacts: List<Impact>): List<Impact> {
        val kept = mutableListOf<Impact>()
        for (impact in impacts.sortedByDescending { it.confidence }) {
            val duplicate =
                kept.any {
                    hypot(it.plot.x - impact.plot.x, it.plot.y - impact.plot.y) <
                        DEDUPE_FACE_DISTANCE
                }
            if (!duplicate) kept += impact
        }
        return kept
    }
}
