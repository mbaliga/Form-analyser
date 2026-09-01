package xyz.mdhv.formanalyser.model

/**
 * The archer's draw hand — the canonical, app-wide identity value (stored as "RH"/"LH").
 *
 * Note: the archery pose pipeline has its own internal `pose.Handedness { RIGHT, LEFT }` used by
 * the segmenter; that stays an implementation detail. After [HandednessNormalizer] mirrors an LH
 * capture into the canonical right-handed frame, the segmenter always runs right-handed. This
 * enum is what onboarding, the DB, and cross-cutting logic speak.
 */
enum class Handedness {
    /** Right-handed: bow in the left hand, draw with the right. */
    RH,

    /** Left-handed: bow in the right hand, draw with the left. */
    LH,
    ;

    companion object {
        fun fromStorage(s: String?): Handedness = when (s?.uppercase()) {
            "LH" -> LH
            else -> RH // default + forward-compatible
        }
    }

    fun toStorage(): String = name
}
