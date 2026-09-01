package xyz.mdhv.formanalyser.coach

/** Chat roles the prompt builder emits. */
enum class MessageRole { SYSTEM, USER, ASSISTANT }

/** One turn in a completion request/response. */
data class ChatMessage(val role: MessageRole, val content: String)

/**
 * A provider-agnostic completion request. Carries the chosen [CoachModel] so the Android adapter
 * knows which provider/endpoint to route to and whether a BYOK key is required.
 */
data class CompletionRequest(
    val model: CoachModel,
    val messages: List<ChatMessage>,
    val maxTokens: Int = 1024,
    val temperature: Double = 0.2,
)

/** A successful completion payload. */
data class CompletionResponse(
    val text: String,
    val modelId: String,
    val stopReason: String? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
)

/** Categories of failure the Android adapter maps every provider's errors onto. */
enum class LlmErrorKind {
    MISSING_API_KEY,
    RATE_LIMITED,
    NETWORK,
    INVALID_REQUEST,
    CONTENT_FILTERED,
    PROVIDER_ERROR,
    UNSUPPORTED,
}

data class LlmError(val kind: LlmErrorKind, val message: String)

/** Result of a completion — success or a typed error. No exceptions cross the seam. */
sealed interface CompletionResult {
    data class Success(val response: CompletionResponse) : CompletionResult
    data class Failure(val error: LlmError) : CompletionResult
}

/**
 * The one seam the Android layer implements per provider (Anthropic/OpenAI/Google BYOK adapters,
 * an on-device runtime). Deliberately synchronous and coroutine-free: the core stays pure JVM and
 * unit-testable; the Android adapter runs [complete] off the main thread and owns the API key.
 */
interface LlmClient {
    /** Which model this client can serve. Callers match it against [CompletionRequest.model]. */
    fun supports(model: CoachModel): Boolean

    fun complete(request: CompletionRequest): CompletionResult
}
