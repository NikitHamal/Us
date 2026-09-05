package com.us.copilot.ai.nebians

import com.us.copilot.domain.repository.NebiansConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes one chat turn to the right device-native client.
 *
 * The caller never touches clients directly: it resolves the provider from
 * [NebiansCatalog], the model from config, and the credentials from encrypted
 * storage, then this dispatcher picks the wire format. Adding a new Nebians
 * backend later means one catalog entry plus one `when` branch here.
 */
@Singleton
class NebiansDispatcher @Inject constructor(
    private val official: NebiansOfficialClient,
    private val tryingOpen: TryingOpenClient,
    private val guests: GuestScraperClients,
) {

    suspend fun chat(
        config: NebiansConfig,
        messages: List<NebiansMessage>,
        attachments: List<NebiansAttachment> = emptyList(),
        maxTokensOverride: Int? = null,
    ): NebiansChatResult {
        val provider = NebiansCatalog.find(config.providerSlug)
            ?: throw NebiansException("Unknown provider '${config.providerSlug}'", retryable = false)
        val model = NebiansCatalog.effectiveModel(provider.slug, config.modelId)
        return when (provider.format) {
            NebiansWireFormat.OPENAI,
            NebiansWireFormat.ANTHROPIC,
            NebiansWireFormat.GEMINI,
            -> official.chat(
                provider = provider,
                model = model,
                messages = messages,
                apiKey = config.apiKey,
                baseUrlOverride = if (provider.slug == "custom") config.baseUrlOverride else "",
                attachments = if (provider.supportsFiles) attachments else emptyList(),
                temperature = config.temperature,
                maxTokens = maxTokensOverride ?: config.maxTokens,
            )
            NebiansWireFormat.TRYING_OPEN -> tryingOpen.chat(
                provider = provider,
                model = model,
                messages = messages,
                effort = config.effort,
                attachments = if (provider.supportsFiles) attachments else emptyList(),
            )
            NebiansWireFormat.K2THINK,
            NebiansWireFormat.POOLSIDE,
            NebiansWireFormat.MOTIF,
            NebiansWireFormat.YQCLOUD,
            NebiansWireFormat.CHATJIMMY,
            -> guests.chat(provider = provider, model = model, messages = messages)
        }
    }

    /** Single user turn with an optional system prompt. */
    suspend fun ask(
        config: NebiansConfig,
        system: String,
        user: String,
        history: List<NebiansMessage> = emptyList(),
        attachments: List<NebiansAttachment> = emptyList(),
        maxTokensOverride: Int? = null,
    ): NebiansChatResult {
        val messages = buildList {
            if (system.isNotBlank()) add(NebiansMessage("system", system))
            addAll(history)
            add(NebiansMessage("user", user))
        }
        return chat(config, messages, attachments, maxTokensOverride)
    }
}
