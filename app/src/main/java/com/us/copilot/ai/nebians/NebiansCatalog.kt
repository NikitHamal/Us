package com.us.copilot.ai.nebians

/**
 * Device-native port of the Nebians LLM registry (`api/llm/registry.py`).
 *
 * Only providers that can be called directly from the phone are listed here —
 * keyless guest scrapers with plain POST endpoints plus official APIs the user
 * can bring a key for. Server-only reversals (chat.qwen.ai WAF sessions,
 * QwenCloud umid tokens, LongCat H5guard signing, GeminiWeb cookie scraping)
 * are deliberately excluded: they need a Python backend with TLS
 * impersonation and cannot run reliably on device. TryingOpen already serves
 * the same Qwen model family from the phone with no key.
 */
enum class NebiansWireFormat {
    OPENAI,
    ANTHROPIC,
    GEMINI,
    TRYING_OPEN,
    K2THINK,
    POOLSIDE,
    MOTIF,
    YQCLOUD,
    CHATJIMMY,
}

/** One selectable model with the capabilities the UI needs to adapt. */
data class NebiansModel(
    val id: String,
    val label: String,
    val note: String = "",
    val vision: Boolean = false,
    val thinking: Boolean = false,
    val fileUpload: Boolean = false,
    val webSearch: Boolean = false,
    val contextWindow: Int = 128_000,
)

/** One provider entry: endpoint, auth, models and what the UI may offer. */
data class NebiansProvider(
    val slug: String,
    val label: String,
    val format: NebiansWireFormat,
    val baseUrl: String,
    val defaultModel: String,
    val models: List<NebiansModel>,
    val contextWindow: Int = 128_000,
    val maxOutputTokens: Int = 4_000,
    /** True when the user must paste a key (official APIs). False = free. */
    val keyRequired: Boolean = false,
    /** Key sent when the user has none (keyless pools). Empty = no header. */
    val defaultKey: String = "",
    val extraHeaders: Map<String, String> = emptyMap(),
    val freeNote: String = "",
    /** Which reasoning controls the settings UI should show for this provider. */
    val reasoning: ReasoningSupport = ReasoningSupport.NONE,
    /** True when the provider accepts file attachments from the phone. */
    val supportsFiles: Boolean = false,
)

enum class ReasoningSupport {
    /** No reasoning controls (fixed upstream behaviour). */
    NONE,

    /** TryingOpen-style effort selector: quick / balanced / deep. */
    EFFORT,

    /** Temperature + max tokens sliders for official-style APIs. */
    TEMPERATURE,
}

private fun m(
    id: String,
    label: String,
    note: String = "",
    vision: Boolean = false,
    thinking: Boolean = false,
    fileUpload: Boolean = false,
    webSearch: Boolean = false,
    contextWindow: Int = 128_000,
) = NebiansModel(id, label, note, vision, thinking, fileUpload, webSearch, contextWindow)

object NebiansCatalog {

