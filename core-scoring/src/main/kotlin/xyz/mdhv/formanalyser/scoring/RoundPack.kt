package xyz.mdhv.formanalyser.scoring

/** Stable, built-in Olympic Recurve round definitions used by Crocodyl's Recurve profile. */
object RoundPack {
    val WA_RECURVE_70M_72 = RoundDefinition(
        id = "wa.recurve.70m.72.v1",
        name = "WA Recurve 70 m · 72 arrows",
        distanceMeters = 70,
        targetFaceCm = 122,
        arrowsPerEnd = 6,
        endCount = 12,
        scoringKind = ScoringKind.QUALIFICATION,
        faceLayout = FaceLayout.SINGLE,
    )

    /** Recognised 72-arrow 60 m Recurve qualification profile (used by WA age divisions). */
    val WA_RECURVE_60M_72 = RoundDefinition(
        id = "wa.recurve.60m.72.v1",
        name = "WA Recurve 60 m · 72 arrows",
        distanceMeters = 60,
        targetFaceCm = 122,
        arrowsPerEnd = 6,
        endCount = 12,
        scoringKind = ScoringKind.QUALIFICATION,
        faceLayout = FaceLayout.SINGLE,
    )

    val WA_RECURVE_18M_60 = RoundDefinition(
        id = "wa.recurve.18m.60.v1",
        name = "WA Recurve Indoor 18 m · 60 arrows",
        distanceMeters = 18,
        targetFaceCm = 40,
        arrowsPerEnd = 3,
        endCount = 20,
        scoringKind = ScoringKind.QUALIFICATION,
        faceLayout = FaceLayout.VERTICAL_TRIPLE,
    )

    /** Individual Recurve match: up to five three-arrow sets; first athlete to six set points wins. */
    val WA_RECURVE_70M_MATCH = RoundDefinition(
        id = "wa.recurve.70m.match.v1",
        name = "WA Recurve 70 m · Match",
        distanceMeters = 70,
        targetFaceCm = 122,
        arrowsPerEnd = 3,
        endCount = 5,
        scoringKind = ScoringKind.SET_MATCH,
        faceLayout = FaceLayout.SINGLE,
    )

    val builtIns: List<RoundDefinition> = listOf(
        WA_RECURVE_70M_72,
        WA_RECURVE_60M_72,
        WA_RECURVE_18M_60,
        WA_RECURVE_70M_MATCH,
    )

    fun byId(id: String): RoundDefinition? = builtIns.firstOrNull { it.id == id }

    fun customPractice(
        id: String,
        name: String,
        distanceMeters: Int,
        targetFaceCm: Int,
        arrowsPerEnd: Int,
        endCount: Int,
        faceLayout: FaceLayout = FaceLayout.SINGLE,
    ): RoundDefinition = RoundDefinition(
        id = id,
        name = name,
        distanceMeters = distanceMeters,
        targetFaceCm = targetFaceCm,
        arrowsPerEnd = arrowsPerEnd,
        endCount = endCount,
        scoringKind = ScoringKind.PRACTICE,
        faceLayout = faceLayout,
    )
}
