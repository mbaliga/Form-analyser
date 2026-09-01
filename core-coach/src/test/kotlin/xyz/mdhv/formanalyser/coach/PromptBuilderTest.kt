package xyz.mdhv.formanalyser.coach

import xyz.mdhv.formanalyser.wellness.ReadinessLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PromptBuilderTest {

    private fun facts() = CoachFacts(
        readinessLevel = ReadinessLevel.READY,
        acwr = 1.1,
        moodNote = "SECRET-MOOD-CONTENT",
        cyclePhase = "SECRET-CYCLE",
        medications = listOf("SECRET-MED"),
    )

    @Test
    fun `prompt contains only redacted content for cloud`() {
        val redacted = Redaction.redact(facts(), RedactionPolicy(CoachDestination.CLOUD))
        val msgs = PromptBuilder.build(CoachIntent.READINESS_EXPLAINER, redacted)
        val whole = msgs.joinToString("\n") { it.content }
        assertFalse(whole.contains("SECRET-MOOD-CONTENT"))
        assertFalse(whole.contains("SECRET-CYCLE"))
        assertFalse(whole.contains("SECRET-MED"))
        assertTrue(whole.contains("Readiness: READY"))
    }

    @Test
    fun `prompt has a system and a user message`() {
        val redacted = Redaction.redact(facts(), RedactionPolicy(CoachDestination.CLOUD))
        val msgs = PromptBuilder.build(CoachIntent.SESSION_DEBRIEF, redacted)
        assertEquals(2, msgs.size)
        assertEquals(MessageRole.SYSTEM, msgs[0].role)
        assertEquals(MessageRole.USER, msgs[1].role)
        assertTrue(msgs[1].content.contains("ATHLETE FACTS:"))
    }

    @Test
    fun `every intent produces a distinct system task`() {
        val redacted = Redaction.redact(facts(), RedactionPolicy(CoachDestination.CLOUD))
        val systems = CoachIntent.values().map { PromptBuilder.build(it, redacted)[0].content }
        assertEquals(systems.size, systems.toSet().size, "each intent should yield a distinct system prompt")
    }

    @Test
    fun `system prompt carries guardrails`() {
        val redacted = Redaction.redact(facts(), RedactionPolicy(CoachDestination.CLOUD))
        val sys = PromptBuilder.build(CoachIntent.INJURY_AWARE_ADVICE, redacted)[0].content
        assertTrue(sys.contains("not a medical professional"))
        assertTrue(sys.contains("Ground every statement"))
    }
}
