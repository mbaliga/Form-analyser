package xyz.mdhv.formanalyser.coach

/**
 * Published list price for one model, USD per **million** tokens, as advertised on [asOfIso].
 *
 * Not a bill: providers change prices without notice, discount cached/batched input, and bill
 * "thinking"/reasoning tokens as output even when they are never shown to the athlete. Everything
 * derived from this (see [CostEstimator]) is an ESTIMATE and every UI surface using it must say so.
 */
data class TokenPrice(
    val usdPerMillionInput: Double,
    val usdPerMillionOutput: Double,
    val asOfIso: String,
)

/**
 * Rates this build can actually cite, keyed by [CoachModel.id].
 *
 * [byModelId] and [unpriced] are meant to partition every CLOUD model in [ModelRegistry] — a model in
 * neither is a bug, not silence, and `ModelPricingTest` enforces the closure. [unpriced] is a literal,
 * hand-maintained list rather than "everything [byModelId] doesn't have a rate for": deriving it from
 * the complement would make the forcing function vacuous (a newly-added registry model would just
 * silently fall into "unpriced" with nobody ever having decided that). Listing it explicitly is the
 * same conscious-decision idiom [xyz.mdhv.formanalyser.wellness.PrivacyRegistry] uses for tables it
 * has not yet classified — the gap becomes a visible, named fact instead of a default.
 *
 * ON_DEVICE models are never looked up here: [CostEstimator] treats them as [CostEstimate.NoCharge]
 * regardless of price data, because no provider bills anything for a local, on-device generation.
 *
 * Most current [ModelRegistry] cloud ids (`claude-opus-4-8`, `claude-sonnet-5`, `gpt-5`,
 * `deepseek-v4-*`, …) have no rate this codebase can currently cite from published, sourced pricing —
 * they are deliberately left in [unpriced] rather than filled with a plausible-looking guess. A
 * screen that shows an invented "$" figure is worse than one that honestly shows "tokens only"; the
 * house rule is to say what we don't know. Filling these in with real, dated, sourced numbers is
 * left as explicit follow-up work, not done blind in this environment.
 */
object ModelPricing {
    /** Rates this build can currently cite. Empty today — see the class KDoc for why that is honest. */
    val byModelId: Map<String, TokenPrice> = emptyMap()

    /** Registry cloud models deliberately shipped without a citable rate (see the class KDoc). */
    val unpriced: Set<String> =
        setOf(
            "claude-opus-4-8",
            "claude-sonnet-5",
            "claude-haiku-4-5",
            "claude-fable-5",
            "gpt-5",
            "gpt-5-mini",
            "gemini-2.5-pro",
            "gemini-2.5-flash",
            "deepseek-v4-flash",
            "deepseek-v4-pro",
        )

    /** The published rate for [modelId], or null if this build has none (see [unpriced]). */
    fun priceFor(modelId: String): TokenPrice? = byModelId[modelId]
}

/**
 * Token counts known for one completion. [approximate] flags a count that did not come from the
 * provider's own usage report (e.g. an on-device `sizeInTokens` estimate) — [CostEstimator] does not
 * currently change its behaviour on this flag, but callers that display a token count must render it
 * differently (a `≈` prefix) when it is set, so it is captured here rather than lost before display.
 */
data class UsageSnapshot(
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val approximate: Boolean = false,
)

/** An estimated cost for one completion, or an honest reason there isn't one. */
sealed interface CostEstimate {
    data class Usd(val amount: Double, val asOfIso: String) : CostEstimate

    /** On-device: no provider bills anything for this ask. Distinct from `Usd(0.0, ...)` on purpose. */
    data object NoCharge : CostEstimate

    /** Tokens may be known, but a dollar figure isn't — show tokens, never a guessed price. */
    data class Unknown(val reason: String) : CostEstimate
}

/**
 * Pure math over [ModelPricing] + [UsageSnapshot]. No I/O, no persistence (see [UsageLedger] for the
 * running totals this feeds) — every honesty rule below is encoded here, not left to callers to
 * remember:
 * - [ModelKind.ON_DEVICE] is always [CostEstimate.NoCharge], regardless of token counts.
 * - No price entry for the model → [CostEstimate.Unknown] naming the model, never a guess.
 * - Neither token count reported → [CostEstimate.Unknown] naming the provider (this is the
 *   OpenAI-without-`stream_options.include_usage` case, and the on-device case before token counting
 *   is wired up).
 * - Currency is always USD, because that is how every provider here bills; no FX conversion is
 *   applied since there is no live rate to apply.
 */
object CostEstimator {
    fun estimate(model: CoachModel, usage: UsageSnapshot): CostEstimate {
        if (model.kind == ModelKind.ON_DEVICE) return CostEstimate.NoCharge

        val input = usage.inputTokens
        val output = usage.outputTokens
        if (input == null && output == null) {
            return CostEstimate.Unknown("${model.provider.label()} didn't report token usage")
        }

        val price = ModelPricing.priceFor(model.id)
            ?: return CostEstimate.Unknown("no published rate for ${model.displayName} in this build")

        return CostEstimate.Usd(costUsd(price, input ?: 0, output ?: 0), price.asOfIso)
    }

    /**
     * The arithmetic itself, exposed separately from [estimate] so it is testable independent of
     * whatever [ModelPricing.byModelId] currently holds (which is empty in this build — see its
     * KDoc). $/token = $/million ÷ 1,000,000.
     */
    fun costUsd(price: TokenPrice, inputTokens: Int, outputTokens: Int): Double =
        (inputTokens.toDouble() / 1_000_000.0) * price.usdPerMillionInput +
            (outputTokens.toDouble() / 1_000_000.0) * price.usdPerMillionOutput

    /**
     * Render an estimate for display. A positive-but-sub-cent amount reads as "< $0.01" rather than
     * "$0.00" — the latter would misreport a real, if tiny, cost as free. Never adds a currency
     * conversion or a trailing "estimate" qualifier here; callers own the surrounding "(est.)" label
     * and the `asOfIso` tooltip so this function stays a pure formatter.
     */
    fun format(estimate: CostEstimate): String =
        when (estimate) {
            is CostEstimate.Usd ->
                if (estimate.amount > 0.0 && estimate.amount < 0.01) "< \$0.01"
                // Locale.ROOT: a device set to a comma-decimal locale must not turn "$1.90" into a
                // string a later `parse` (or a screenshot support ticket) misreads as "$190".
                else "$" + String.format(java.util.Locale.ROOT, "%.2f", estimate.amount)
            CostEstimate.NoCharge -> "on-device, no API charge"
            is CostEstimate.Unknown -> "tokens only"
        }
}

/** Human label for a [Provider], used only in [CostEstimator]'s user-facing "unknown" reasons. */
internal fun Provider.label(): String =
    when (this) {
        Provider.ANTHROPIC -> "Anthropic"
        Provider.OPENAI -> "OpenAI"
        Provider.GOOGLE -> "Google"
        Provider.DEEPSEEK -> "DeepSeek"
        Provider.ON_DEVICE -> "On-device"
        Provider.OTHER -> "Other"
    }
