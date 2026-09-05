package com.us.copilot.ai.cloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Wire types for OpenAI-compatible tool calling.
 *
 * Kept separate from [ChatRequest] because the agent path needs a message shape the simple
 * single-shot path does not: assistant turns carrying `tool_calls`, and `tool` role replies
 * carrying results keyed by call id.
 */

@Serializable
data class AgentMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCallDto>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null,
)

@Serializable
data class ToolCallDto(
    val id: String = "",
    val type: String = "function",
    val function: FunctionCallDto,
)

@Serializable
data class FunctionCallDto(
    val name: String = "",
    /** JSON-encoded string, per the OpenAI spec — not a nested object. */
    val arguments: String = "",
)

@Serializable
data class ToolDefinitionDto(
    val type: String = "function",
    val function: FunctionSchemaDto,
)

@Serializable
data class FunctionSchemaDto(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

@Serializable
data class AgentRequest(
    val model: String,
    val messages: List<AgentMessage>,
    val tools: List<ToolDefinitionDto>? = null,
    @SerialName("tool_choice") val toolChoice: String? = "auto",
    val temperature: Double = 0.5,
    @SerialName("max_tokens") val maxTokens: Int = 1200,
)

@Serializable
data class AgentResponse(val choices: List<AgentChoice> = emptyList()) {
    val message: AgentMessage? get() = choices.firstOrNull()?.message
    val finishReason: String? get() = choices.firstOrNull()?.finishReason
}

@Serializable
data class AgentChoice(
    val message: AgentMessage,
    @SerialName("finish_reason") val finishReason: String? = null,
)
