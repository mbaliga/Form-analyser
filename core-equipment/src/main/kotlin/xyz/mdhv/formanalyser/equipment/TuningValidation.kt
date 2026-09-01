package xyz.mdhv.formanalyser.equipment

import kotlin.math.abs

/** Severity of a [TuningWarning]. ERROR = physically impossible / unsafe; WARNING = out of the usual band. */
enum class WarningSeverity { WARNING, ERROR }

/** Machine code for a tuning warning, so UI copy is decoupled from the check. */
enum class TuningWarningCode {
    BRACE_HEIGHT_LOW,
    BRACE_HEIGHT_HIGH,
    NOCKING_POINT_NEGATIVE,
    ARROW_TOO_SHORT,
    TILLER_DIFFERENCE_LARGE,
    GPP_TOO_LOW,
    FOC_OUT_OF_RANGE,
    STRING_STRANDS_OUT_OF_RANGE,
    VANE_COUNT_UNUSUAL,
}

data class TuningWarning(
    val code: TuningWarningCode,
    val severity: WarningSeverity,
    val message: String,
)

/**
 * Rig context needed to judge some tuning values (brace band depends on bow length, arrow length depends on
 * draw length). All optional so a partial spec still validates what it can. Pure data.
 */
data class TuningContext(
    val bowLengthIn: Double? = null,
    val drawLengthMm: Double? = null,
    val effectiveLbs: Double? = null,
)

/**
 * Deterministic range/sanity validation over a [TuningSpec] (+ optional [TuningContext]). Returns a stable,
 * order-deterministic list of [TuningWarning]s; empty means everything checked was in-band. Coaching over the
 * athlete's OWN local setup — no paid analytics.
 */
object TuningValidator {
    // Typical brace-height band as a fraction of bow length (heuristic, documented). e.g. a 68" bow → ~8.8"–9.9".
    const val BRACE_MIN_FRACTION = 0.130
    const val BRACE_MAX_FRACTION = 0.145

    /** An arrow should extend at least this far past the archer's draw length (rest/clicker clearance). */
    const val ARROW_MIN_MARGIN_MM = 12.7 // ~0.5"

    /** Top/bottom tiller usually within this many mm of each other on a target recurve. */
    const val TILLER_MAX_DIFF_MM = 10.0

    /** Below this GPP, arrows are dangerously light for the poundage (dry-fire-like stress). */
    const val MIN_SAFE_GPP = 5.0

    /** Recommended target FOC band, percent. */
    const val FOC_MIN_PCT = 7.0
    const val FOC_MAX_PCT = 16.0

    /** Sane Dacron/FastFlight strand counts. */
    const val MIN_STRANDS = 8
    const val MAX_STRANDS = 20

    fun validate(spec: TuningSpec, context: TuningContext = TuningContext()): List<TuningWarning> {
        val out = ArrayList<TuningWarning>()

        // Brace height vs bow-length band.
        val brace = spec.braceHeightMm
        val bowLenIn = context.bowLengthIn
        if (brace != null && bowLenIn != null) {
            val bowLenMm = bowLenIn * ArrowMath.MM_PER_IN
            val min = bowLenMm * BRACE_MIN_FRACTION
            val max = bowLenMm * BRACE_MAX_FRACTION
            if (brace < min) {
                out += TuningWarning(
                    TuningWarningCode.BRACE_HEIGHT_LOW, WarningSeverity.WARNING,
                    "Brace height ${fmt(brace)} mm is below the typical ${fmt(min)}–${fmt(max)} mm band for a ${fmt(bowLenIn)}\" bow.",
                )
            } else if (brace > max) {
                out += TuningWarning(
                    TuningWarningCode.BRACE_HEIGHT_HIGH, WarningSeverity.WARNING,
                    "Brace height ${fmt(brace)} mm is above the typical ${fmt(min)}–${fmt(max)} mm band for a ${fmt(bowLenIn)}\" bow.",
                )
            }
        }

        // Nocking point should sit above square (positive).
        val nock = spec.nockingPointHeightMm
        if (nock != null && nock < 0.0) {
            out += TuningWarning(
                TuningWarningCode.NOCKING_POINT_NEGATIVE, WarningSeverity.ERROR,
                "Nocking point height ${fmt(nock)} mm is negative; it should sit above square (positive).",
            )
        }

        // Arrow length vs draw length.
        val arrowLen = spec.arrow?.lengthMm
        val drawMm = context.drawLengthMm
        if (arrowLen != null && drawMm != null && arrowLen < drawMm + ARROW_MIN_MARGIN_MM) {
            out += TuningWarning(
                TuningWarningCode.ARROW_TOO_SHORT, WarningSeverity.ERROR,
                "Arrow length ${fmt(arrowLen)} mm is too short for a ${fmt(drawMm)} mm draw (need ≥ ${fmt(drawMm + ARROW_MIN_MARGIN_MM)} mm clearance).",
            )
        }

        // Tiller difference.
        val tTop = spec.tillerTopMm
        val tBot = spec.tillerBottomMm
        if (tTop != null && tBot != null) {
            val diff = abs(tTop - tBot)
            if (diff > TILLER_MAX_DIFF_MM) {
                out += TuningWarning(
                    TuningWarningCode.TILLER_DIFFERENCE_LARGE, WarningSeverity.WARNING,
                    "Tiller difference ${fmt(diff)} mm exceeds the usual ≤ ${fmt(TILLER_MAX_DIFF_MM)} mm.",
                )
            }
        }

        // GPP safety floor.
        val mass = spec.arrow?.totalMassGrains
        val eff = context.effectiveLbs
        if (mass != null && eff != null && eff > 0.0) {
            val gpp = ArrowMath.gpp(mass, eff)
            if (gpp < MIN_SAFE_GPP) {
                out += TuningWarning(
                    TuningWarningCode.GPP_TOO_LOW, WarningSeverity.ERROR,
                    "Arrow is ${fmt(gpp)} gr/lb — below the ${fmt(MIN_SAFE_GPP)} gr/lb safety floor for the effective poundage.",
                )
            }
        }

        // FOC band.
        if (arrowLen != null && spec.arrow?.balancePointMm != null && arrowLen > 0.0) {
            val foc = ArrowMath.focPercent(arrowLen, spec.arrow.balancePointMm)
            if (foc < FOC_MIN_PCT || foc > FOC_MAX_PCT) {
                out += TuningWarning(
                    TuningWarningCode.FOC_OUT_OF_RANGE, WarningSeverity.WARNING,
                    "FOC ${fmt(foc)}% is outside the recommended ${fmt(FOC_MIN_PCT)}–${fmt(FOC_MAX_PCT)}% band.",
                )
            }
        }

        // String strands.
        val strands = spec.stringStrands
        if (strands != null && (strands < MIN_STRANDS || strands > MAX_STRANDS)) {
            out += TuningWarning(
                TuningWarningCode.STRING_STRANDS_OUT_OF_RANGE, WarningSeverity.WARNING,
                "String strand count $strands is outside the usual $MIN_STRANDS–$MAX_STRANDS range.",
            )
        }

        // Vane count.
        val vanes = spec.arrow?.vaneCount
        if (vanes != null && vanes != 3 && vanes != 4) {
            out += TuningWarning(
                TuningWarningCode.VANE_COUNT_UNUSUAL, WarningSeverity.WARNING,
                "Vane count $vanes is unusual; target arrows almost always use 3 or 4.",
            )
        }

        return out
    }

    private fun fmt(v: Double): String {
        val rounded = Math.round(v * 10.0) / 10.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }
}
