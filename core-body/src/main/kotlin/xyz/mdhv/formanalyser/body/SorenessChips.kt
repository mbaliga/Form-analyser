package xyz.mdhv.formanalyser.body

import xyz.mdhv.formanalyser.model.Handedness

/**
 * Functional (not anatomical) soreness chips for the quick pre-check-in (Phase 2 §B, Appendix B).
 * "Draw shoulder" resolves to the right shoulder for a right-handed archer and the left for a
 * lefty — the resolver applies the handedness mirror so the same chip means the same *role*.
 */
enum class SorenessChip {
    NECK, DRAW_SHOULDER, BOW_SHOULDER, UPPER_BACK, LOWER_BACK, DRAW_FOREARM, BOW_ARM, CORE, LEGS,
}

object SorenessChipResolver {
    /** The draw-side anatomical side for a given handedness (RH draws with the right). */
    private fun drawSide(h: Handedness) = if (h == Handedness.RH) "r" else "l"
    private fun bowSide(h: Handedness) = if (h == Handedness.RH) "l" else "r"

    fun resolve(chip: SorenessChip, handedness: Handedness): List<String> {
        val draw = drawSide(handedness)
        val bow = bowSide(handedness)
        return when (chip) {
            SorenessChip.NECK -> listOf(RegionIds.NECK_ANT, RegionIds.NECK_POST)
            SorenessChip.DRAW_SHOULDER -> listOf("delt_ant_$draw", "delt_lat_$draw", "delt_post_$draw", "rotator_cuff_$draw")
            SorenessChip.BOW_SHOULDER -> listOf("delt_ant_$bow", "delt_lat_$bow", "delt_post_$bow", "rotator_cuff_$bow")
            SorenessChip.UPPER_BACK -> listOf(
                RegionIds.TRAP_UPPER_L, RegionIds.TRAP_UPPER_R, RegionIds.TRAP_MID_L, RegionIds.TRAP_MID_R,
                RegionIds.RHOMBOID_L, RegionIds.RHOMBOID_R,
            )
            SorenessChip.LOWER_BACK -> listOf(RegionIds.ERECTOR_L, RegionIds.ERECTOR_R)
            SorenessChip.DRAW_FOREARM -> listOf("forearm_flex_$draw", "forearm_ext_$draw")
            SorenessChip.BOW_ARM -> listOf("biceps_$bow", "triceps_$bow", "forearm_ext_$bow")
            SorenessChip.CORE -> listOf(RegionIds.ABS_UPPER, RegionIds.ABS_LOWER, RegionIds.OBLIQUE_L, RegionIds.OBLIQUE_R)
            SorenessChip.LEGS -> listOf(
                RegionIds.QUAD_L, RegionIds.QUAD_R, RegionIds.HAMSTRING_L, RegionIds.HAMSTRING_R,
                RegionIds.CALF_L, RegionIds.CALF_R,
            )
        }
    }
}
