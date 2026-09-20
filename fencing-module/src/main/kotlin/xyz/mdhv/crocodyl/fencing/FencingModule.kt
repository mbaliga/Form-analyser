package xyz.mdhv.crocodyl.fencing

import xyz.mdhv.crocodyl.engine.model.FeatureVector
import xyz.mdhv.crocodyl.engine.sport.FeatureExtractor
import xyz.mdhv.crocodyl.engine.sport.RepSegmenter
import xyz.mdhv.crocodyl.engine.sport.ScoringRubric
import xyz.mdhv.crocodyl.engine.sport.SportModule

/** The three FIE weapons supported by the initial Fencing surface. */
enum class FencingWeapon(
    val id: String,
    val displayName: String,
    val targetSummary: String,
    val priorityRule: String,
) {
    SABRE("sabre", "Sabre", "Head, torso and arms", "Right of way"),
    FOIL("foil", "Foil", "Torso", "Right of way"),
    EPEE("epee", "Epee", "Whole body", "First valid touch"),
}

enum class FencerSide { LEFT, RIGHT }

/** A placeholder exchange produced by a future pose/video or piste-signal adapter. */
data class FencingExchange(
    val id: String,
    val durationMs: Long,
    val features: FeatureVector = emptyMap(),
    val detectedPoint: FencerSide? = null,
)

/** Stable referee vocabulary. Computer vision must never silently invent a call. */
enum class RefereeCall(val spoken: String) {
    PRET("Prêt"),
    ALLEZ("Allez"),
    HALT("Halte"),
    POINT_LEFT("Point — left fencer"),
    POINT_RIGHT("Point — right fencer"),
    NO_POINT("No point"),
    DOUBLE_TOUCH("Double touch"),
}

data class RefereeDecision(
    val call: RefereeCall,
    val weapon: FencingWeapon,
    val pointWinner: FencerSide? = null,
    /** Nullable until a human referee or a validated signal model confirms the call. */
    val confidence: Double? = null,
    val provisional: Boolean = true,
)

/**
 * Deterministic referee-mode state machine. It is deliberately signal-agnostic: a future camera,
 * piste box or human tap can feed it, while the UI already has a stable call vocabulary.
 */
class RefereeModeSession(val weapon: FencingWeapon) {
    private var started = false

    fun ready(): RefereeDecision = RefereeDecision(RefereeCall.PRET, weapon)

    fun start(): RefereeDecision {
        started = true
        return RefereeDecision(RefereeCall.ALLEZ, weapon)
    }

    fun halt(): RefereeDecision {
        started = false
        return RefereeDecision(RefereeCall.HALT, weapon)
    }

    fun award(side: FencerSide, confidence: Double? = null): RefereeDecision {
        check(started) { "A point cannot be awarded before Allez" }
        require(confidence == null || confidence in 0.0..1.0) { "confidence must be 0..1" }
        return RefereeDecision(
            call = if (side == FencerSide.LEFT) RefereeCall.POINT_LEFT else RefereeCall.POINT_RIGHT,
            weapon = weapon,
            pointWinner = side,
            confidence = confidence,
        )
    }
}

object FencingModule : SportModule<List<FencingExchange>, FencingExchange> {
    override val sportId: String = "fencing"
    override val segmenter: RepSegmenter<List<FencingExchange>, FencingExchange> =
        RepSegmenter { it }
    override val extractor: FeatureExtractor<FencingExchange> = object : FeatureExtractor<FencingExchange> {
        override val featureNames: List<String> = listOf(
            "exchange_duration_ms",
            "left_initiated",
            "right_initiated",
        )

        override fun extract(rep: FencingExchange): FeatureVector = buildMap {
            put("exchange_duration_ms", rep.durationMs.toDouble())
            put("left_initiated", if (rep.detectedPoint == FencerSide.LEFT) 1.0 else 0.0)
            put("right_initiated", if (rep.detectedPoint == FencerSide.RIGHT) 1.0 else 0.0)
            putAll(rep.features)
        }
    }
    override val scoring: ScoringRubric = object : ScoringRubric {
        override val maxScore: Double = 1.0
        override fun isValid(score: Double): Boolean = score == 0.0 || score == 1.0
    }
    override val deviationWeights: Map<String, Double> = mapOf(
        "exchange_duration_ms" to 0.7,
        "left_initiated" to 1.0,
        "right_initiated" to 1.0,
    )
}
