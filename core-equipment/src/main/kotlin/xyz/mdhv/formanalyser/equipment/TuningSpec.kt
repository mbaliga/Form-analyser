package xyz.mdhv.formanalyser.equipment

/**
 * Versioned, additive tuning setup for a single bow (Phase 4 tuning depth).
 *
 * ## Units (documented, do not change silently)
 * - **Lengths in millimetres (mm)**: brace height, tiller, nocking point height, arrow length, balance point.
 * - **Rod lengths in inches (in)**: stabilizer rods, following archery-shop convention (rods are sold in inches).
 * - **Weights in ounces (oz)**: stabilizer front/side weights, following the convention that stabilizer
 *   weights are stamped in ounces. Use [ozToGrams] if a caller needs grams.
 * - **Grains (gr)** for arrow point / total mass, **thousandths-of-inch spine number** for spine.
 *
 * ## Versioning / additive contract
 * Every field is optional (nullable, defaulting to `null` == "not recorded"). Newer app versions may ADD
 * fields; they must never repurpose or remove one. [schemaVersion] records the writer's schema. Stored data
 * is decoded through [fromMap], which tolerates any missing key (older payloads upgrade cleanly by defaulting)
 * and any unknown key (newer payloads down-grade cleanly by ignoring it). Round-trip: [toMap] then [fromMap]
 * returns an equal spec, and a partial map fills the rest with defaults.
 */
data class TuningSpec(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,

    // Limb / string geometry
    val braceHeightMm: Double? = null,
    val tillerTopMm: Double? = null,
    val tillerBottomMm: Double? = null,
    val nockingPointHeightMm: Double? = null,

    // Plunger / clicker
    val plungerTensionSteps: Int? = null,
    val clickerPositionMm: Double? = null,

    // String build
    val stringStrands: Int? = null,
    val stringMaterial: String? = null,

    // Stabilization
    val stabilizer: StabilizerSetup? = null,

    // Arrow
    val arrow: ArrowSpec? = null,
) {
    fun toMap(): Map<String, Any?> = buildMap {
        put(K_VERSION, schemaVersion)
        put(K_BRACE, braceHeightMm)
        put(K_TILLER_TOP, tillerTopMm)
        put(K_TILLER_BOT, tillerBottomMm)
        put(K_NOCK, nockingPointHeightMm)
        put(K_PLUNGER, plungerTensionSteps)
        put(K_CLICKER, clickerPositionMm)
        put(K_STRANDS, stringStrands)
        put(K_MATERIAL, stringMaterial)
        put(K_STAB, stabilizer?.toMap())
        put(K_ARROW, arrow?.toMap())
    }

    companion object {
        /** Bump when adding fields. Stored payloads carry the writer's version so readers can adapt. */
        const val CURRENT_SCHEMA_VERSION = 1

        const val OZ_TO_GRAMS = 28.349523125
        fun ozToGrams(oz: Double): Double = oz * OZ_TO_GRAMS

        private const val K_VERSION = "schemaVersion"
        private const val K_BRACE = "braceHeightMm"
        private const val K_TILLER_TOP = "tillerTopMm"
        private const val K_TILLER_BOT = "tillerBottomMm"
        private const val K_NOCK = "nockingPointHeightMm"
        private const val K_PLUNGER = "plungerTensionSteps"
        private const val K_CLICKER = "clickerPositionMm"
        private const val K_STRANDS = "stringStrands"
        private const val K_MATERIAL = "stringMaterial"
        private const val K_STAB = "stabilizer"
        private const val K_ARROW = "arrow"

        /**
         * Decode a stored payload. Any missing key defaults (older data upgrades cleanly); unknown keys are
         * ignored (newer data down-grades cleanly). A payload with no version defaults to version 1.
         */
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): TuningSpec = TuningSpec(
            schemaVersion = (map[K_VERSION] as? Number)?.toInt() ?: CURRENT_SCHEMA_VERSION,
            braceHeightMm = (map[K_BRACE] as? Number)?.toDouble(),
            tillerTopMm = (map[K_TILLER_TOP] as? Number)?.toDouble(),
            tillerBottomMm = (map[K_TILLER_BOT] as? Number)?.toDouble(),
            nockingPointHeightMm = (map[K_NOCK] as? Number)?.toDouble(),
            plungerTensionSteps = (map[K_PLUNGER] as? Number)?.toInt(),
            clickerPositionMm = (map[K_CLICKER] as? Number)?.toDouble(),
            stringStrands = (map[K_STRANDS] as? Number)?.toInt(),
            stringMaterial = map[K_MATERIAL] as? String,
            stabilizer = (map[K_STAB] as? Map<String, Any?>)?.let { StabilizerSetup.fromMap(it) },
            arrow = (map[K_ARROW] as? Map<String, Any?>)?.let { ArrowSpec.fromMap(it) },
        )
    }
}

/**
 * Stabilizer build. Rod lengths in **inches**, weights in **ounces** (see [TuningSpec] units doc).
 * All fields optional and additive.
 */
data class StabilizerSetup(
    val longRodLengthIn: Double? = null,
    val sideRodLengthIn: Double? = null,
    val extenderLengthIn: Double? = null,
    val frontWeightOz: Double? = null,
    val sideWeightOz: Double? = null,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "longRodLengthIn" to longRodLengthIn,
        "sideRodLengthIn" to sideRodLengthIn,
        "extenderLengthIn" to extenderLengthIn,
        "frontWeightOz" to frontWeightOz,
        "sideWeightOz" to sideWeightOz,
    )

    companion object {
        fun fromMap(m: Map<String, Any?>): StabilizerSetup = StabilizerSetup(
            longRodLengthIn = (m["longRodLengthIn"] as? Number)?.toDouble(),
            sideRodLengthIn = (m["sideRodLengthIn"] as? Number)?.toDouble(),
            extenderLengthIn = (m["extenderLengthIn"] as? Number)?.toDouble(),
            frontWeightOz = (m["frontWeightOz"] as? Number)?.toDouble(),
            sideWeightOz = (m["sideWeightOz"] as? Number)?.toDouble(),
        )
    }
}

/**
 * Arrow build. [spine] is the deflection number (thousandths-of-inch, e.g. 620). Lengths in mm, masses in
 * grains. [balancePointMm] is measured from the nock throat toward the point (the arrow's centre of mass).
 */
data class ArrowSpec(
    val spine: Int? = null,
    val lengthMm: Double? = null,
    val pointGrains: Double? = null,
    val totalMassGrains: Double? = null,
    val balancePointMm: Double? = null,
    val vaneCount: Int? = null,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "spine" to spine,
        "lengthMm" to lengthMm,
        "pointGrains" to pointGrains,
        "totalMassGrains" to totalMassGrains,
        "balancePointMm" to balancePointMm,
        "vaneCount" to vaneCount,
    )

    companion object {
        fun fromMap(m: Map<String, Any?>): ArrowSpec = ArrowSpec(
            spine = (m["spine"] as? Number)?.toInt(),
            lengthMm = (m["lengthMm"] as? Number)?.toDouble(),
            pointGrains = (m["pointGrains"] as? Number)?.toDouble(),
            totalMassGrains = (m["totalMassGrains"] as? Number)?.toDouble(),
            balancePointMm = (m["balancePointMm"] as? Number)?.toDouble(),
            vaneCount = (m["vaneCount"] as? Number)?.toInt(),
        )
    }
}
