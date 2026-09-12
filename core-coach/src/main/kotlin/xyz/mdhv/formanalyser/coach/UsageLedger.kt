package xyz.mdhv.formanalyser.coach

/** Token/ask totals accumulated for one model since a [UsageLedger.sinceEpochMs]. */
data class ModelUsageTotals(
    val asks: Int = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
)

/**
 * Aggregate estimate across every model in a [UsageLedger] — see [UsageLedger.aggregate].
 * [unpricedAsks] must never be silently dropped from a displayed total: a sum that pretended those
 * asks cost nothing would be a lie of composition, not just an estimate — the UI is expected to
 * render it as e.g. "~$1.90 (+3 asks with no published rate)".
 */
data class AggregateEstimate(
    val knownUsd: Double,
    val unpricedAsks: Int,
    /** The most recent [TokenPrice.asOfIso] contributing to [knownUsd], or null if nothing priced. */
    val asOfIso: String?,
)

/**
 * A pure, foldable running total of coach-ask token usage, keyed by model id.
 *
 * Tokens are stored; cost is computed at display time (via [CostEstimator], see [aggregate]) from
 * the CURRENT [ModelPricing] table — so a later correction to a price retroactively fixes old
 * estimates instead of baking a stale number into storage.
 *
 * **Deliberately NOT a Room table.** The Android persistence for this (`AiSettings`'s
 * `usage_ledger_json` DataStore string) carries no migration and no `PrivacyRegistry` entry, and is
 * structurally unreachable by `ConsentFilter`/`.crocbak` export, which both enumerate Room tables —
 * because this ledger holds only model ids, integer counters, and a start date, and NEVER prompt
 * text, factsheet content, or response text. The classification decision is written down here for
 * the day this does move into a table: it would be **PRIVATE** (an AI-usage behaviour log with no
 * coaching value to a recipient), and "Reset" would need the same nullable-timestamp-filtered-at-the-
 * DAO idiom `MIGRATION_6_7`/`MIGRATION_7_8` established for athlete history — never a hard `DELETE`.
 * [startingNow] mirrors that idiom today by moving [sinceEpochMs] forward rather than discarding
 * anything irrecoverable; the totals really are gone, but nothing here is athlete history.
 */
data class UsageLedger(
    val sinceEpochMs: Long,
    val byModelId: Map<String, ModelUsageTotals> = emptyMap(),
) {
    /**
     * Fold one ask's usage into the ledger. An ask with unknown token counts (both null) still
     * increments [ModelUsageTotals.asks] — it happened and should count toward "N asks" — without
     * corrupting the token totals with a fabricated zero that would understate a real average.
     */
    fun record(modelId: String, usage: UsageSnapshot): UsageLedger {
        val prior = byModelId[modelId] ?: ModelUsageTotals()
        val updated = prior.copy(
            asks = prior.asks + 1,
            inputTokens = prior.inputTokens + (usage.inputTokens?.toLong() ?: 0L),
            outputTokens = prior.outputTokens + (usage.outputTokens?.toLong() ?: 0L),
        )
        return copy(byModelId = byModelId + (modelId to updated))
    }

    /**
     * Fold every model's totals into one figure via [CostEstimator]. [registry] resolves a stored
     * model id back to a [CoachModel]; an id the registry no longer recognises (a retired model) is
     * counted into [AggregateEstimate.unpricedAsks] rather than dropped — its tokens are still stored,
     * there is simply nothing left to price it against.
     */
    fun aggregate(registry: (String) -> CoachModel? = ModelRegistry::byId): AggregateEstimate {
        var knownUsd = 0.0
        var unpricedAsks = 0
        var asOfIso: String? = null
        byModelId.forEach { (modelId, totals) ->
            val model = registry(modelId)
            if (model == null) {
                unpricedAsks += totals.asks
                return@forEach
            }
            val usage = UsageSnapshot(
                inputTokens = totals.inputTokens.toIntClamped(),
                outputTokens = totals.outputTokens.toIntClamped(),
            )
            when (val estimate = CostEstimator.estimate(model, usage)) {
                is CostEstimate.Usd -> {
                    knownUsd += estimate.amount
                    asOfIso = estimate.asOfIso
                }
                CostEstimate.NoCharge -> Unit
                is CostEstimate.Unknown -> unpricedAsks += totals.asks
            }
        }
        return AggregateEstimate(knownUsd, unpricedAsks, asOfIso)
    }

    companion object {
        /**
         * A fresh ledger starting at [nowEpochMs] — what "Reset" writes. The UI must always show
         * "Since <date>" (never imply a lifetime total) so a reset can't make a fresh counter look
         * like it covers more history than it does.
         */
        fun startingNow(nowEpochMs: Long): UsageLedger = UsageLedger(sinceEpochMs = nowEpochMs)
    }
}

/** Clamp a token total to Int range for [UsageSnapshot] — cost math has no need for Long precision. */
private fun Long.toIntClamped(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
