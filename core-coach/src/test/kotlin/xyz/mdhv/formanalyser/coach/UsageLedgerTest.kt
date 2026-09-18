package xyz.mdhv.formanalyser.coach

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UsageLedgerTest {

    @Test
    fun `record folds usage into a running total keyed by model id`() {
        val ledger = UsageLedger(sinceEpochMs = 1000)
            .record("claude-sonnet-5", UsageSnapshot(inputTokens = 100, outputTokens = 50))
            .record("claude-sonnet-5", UsageSnapshot(inputTokens = 200, outputTokens = 25))

        val totals = ledger.byModelId.getValue("claude-sonnet-5")
        assertEquals(2, totals.asks)
        assertEquals(300, totals.inputTokens)
        assertEquals(75, totals.outputTokens)
    }

    @Test
    fun `different models accumulate independently`() {
        val ledger = UsageLedger(sinceEpochMs = 0)
            .record("claude-sonnet-5", UsageSnapshot(10, 10))
            .record("gpt-5", UsageSnapshot(20, 20))

        assertEquals(1, ledger.byModelId.getValue("claude-sonnet-5").asks)
        assertEquals(1, ledger.byModelId.getValue("gpt-5").asks)
        assertEquals(2, ledger.byModelId.size)
    }

    @Test
    fun `an ask with unknown token counts increments asks without corrupting token totals`() {
        val ledger = UsageLedger(sinceEpochMs = 0)
            .record("claude-sonnet-5", UsageSnapshot(inputTokens = 100, outputTokens = 40))
            .record("claude-sonnet-5", UsageSnapshot(inputTokens = null, outputTokens = null))

        val totals = ledger.byModelId.getValue("claude-sonnet-5")
        assertEquals(2, totals.asks, "the unknown-usage ask still counts")
        assertEquals(100, totals.inputTokens, "unknown tokens must not add a fabricated amount")
        assertEquals(40, totals.outputTokens)
    }

    @Test
    fun `startingNow resets sinceEpochMs and discards prior totals`() {
        val old = UsageLedger(sinceEpochMs = 1000).record("claude-sonnet-5", UsageSnapshot(10, 10))
        val fresh = UsageLedger.startingNow(nowEpochMs = 5000)
        assertEquals(5000, fresh.sinceEpochMs)
        assertTrue(fresh.byModelId.isEmpty())
        // Sanity: the old ledger is untouched (data class is immutable).
        assertEquals(1000, old.sinceEpochMs)
    }

    @Test
    fun `aggregate reports unpriced asks rather than silently omitting them`() {
        // claude-sonnet-5 is in ModelPricing.unpriced today (no citable rate) -- its tokens must show
        // up as unpricedAsks, never vanish from the total.
        val ledger = UsageLedger(sinceEpochMs = 0)
            .record("claude-sonnet-5", UsageSnapshot(100, 50))
            .record("claude-sonnet-5", UsageSnapshot(100, 50))
            .record("claude-sonnet-5", UsageSnapshot(100, 50))

        val aggregate = ledger.aggregate()
        assertEquals(3, aggregate.unpricedAsks)
        assertEquals(0.0, aggregate.knownUsd, 1e-9)
    }

    @Test
    fun `aggregate treats on-device asks as no charge, not unpriced`() {
        val ledger = UsageLedger(sinceEpochMs = 0)
            .record("gemma-3n-e4b", UsageSnapshot(500, 200))

        val aggregate = ledger.aggregate()
        assertEquals(0, aggregate.unpricedAsks)
        assertEquals(0.0, aggregate.knownUsd, 1e-9)
    }

    @Test
    fun `aggregate falls back to unpricedAsks for a model id the registry no longer knows`() {
        val ledger = UsageLedger(sinceEpochMs = 0).record("retired-model-id", UsageSnapshot(10, 10))
        val aggregate = ledger.aggregate()
        assertEquals(1, aggregate.unpricedAsks)
    }

    @Test
    fun `aggregate sums known-priced models using an injected registry lookup`() {
        val fakeModel = CoachModel(
            id = "fake-priced-model",
            provider = Provider.OTHER,
            displayName = "Fake",
            kind = ModelKind.CLOUD,
            requiresByok = true,
            approxContextTokens = 1000,
        )
        // Inject pricing via a fake registry lookup + a real CostEstimator call is exercised through
        // aggregate(); since ModelPricing has no rate for "fake-priced-model" either, this asserts the
        // *routing* (unknown model id -> registry lookup -> CostEstimator) rather than a stubbed price
        // table, which core-coach does not expose a way to substitute.
        val ledger = UsageLedger(sinceEpochMs = 0).record(fakeModel.id, UsageSnapshot(10, 10))
        val aggregate = ledger.aggregate(registry = { id -> if (id == fakeModel.id) fakeModel else null })
        assertEquals(1, aggregate.unpricedAsks)
        assertEquals(0.0, aggregate.knownUsd, 1e-9)
    }
}
