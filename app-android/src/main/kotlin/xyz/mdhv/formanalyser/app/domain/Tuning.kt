package xyz.mdhv.formanalyser.app.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xyz.mdhv.formanalyser.app.data.RigEntity
import xyz.mdhv.formanalyser.equipment.ArrowMath
import xyz.mdhv.formanalyser.equipment.EffectivePoundage
import xyz.mdhv.formanalyser.equipment.PoundageEstimator
import xyz.mdhv.formanalyser.equipment.TuningContext
import xyz.mdhv.formanalyser.equipment.TuningSpec
import xyz.mdhv.formanalyser.equipment.TuningValidator
import xyz.mdhv.formanalyser.equipment.TuningWarning
import xyz.mdhv.formanalyser.model.BowType

/** Rig tuning, versioned (Phase 1 §A3; Phase 4 extends in place). Unknown-field tolerant. */
@Serializable
data class TuningV0(
    val v: Int = 0,
    val markedLbs: Double? = null,     // rated poundage printed on the limbs
    val riserLengthIn: Double? = null, // 23/25/27 typical (ILF); null for compound
    val otfLbs: Double? = null,        // MEASURED on the fingers at the athlete's draw length
)

/**
 * Serializable mirror of core-equipment [StabilizerSetup] (rods in inches, weights in ounces).
 * Kept in the app layer so the rig JSON is self-describing; bridges to the core type for math.
 */
@Serializable
data class StabilizerDto(
    val longRodLengthIn: Double? = null,
    val sideRodLengthIn: Double? = null,
    val extenderLengthIn: Double? = null,
    val frontWeightOz: Double? = null,
    val sideWeightOz: Double? = null,
) {
    fun isEmpty(): Boolean =
        longRodLengthIn == null && sideRodLengthIn == null && extenderLengthIn == null &&
            frontWeightOz == null && sideWeightOz == null
}

/**
 * Serializable mirror of core-equipment [ArrowSpec] (spine number, lengths in mm, masses in grains).
 */
@Serializable
data class ArrowDto(
    val spine: Int? = null,
    val lengthMm: Double? = null,
    val pointGrains: Double? = null,
    val totalMassGrains: Double? = null,
    val balancePointMm: Double? = null,
    val vaneCount: Int? = null,
) {
    fun isEmpty(): Boolean =
        spine == null && lengthMm == null && pointGrains == null &&
            totalMassGrains == null && balancePointMm == null && vaneCount == null
}

/**
 * Phase 4 full rig tuning: the Phase 1 poundage fields (marked/riser/otf, kept at the top level so
 * the legacy [TuningV0] reader still resolves poundage) PLUS the richer setup (limb/string geometry,
 * plunger/clicker, string build, stabilizer, arrow).
 *
 * ## Version tolerance / additive contract
 * Every field is optional and defaulted. [Tuning.parseFull] decodes any older payload cleanly:
 *  - legacy TuningV0 JSON (`{"v":0,"markedLbs":...}`) decodes with the advanced fields left null;
 *  - unknown keys from a newer writer are ignored (`ignoreUnknownKeys`);
 *  - a new payload still exposes marked/riser/otf at the top level, so the untouched [TuningV0]
 *    path ([Tuning.parse]/[Tuning.effectivePoundage]) keeps working on new JSON (forward-compat).
 * Never repurpose or remove a field — only add.
 */
