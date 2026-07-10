package xyz.mdhv.formanalyser.coach

/**
 * The structured coaching tasks the app offers. Deliberately closed — there is NO open free-chat
 * intent. Every prompt is a data-grounded task over the athlete's own (redacted) facts.
 */
enum class CoachIntent {
    SESSION_DEBRIEF,
    READINESS_EXPLAINER,
    INJURY_AWARE_ADVICE,
    FORM_CUE,
    TUNING_SUGGESTION,
}

/**
 * Assembles a system+user prompt from a [CoachIntent] and the REDACTED facts only. The user turn
 * is built from [RedactionResult.factsheet], which is composed solely of kept lines — so nothing a
 * redaction withheld can appear in the prompt.
 */
object PromptBuilder {

    private const val GUARDRAILS =
        "You are Crocodyl's archery coach. Ground every statement in the ATHLETE FACTS below and " +
            "say so when the facts are insufficient. Do not invent numbers. You are not a medical " +
            "professional: for pain or injury, advise load management and seeing a clinician, never " +
            "diagnosis or treatment. Be concise and specific."

    private fun systemFor(intent: CoachIntent): String {
        val task = when (intent) {
            CoachIntent.SESSION_DEBRIEF ->
                "Task: debrief the athlete's recent training — what the load and form facts show and one focus for next session."
            CoachIntent.READINESS_EXPLAINER ->
                "Task: explain, in plain language, why today's readiness is what it is, citing the readiness reasons."
            CoachIntent.INJURY_AWARE_ADVICE ->
                "Task: give training advice that respects the athlete's active injuries — protect the affected regions."
            CoachIntent.FORM_CUE ->
                "Task: offer one or two concrete technique cues grounded in the form-feature facts."
            CoachIntent.TUNING_SUGGESTION ->
                "Task: suggest equipment-tuning next steps grounded in the rig facts; keep changes small and testable."
        }
        return "$GUARDRAILS\n$task"
    }

    private fun userQuestionFor(intent: CoachIntent): String = when (intent) {
        CoachIntent.SESSION_DEBRIEF -> "Debrief my recent sessions."
        CoachIntent.READINESS_EXPLAINER -> "Why is my readiness where it is today?"
        CoachIntent.INJURY_AWARE_ADVICE -> "What should I train while protecting my injuries?"
        CoachIntent.FORM_CUE -> "Give me a form cue to work on."
        CoachIntent.TUNING_SUGGESTION -> "What tuning should I try next?"
    }

    /**
     * Build the message list for [intent] over already-[redacted] facts. The user turn embeds the
     * redacted factsheet verbatim; the withheld facts never appear.
     */
    fun build(intent: CoachIntent, redacted: RedactionResult): List<ChatMessage> {
        val user = buildString {
            append("ATHLETE FACTS:\n")
            append(redacted.factsheet)
            append("\n\n")
            append(userQuestionFor(intent))
        }
        return listOf(
            ChatMessage(MessageRole.SYSTEM, systemFor(intent)),
            ChatMessage(MessageRole.USER, user),
        )
    }
}
