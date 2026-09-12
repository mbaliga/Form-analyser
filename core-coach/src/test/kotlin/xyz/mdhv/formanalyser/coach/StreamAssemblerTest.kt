package xyz.mdhv.formanalyser.coach

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamAssemblerTest {

    private fun alwaysContinue() = StreamSink { StreamDirective.CONTINUE }

    @Test
    fun `deltas concatenate in order`() {
        val a = StreamAssembler("fallback-model", alwaysContinue())
        a.started("real-model", 10)
        a.delta("Hello")
        a.delta(", ")
        a.delta("world.")
        val result = a.finish("stop") as CompletionResult.Success
        assertEquals("Hello, world.", result.response.text)
        assertEquals("real-model", result.response.modelId)
        assertFalse(result.response.truncated)
        assertEquals("stop", result.response.stopReason)
    }

    @Test
    fun `last non-null usage field wins independently per field`() {
        val a = StreamAssembler("m", alwaysContinue())
        a.delta("x")
        a.usage(inputTokens = 100, outputTokens = null)
        a.usage(inputTokens = null, outputTokens = 20)
        // A later frame with a null input must not erase the 100 already recorded.
        a.usage(inputTokens = null, outputTokens = 25)
        val result = a.finish(null) as CompletionResult.Success
        assertEquals(100, result.response.inputTokens)
        assertEquals(25, result.response.outputTokens)
    }

    @Test
    fun `CANCEL directive latches and finish reports a truncated success with the partial text`() {
        var callCount = 0
        val a = StreamAssembler("m", StreamSink {
            callCount++
            if (callCount == 2) StreamDirective.CANCEL else StreamDirective.CONTINUE
        })
        a.delta("first ") // call 1 -> CONTINUE
        a.delta("second") // call 2 -> CANCEL, latches
        val thirdDirective = a.delta(" third") // must short-circuit to CANCEL without calling sink again
        assertEquals(StreamDirective.CANCEL, thirdDirective)
        assertEquals(2, callCount, "a latched cancel must not keep invoking the sink")
        assertTrue(a.cancelled)

        val result = a.finish("end_turn") as CompletionResult.Success
        assertTrue(result.response.truncated)
        assertEquals("cancelled", result.response.stopReason)
        // The cancel happened on the *second* delta; " third" after it must not have been appended.
        assertEquals("first second", result.response.text)
        assertEquals("first second", a.partialText)
    }

    @Test
    fun `clean finish with zero deltas is a Failure`() {
        val a = StreamAssembler("claude-sonnet-5", alwaysContinue())
        a.started("claude-sonnet-5", 5)
        val result = a.finish("stop")
        assertTrue(result is CompletionResult.Failure)
        assertEquals(LlmErrorKind.PROVIDER_ERROR, result.error.kind)
    }

    @Test
    fun `failure preserves partialText for the caller to still show`() {
        val a = StreamAssembler("m", alwaysContinue())
        a.delta("partial answer before it broke")
        val result = a.failure(LlmErrorKind.NETWORK, "connection reset")
        assertTrue(result is CompletionResult.Failure)
        assertEquals(LlmErrorKind.NETWORK, result.error.kind)
        assertEquals("partial answer before it broke", a.partialText)
    }

    @Test
    fun `blank delta is a no-op that does not notify the sink`() {
        var notifications = 0
        val a = StreamAssembler("m", StreamSink { notifications++; StreamDirective.CONTINUE })
        a.delta("")
        assertEquals(0, notifications)
        assertEquals("", a.partialText)
    }

    @Test
    fun `fallbackModelId is used when the provider never names the model`() {
        val a = StreamAssembler("fallback-id", alwaysContinue())
        a.delta("hi")
        val result = a.finish(null) as CompletionResult.Success
        assertEquals("fallback-id", result.response.modelId)
    }
}
