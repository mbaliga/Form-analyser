package xyz.mdhv.formanalyser.equipment

import xyz.mdhv.formanalyser.model.BowType

/** Where an effective poundage figure came from — drives the UI's `≈` prefix + caveat copy. */
enum class PoundageSource { MEASURED, ESTIMATED, MARKED, NONE }

data class EffectivePoundage(val lbs: Double, val source: PoundageSource) {
    val kg: Double get() = PoundageEstimator.lbsToKg(lbs)
}

/**
 * On-the-fingers poundage for recurve/barebow (Phase 1 §A3.1, DECISION-21).
 *
 * Marked limb poundage is a *rating*, specified by ILF convention at 28″ AMO draw on a 25″ riser.
 * What the archer actually holds scales with both draw length and riser length. Constants are
 * conventions to be confirmed from coaching ground truth; limb-bolt position shifts real OTF by
 * up to ±10%, which this estimate ignores and the UI admits.
 */
object PoundageEstimator {
    const val K_DRAW_LBS_PER_IN = 2.0    // per inch of draw beyond/below 28″ AMO
    const val K_RISER_LBS_PER_IN = 1.0   // per inch of riser below/above 25″
    const val AMO_DRAW_IN = 28.0
    const val REF_RISER_IN = 25.0
    const val MM_PER_IN = 25.4
    const val LBS_TO_KG = 0.45359237

    fun estimateOtfLbs(markedLbs: Double, riserLengthIn: Double, drawLengthMm: Int): Double {
        val drawIn = drawLengthMm / MM_PER_IN
        return markedLbs +
            K_DRAW_LBS_PER_IN * (drawIn - AMO_DRAW_IN) +
            K_RISER_LBS_PER_IN * (REF_RISER_IN - riserLengthIn)
    }

    fun lbsToKg(lbs: Double): Double = lbs * LBS_TO_KG

    /**
     * Precedence, everywhere poundage is consumed: **measured > estimated > marked**.
     * Compound bows never estimate (cam systems don't scale per-inch): measured or marked only.
     * Returns null only when nothing at all is known.
     */
    fun resolve(
        bowType: BowType,
        otfLbs: Double?,
        markedLbs: Double?,
        riserLengthIn: Double?,
        drawLengthMm: Int?,
    ): EffectivePoundage? {
        if (otfLbs != null) return EffectivePoundage(otfLbs, PoundageSource.MEASURED)
        if (bowType != BowType.COMPOUND && markedLbs != null && riserLengthIn != null && drawLengthMm != null) {
            return EffectivePoundage(estimateOtfLbs(markedLbs, riserLengthIn, drawLengthMm), PoundageSource.ESTIMATED)
        }
        if (markedLbs != null) return EffectivePoundage(markedLbs, PoundageSource.MARKED)
        return null
    }
}
