package xyz.mdhv.formanalyser.coach

/**
 * Where a model runs / who is billed.
 *
 * ANTHROPIC/OPENAI/GOOGLE/DEEPSEEK are hosted clouds (bring-your-own-key). ON_DEVICE runs locally
 * on the athlete's phone. OTHER is the extensibility seam for a provider the Android layer wires up
 * later (a self-hosted endpoint, another BYOK cloud) without touching this module.
 */
enum class Provider {
    ANTHROPIC,
    OPENAI,
    GOOGLE,
    DEEPSEEK,
    ON_DEVICE,
    OTHER,
}

/** Execution locus: a hosted CLOUD endpoint vs a local ON_DEVICE runtime. */
enum class ModelKind {
    CLOUD,
    ON_DEVICE,
}

/**
 * A single allowed coaching model.
 *
 * The product law: there is **no free hosted-chat tier**. Every model is either a CLOUD model the
 * athlete reaches with *their own* API key ([requiresByok] = true) or an ON_DEVICE model that runs
 * locally ([requiresByok] = false). A CLOUD model that did not require a key would be a free hosted
 * tier — [isFreeHostedChat] flags exactly that shape so a test can assert the registry has none.
 */
data class CoachModel(
    val id: String,
    val provider: Provider,
    val displayName: String,
    val kind: ModelKind,
    val requiresByok: Boolean,
    val approxContextTokens: Int,
) {
    /**
     * True iff this were a free, hosted chat tier — a CLOUD model needing no key. Must be false.
     */
    val isFreeHostedChat: Boolean
        get() = kind == ModelKind.CLOUD && !requiresByok
}

/**
 * The allow-list of models the coach may drive. Provider-agnostic: this module never speaks HTTP;
 * the Android layer selects an entry and hands it to an [LlmClient]. Current-generation entries as
 * of this build — additive as new models ship.
 */
object ModelRegistry {
    val models =
        listOf(
            // ── Anthropic (BYOK cloud) ──────────────────────────────────────────
            CoachModel(
                "claude-opus-4-8",
                Provider.ANTHROPIC,
                "Claude Opus 4.8",
                ModelKind.CLOUD,
                true,
                200_000,
            ),
            CoachModel(
                "claude-sonnet-5",
                Provider.ANTHROPIC,
                "Claude Sonnet 5",
                ModelKind.CLOUD,
                true,
                200_000,
            ),
            CoachModel(
                "claude-haiku-4-5",
                Provider.ANTHROPIC,
                "Claude Haiku 4.5",
                ModelKind.CLOUD,
                true,
                200_000,
            ),
            CoachModel(
                "claude-fable-5",
                Provider.ANTHROPIC,
                "Claude Fable 5",
                ModelKind.CLOUD,
                true,
                200_000,
            ),
            // ── OpenAI (BYOK cloud) ─────────────────────────────────────────────
            CoachModel("gpt-5", Provider.OPENAI, "GPT-5", ModelKind.CLOUD, true, 400_000),
            CoachModel("gpt-5-mini", Provider.OPENAI, "GPT-5 mini", ModelKind.CLOUD, true, 128_000),
            // ── Google (BYOK cloud) ─────────────────────────────────────────────
            CoachModel(
                "gemini-2.5-pro",
                Provider.GOOGLE,
                "Gemini 2.5 Pro",
                ModelKind.CLOUD,
                true,
                1_000_000,
            ),
            CoachModel(
                "gemini-2.5-flash",
                Provider.GOOGLE,
                "Gemini 2.5 Flash",
                ModelKind.CLOUD,
                true,
                1_000_000,
            ),
            // ── DeepSeek (BYOK cloud) ───────────────────────────────────────────
            CoachModel(
                "deepseek-v4-flash",
                Provider.DEEPSEEK,
                "DeepSeek V4 Flash",
                ModelKind.CLOUD,
                true,
                128_000,
            ),
            CoachModel(
                "deepseek-v4-pro",
                Provider.DEEPSEEK,
                "DeepSeek V4 Pro",
                ModelKind.CLOUD,
                true,
                128_000,
            ),
            // ── On-device (local, no key) ───────────────────────────────────────
            CoachModel(
                "gemma-3n-e4b",
                Provider.ON_DEVICE,
                "Gemma 3n E4B (on-device)",
                ModelKind.ON_DEVICE,
                false,
                8_192,
            ),
            CoachModel(
                "gemma-3n-e2b",
                Provider.ON_DEVICE,
                "Gemma 3n E2B (on-device)",
                ModelKind.ON_DEVICE,
                false,
                4_096,
            ),
        )
    private val byId = models.associateBy { it.id }

    fun byId(id: String) = byId[id]

    fun byProvider(provider: Provider) = models.filter { it.provider == provider }

    /** Cloud models — all bring-your-own-key. */
    fun cloudModels() = models.filter { it.kind == ModelKind.CLOUD }

    /** Local models — no key, safe destination for richer facts. */
    fun onDeviceModels() = models.filter { it.kind == ModelKind.ON_DEVICE }

    /**
     * The invariant, exposed for callers as well as tests: there is no free hosted-chat tier — no
     * CLOUD model is reachable without the athlete's own key.
     */
    fun hasFreeHostedTier() = models.any { it.isFreeHostedChat }
}
