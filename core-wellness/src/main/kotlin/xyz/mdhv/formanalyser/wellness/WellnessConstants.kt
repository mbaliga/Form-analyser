package xyz.mdhv.formanalyser.wellness

/**
 * Single source for every wellness constant (Phase 2 §A / Appendix A). KDoc'd, referenced by
 * tests. Tunable defaults marked DECISION-P2 are called out for later confirmation from use.
 */
object WellnessConstants {
    const val LBS_TO_KG = 0.45359237

    // ACWR EWMA smoothing: λ = 2 / (N + 1)
    const val LAMBDA_ACUTE = 0.25            // 2 / (7 + 1)
    const val LAMBDA_CHRONIC = 2.0 / 29.0    // ≈ 0.06897 ; 2 / (28 + 1)
    const val CHRONIC_GUARD = 1e-6           // acwr undefined when chronic below this

    // ACWR zone thresholds (spec §3.8)
    const val ACWR_DETRAIN_HI = 0.8
    const val ACWR_SWEET_HI = 1.3
    const val ACWR_CAUTION_HI = 1.5

    // ACWR warm-up gate
    const val WARMUP_DAYS = 21
    const val WARMUP_TRAINING_DAYS = 10

    // Session duration
    const val GAP_CAP_S = 180.0

    // Readiness (DECISION-P2 — tune from lived use)
    const val STALENESS_H = 36
    const val ENERGY_REST = 1        // energy ≤ 1 → REST_ADVISED
    const val ENERGY_CAUTION = 2     // energy == 2 → CAUTION
    const val SLEEP_CAUTION = 2      // sleep ≤ 2 → CAUTION
    const val SORENESS_CAUTION = 3   // ≥ 3 regions → CAUTION
    const val LIFE_IMPACT_CAUTION = 3

    // Cycle estimator
    const val CYCLE_GATE = 3         // ≥ 3 completed cycles (intervals) before estimating
    const val CYCLE_WINDOW = 6       // median over last ≤ 6 intervals
    const val LUTEAL_OFFSET_DAYS = 14
    const val OVULATORY_WINDOW = 1   // ± 1 day
    const val DEFAULT_BLEED_LEN = 5
}
