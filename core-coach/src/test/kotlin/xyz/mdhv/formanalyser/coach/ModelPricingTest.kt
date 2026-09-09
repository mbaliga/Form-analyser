package xyz.mdhv.formanalyser.coach

import kotlin.test.Test
import kotlin.test.assertTrue
import java.time.Instant
import java.time.format.DateTimeParseException

class ModelPricingTest {

    @Test
    fun `every registry cloud model is priced or explicitly unpriced`() {
        // The forcing function: a new cloud model landing in neither map is a silent gap, exactly the
        // shape PrivacyRegistryTest forbids for an unclassified table.
        val cloudIds = ModelRegistry.cloudModels().map { it.id }
        val missing = cloudIds.filterNot { it in ModelPricing.byModelId || it in ModelPricing.unpriced }
        assertTrue(
            missing.isEmpty(),
            "cloud model(s) with no pricing decision recorded: $missing " +
                "-- add a TokenPrice to ModelPricing.byModelId or list the id in ModelPricing.unpriced",
        )
    }

    @Test
    fun `unpriced ids are real registry ids, not stale leftovers`() {
        val allIds = ModelRegistry.models.map { it.id }.toSet()
        val stale = ModelPricing.unpriced.filterNot { it in allIds }
        assertTrue(stale.isEmpty(), "ModelPricing.unpriced names id(s) no longer in ModelRegistry: $stale")
    }

    @Test
    fun `no priced model has a negative rate`() {
        assertTrue(ModelPricing.byModelId.values.all { it.usdPerMillionInput >= 0.0 })
        assertTrue(ModelPricing.byModelId.values.all { it.usdPerMillionOutput >= 0.0 })
    }

    @Test
    fun `every priced model's asOfIso parses as an instant`() {
        ModelPricing.byModelId.values.forEach { price ->
            try {
                Instant.parse(price.asOfIso)
            } catch (e: DateTimeParseException) {
                throw AssertionError("TokenPrice.asOfIso '${price.asOfIso}' is not a parseable instant", e)
            }
        }
    }

    @Test
    fun `on-device models are never priced`() {
        val onDeviceIds = ModelRegistry.onDeviceModels().map { it.id }
        assertTrue(onDeviceIds.none { it in ModelPricing.byModelId })
        assertTrue(onDeviceIds.none { it in ModelPricing.unpriced }, "on-device models are NoCharge, not unpriced")
    }
}
