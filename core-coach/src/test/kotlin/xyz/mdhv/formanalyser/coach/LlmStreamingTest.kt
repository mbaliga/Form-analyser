package xyz.mdhv.formanalyser.coach

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A fake [LlmClient] that returns a canned [CompletionResult] and never overrides [stream]. */
private class FakeClient(private val result: CompletionResult) : LlmClient {
    override fun supports(model: CoachModel): Boolean = true
    override fun complete(request: CompletionRequest): CompletionResult = result
}

private val model = ModelRegistry.byId("claude-sonnet-5")!!
private val request = CompletionRequest(model = model, messages = listOf(ChatMessage(MessageRole.USER, "hi")))

class LlmStreamingTest {

    @Test
    fun `supportsStreaming defaults to false`() {
        val client = FakeClient(CompletionResult.Success(CompletionResponse("hi", model.id)))
        assertFalse(client.supportsStreaming(model))
    }

    @Test
    fun `default bridge emits Started, Delta, UsageUpdate exactly once each in order`() {
        val response = CompletionResponse(
            text = "full answer",
            modelId = model.id,
            stopReason = "stop",
            inputTokens = 12,
            outputTokens = 34,
        )
        val client = FakeClient(CompletionResult.Success(response))
        val events = mutableListOf<StreamEvent>()
        val result = client.stream(request) { event -> events.add(event); StreamDirective.CONTINUE }

        assertEquals(3, events.size)
        assertEquals(StreamEvent.Started(model.id, 12), events[0])
        assertEquals(StreamEvent.Delta("full answer"), events[1])
        assertEquals(StreamEvent.UsageUpdate(12, 34), events[2])
        assertEquals(CompletionResult.Success(response), result)
    }

    @Test
    fun `default bridge returns the identical result it was given`() {
        val response = CompletionResponse("x", model.id)
        val result = CompletionResult.Success(response)
        val client = FakeClient(result)
        val returned = client.stream(request) { StreamDirective.CONTINUE }
        assertTrue(returned === result || returned == result)
    }

    @Test
    fun `a Failure result emits nothing to the sink`() {
        val failure = CompletionResult.Failure(LlmError(LlmErrorKind.NETWORK, "boom"))
        val client = FakeClient(failure)
        val events = mutableListOf<StreamEvent>()
        val result = client.stream(request) { event -> events.add(event); StreamDirective.CONTINUE }
        assertTrue(events.isEmpty())
        assertEquals(failure, result)
    }

    @Test
    fun `default bridge ignores a CANCEL directive since the answer already arrived whole`() {
        // The one-shot bridge has nothing left to stop by the time the sink sees anything — CANCEL
        // must not turn a complete answer into a truncated one.
        val response = CompletionResponse("whole answer", model.id, outputTokens = 5)
        val client = FakeClient(CompletionResult.Success(response))
        val result = client.stream(request) { StreamDirective.CANCEL }
        assertEquals(CompletionResult.Success(response), result)
        assertFalse((result as CompletionResult.Success).response.truncated)
    }
}