@Serializable
data class RigTuning(
    val v: Int = SCHEMA_VERSION,
    // --- Phase 1 poundage (shared field names with TuningV0) ---
    val markedLbs: Double? = null,
    val riserLengthIn: Double? = null,
    val otfLbs: Double? = null,
    // --- Phase 4 advanced setup ---
    val braceHeightMm: Double? = null,
    val tillerTopMm: Double? = null,
    val tillerBottomMm: Double? = null,
    val nockingPointHeightMm: Double? = null,
    val plungerTensionSteps: Int? = null,
    val clickerPositionMm: Double? = null,
    val stringStrands: Int? = null,
    val stringMaterial: String? = null,
    val stabilizer: StabilizerDto? = null,
    val arrow: ArrowDto? = null,
) {
    /**
     * Bridge to the core-equipment [TuningSpec] for math/validation. Built through
     * [TuningSpec.fromMap] so the app layer reuses the core's single decode path (and inherits its
     * missing/unknown-key tolerance) rather than duplicating the mapping.
     */
    fun toSpec(): TuningSpec = TuningSpec.fromMap(
        buildMap {
            put("schemaVersion", TuningSpec.CURRENT_SCHEMA_VERSION)
            put("braceHeightMm", braceHeightMm)
            put("tillerTopMm", tillerTopMm)
            put("tillerBottomMm", tillerBottomMm)
            put("nockingPointHeightMm", nockingPointHeightMm)
            put("plungerTensionSteps", plungerTensionSteps)
            put("clickerPositionMm", clickerPositionMm)
            put("stringStrands", stringStrands)
            put("stringMaterial", stringMaterial)
            put("stabilizer", stabilizer?.let {
                mapOf(
                    "longRodLengthIn" to it.longRodLengthIn,
                    "sideRodLengthIn" to it.sideRodLengthIn,
                    "extenderLengthIn" to it.extenderLengthIn,
                    "frontWeightOz" to it.frontWeightOz,
                    "sideWeightOz" to it.sideWeightOz,
                )
            })
            put("arrow", arrow?.let {
                mapOf(
                    "spine" to it.spine,
                    "lengthMm" to it.lengthMm,
                    "pointGrains" to it.pointGrains,
                    "totalMassGrains" to it.totalMassGrains,
                    "balancePointMm" to it.balancePointMm,
                    "vaneCount" to it.vaneCount,
                )
            })
        },
    )

    companion object {
        const val SCHEMA_VERSION = 2

        /** Lift a legacy [TuningV0] into the full model (advanced fields left unrecorded). */
        fun fromV0(v0: TuningV0): RigTuning =
            RigTuning(markedLbs = v0.markedLbs, riserLengthIn = v0.riserLengthIn, otfLbs = v0.otfLbs)

        /** Rebuild the app model from a core [TuningSpec] (uses [TuningSpec.toMap] for the numeric keys). */
        fun fromSpec(spec: TuningSpec, base: RigTuning = RigTuning()): RigTuning {
            val m = spec.toMap()
            return base.copy(
                braceHeightMm = (m["braceHeightMm"] as? Number)?.toDouble(),
                tillerTopMm = (m["tillerTopMm"] as? Number)?.toDouble(),
                tillerBottomMm = (m["tillerBottomMm"] as? Number)?.toDouble(),
                nockingPointHeightMm = (m["nockingPointHeightMm"] as? Number)?.toDouble(),
                plungerTensionSteps = (m["plungerTensionSteps"] as? Number)?.toInt(),
                clickerPositionMm = (m["clickerPositionMm"] as? Number)?.toDouble(),
                stringStrands = (m["stringStrands"] as? Number)?.toInt(),
                stringMaterial = m["stringMaterial"] as? String,
                stabilizer = spec.stabilizer?.let {
                    StabilizerDto(
                        it.longRodLengthIn, it.sideRodLengthIn, it.extenderLengthIn,
                        it.frontWeightOz, it.sideWeightOz,
                    )
                },
                arrow = spec.arrow?.let {
                    ArrowDto(
                        it.spine, it.lengthMm, it.pointGrains, it.totalMassGrains,
                        it.balancePointMm, it.vaneCount,
                    )
                },
            )
        }
    }
}

object Tuning {
    private val json = Json { ignoreUnknownKeys = true }

    // ---------------------------------------------------------------------------------------------
    // Phase 1 (V0) — UNCHANGED public API. Still the poundage path; reads V0 fields out of any JSON.
    // ---------------------------------------------------------------------------------------------

