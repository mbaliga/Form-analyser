package xyz.mdhv.formanalyser.coach

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModelRegistryTest {

    @Test
    fun `no free hosted tier exists`() {
        assertFalse(ModelRegistry.hasFreeHostedTier(), "there must be no free hosted-chat tier")
        assertTrue(ModelRegistry.models.none { it.isFreeHostedChat })
    }

    @Test
    fun `every cloud model requires byok`() {
        val cloud = ModelRegistry.cloudModels()
        assertTrue(cloud.isNotEmpty())
        assertTrue(cloud.all { it.requiresByok }, "every CLOUD model must be bring-your-own-key")
    }

    @Test
    fun `every on-device model runs locally without a key`() {
        val local = ModelRegistry.onDeviceModels()
        assertTrue(local.isNotEmpty(), "at least one on-device model must exist")
        assertTrue(local.all { !it.requiresByok })
        assertTrue(local.all { it.kind == ModelKind.ON_DEVICE })
    }

    @Test
    fun `every model is either byok-cloud or on-device`() {
        // The product law restated: no third "free" shape.
        assertTrue(ModelRegistry.models.all { m ->
            (m.kind == ModelKind.CLOUD && m.requiresByok) ||
                (m.kind == ModelKind.ON_DEVICE && !m.requiresByok)
        })
    }

    @Test
    fun `expected current-generation entries are present`() {
        val opus = ModelRegistry.byId("claude-opus-4-8")
        assertNotNull(opus)
        assertEquals(Provider.ANTHROPIC, opus.provider)
        assertEquals(ModelKind.CLOUD, opus.kind)

        for (id in listOf("claude-sonnet-5", "claude-haiku-4-5", "claude-fable-5")) {
            assertEquals(Provider.ANTHROPIC, ModelRegistry.byId(id)?.provider, "missing $id")
        }
        assertTrue(ModelRegistry.byProvider(Provider.OPENAI).size >= 2)
        assertTrue(ModelRegistry.byProvider(Provider.GOOGLE).size >= 2)
        assertTrue(ModelRegistry.byProvider(Provider.ON_DEVICE).isNotEmpty())
    }

    @Test
    fun `ids are unique and context sizes positive`() {
        val ids = ModelRegistry.models.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "model ids must be unique")
        assertTrue(ModelRegistry.models.all { it.approxContextTokens > 0 })
    }

    @Test
    fun `OTHER provider is available as an extensibility seam`() {
        // Enum entry exists even if no built-in model uses it yet.
        assertNotNull(Provider.valueOf("OTHER"))
    }
}