    val providers: List<NebiansProvider> = listOf(
        NebiansProvider(
            slug = "tryingopen",
            label = "TryingOpen (free · 16 models)",
            format = NebiansWireFormat.TRYING_OPEN,
            baseUrl = "https://www.tryingopen.com/api/open",
            defaultModel = "qwen/qwen3.8-27b",
            models = listOf(
                m("qwen/qwen3.8-27b", "Qwen3.8 27B", "Default · vision · 262k", vision = true, thinking = true, fileUpload = true, contextWindow = 262_000),
                m("qwen/qwen3.6-27b", "Qwen3.6 27B", "Vision · 262k", vision = true, thinking = true, fileUpload = true, contextWindow = 262_000),
                m("qwen/qwen3.8-2.4t-a95b", "Qwen3.8 2.4T", "Largest Qwen · 1M", thinking = true, fileUpload = true, contextWindow = 1_000_000),
                m("nvidia/nemotron-3.5-lightning", "Nemotron 3.5 Lightning", "NVIDIA · fast · 1M", thinking = true, contextWindow = 1_000_000),
                m("z-ai/glm-5.3", "GLM 5.3", "Z.ai · reasoning · 1M", thinking = true, contextWindow = 1_000_000),
                m("z-ai/glm-5.2", "GLM 5.2", "Z.ai · multi-step · 1M", thinking = true, contextWindow = 1_000_000),
                m("moonshotai/kimi-k3", "Kimi K3", "Moonshot · vision · 1M", vision = true, thinking = true, fileUpload = true, contextWindow = 1_000_000),
                m("minimax/minimax-m3", "MiniMax M3", "Multimodal · 1M", vision = true, thinking = true, fileUpload = true, contextWindow = 1_000_000),
                m("deepseek/deepseek-v4-flash-0731", "DeepSeek V4 Flash", "Coding · 1M", thinking = true, contextWindow = 1_000_000),
                m("deepseek/deepseek-v4-pro-0813", "DeepSeek V4 Pro", "Full V4 · 1M", thinking = true, contextWindow = 1_000_000),
                m("google/gemma-4-31b-it", "Gemma 4 31B", "Google · vision · 262k", vision = true, thinking = true, fileUpload = true, contextWindow = 262_000),
                m("google/gemma-4-26b-a4b-it", "Gemma 4 26B", "Google · fast · 262k", vision = true, thinking = true, fileUpload = true, contextWindow = 262_000),
                m("mistralai/mistral-small-2603", "Mistral Small 4", "Vision · cheap · 262k", vision = true, thinking = true, fileUpload = true, contextWindow = 262_000),
                m("meta/muse-glimmer-30b", "Muse Glimmer 30B", "Meta · vision · 131k", vision = true, thinking = true, fileUpload = true, contextWindow = 131_000),
                m("thinkingmachines/inkling-small", "Inkling Small", "Quick multimodal · 524k", vision = true, thinking = true, fileUpload = true, contextWindow = 524_000),
                m("thinkingmachines/inkling", "Inkling", "Flagship reasoning · 1M", vision = true, thinking = true, fileUpload = true, contextWindow = 1_000_000),
            ),
            contextWindow = 1_000_000,
            maxOutputTokens = 8_000,
            freeNote = "Free, no key · quick / balanced / deep reasoning · images + files",
            reasoning = ReasoningSupport.EFFORT,
            supportsFiles = true,
        ),
        NebiansProvider(
            slug = "llm7",
            label = "LLM7 (free · anonymous)",
            format = NebiansWireFormat.OPENAI,
            baseUrl = "https://api.llm7.io/v1",
            defaultModel = "default",
            models = listOf(
                m("default", "Default (auto)", "Rotating capable model"),
                m("fast", "Fast", "Low-latency tier"),
                m("pro", "Pro", "Strongest tier"),
            ),
            maxOutputTokens = 4_000,
            defaultKey = "unused",
            freeNote = "Anonymous: 10/min, 60/hr — no key needed",
            reasoning = ReasoningSupport.TEMPERATURE,
        ),
        NebiansProvider(
            slug = "kilo",
            label = "Kilo Gateway (free :free models)",
            format = NebiansWireFormat.OPENAI,
            baseUrl = "https://api.kilo.ai/api/gateway",
            defaultModel = "stepfun/step-3.7-flash:free",
            models = listOf(
                m("stepfun/step-3.7-flash:free", "Step 3.7 Flash", "Fast reasoning", thinking = true),
                m("nvidia/nemotron-3-ultra-550b-a55b:free", "Nemotron 3 Ultra 550B", thinking = true),
                m("openrouter/free", "OpenRouter Free", "Best available free model"),
                m("kilo-auto/free", "Kilo Auto Free", "Auto-routed free pool"),
                m("poolside/laguna-s-2.1:free", "Laguna S 2.1", "128K context"),
                m("tencent/hy3:free", "Tencent Hy3"),
            ),
            maxOutputTokens = 4_000,
            freeNote = "Anonymous: 200/hr per IP — no key needed",
            reasoning = ReasoningSupport.TEMPERATURE,
        ),
        NebiansProvider(
            slug = "zen",
            label = "OpenCode Zen (free -free models)",
            format = NebiansWireFormat.OPENAI,
            baseUrl = "https://opencode.ai/zen/v1",
            defaultModel = "laguna-s-2.1-free",
            models = listOf(
                m("laguna-s-2.1-free", "Laguna S 2.1", "Clean answers · 128K"),
                m("mimo-v2.5-free", "MiMo V2.5"),
                m("nemotron-3-ultra-free", "Nemotron 3 Ultra", thinking = true),
                m("nemotron-3.5-lightning-free", "Nemotron 3.5 Lightning", thinking = true),
                m("big-pickle", "Big Pickle"),
                m("ling-3.0-flash-fin-free", "Ling 3.0 Flash"),
                m("deepseek-v4-flash-free", "DeepSeek V4 Flash", "Saturates often — has fallbacks", thinking = true),
            ),
            maxOutputTokens = 4_000,
            defaultKey = "public",
            extraHeaders = mapOf("User-Agent" to "opencode/1.0"),
            freeNote = "Anonymous shared pool — retries advised",
            reasoning = ReasoningSupport.TEMPERATURE,
        ),
        NebiansProvider(
            slug = "k2think",
            label = "K2 Horizon (free · reasoning)",
            format = NebiansWireFormat.K2THINK,
            baseUrl = "https://chat.ifm.ai",
            defaultModel = "IFM/K2-Horizon-375B-A23B",
            models = listOf(
                m("IFM/K2-Horizon-375B-A23B", "K2 Horizon 375B", "Reasoning model · 128K", thinking = true, contextWindow = 128_000),
            ),
            contextWindow = 128_000,
            maxOutputTokens = 8_192,
            freeNote = "Free guest chat — no key needed",
        ),
        NebiansProvider(
            slug = "poolside",
            label = "Poolside (free · code)",
            format = NebiansWireFormat.POOLSIDE,
            baseUrl = "https://chat.poolside.ai",
            defaultModel = "laguna-s-2.1",
            models = listOf(
                m("laguna-s-2.1", "Laguna S 2.1", "128K · agent coding", webSearch = true),
                m("laguna-xs-2.1", "Laguna XS 2.1", "128K · fast", webSearch = true),
            ),
            contextWindow = 128_000,
            maxOutputTokens = 8_192,
            freeNote = "Free guest chat — no key needed",
        ),
        NebiansProvider(
            slug = "motiftech",
            label = "Motif (free)",
            format = NebiansWireFormat.MOTIF,
            baseUrl = "https://chat.motiftech.io",
            defaultModel = "motif-102b",
            models = listOf(
                m("motif-102b", "Motif 3", "Flagship · 128K", thinking = true, contextWindow = 128_000),
                m("motif-12-7b", "Motif 12.7B", "128K", contextWindow = 128_000),
                m("motif-12-7b-reasoning", "Motif 12.7B Reasoning", "Deep thinking · 128K", thinking = true, contextWindow = 128_000),
                m("motif-tiny", "Motif Tiny", "Fast"),
            ),
            contextWindow = 128_000,
            maxOutputTokens = 8_192,
            freeNote = "Free guest chat — no key needed",
        ),
        NebiansProvider(
            slug = "yqcloud",
            label = "Yqcloud (free · web search)",
            format = NebiansWireFormat.YQCLOUD,
            baseUrl = "https://chat9.yqcloud.top",
            defaultModel = "yqcloud-default",
            models = listOf(
                m("yqcloud-default", "Yqcloud Chat", "Free · web search", webSearch = true, contextWindow = 32_000),
            ),
            contextWindow = 32_000,
            maxOutputTokens = 4_000,
            freeNote = "Free, no login",
        ),
        NebiansProvider(
            slug = "chatjimmy",
            label = "ChatJimmy (free · Llama 3.1 8B)",
            format = NebiansWireFormat.CHATJIMMY,
            baseUrl = "https://chatjimmy.ai",
            defaultModel = "llama3.1-8B",
            models = listOf(
                m("llama3.1-8B", "Llama 3.1 8B", contextWindow = 32_000),
            ),
            contextWindow = 32_000,
            maxOutputTokens = 4_000,
            freeNote = "Free, no login",
        ),
        NebiansProvider(
            slug = "agnes",
            label = "Agnes (Sapiens AI)",
            format = NebiansWireFormat.OPENAI,
            baseUrl = "https://apihub.agnes-ai.com/v1",
            defaultModel = "agnes-2.0-flash",
            models = listOf(
                m("agnes-2.0-flash", "Agnes 2.0 Flash", "512K ctx · agents, coding, vision", vision = true, thinking = true, fileUpload = true, contextWindow = 512_000),
            ),
            contextWindow = 512_000,
            maxOutputTokens = 6_000,
            keyRequired = true,
            freeNote = "Free during launch ($0 / 1M tokens)",
            reasoning = ReasoningSupport.TEMPERATURE,
            supportsFiles = true,
        ),
        NebiansProvider(
            slug = "openai",
            label = "OpenAI (ChatGPT)",
            format = NebiansWireFormat.OPENAI,
            baseUrl = "https://api.openai.com/v1",
            defaultModel = "gpt-5.4-mini",
            models = listOf(
                m("gpt-5.4-mini", "GPT-5.4 Mini", "Fast + cheap, great for agents", vision = true, thinking = true, fileUpload = true, contextWindow = 400_000),
                m("gpt-5.4-nano", "GPT-5.4 Nano", "Cheapest, high volume", vision = true, fileUpload = true, contextWindow = 400_000),
                m("gpt-5.5", "GPT-5.5", "Previous flagship", vision = true, thinking = true, fileUpload = true, contextWindow = 400_000),
                m("gpt-5.6-terra", "GPT-5.6 Terra", "Balanced newest flagship", vision = true, thinking = true, fileUpload = true, contextWindow = 400_000),
                m("gpt-5.6-sol", "GPT-5.6 Sol", "Strongest reasoning", vision = true, thinking = true, fileUpload = true, contextWindow = 400_000),
                m("gpt-5.6-luna", "GPT-5.6 Luna", "Budget newest", vision = true, fileUpload = true, contextWindow = 400_000),
                m("gpt-5.2-pro", "GPT-5.2 Pro", "Extreme accuracy (slow)", vision = true, thinking = true, fileUpload = true, contextWindow = 400_000),
            ),
            contextWindow = 400_000,
            keyRequired = true,
            reasoning = ReasoningSupport.TEMPERATURE,
            supportsFiles = true,
        ),
        NebiansProvider(
            slug = "anthropic",
            label = "Anthropic (Claude)",
            format = NebiansWireFormat.ANTHROPIC,
            baseUrl = "https://api.anthropic.com",
            defaultModel = "claude-sonnet-5",
            models = listOf(
                m("claude-sonnet-5", "Claude Sonnet 5", "Best speed/intelligence mix", vision = true, thinking = true, contextWindow = 1_000_000),
                m("claude-opus-4-8", "Claude Opus 4.8", "Complex agentic coding", vision = true, thinking = true, contextWindow = 1_000_000),
                m("claude-haiku-4-5", "Claude Haiku 4.5", "Fastest", vision = true, contextWindow = 1_000_000),
                m("claude-fable-5", "Claude Fable 5", "Long-running agents", vision = true, thinking = true, contextWindow = 1_000_000),
            ),
            contextWindow = 1_000_000,
            keyRequired = true,
            reasoning = ReasoningSupport.TEMPERATURE,
        ),
        NebiansProvider(
            slug = "gemini",
            label = "Google Gemini",
            format = NebiansWireFormat.GEMINI,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            defaultModel = "gemini-3.5-flash",
            models = listOf(
                m("gemini-3.5-flash", "Gemini 3.5 Flash", "Newest fast model", vision = true, thinking = true, contextWindow = 1_000_000),
                m("gemini-3-flash-preview", "Gemini 3 Flash (Preview)", vision = true, thinking = true, contextWindow = 1_000_000),
                m("gemini-2.5-flash", "Gemini 2.5 Flash", "Stable", vision = true, contextWindow = 1_000_000),
                m("gemini-2.5-pro", "Gemini 2.5 Pro", "Stable, strongest 2.5", vision = true, thinking = true, contextWindow = 1_000_000),
            ),
            contextWindow = 1_000_000,
            keyRequired = true,
            reasoning = ReasoningSupport.TEMPERATURE,
        ),
        NebiansProvider(
            slug = "deepseek",
            label = "DeepSeek",
            format = NebiansWireFormat.OPENAI,
            baseUrl = "https://api.deepseek.com/v1",
            defaultModel = "deepseek-chat",
            models = listOf(
                m("deepseek-chat", "DeepSeek Chat (V3)", "Great coder, cheap", thinking = true),
                m("deepseek-reasoner", "DeepSeek Reasoner (R1)", "Chain-of-thought", thinking = true),
            ),
            contextWindow = 128_000,
            keyRequired = true,
            reasoning = ReasoningSupport.TEMPERATURE,
        ),
        NebiansProvider(
            slug = "custom",
            label = "Custom endpoint",
            format = NebiansWireFormat.OPENAI,
            baseUrl = "",
            defaultModel = "",
            models = emptyList(),
            keyRequired = false,
            freeNote = "Any OpenAI-compatible URL",
            reasoning = ReasoningSupport.TEMPERATURE,
            supportsFiles = true,
        ),
    )

    fun find(slug: String): NebiansProvider? =
        providers.firstOrNull { it.slug == slug.trim().lowercase() }

    fun modelFor(slug: String, modelId: String): NebiansModel? {
        val provider = find(slug) ?: return null
        if (modelId.isBlank()) return null
        return provider.models.firstOrNull { it.id.equals(modelId, ignoreCase = true) }
    }

    /** Resolves the effective model id, falling back to the provider default. */
    fun effectiveModel(slug: String, modelId: String): String {
        val provider = find(slug) ?: return modelId
        if (modelId.isNotBlank()) return modelId
        return provider.defaultModel
    }

    /** Providers that work with zero configuration — the offline-first default. */
    fun freeProviders(): List<NebiansProvider> = providers.filter { !it.keyRequired }
}
