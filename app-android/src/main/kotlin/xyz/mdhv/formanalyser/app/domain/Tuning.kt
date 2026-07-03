package xyz.mdhv.formanalyser.app.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xyz.mdhv.formanalyser.app.data.RigEntity
import xyz.mdhv.formanalyser.equipment.EffectivePoundage
import xyz.mdhv.formanalyser.equipment.PoundageEstimator
import xyz.mdhv.formanalyser.model.BowType

/** Rig tuning, versioned (Phase 1 §A3; Phase 4 extends in place). Unknown-field tolerant. */
@Serializable
data class TuningV0(
    val v: Int = 0,
    val markedLbs: Double? = null,     // rated poundage printed on the limbs
    val riserLengthIn: Double? = null, // 23/25/27 typical (ILF); null for compound
    val otfLbs: Double? = null,        // MEASURED on the fingers at the athlete's draw length
)

object Tuning {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(tuningJson: String?): TuningV0 =
        if (tuningJson.isNullOrBlank()) TuningV0()
        else runCatching { json.decodeFromString<TuningV0>(tuningJson) }.getOrElse { TuningV0() }

    fun encode(tuning: TuningV0): String = json.encodeToString(tuning)

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
}
