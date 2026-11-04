package xyz.mdhv.formanalyser.exchange

import kotlin.test.Test
import kotlin.test.assertEquals

class ExchangeTrustTest {
    @Test
    fun `unsigned archives remain legacy even if an athlete was pinned`() {
        assertEquals(ExchangeTrustState.LEGACY_UNSIGNED, ExchangeTrust.evaluate(null, listOf("old")))
    }

    @Test
    fun `first signed contact requires explicit trust`() {
        assertEquals(ExchangeTrustState.FIRST_CONTACT, ExchangeTrust.evaluate("new", emptyList()))
    }

    @Test
    fun `matching pinned identity is trusted`() {
        assertEquals(ExchangeTrustState.TRUSTED, ExchangeTrust.evaluate("same", listOf("same")))
    }

    @Test
    fun `any changed pinned identity is quarantined`() {
        assertEquals(ExchangeTrustState.KEY_CHANGED, ExchangeTrust.evaluate("new", listOf("new", "old")))
    }
}
