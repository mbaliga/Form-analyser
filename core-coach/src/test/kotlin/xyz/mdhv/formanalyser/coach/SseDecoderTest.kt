package xyz.mdhv.formanalyser.coach

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SseDecoderTest {

    private fun feed(decoder: SseDecoder, lines: List<String>): List<SseEvent> {
        val out = mutableListOf<SseEvent>()
        lines.forEach { decoder.pushLine(it)?.let(out::add) }
        return out
    }

    @Test
    fun `multi-line data accumulates joined by newline and dispatches on blank line`() {
        val decoder = SseDecoder()
        val events = feed(decoder, listOf("data: line one", "data: line two", ""))
        assertEquals(1, events.size)
        assertEquals("line one\nline two", events[0].data)
        assertEquals("message", events[0].event)
    }

    @Test
    fun `named event is carried on the dispatched SseEvent`() {
        val decoder = SseDecoder()
        val events = feed(decoder, listOf("event: message_start", "data: {\"a\":1}", ""))
        assertEquals(1, events.size)
        assertEquals("message_start", events[0].event)
        assertEquals("{\"a\":1}", events[0].data)
    }

    @Test
    fun `a leading colon line is a comment and produces nothing`() {
        val decoder = SseDecoder()
        val events = feed(decoder, listOf(": keep-alive", "data: real", ""))
        assertEquals(1, events.size)
        assertEquals("real", events[0].data)
    }

    @Test
    fun `exactly one leading space after the colon is stripped`() {
        val decoder = SseDecoder()
        // "data:  two spaces" -> one space stripped, one space kept in the value.
        val events = feed(decoder, listOf("data:  two spaces", ""))
        assertEquals(" two spaces", events[0].data)
    }

    @Test
    fun `no space after colon keeps the value as-is`() {
        val decoder = SseDecoder()
        val events = feed(decoder, listOf("data:nospace", ""))
        assertEquals("nospace", events[0].data)
    }

    @Test
    fun `CRLF line endings are tolerated`() {
        val decoder = SseDecoder()
        val events = feed(decoder, listOf("data: crlf\r", "\r"))
        assertEquals(1, events.size)
        assertEquals("crlf", events[0].data)
    }

    @Test
    fun `an event split across separate pushLine calls still dispatches once`() {
        val decoder = SseDecoder()
        assertNull(decoder.pushLine("event: content_block_delta"))
        assertNull(decoder.pushLine("data: {\"delta\":"))
        assertNull(decoder.pushLine("data: {\"text\":\"hi\"}}"))
        val dispatched = decoder.pushLine("")
        assertEquals("content_block_delta", dispatched!!.event)
        assertEquals("{\"delta\":\n{\"text\":\"hi\"}}", dispatched.data)
    }

    @Test
    fun `DONE sentinel passes through as ordinary data`() {
        val decoder = SseDecoder()
        val events = feed(decoder, listOf("data: [DONE]", ""))
        assertEquals("[DONE]", events[0].data)
    }

    @Test
    fun `flush dispatches a pending event with no trailing blank line`() {
        val decoder = SseDecoder()
        assertNull(decoder.pushLine("data: unterminated"))
        val flushed = decoder.flush()
        assertEquals("unterminated", flushed!!.data)
    }

    @Test
    fun `flush on an empty decoder returns null`() {
        val decoder = SseDecoder()
        assertNull(decoder.flush())
    }

    @Test
    fun `a blank line with no data field dispatches nothing`() {
        val decoder = SseDecoder()
        val events = feed(decoder, listOf("event: ping", ""))
        assertTrue(events.isEmpty())
    }

    @Test
    fun `multiple events in sequence decode independently`() {
        val decoder = SseDecoder()
        val events = feed(
            decoder,
            listOf(
                "event: a", "data: one", "",
                "event: b", "data: two", "",
            ),
        )
        assertEquals(2, events.size)
        assertEquals("a" to "one", events[0].event to events[0].data)
        assertEquals("b" to "two", events[1].event to events[1].data)
    }

    @Test
    fun `id and retry fields are ignored without becoming data`() {
        val decoder = SseDecoder()
        val events = feed(decoder, listOf("id: 42", "retry: 3000", "data: payload", ""))
        assertEquals(1, events.size)
        assertEquals("payload", events[0].data)
    }
}
