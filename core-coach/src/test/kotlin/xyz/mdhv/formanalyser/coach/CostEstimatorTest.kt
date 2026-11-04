package xyz.mdhv.formanalyser.coach

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CostEstimatorTest {

    private val onDevice = ModelRegistry.byId("gemma-3n-e4b")!!
    private val unpricedCloud = ModelRegistry.byId("claude-sonnet-5")!! // in ModelPricing.unpriced today

    @Test
    fun `on-device is always NoCharge regardless of token counts`() {
        val estimate = CostEstimator.estimate(onDevice, UsageSnapshot(inputTokens = 999, outputTokens = 999))
        assertEquals(CostEstimate.NoCharge, estimate)
    }

    @Test
    fun `a cloud model with no published rate is Unknown naming the model`() {
        val estimate = CostEstimator.estimate(unpricedCloud, UsageSnapshot(inputTokens = 100, outputTokens = 50))
        assertIs<CostEstimate.Unknown>(estimate)
        assertTrue(estimate.reason.contains(unpricedCloud.displayName))
    }

    @Test
    fun `null token counts on a cloud model are Unknown naming the provider`() {
        val estimate = CostEstimator.estimate(unpricedCloud, UsageSnapshot(inputTokens = null, outputTokens = null))
        assertIs<CostEstimate.Unknown>(estimate)
        assertTrue(estimate.reason.contains("Anthropic"))
    }

    @Test
    fun `costUsd arithmetic is exact for a known rate`() {
        val price = TokenPrice(usdPerMillionInput = 3.0, usdPerMillionOutput = 15.0, asOfIso = "2026-01-01T00:00:00Z")
        // 1,000,000 in + 200,000 out = $3.00 + $3.00 = $6.00
        val usd = CostEstimator.costUsd(price, inputTokens = 1_000_000, outputTokens = 200_000)
        assertEquals(6.0, usd, 1e-9)
    }

    @Test
    fun `zero tokens costs zero`() {
        val price = TokenPrice(1.0, 2.0, "2026-01-01T00:00:00Z")
        assertEquals(0.0, CostEstimator.costUsd(price, 0, 0), 1e-9)
    }

    @Test
    fun `format renders sub-cent amounts as under a cent, never as free`() {
        assertEquals("< $0.01", CostEstimator.format(CostEstimate.Usd(0.0004, "2026-01-01T00:00:00Z")))
    }

    @Test
    fun `format renders zero as a real two-decimal dollar figure`() {
        assertEquals("$0.00", CostEstimator.format(CostEstimate.Usd(0.0, "2026-01-01T00:00:00Z")))
    }

    @Test
    fun `format renders a normal amount to two decimals`() {
        assertEquals("$1.90", CostEstimator.format(CostEstimate.Usd(1.9, "2026-01-01T00:00:00Z")))
    }

    @Test
    fun `format for NoCharge and Unknown are distinct, non-dollar strings`() {
        assertEquals("on-device, no API charge", CostEstimator.format(CostEstimate.NoCharge))
        assertEquals("tokens only", CostEstimator.format(CostEstimate.Unknown("whatever")))
    }
}
