package xyz.mdhv.formanalyser.equipment

/**
 * Pure, deterministic arrow ballistics helpers (Phase 4). No clocks, no randomness — every result is a
 * closed-form function of its arguments and is unit-tested against hand-computed values.
 */
object ArrowMath {
    const val MM_PER_IN = 25.4
    const val MM_PER_FT = 304.8

    /** grains·fps² per foot-pound (standard KE constant: KE = m·v² / 450240). */
    const val KE_DIVISOR = 450240.0

    /**
     * Fraction of triangle-approximated stored draw energy that a recurve delivers to the arrow. A crude but
     * documented efficiency constant used only by [kineticEnergyProxyFtLb]. Real dynamic efficiency varies
     * with limb design; this is a coaching-grade proxy, not a chronograph.
     */
    const val RECURVE_STORE_EFFICIENCY = 0.70

    /**
     * Front-of-centre, percent. FOC = 100 · (balancePoint − length/2) / length, with the balance point
     * measured from the nock throat toward the point. Positive ⇒ mass forward of centre.
     *
     * @throws IllegalArgumentException if [arrowLengthMm] is not positive.
     */
    fun focPercent(arrowLengthMm: Double, balancePointMm: Double): Double {
        require(arrowLengthMm > 0.0) { "arrowLengthMm must be > 0, was $arrowLengthMm" }
        return 100.0 * (balancePointMm - arrowLengthMm / 2.0) / arrowLengthMm
    }

    /**
     * Grains-per-pound: total arrow mass divided by effective on-the-fingers poundage. Feed [effectiveLbs]
     * from [PoundageEstimator.resolve] / [EffectivePoundage.lbs].
     *
     * @throws IllegalArgumentException if [effectiveLbs] is not positive.
     */
    fun gpp(totalMassGrains: Double, effectiveLbs: Double): Double {
        require(effectiveLbs > 0.0) { "effectiveLbs must be > 0, was $effectiveLbs" }
        return totalMassGrains / effectiveLbs
    }

    /** Standard kinetic energy in foot-pounds from arrow mass (grains) and measured launch speed (fps). */
    fun kineticEnergyFtLb(massGrains: Double, velocityFps: Double): Double =
        massGrains * velocityFps * velocityFps / KE_DIVISOR

    /**
     * Kinetic-energy PROXY (foot-pounds) when no chronograph velocity is known. Approximates stored draw
     * energy as a triangle E = ½ · F · powerStroke (F = effective poundage in lbf, powerStroke = draw − brace),
     * then scales by [efficiency]. Deterministic and hand-computable; treat as an estimate only.
     *
     * @throws IllegalArgumentException if the power stroke (drawLength − braceHeight) is not positive.
     */
    fun kineticEnergyProxyFtLb(
        effectiveLbs: Double,
        drawLengthMm: Double,
        braceHeightMm: Double,
        efficiency: Double = RECURVE_STORE_EFFICIENCY,
    ): Double {
        val powerStrokeMm = drawLengthMm - braceHeightMm
        require(powerStrokeMm > 0.0) { "power stroke (draw − brace) must be > 0, was $powerStrokeMm mm" }
        val powerStrokeFt = powerStrokeMm / MM_PER_FT
        val storedFtLb = 0.5 * effectiveLbs * powerStrokeFt
        return efficiency * storedFtLb
    }
}