    fun parse(tuningJson: String?): TuningV0 =
        if (tuningJson.isNullOrBlank()) TuningV0()
        else runCatching { json.decodeFromString(TuningV0.serializer(), tuningJson) }.getOrElse { TuningV0() }

    fun encode(tuning: TuningV0): String = json.encodeToString(TuningV0.serializer(), tuning)

    /** Effective poundage for a rig, precedence measured>estimated>marked (core-equipment). */
    fun effectivePoundage(rig: RigEntity, drawLengthMm: Int?): EffectivePoundage? {
        val t = parse(rig.tuningJson)
        return PoundageEstimator.resolve(
            bowType = BowType.fromStorage(rig.bowType),
            otfLbs = t.otfLbs,
            markedLbs = t.markedLbs,
            riserLengthIn = t.riserLengthIn,
            drawLengthMm = drawLengthMm,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Phase 4 — full rig tuning (additive). Round-trips through the rig's tuningJson.
    // ---------------------------------------------------------------------------------------------

    /**
     * Decode the full rig tuning, tolerating any older payload. Legacy TuningV0 JSON upgrades
     * cleanly; a malformed payload degrades to an empty spec rather than throwing.
     */
    fun parseFull(tuningJson: String?): RigTuning {
        if (tuningJson.isNullOrBlank()) return RigTuning()
        return runCatching { json.decodeFromString(RigTuning.serializer(), tuningJson) }
            .getOrElse { RigTuning.fromV0(parse(tuningJson)) }
    }

    /** Encode the full rig tuning back to the rig's tuningJson (V0 fields stay top-level). */
    fun encodeFull(rig: RigTuning): String = json.encodeToString(RigTuning.serializer(), rig)

    /** Effective poundage from the full model (same precedence as the V0 path). */
    fun effectivePoundage(rig: RigTuning, bowType: BowType, drawLengthMm: Int?): EffectivePoundage? =
        PoundageEstimator.resolve(
            bowType = bowType,
            otfLbs = rig.otfLbs,
            markedLbs = rig.markedLbs,
            riserLengthIn = rig.riserLengthIn,
            drawLengthMm = drawLengthMm,
        )

    /** FOC %, or null if arrow length/balance point are not both recorded. */
    fun focPercent(rig: RigTuning): Double? {
        val len = rig.arrow?.lengthMm ?: return null
        val bp = rig.arrow.balancePointMm ?: return null
        if (len <= 0.0) return null
        return ArrowMath.focPercent(len, bp)
    }

    /** Grains-per-pound vs effective poundage, or null if mass/poundage are unknown. */
    fun gpp(rig: RigTuning, effectiveLbs: Double?): Double? {
        val mass = rig.arrow?.totalMassGrains ?: return null
        if (effectiveLbs == null || effectiveLbs <= 0.0) return null
        return ArrowMath.gpp(mass, effectiveLbs)
    }

    /**
     * Coaching-grade estimate of the assembled bow length, used only to widen the brace-height
     * band check. ILF heuristic: riser + ~43" of medium limbs (23"→66", 25"→68", 27"→70").
     * Null for compound or unknown riser (so the brace check simply won't fire).
     */
    fun estimatedBowLengthIn(riserLengthIn: Double?, bowType: BowType): Double? {
        if (bowType == BowType.COMPOUND || riserLengthIn == null) return null
        return riserLengthIn + 43.0
    }

    /**
     * Deterministic tuning warnings for display. Computes the [TuningContext] (estimated bow length,
     * draw length, resolved effective poundage) so brace/arrow-length/GPP checks can run.
     */
    fun warnings(rig: RigTuning, bowType: BowType, drawLengthMm: Int?): List<TuningWarning> {
        val eff = effectivePoundage(rig, bowType, drawLengthMm)
        val ctx = TuningContext(
            bowLengthIn = estimatedBowLengthIn(rig.riserLengthIn, bowType),
            drawLengthMm = drawLengthMm?.toDouble(),
            effectiveLbs = eff?.lbs,
        )
        return TuningValidator.validate(rig.toSpec(), ctx)
    }
}
