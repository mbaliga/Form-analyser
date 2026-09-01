package xyz.mdhv.formanalyser.body

/** Which anatomical side a region sits on. */
enum class Side { LEFT, RIGHT, CENTER }

/** Front or back of the body (used by the Phase 3 atlas geometry). */
enum class BodyFace { FRONT, BACK }

data class RegionMeta(val id: String, val side: Side, val face: BodyFace)

/**
 * The 52-region body-map contract (spec §3.6 / Phase 3 appendix) — the shared string-ID vocabulary
 * for pain, injuries, physio, soreness, and (Phase 5) exchange payloads. This list IS the contract.
 */
object RegionIds {
    // FRONT (25)
    const val NECK_ANT = "neck_ant"
    const val DELT_ANT_L = "delt_ant_l"; const val DELT_ANT_R = "delt_ant_r"
    const val DELT_LAT_L = "delt_lat_l"; const val DELT_LAT_R = "delt_lat_r"
    const val PEC_L = "pec_l"; const val PEC_R = "pec_r"
    const val BICEPS_L = "biceps_l"; const val BICEPS_R = "biceps_r"
    const val FOREARM_FLEX_L = "forearm_flex_l"; const val FOREARM_FLEX_R = "forearm_flex_r"
    const val HAND_L = "hand_l"; const val HAND_R = "hand_r"
    const val ABS_UPPER = "abs_upper"; const val ABS_LOWER = "abs_lower"
    const val OBLIQUE_L = "oblique_l"; const val OBLIQUE_R = "oblique_r"
    const val HIP_L = "hip_l"; const val HIP_R = "hip_r"
    const val QUAD_L = "quad_l"; const val QUAD_R = "quad_r"
    const val KNEE_L = "knee_l"; const val KNEE_R = "knee_r"
    const val SHIN_L = "shin_l"; const val SHIN_R = "shin_r"

    // BACK (27)
    const val NECK_POST = "neck_post"
    const val TRAP_UPPER_L = "trap_upper_l"; const val TRAP_UPPER_R = "trap_upper_r"
    const val TRAP_MID_L = "trap_mid_l"; const val TRAP_MID_R = "trap_mid_r"
    const val TRAP_LOWER_L = "trap_lower_l"; const val TRAP_LOWER_R = "trap_lower_r"
    const val RHOMBOID_L = "rhomboid_l"; const val RHOMBOID_R = "rhomboid_r"
    const val DELT_POST_L = "delt_post_l"; const val DELT_POST_R = "delt_post_r"
    const val ROTATOR_CUFF_L = "rotator_cuff_l"; const val ROTATOR_CUFF_R = "rotator_cuff_r"
    const val LAT_L = "lat_l"; const val LAT_R = "lat_r"
    const val ERECTOR_L = "erector_l"; const val ERECTOR_R = "erector_r"
    const val TRICEPS_L = "triceps_l"; const val TRICEPS_R = "triceps_r"
    const val FOREARM_EXT_L = "forearm_ext_l"; const val FOREARM_EXT_R = "forearm_ext_r"
    const val GLUTE_L = "glute_l"; const val GLUTE_R = "glute_r"
    const val HAMSTRING_L = "hamstring_l"; const val HAMSTRING_R = "hamstring_r"
    const val CALF_L = "calf_l"; const val CALF_R = "calf_r"

    private val FRONT = listOf(
        NECK_ANT, DELT_ANT_L, DELT_ANT_R, DELT_LAT_L, DELT_LAT_R, PEC_L, PEC_R,
        BICEPS_L, BICEPS_R, FOREARM_FLEX_L, FOREARM_FLEX_R, HAND_L, HAND_R,
        ABS_UPPER, ABS_LOWER, OBLIQUE_L, OBLIQUE_R, HIP_L, HIP_R,
        QUAD_L, QUAD_R, KNEE_L, KNEE_R, SHIN_L, SHIN_R,
    )
    private val BACK = listOf(
        NECK_POST, TRAP_UPPER_L, TRAP_UPPER_R, TRAP_MID_L, TRAP_MID_R, TRAP_LOWER_L, TRAP_LOWER_R,
        RHOMBOID_L, RHOMBOID_R, DELT_POST_L, DELT_POST_R, ROTATOR_CUFF_L, ROTATOR_CUFF_R,
        LAT_L, LAT_R, ERECTOR_L, ERECTOR_R, TRICEPS_L, TRICEPS_R, FOREARM_EXT_L, FOREARM_EXT_R,
        GLUTE_L, GLUTE_R, HAMSTRING_L, HAMSTRING_R, CALF_L, CALF_R,
    )

    val ALL: List<String> = FRONT + BACK

    val META: Map<String, RegionMeta> = ALL.associateWith { id ->
        val side = when {
            id.endsWith("_l") -> Side.LEFT
            id.endsWith("_r") -> Side.RIGHT
            else -> Side.CENTER
        }
        RegionMeta(id, side, if (id in FRONT) BodyFace.FRONT else BodyFace.BACK)
    }

    fun isValid(id: String): Boolean = id in META
}
