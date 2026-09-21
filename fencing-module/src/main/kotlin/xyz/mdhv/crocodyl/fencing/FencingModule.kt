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
    val equipmentSummary: String,
) {
    SABRE("sabre", "Sabre", "Head, torso and arms", "Right of way", "Electric sabre, lamé jacket and conductive mask bib"),
    FOIL("foil", "Foil", "Torso", "Right of way", "Electric foil, lamé jacket and conductive mask bib"),
    EPEE("epee", "Epee", "Whole body", "First valid touch", "Electric epee and full-body scoring target"),
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

enum class RefereePhase { READY, IN_PROGRESS, HALTED }

data class RefereeMatchState(
    val weapon: FencingWeapon,
    val phase: RefereePhase,
    val leftScore: Int,
    val rightScore: Int,
    val lastDecision: RefereeDecision?,
    val history: List<RefereeDecision>,
)

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
    private var phase = RefereePhase.READY
    private var leftScore = 0
    private var rightScore = 0
    private val history = mutableListOf<RefereeDecision>()

    fun ready(): RefereeDecision {
        started = false
        phase = RefereePhase.READY
        return record(RefereeDecision(RefereeCall.PRET, weapon))
    }

    fun start(): RefereeDecision {
        started = true
        phase = RefereePhase.IN_PROGRESS
        return record(RefereeDecision(RefereeCall.ALLEZ, weapon))
    }

    fun halt(): RefereeDecision {
        started = false
        phase = RefereePhase.HALTED
        return record(RefereeDecision(RefereeCall.HALT, weapon))
    }

    fun award(side: FencerSide, confidence: Double? = null): RefereeDecision {
        check(started) { "A point cannot be awarded before Allez" }
        require(confidence == null || confidence in 0.0..1.0) { "confidence must be 0..1" }
        val decision = RefereeDecision(
            call = if (side == FencerSide.LEFT) RefereeCall.POINT_LEFT else RefereeCall.POINT_RIGHT,
            weapon = weapon,
            pointWinner = side,
            confidence = confidence,
        )
        if (side == FencerSide.LEFT) leftScore++ else rightScore++
        started = false
        phase = RefereePhase.HALTED
        return record(decision)
    }

    fun undoLastPoint(): RefereeDecision? {
        val index = history.indexOfLast { it.pointWinner != null }
        if (index < 0) return null
        val point = history.removeAt(index)
        if (point.pointWinner == FencerSide.LEFT) leftScore-- else rightScore--
        return point
    }

    fun reset() {
        started = false
        phase = RefereePhase.READY
        leftScore = 0
        rightScore = 0
        history.clear()
    }

    fun snapshot(): RefereeMatchState =
        RefereeMatchState(weapon, phase, leftScore, rightScore, history.lastOrNull(), history.toList())

    private fun record(decision: RefereeDecision): RefereeDecision {
        history += decision
        return decision
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
